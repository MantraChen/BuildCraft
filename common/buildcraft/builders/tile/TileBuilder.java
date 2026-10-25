/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra
 */
package buildcraft.builders.tile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IPathProvider;
import buildcraft.api.enums.EnumOptionalSnapshotType;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.*;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.builders.block.BlockBuilder;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.*;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.fluid.Tank;
import buildcraft.lib.fluid.TankManager;
import buildcraft.lib.inventory.StorageItemTransactor;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.TickingBlockEntity;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

public class TileBuilder extends TileBC_Neptune
        implements TickingBlockEntity, IDebuggable, ITileForTemplateBuilder, ITileForBlueprintBuilder {
    public static final IdAllocator IDS = TileBC_Neptune.IDS.makeChild("builder");
    public static final int NET_CAN_EXCAVATE = IDS.allocId("CAN_EXCAVATE");
    private static final int MAX_PATH_POINTS = 256, MAX_PATH_STEPS = 65536;
    public final ItemHandlerSimple invSnapshot = itemManager.addInvHandler("snapshot", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSnapshot item && item.getHeader(stack) != null, EnumAccess.NONE);
    public final ItemHandlerSimple invResources = itemManager.addInvHandler("resources", 27, EnumAccess.INSERT, EnumPipePart.VALUES);
    private final Storage<ItemVariant> resourceStorage = invResources.asStorage(true, true);
    private final IItemTransactor resources = new StorageItemTransactor(resourceStorage);
    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
    public final TemplateBuilder templateBuilder = new TemplateBuilder(this);
    public final BlueprintBuilder blueprintBuilder = new BlueprintBuilder(this);
    public EnumSnapshotType snapshotType;
    public List<BlockPos> path = List.of();
    private List<BlockPos> basePositions = List.of();
    private int pathIndex;
    private boolean canExcavate = true, finished, initialize = true, snapshotChanged;
    private Template.BuildingInfo templateInfo;
    private Blueprint.BuildingInfo blueprintInfo;
    private Snapshot snapshot;
    private final Box currentBox = new Box();
    private NbtCompound savedWork;
    private int clientToBreak, clientToPlace, clientEntities;
    private List<BlueprintBuilder.Material> clientMaterials = List.of();

    public TileBuilder(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        invSnapshot.setLimitedInsertor(1);
        for (int i = 1; i <= 4; i++) tankManager.add(new Tank("tank" + i, 8000, this));
        caps.addProvider(new MjCapabilityHelper(new MjBatteryReceiver(battery)));
    }
    @Override public IdAllocator getIdAllocator() { return IDS; }
    @Override public void onLoad() {
        super.onLoad();
        if (!world.isClient) initializePlan();
    }
    private void initializePlan() {
        if (!initialize) return;
        rebuildPath();
        loadSelected(false);
        initialize = false;
        snapshotChanged = false;
        if (savedWork != null && getBuilder() != null) {
            getBuilder().deserializeNBT(savedWork);
            savedWork = null;
        }
    }
    @Override public void onPlacedBy(LivingEntity placer, ItemStack stack) {
        super.onPlacedBy(placer, stack);
        if (world.isClient) return;
        BlockPos start = pos.offset(facing().getOpposite());
        if (world.isChunkLoaded(start) && world.getBlockEntity(start) instanceof IPathProvider provider) {
            List<BlockPos> candidate = provider.getPath();
            if (validPath(candidate)) {
                path = candidate.stream().map(BlockPos::toImmutable).toList();
                provider.removeFromWorld();
            }
        }
        rebuildPath();
        loadSelected(true);
        markDirty();
        sendNetworkUpdate(NET_RENDER_DATA);
    }
    private Direction facing() { return getCachedState().get(BlockBCBase_Neptune.PROP_FACING); }
    private static boolean validPath(List<BlockPos> path) {
        if (path.size() < 2 || path.size() > MAX_PATH_POINTS) return false;
        long steps = 1;
        for (int i = 1; i < path.size(); i++) {
            BlockPos a = path.get(i - 1), b = path.get(i);
            steps += Math.abs((long) a.getX() - b.getX()) + Math.abs((long) a.getY() - b.getY())
                + Math.abs((long) a.getZ() - b.getZ());
            if (steps > MAX_PATH_STEPS) return false;
        }
        return true;
    }
    private void rebuildPath() {
        if (!validPath(path)) path = List.of();
        List<BlockPos> positions = new ArrayList<>();
        if (path.isEmpty()) positions.add(pos.offset(facing().getOpposite()));
        else {
            positions.add(path.get(0));
            for (int i = 1; i < path.size(); i++) positions.addAll(PositionUtil.getAllOnPath(path.get(i - 1), path.get(i)));
        }
        basePositions = List.copyOf(positions);
        pathIndex = Math.max(0, Math.min(pathIndex, basePositions.size() - 1));
    }
    private void loadSelected(boolean resetProgress) {
        if (resetProgress) { pathIndex = 0; finished = false; savedWork = null; }
        ItemStack stack = invSnapshot.getStackInSlot(0);
        Snapshot.Header header = stack.getItem() instanceof ItemSnapshot item ? item.getHeader(stack) : null;
        snapshot = header == null ? null : GlobalSavedDataSnapshots.get(world).getSnapshot(header.key);
        updatePlan();
    }
    private void updatePlan() {
        templateBuilder.cancelAllTasks();
        blueprintBuilder.cancelAllTasks();
        templateInfo = null;
        blueprintInfo = null;
        snapshotType = snapshot == null ? null : snapshot.getType();
        currentBox.reset();
        if (snapshot != null && !basePositions.isEmpty()) {
            BlockRotation rotation = Arrays.stream(BlockRotation.values()).filter(r -> r.rotate(snapshot.facing) == facing())
                .findFirst().orElse(BlockRotation.NONE);
            BlockPos base = basePositions.get(pathIndex);
            if (snapshot instanceof Template template) templateInfo = template.new BuildingInfo(base, rotation);
            if (snapshot instanceof Blueprint blueprint) blueprintInfo = blueprint.new BuildingInfo(base, rotation);
            var info = templateInfo != null ? templateInfo : blueprintInfo;
            currentBox.setMin(info.box.min());
            currentBox.setMax(info.box.max());
        }
        templateBuilder.updateSnapshot();
        blueprintBuilder.updateSnapshot();
        BlockState state = getCachedState();
        if (world != null && !world.isClient && state.contains(BlockBuilder.SNAPSHOT_TYPE)) {
            var type = EnumOptionalSnapshotType.fromNullable(snapshotType);
            if (state.get(BlockBuilder.SNAPSHOT_TYPE) != type)
                world.setBlockState(pos, state.with(BlockBuilder.SNAPSHOT_TYPE, type), Block.NOTIFY_LISTENERS);
        }
    }
    @Override protected void onSlotChange(Object handler, int slot, ItemStack before, ItemStack after) {
        super.onSlotChange(handler, slot, before, after);
        if (world == null || world.isClient || initialize) return;
        if (handler == invSnapshot) {
            // Return queued credit immediately, including if the world saves before the next tick.
            templateBuilder.cancelAllTasks();
            blueprintBuilder.cancelAllTasks();
            snapshotChanged = true;
            finished = false;
            pathIndex = 0;
        }
        if (handler == invResources && getBuilder() != null) getBuilder().resourcesChanged();
    }
    @Override public void tick() {
        if (world == null || world.isClient) return;
        initializePlan();
        battery.tick(world, pos);
        if (snapshotChanged) {
            snapshotChanged = false;
            loadSelected(true);
            sendNetworkUpdate(NET_RENDER_DATA);
            markDirty();
        }
        var builder = getBuilder();
        if (builder == null || finished) return;
        if (builder.tick()) {
            if (pathIndex + 1 < basePositions.size()) { pathIndex++; updatePlan(); }
            else finished = true;
            markDirty();
            sendNetworkUpdate(NET_RENDER_DATA);
        }
    }
    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put("battery", battery.writeToNbt());
        nbt.putBoolean("canExcavate", canExcavate);
        nbt.putBoolean("finished", finished);
        nbt.putInt("pathIndex", pathIndex);
        nbt.putLongArray("pathPositions", path.stream().mapToLong(BlockPos::asLong).toArray());
        if (savedWork != null) nbt.put("builder", savedWork.copy());
        else if (getBuilder() != null) nbt.put("builder", getBuilder().serializeNBT());
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        initialize = true;
        snapshot = null; snapshotType = null; templateInfo = null; blueprintInfo = null;
        templateBuilder.deserializeNBT(new NbtCompound());
        blueprintBuilder.deserializeNBT(new NbtCompound());
        battery.readFromNbt(nbt.getCompound("battery"));
        canExcavate = !nbt.contains("canExcavate") || nbt.getBoolean("canExcavate");
        finished = nbt.getBoolean("finished");
        pathIndex = Math.max(0, nbt.getInt("pathIndex"));
        long[] positions = nbt.getLongArray("pathPositions");
        path = positions.length <= MAX_PATH_POINTS ? Arrays.stream(positions).mapToObj(BlockPos::fromLong).toList() : List.of();
        savedWork = nbt.contains("builder") ? nbt.getCompound("builder").copy() : null;
    }
    @Override public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side == NetSide.SERVER && (id == NET_RENDER_DATA || id == NET_GUI_TICK)) {
            currentBox.writeData(buffer);
            buffer.writeEnumConstant(EnumOptionalSnapshotType.fromNullable(snapshotType));
            buffer.writeBoolean(canExcavate);
            buffer.writeBoolean(finished);
            buffer.writeVarInt(getCountToBreak()); buffer.writeVarInt(getCountToPlace());
            buffer.writeVarInt(getPendingEntities());
            buffer.writeVarInt(pathIndex); buffer.writeVarInt(basePositions.size());
            tankManager.writeToBuffer(buffer);
            var materials = getRemainingMaterials();
            buffer.writeVarInt(materials.size());
            for (var material : materials) {
                buffer.writeItemStack(material.stack());
                buffer.writeVarLong(material.count());
            }
        }
    }
    @Override public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object context) throws IOException {
        super.readPayload(id, buffer, side, context);
        if (side == NetSide.CLIENT && (id == NET_RENDER_DATA || id == NET_GUI_TICK)) {
            currentBox.readData(buffer);
            snapshotType = buffer.readEnumConstant(EnumOptionalSnapshotType.class).type;
            canExcavate = buffer.readBoolean(); finished = buffer.readBoolean();
            clientToBreak = buffer.readVarInt(); clientToPlace = buffer.readVarInt(); clientEntities = buffer.readVarInt();
            pathIndex = buffer.readVarInt(); buffer.readVarInt();
            tankManager.readFromBuffer(buffer);
            int count = buffer.readVarInt();
            if (count < 0 || count > 24) throw new IOException("Too many builder material entries");
            List<BlueprintBuilder.Material> materials = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ItemStack stack = buffer.readItemStack();
                long amount = buffer.readVarLong();
                if (stack.isEmpty() || amount <= 0) throw new IOException("Invalid builder material entry");
                materials.add(new BlueprintBuilder.Material(stack, amount));
            }
            clientMaterials = List.copyOf(materials);
        } else if (side == NetSide.SERVER && id == NET_CAN_EXCAVATE) {
            boolean value = buffer.readBoolean();
            if (buffer.isReadable()) throw new IOException("Unexpected builder setting payload");
            canExcavate = value;
            markDirty(); sendNetworkUpdate(NET_RENDER_DATA);
        }
    }
    public void sendCanExcavate(boolean value) {
        if (world != null && world.isClient) MessageManager.sendToServer(createMessage(NET_CAN_EXCAVATE, b -> b.writeBoolean(value)));
    }
    @Override public World getWorldBC() { return world; }
    @Override public BlockPos getBuilderPos() { return pos; }
    @Override public MjBattery getBattery() { return battery; }
    @Override public boolean canExcavate() { return canExcavate; }
    @Override public SnapshotBuilder<?> getBuilder() {
        return snapshotType == EnumSnapshotType.TEMPLATE ? templateBuilder : snapshotType == EnumSnapshotType.BLUEPRINT ? blueprintBuilder : null;
    }
    @Override public Template.BuildingInfo getTemplateBuildingInfo() { return templateInfo; }
    @Override public Blueprint.BuildingInfo getBlueprintBuildingInfo() { return blueprintInfo; }
    @Override public IItemTransactor getInvResources() { return resources; }
    @Override public Storage<ItemVariant> getResourceStorage() { return resourceStorage; }
    @Override public TankManager getTankManager() { return tankManager; }
    public Box getBox() { return currentBox; }
    public boolean isFinished() { return finished; }
    public List<BlueprintBuilder.Material> getRemainingMaterials() {
        return world != null && world.isClient ? clientMaterials : blueprintBuilder.getRemainingMaterials(24);
    }
    public int getCountToBreak() { return world != null && world.isClient ? clientToBreak : getBuilder() == null ? 0 : getBuilder().leftToBreak; }
    public int getCountToPlace() { return world != null && world.isClient ? clientToPlace : getBuilder() == null ? 0 : getBuilder().leftToPlace; }
    public int getPendingEntities() { return world != null && world.isClient ? clientEntities : blueprintBuilder.pendingEntities(); }
    @Override public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("Remake by FelixMantra");
        left.add("Plan: " + snapshotType + "; path index: " + pathIndex + "; finished: " + finished);
        left.add("Break: " + getCountToBreak() + "; place: " + getCountToPlace() + "; entities pending: " + getPendingEntities());
        left.add("MJ: " + battery.getStored() + "/" + battery.getCapacity());
    }
}
