/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.lib.tile;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.explosion.Explosion;

// STUB(R.Chen): the Forge capability provider model (Capability<T>, hasCapability/getCapability,
// IItemHandlerModifiable) has no direct Fabric equivalent. Capability exposure now happens via the
// Transfer API lookups registered in the owning Block / ModInitializer; subclasses override the
// relevant *Storage getters instead of overriding getCapability here. See LegacyCapabilityStubs.

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.IPlayerOwned;

import buildcraft.lib.cache.CachedChunk;
import buildcraft.lib.cache.IChunkCache;
import buildcraft.lib.cache.ITileCache;
import buildcraft.lib.cache.TileCacheRet;
import buildcraft.lib.cache.TileCacheType;
import buildcraft.lib.cap.CapabilityHelper;
import buildcraft.lib.client.render.DetachedRenderer.IDetachedRenderer;
import buildcraft.lib.debug.BCAdvDebugging;
import buildcraft.lib.debug.IAdvDebugTarget;
import buildcraft.lib.delta.DeltaManager;
import buildcraft.lib.delta.DeltaManager.EnumDeltaMessage;
import buildcraft.lib.fluid.TankManager;
import buildcraft.lib.migrate.BCVersion;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.ChunkUtil;
import buildcraft.lib.misc.FakePlayerProvider;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.PermissionUtil;
import buildcraft.lib.misc.PermissionUtil.PermissionBlock;
import buildcraft.lib.misc.StringUtilBC;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.IPayloadReceiver;
import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.net.MessageUpdateTile;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.item.ItemHandlerManager;


public abstract class TileBC_Neptune extends BlockEntity implements IPayloadReceiver, IAdvDebugTarget, IPlayerOwned {
    public static final boolean DEBUG = BCDebugging.shouldDebugLog("lib.tile");

    protected static final IdAllocator IDS = new IdAllocator("tile");

    /** Used for sending all data used for rendering the tile on a client. This does not include items, power, stages,
     * etc (Unless some are shown in the world) */
    public static final int NET_RENDER_DATA = IDS.allocId("RENDER_DATA");
    /** Used for sending all data in the GUI. Basically what has been omitted from {@link #NET_RENDER_DATA} that is
     * shown in the GUI. */
    public static final int NET_GUI_DATA = IDS.allocId("GUI_DATA");
    /** Used for sending the data that would normally be sent with a screen handler's change detection. Note that
     * if no bytes are written then the update message won't be sent. You should detect if any changes have been made to
     * the gui since the last tick, so you don't resend duplicate information if nothing has changed by the next
     * tick. */
    public static final int NET_GUI_TICK = IDS.allocId("GUI_TICK");

    public static final int NET_REN_DELTA_SINGLE = IDS.allocId("REN_DELTA_SINGLE");
    public static final int NET_REN_DELTA_CLEAR = IDS.allocId("REN_DELTA_CLEAR");
    public static final int NET_GUI_DELTA_SINGLE = IDS.allocId("GUI_DELTA_SINGLE");
    public static final int NET_GUI_DELTA_CLEAR = IDS.allocId("GUI_DELTA_CLEAR");

    /** Used for detailed debugging for inspecting every part of the current tile. For example, tanks use this to
     * display which other tanks makeup the whole structure. */
    public static final int NET_ADV_DEBUG = IDS.allocId("DEBUG_DATA");
    public static final int NET_ADV_DEBUG_DISABLE = IDS.allocId("DEBUG_DISABLE");

    /** Used to tell the client to redraw the block. */
    public static final int NET_REDRAW = IDS.allocId("REDRAW");

    protected final CapabilityHelper caps = new CapabilityHelper();
    protected final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);
    protected final TankManager tankManager = new TankManager();
    private boolean contentsDropped;

    /** Handles all of the players that are currently using this tile (have a GUI open) */
    private final Set<PlayerEntity> usingPlayers = Sets.newIdentityHashSet();
    private GameProfile owner;

    private final IChunkCache chunkCache = new CachedChunk(this);
    private final ITileCache tileCache = TileCacheType.NEIGHBOUR_CACHE.create(this);

    protected final DeltaManager deltaManager = new DeltaManager((gui, type, writer) -> {
        final int id;
        if (type == EnumDeltaMessage.ADD_SINGLE) {
            id = gui ? NET_GUI_DELTA_SINGLE : NET_REN_DELTA_SINGLE;
        } else if (type == EnumDeltaMessage.SET_VALUE) {
            id = gui ? NET_GUI_DELTA_CLEAR : NET_REN_DELTA_CLEAR;
        } else {
            throw new IllegalArgumentException("Unknown delta message type " + type);
        }
        if (gui) {
            createAndSendGuiMessage(id, writer);
        } else {
            createAndSendMessage(id, writer);
        }
    });

    // TODO(R.Chen): Yarn's BlockEntity has no no-arg constructor — it requires
    // (BlockEntityType<?>, BlockPos, BlockState). This signature change cascades into EVERY subclass,
    // each of which must pass its registered BlockEntityType. Subclasses are migrated in later passes.
    public TileBC_Neptune(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        caps.addProvider(itemManager);
    }

    // ##################################################
    //
    // Local blockstate + tile entity getters
    //
    // Some of these (may) use a cached version
    // at some point in the future, or are already
    // based on a cache.
    //
    // ##################################################

    public final BlockState getCurrentState() {
        return BlockUtil.getBlockState(world, pos);
    }

    @Nullable
    public final BlockState getCurrentStateForBlock(Block expectedBlock) {
        BlockState state = getCurrentState();
        if (state.getBlock() == expectedBlock) {
            return state;
        }
        return null;
    }

    public final BlockState getNeighbourState(Direction offset) {
        // In the future it is plausible that we might cache block states here.
        // However, until that is implemented, just call the world directly.
        return getOffsetState(offset.getVector());
    }

    /** @param offset The position of the {@link BlockState}, <i>relative</i> to this {@link #getPos()}. */
    public final BlockState getOffsetState(Vec3i offset) {
        return getLocalState(pos.add(offset));
    }

    /** @param pos The <i>absolute</i> position of the {@link BlockState} . */
    public final BlockState getLocalState(BlockPos pos) {
        if (DEBUG && !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            BCLog.logger.warn(
                "[lib.tile] Ghost-loading block at " + StringUtilBC.blockPosToString(pos) + " (from " + StringUtilBC
                    .blockPosToString(getPos()) + ")"
            );
        }
        return BlockUtil.getBlockState(world, pos, true);
    }

    public final BlockEntity getNeighbourTile(Direction offset) {
        TileCacheRet cached = tileCache.getTile(offset);
        if (cached != null) {
            return cached.tile;
        }
        if (DEBUG && !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            BCLog.logger.warn(
                "[lib.tile] Ghost-loading tile at " + StringUtilBC.blockPosToString(pos) + " (from " + StringUtilBC
                    .blockPosToString(getPos()) + ")"
            );
        }
        return BlockUtil.getBlockEntity(getWorld(), getPos().offset(offset), true);
    }

    /** @param offset The position of the {@link BlockEntity} to retrieve, <i>relative</i> to this
     *            {@link #getPos()} . */
    public final BlockEntity getOffsetTile(Vec3i offset) {
        return getLocalTile(pos.add(offset));
    }

    /** @param pos The <i>absolute</i> position of the {@link BlockEntity} . */
    public final BlockEntity getLocalTile(BlockPos pos) {
        TileCacheRet cached = tileCache.getTile(pos);
        if (cached != null) {
            return cached.tile;
        }
        if (DEBUG && !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            BCLog.logger.warn(
                "[lib.tile] Ghost-loading tile at " + StringUtilBC.blockPosToString(pos) + " (from " + StringUtilBC
                    .blockPosToString(getPos()) + ")"
            );
        }
        return BlockUtil.getBlockEntity(world, pos, true);
    }

    public final WorldChunk getContainingChunk() {
        return chunkCache.getChunk(getPos());
    }

    public final WorldChunk getChunk(BlockPos pos) {
        WorldChunk chunk = chunkCache.getChunk(pos);
        if (chunk == null) {
            return ChunkUtil.getChunk(getWorld(), pos, true);
        }
        return chunk;
    }

    // ##################
    //
    // Misc overridables
    //
    // ##################

    /** @return The {@link IdAllocator} that allocates all ID's for this class, and its parent classes. All subclasses
     *         should override this if they allocate their own ids after calling
     *         {@link IdAllocator#makeChild(String)} */
    public IdAllocator getIdAllocator() {
        return IDS;
    }

    /** Checks to see if this tile can update. The base implementation only checks to see if it has a world. */
    public boolean cannotUpdate() {
        return !hasWorld();
    }

    // STUB(R.Chen): Forge's shouldRefresh(World, BlockPos, BlockState, BlockState) controlled whether a
    // BlockEntity survived a blockstate change. Fabric/vanilla has no such hook — the BlockEntity is kept as
    // long as the new block still has the same BlockEntityType. No replacement override is required here.

    /** Called whenever the block holding this tile is exploded. Called by the owning block's explosion handler. */
    public void onExplode(Explosion explosion) {

    }

    /** Called whenever the block is removed. Called by {@link #onExplode(Explosion)}, and the owning block's
     * onStateReplaced/onBreak hook. */
    public void onRemove() {
        if (world == null || world.isClient || contentsDropped) return;
        contentsDropped = true;
        DefaultedList<ItemStack> toDrop = DefaultedList.of();
        boolean drop = world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_TILE_DROPS);
        if (drop) addDrops(toDrop, 0);
        itemManager.clearForRemoval();
        tankManager.clearForRemoval();
        if (drop) InventoryUtil.dropAll(world, pos, toDrop);
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        chunkCache.invalidate();
        tileCache.invalidate();
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        chunkCache.invalidate();
        tileCache.invalidate();
    }

    /** Invoked after Fabric's block-entity load event, once its NBT has been applied. */
    public void onLoad() {
        chunkCache.invalidate();
        tileCache.invalidate();
    }

    /** Invoked by Fabric's block-entity unload event on the corresponding game thread. */
    public void onChunkUnload() {
        chunkCache.invalidate();
        tileCache.invalidate();
    }

    /** Called whenever {@link #onRemove()} is called (by default). */
    public void addDrops(DefaultedList<ItemStack> toDrop, int fortune) {
        itemManager.addDrops(toDrop);
        tankManager.addDrops(toDrop);
    }

    /** Cached automation views must stop working when their owning block leaves the world. */
    public final boolean isStorageAvailable() {
        return !contentsDropped && !isRemoved() && world != null
            && world.isChunkLoaded(pos) && world.getBlockEntity(pos) == this;
    }

    public void onPlacedBy(LivingEntity placer, ItemStack stack) {
        // Native placement contexts used by automation may have no living placer.
        if (placer == null) return;
        if (!placer.getWorld().isClient) {
            if (placer instanceof PlayerEntity) {
                PlayerEntity player = (PlayerEntity) placer;
                owner = player.getGameProfile();
                if (owner.getId() == null) {
                    // Basically everything relies on the UUID
                    throw new IllegalArgumentException("No UUID for owner! ( " + placer.getClass() + " " + placer + " -> " + owner + " )");
                }
            } else {
                throw new IllegalArgumentException("Not an PlayerEntity! (placer = " + placer + ")");
            }
        }
    }

    public void onPlayerOpen(PlayerEntity player) {
        if (owner == null || owner == FakePlayerProvider.NULL_PROFILE) {
            owner = player.getGameProfile();
            if (owner.getId() == null) {
                // Basically everything relies on the UUID
                throw new IllegalArgumentException("No UUID for owner! ( " + player.getClass() + " " + player + " -> " + owner + " )");
            }
        }
        sendNetworkUpdate(NET_GUI_DATA, player);
        usingPlayers.add(player);
    }

    public void onPlayerClose(PlayerEntity player) {
        usingPlayers.remove(player);
    }

    public boolean onActivated(PlayerEntity player, Hand hand, Direction facing, float hitX, float hitY,
        float hitZ) {
        if (!canInteractWith(player)) return false;
        return tankManager.onActivated(player, getPos(), hand);
    }

    public void onNeighbourBlockChanged(Block block, BlockPos nehighbour) {
        tileCache.invalidate();
    }

    // STUB(R.Chen): Forge hasCapability(Capability, Direction) / getCapability(Capability, Direction)
    // removed. Capability exposure is now via the Transfer API: register ItemStorage.SIDED /
    // FluidStorage.SIDED / EnergyStorage.SIDED lookups against this BlockEntity's type in the
    // ModInitializer, delegating to itemManager / tankManager / the MJ storage. Subclasses provide the
    // backing Storage instances.

    @Nullable
    public buildcraft.api.mj.IMjConnector getMjConnector(@Nullable Direction side) {
        return caps.getMjConnector();
    }

    // Native fluid and item storage lookups.
    @Nullable
    public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>
        getFluidStorage(@Nullable Direction side) {
        return tankManager.isEmpty() ? null : tankManager;
    }

    public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
        getItemStorage(@Nullable Direction side) {
        return itemManager.getStorage(side);
    }

    protected void onSlotChange(/* STUB(R.Chen): IItemHandlerModifiable */ Object handler, int slot,
        @Nonnull ItemStack before, @Nonnull ItemStack after) {
        if (world != null && world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            if (getCurrentState().hasComparatorOutput()) {
                markDirty();
            } else {
                markChunkDirty();
            }
        }
    }

    /** Cheaper version of {@link #markDirty()} that doesn't update nearby comparators, so all it will do is ensure that
     * the current chunk is saved after the last tick. */
    public void markChunkDirty() {
        if (world != null) {
            // TODO(R.Chen): Forge World.markChunkDirty(pos, tile) had no comparator update. Yarn's
            // World.markDirty(BlockPos) is the closest match; verify it does not trigger comparator updates.
            world.markDirty(pos);
        }
    }

    // ##################
    //
    // Permission related
    //
    // ##################

    protected boolean hasOwner() {
        return owner != null;
    }

    @Override
    public GameProfile getOwner() {
        if (owner == null) {
            String msg = "[lib.tile] Unknown owner for " + getClass() + " at ";
            BCLog.logger.warn(msg + StringUtilBC.blockPosToString(getPos()));
            owner = FakePlayerProvider.NULL_PROFILE;
        }
        return owner;
    }

    public PermissionUtil.PermissionBlock getPermBlock() {
        return new PermissionBlock(this, pos);
    }

    public boolean canEditOther(BlockPos other) {
        return PermissionUtil.hasPermission(
            PermissionUtil.PERM_EDIT, getPermBlock(), PermissionUtil.createFrom(world, other)
        );
    }

    public boolean canPlayerEdit(PlayerEntity player) {
        return PermissionUtil.hasPermission(PermissionUtil.PERM_EDIT, player, getPermBlock());
    }

    public boolean canInteractWith(PlayerEntity player) {
        if (world == null || isRemoved() || player.isSpectator() || player.getWorld() != world || !world.isChunkLoaded(pos)
            || world.getBlockEntity(pos) != this) {
            return false;
        }
        if (player.squaredDistanceTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        // edit rather than view because you can normally change the contents from gui interaction
        return canPlayerEdit(player);
    }

    /** Native wrench hook for machines whose rotation changes more than the clicked block. */
    public net.minecraft.util.ActionResult attemptRotation(PlayerEntity player, Direction side) {
        return net.minecraft.util.ActionResult.PASS;
    }

    // ##################
    //
    // Network helpers
    //
    // ##################

    /** Tells MC to redraw this block. Note that this sends the NET_REDRAW message. */
    public final void redrawBlock() {
        if (hasWorld()) {
            if (world.isClient) {
                BlockState state = world.getBlockState(pos);
                world.updateListeners(pos, state, state, 0);

                if (DEBUG) {
                    double x = pos.getX() + 0.5;
                    double y = pos.getY() + 0.5;
                    double z = pos.getZ() + 0.5;
                    world.addParticle(ParticleTypes.HEART, x, y, z, 0, 0, 0);
                }
            } else {
                sendNetworkUpdate(NET_REDRAW);
            }
        }
    }

    /** Sends a network update update of the specified ID. */
    public final void sendNetworkUpdate(int id) {
        if (hasWorld()) {
            MessageUpdateTile message = createNetworkUpdate(id);
            if (world.isClient) {
                MessageManager.sendToServer(message);
            } else {
                MessageUtil.sendToAllWatching(world, pos, message);
            }
        }
    }

    public final void sendNetworkGuiTick(PlayerEntity player) {
        if (hasWorld() && !world.isClient) {
            MessageUpdateTile message = createNetworkUpdate(NET_GUI_TICK);
            if (message.getPayloadSize() <= Integer.BYTES + Short.BYTES) {
                return;
            }
            MessageManager.sendTo(message, (ServerPlayerEntity) player);
        }
    }

    public final void sendNetworkGuiUpdate(int id) {
        if (hasWorld()) {
            for (PlayerEntity player : usingPlayers) {
                sendNetworkUpdate(id, player);
            }
        }
    }

    public final void sendNetworkUpdate(int id, PlayerEntity target) {
        if (hasWorld() && target instanceof ServerPlayerEntity) {
            MessageUpdateTile message = createNetworkUpdate(id);
            MessageManager.sendTo(message, (ServerPlayerEntity) target);
        }
    }

    public final MessageUpdateTile createNetworkUpdate(final int id) {
        if (hasWorld()) {
            // TODO(R.Chen): replace the Forge "Side" discriminator with a Fabric-native NetSide enum.
            final NetSide side = world.isClient ? NetSide.CLIENT : NetSide.SERVER;
            return createMessage(id, (buffer) -> writePayload(id, buffer, side));
        } else {
            BCLog.logger.warn("Did not have a world at " + pos + "!");
        }
        return null;
    }

    public final void createAndSendMessage(int id, IPayloadWriter writer) {
        if (hasWorld()) {
            MessageUpdateTile message = createMessage(id, writer);
            if (world.isClient) {
                MessageManager.sendToServer(message);
            } else {
                MessageUtil.sendToAllWatching(world, pos, message);
            }
        }
    }

    public final void createAndSendGuiMessage(int id, IPayloadWriter writer) {
        if (hasWorld()) {
            MessageUpdateTile message = createMessage(id, writer);
            if (world.isClient) {
                MessageManager.sendToServer(message);
            } else {
                MessageUtil.sendToPlayers(usingPlayers, message);
            }
        }
    }

    public final void createAndSendMessage(int id, ServerPlayerEntity player, IPayloadWriter writer) {
        if (hasWorld()) {
            MessageUpdateTile message = createMessage(id, writer);
            MessageManager.sendTo(message, player);
        }
    }

    public final void createAndSendGuiMessage(int id, ServerPlayerEntity player, IPayloadWriter writer) {
        if (usingPlayers.contains(player)) {
            createAndSendMessage(id, player, writer);
        }
    }

    public final MessageUpdateTile createMessage(int id, IPayloadWriter writer) {
        if (id < 0 || id > 0xffff) throw new IllegalArgumentException("Invalid tile message ID: " + id);
        return MessageUpdateTile.of(pos, buffer -> {
            // Match vanilla block-entity updates: a replacement may arrive after this custom packet.
            buffer.writeInt(net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE.getRawId(getType()));
            buffer.writeShort(id);
            writer.write(buffer);
        });
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeShort(NET_RENDER_DATA);
            writePayload(NET_RENDER_DATA, new PacketBufferBC(buf), NetSide.SERVER);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            NbtCompound nbt = super.toInitialChunkDataNbt();
            nbt.putByteArray("d", bytes);
            return nbt;
        } finally {
            buf.release();
        }
    }

    /** Applies the render-only NBT sent by vanilla chunk and block-entity update packets. */
    public void handleUpdateTag(NbtCompound tag) {
        // Explicitly don't read the (server) data from NBT
        super.readNbt(tag);
        if (!tag.contains("d", NbtElement.BYTE_ARRAY_TYPE)) {
            // A bit odd, but ok - this was probably sent by something else
            return;
        }
        byte[] bytes = tag.getByteArray("d");
        if (bytes.length < 2) {
            // less than 2 bytes won't even be enough to read the ID, so we'll treat it as no data.
            BCLog.logger.warn("[lib.tile] Received an update tag that didn't have any data!\n\t(" + tag + ")");
            return;
        }
        ByteBuf buf = Unpooled.copiedBuffer(bytes);

        try {
            int id = buf.readUnsignedShort();
            PacketBufferBC buffer = new PacketBufferBC(buf);
            readPayload(id, buffer, world.isClient ? NetSide.CLIENT : NetSide.SERVER, null);
            // Make sure that we actually read the entire message rather than just discarding it
            MessageUtil.ensureEmpty(buffer, world.isClient, getClass() + ", id = " + getIdAllocator().getNameFor(id));
            spawnReceiveParticles(id);
        } catch (IOException e) {
            throw new RuntimeException("Received an update tag that failed to read correctly!", e);
        } finally {
            buf.release();
        }
    }

    private void spawnReceiveParticles(int id) {
        if (DEBUG) {
            String name = getIdAllocator().getNameFor(id);

            if (world != null) {
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 0.5;
                double z = pos.getZ() + 0.5;
                // TODO(R.Chen): Forge EnumParticleTypes.REDSTONE carried an RGB tint via (r,g,b) speed args.
                // Yarn uses DustParticleEffect for coloured redstone dust; port the colour mapping later.
                world.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0, 0, 0);
            }
        }
    }

    @Override
    public final MessageUpdateTile receivePayload(buildcraft.lib.net.NetworkContext ctx, PacketBufferBC buffer)
        throws IOException {
        int targetType = buffer.readInt();
        if (targetType != net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE.getRawId(getType())) {
            // The old and new machine can briefly occupy the same position on opposite sides.
            // Its initial vanilla update supplies the new machine's state once placement arrives.
            buffer.skipBytes(buffer.readableBytes());
            return null;
        }
        int id = buffer.readUnsignedShort();
        NetSide side = ctx.isClient() ? NetSide.CLIENT : NetSide.SERVER;
        readPayload(id, buffer, side, ctx);

        // Make sure that we actually read the entire message rather than just discarding it
        MessageUtil.ensureEmpty(buffer, world.isClient, getClass() + ", id = " + getIdAllocator().getNameFor(id));

        if (side == NetSide.CLIENT) {
            spawnReceiveParticles(id);
        }
        return null;
    }

    @Override
    public boolean canReceivePayload(PlayerEntity player) {
        return usingPlayers.contains(player) && canInteractWith(player);
    }

    // ######################
    //
    // Network overridables
    //
    // ######################

    public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        // write render data with gui data
        if (id == NET_GUI_DATA) {

            writePayload(NET_RENDER_DATA, buffer, side);

            if (side == NetSide.SERVER) {
                MessageUtil.writeGameProfile(buffer, owner);
            }
        }
        if (side == NetSide.SERVER) {
            if (id == NET_RENDER_DATA) {
                deltaManager.writeDeltaState(false, buffer);
            } else if (id == NET_GUI_DATA) {
                deltaManager.writeDeltaState(true, buffer);
            }
        }
    }

    /** @param ctx The context. Will be null if this is a generic update payload
     * @throws IOException if something went wrong */
    public void readPayload(int id, PacketBufferBC buffer, NetSide side, /* STUB(R.Chen): MessageContext */ Object ctx)
        throws IOException {
        // read render data with gui data
        if (id == NET_GUI_DATA) {
            readPayload(NET_RENDER_DATA, buffer, side, ctx);

            if (side == NetSide.CLIENT) {
                owner = MessageUtil.readGameProfile(buffer);
            }
        }
        if (side == NetSide.CLIENT) {
            if (id == NET_RENDER_DATA) deltaManager.receiveDeltaData(false, EnumDeltaMessage.CURRENT_STATE, buffer);
            else if (id == NET_GUI_DATA) deltaManager.receiveDeltaData(true, EnumDeltaMessage.CURRENT_STATE, buffer);
            else if (id == NET_REN_DELTA_SINGLE) deltaManager.receiveDeltaData(
                false, EnumDeltaMessage.ADD_SINGLE, buffer
            );
            else if (id == NET_GUI_DELTA_SINGLE) deltaManager.receiveDeltaData(
                true, EnumDeltaMessage.ADD_SINGLE, buffer
            );
            else if (id == NET_REN_DELTA_CLEAR) deltaManager.receiveDeltaData(
                false, EnumDeltaMessage.SET_VALUE, buffer
            );
            else if (id == NET_GUI_DELTA_CLEAR) deltaManager.receiveDeltaData(true, EnumDeltaMessage.SET_VALUE, buffer);
            else if (id == NET_REDRAW) redrawBlock();
            else if (id == NET_ADV_DEBUG) {
                BCAdvDebugging.setClientDebugTarget(this);
            }
        }
    }

    // ######################
    //
    // NBT handling
    //
    // ######################

    /**
     * Persistent data hook called from {@link #readNbt(NbtCompound)}. Subclasses override this
     * so vanilla render-only update NBT cannot accidentally reset persistent fields.
     */
    public void readFromNBT(NbtCompound nbt) {}

    /**
     * Forge-compat hook called from {@link #writeNbt(NbtCompound)}. Unmigrated subclasses override
     * this instead of {@code writeNbt}. Migrated subclasses should override {@code writeNbt} directly.
     */
    public NbtCompound writeToNBT(NbtCompound nbt) { return nbt; }

    @Override
    public final void readNbt(NbtCompound nbt) {
        if (world != null && world.isClient && nbt.contains("d", NbtElement.BYTE_ARRAY_TYPE)) {
            handleUpdateTag(nbt);
            return;
        }
        super.readNbt(nbt);
        migrateOldNBT(nbt.getInt("data-version"), nbt);
        readFromNBT(nbt);
        deltaManager.readFromNBT(nbt.getCompound("deltas"));
        if (nbt.contains("owner")) {
            owner = NbtHelper.toGameProfile(nbt.getCompound("owner"));
        }
        if (nbt.contains("items", NbtElement.COMPOUND_TYPE)) {
            itemManager.deserializeNBT(nbt.getCompound("items"));
        }
        if (nbt.contains("tanks", NbtElement.COMPOUND_TYPE)) {
            tankManager.deserializeNBT(nbt.getCompound("tanks"));
        }
    }

    protected void migrateOldNBT(int version, NbtCompound nbt) {
        // 7.99.0 -> 7.99.4
        // Most tiles with a single tank saved it under "tank"
        NbtCompound tankComp = nbt.getCompound("tank");
        if (!tankComp.isEmpty()) {
            NbtCompound tanks = new NbtCompound();
            tanks.put("tank", tankComp);
            nbt.put("tanks", tanks);
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("data-version", BCVersion.CURRENT.dataVersion);
        writeToNBT(nbt);
        nbt.put("deltas", deltaManager.writeToNBT());
        if (owner != null && owner.isComplete() && owner != FakePlayerProvider.NULL_PROFILE) {
            nbt.put("owner", NbtHelper.writeGameProfile(new NbtCompound(), owner));
        }
        NbtCompound items = itemManager.serializeNBT();
        if (!items.isEmpty()) {
            nbt.put("items", items);
        }
        NbtCompound tanks = tankManager.serializeNBT();
        if (!tanks.isEmpty()) {
            nbt.put("tanks", tanks);
        }
    }

    // STUB(R.Chen): Forge setWorldCreate(World) override removed — Yarn's BlockEntity stores the world via
    // setWorld(World), called by the chunk on load, so no override is required.

    // ##################
    //
    // Advanced debugging
    //
    // ##################

    public boolean isBeingDebugged() {
        return BCAdvDebugging.isBeingDebugged(this);
    }

    public void enableDebugging() {
        if (world.isClient) {
            return;
        }
        BCAdvDebugging.setCurrentDebugTarget(this);
    }

    @Override
    public void disableDebugging() {
        sendNetworkUpdate(NET_ADV_DEBUG_DISABLE);
    }

    @Override
    public boolean doesExistInWorld() {
        return hasWorld() && world.getBlockEntity(pos) == this;
    }

    @Override
    public void sendDebugState() {
        sendNetworkUpdate(NET_ADV_DEBUG);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public IDetachedRenderer getDebugRenderer() {
        return null;
    }

    // TODO(R.Chen): temporary Fabric-native replacement for the Forge SimpleImpl "Side" discriminator used
    // throughout write/readPayload. Promote to its own top-level type (buildcraft.lib.net.NetSide) once the
    // network layer migration lands, and wire it to the Fabric receiver context.
    public enum NetSide {
        CLIENT,
        SERVER;
    }

}
