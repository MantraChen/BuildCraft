/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * Remake by FelixMantra
 */
package buildcraft.builders.tile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.IBox;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.*;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.containers.IFillerStatementContainer;
import buildcraft.api.tiles.IControllable;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.inventory.StorageItemTransactor;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.mj.TransactionalMjReceiver;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import buildcraft.lib.net.MessageManager;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.FullStatement;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.TickingBlockEntity;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.filler.FillerType;
import buildcraft.builders.filler.FillerUtil;
import buildcraft.builders.snapshot.ITileForTemplateBuilder;
import buildcraft.builders.snapshot.Template;
import buildcraft.builders.snapshot.TemplateBuilder;
import buildcraft.builders.snapshot.SnapshotBuilder.RenderWork;
import buildcraft.core.marker.volume.EnumAddonSlot;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.WorldSavedDataVolumeBoxes;

public class TileFiller extends TileBC_Neptune
        implements TickingBlockEntity, IDebuggable, ITileForTemplateBuilder, IFillerStatementContainer, IControllable {
    public static final IdAllocator IDS = TileBC_Neptune.IDS.makeChild("filler");
    public static final int NET_CAN_EXCAVATE = IDS.allocId("CAN_EXCAVATE");
    public static final int NET_WORK = IDS.allocId("WORK");
    public final ItemHandlerSimple invResources = itemManager.addInvHandler("resources", 27,
        (slot, stack) -> stack.getItem() instanceof BlockItem, EnumAccess.INSERT, EnumPipePart.VALUES);
    private final IItemTransactor resources = new StorageItemTransactor(invResources.asStorage(true, true));
    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
    private final SnapshotParticipant<NbtCompound> energySnapshots = new SnapshotParticipant<>() {
        @Override protected NbtCompound createSnapshot() { return battery.writeToNbt(); }
        @Override protected void readSnapshot(NbtCompound value) { battery.readFromNbt(value); }
        @Override protected void onFinalCommit() { markDirty(); }
    };
    private final FillerReceiver receiver = new FillerReceiver();
    private final class FillerReceiver implements IMjReceiver, IMjReadable, TransactionalMjReceiver {
        private boolean live() { return isStorageAvailable() && !world.isClient; }
        @Override public boolean canConnect(IMjConnector other) { return live(); }
        @Override public long getStored() { return battery.getStored(); }
        @Override public long getCapacity() { return battery.getCapacity(); }
        @Override public long getPowerRequested() { return live() ? Math.max(0, getCapacity()-getStored()) : 0; }
        @Override public long receivePower(long power, boolean simulate) {
            if (power <= 0 || !live()) return power;
            long accepted = Math.min(power, getPowerRequested());
            if (!simulate && accepted > 0) {
                var transaction = Transaction.getCurrentUnsafe();
                if (transaction != null) energySnapshots.updateSnapshots(transaction);
                battery.addPower(accepted, false);
                if (transaction == null) markDirty();
            }
            return power - accepted;
        }
        @Override public long receivePower(long power, TransactionContext transaction) {
            if (power <= 0 || !live()) return power;
            long accepted = Math.min(power, getPowerRequested());
            if (accepted > 0) {
                energySnapshots.updateSnapshots(transaction);
                battery.addPower(accepted, false);
            }
            return power - accepted;
        }
    }
    private boolean canExcavate = true;
    public boolean inverted;
    private boolean finished;
    private int lockedTicks;
    private Mode mode = Mode.ON;
    public final Box box = new Box();
    public AddonFillerPlanner addon;
    public boolean markerBox = true;
    private UUID addonBoxId;
    private EnumAddonSlot addonSlot;
    private NbtCompound savedWork;
    private Template.BuildingInfo buildingInfo;
    private Template.BuildingInfo activeInfo;
    public final TemplateBuilder builder = new TemplateBuilder(this);
    public final FullStatement<IFillerPattern> patternStatement = new FullStatement<>(FillerType.INSTANCE, 4,
        (statement, paramIndex) -> onStatementChange());
    private int clientToPlace, clientToBreak;
    private List<RenderWork> renderWork = List.of();
    private boolean readingGuiData;

    public TileFiller(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    @Override public IMjConnector getMjConnector(Direction side) { return isStorageAvailable() ? receiver : null; }
    @Override public IdAllocator getIdAllocator() { return IDS; }

    @Override public void onPlacedBy(LivingEntity placer, ItemStack stack) {
        super.onPlacedBy(placer, stack);
        if (world.isClient) return;
        BlockPos target = pos.offset(getCachedState().get(BlockBCBase_Neptune.PROP_FACING).getOpposite());
        if (!world.isChunkLoaded(target)) return;
        var boxes = WorldSavedDataVolumeBoxes.get(world);
        var volume = boxes.getVolumeBoxAt(target);
        if (volume != null) {
            if (volume.box.contains(pos)) return;
            addon = volume.addons.values().stream().filter(AddonFillerPlanner.class::isInstance)
                .map(AddonFillerPlanner.class::cast).findFirst().orElse(null);
            box.setMin(volume.box.min());
            box.setMax(volume.box.max());
            if (addon != null) {
                addonBoxId = volume.id;
                addonSlot = addon.getSlot();
                addon.updateBuildingInfo();
                volume.locks.add(new Lock(new Lock.Cause.CauseBlock(pos, getCachedState().getBlock()),
                    new Lock.Target.TargetAddon(addonSlot), new Lock.Target.TargetRemove(),
                    new Lock.Target.TargetResize(), new Lock.Target.TargetUsedByMachine(Lock.Target.TargetUsedByMachine.EnumType.STRIPES_WRITE)));
            } else {
                volume.locks.add(new Lock(new Lock.Cause.CauseBlock(pos, getCachedState().getBlock()),
                    new Lock.Target.TargetRemove(), new Lock.Target.TargetResize(),
                    new Lock.Target.TargetUsedByMachine(Lock.Target.TargetUsedByMachine.EnumType.STRIPES_WRITE)));
            }
            boxes.markDirty();
            markerBox = false;
        } else if (world.getBlockEntity(target) instanceof IAreaProvider provider) {
            if (provider instanceof buildcraft.api.tiles.ITileAreaProvider area && !area.isValidFromLocation(pos)) return;
            try { buildcraft.builders.snapshot.Snapshot.getDataSize(provider.max().subtract(provider.min()).add(1,1,1)); }
            catch (IllegalArgumentException invalid) { return; }
            if (!canConsumeMarkers(provider)) return;
            box.setMin(provider.min());
            box.setMax(provider.max());
            provider.removeFromWorld();
        }
        updateBuildingInfo();
        markDirty();
        sendNetworkUpdate(NET_RENDER_DATA);
    }

    private boolean canConsumeMarkers(IAreaProvider provider) {
        if (!(provider instanceof buildcraft.core.tile.TileMarkerVolume marker)) return true;
        var connection = marker.getCurrentConnection();
        if (connection == null || !(world instanceof net.minecraft.server.world.ServerWorld server)) return false;
        var player = buildcraft.lib.misc.FakePlayerProvider.INSTANCE.getFakePlayer(server, getOwner(), pos);
        var positions = List.copyOf(connection.getMarkerPositions());
        var states = new java.util.HashMap<BlockPos, BlockState>();
        var entities = new java.util.HashMap<BlockPos, net.minecraft.block.entity.BlockEntity>();
        for (var p : positions) {
            if (!world.isChunkLoaded(p) || !world.getWorldBorder().contains(p) || !world.canPlayerModifyAt(player,p)) return false;
            states.put(p,world.getBlockState(p)); entities.put(p,world.getBlockEntity(p));
        }
        for (var p : positions) if (!net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker()
            .beforeBlockBreak(world,player,p,states.get(p),entities.get(p))) return false;
        return marker.getCurrentConnection() == connection && positions.equals(List.copyOf(connection.getMarkerPositions()))
            && positions.stream().allMatch(p -> world.isChunkLoaded(p) && world.getBlockState(p)==states.get(p)
                && world.getBlockEntity(p)==entities.get(p) && world.canPlayerModifyAt(player,p));
    }

    @Override public void onLoad() {
        super.onLoad();
        if (world.isClient) return;
        if (addonBoxId != null && addonSlot != null) {
            var volume = WorldSavedDataVolumeBoxes.get(world).getVolumeBoxFromId(addonBoxId);
            if (volume != null && volume.addons.get(addonSlot) instanceof AddonFillerPlanner planner) {
                addon = planner;
                addon.updateBuildingInfo();
            }
        }
        updateBuildingInfo();
        if (savedWork != null) {
            builder.deserializeNBT(savedWork);
            savedWork = null;
        }
    }

    public void updateBuildingInfo() {
        if (world == null || world.isClient) return;
        buildingInfo = addonBoxId != null ? null : hasBox() ? FillerUtil.createBuildingInfo(this, patternStatement,
            IntStream.range(0, 4).mapToObj(patternStatement::get).toArray(IStatementParameter[]::new), inverted) : null;
        activeInfo = addon != null ? addon.buildingInfo : buildingInfo;
        builder.updateSnapshot();
    }

    public void onStatementChange() {
        finished = false;
        updateBuildingInfo();
        markDirty();
        if (world != null && !world.isClient) sendNetworkUpdate(NET_RENDER_DATA);
    }

    @Override protected void onSlotChange(Object handler, int slot, ItemStack before, ItemStack after) {
        super.onSlotChange(handler, slot, before, after);
        if (builder != null && world != null && !world.isClient) builder.resourcesChanged();
    }

    @Override public void tick() {
        if (!isStorageAvailable()) return;
        if (world.isClient) { patternStatement.canInteract = !isLocked(); return; }
        if (Transaction.getCurrentUnsafe() != null) return;
        // Refunds can temporarily exceed nominal capacity; this is paid work, not excess to dissipate.
        if (lockedTicks > 0) lockedTicks--;
        if (addonBoxId != null && addonSlot != null) {
            var volume = WorldSavedDataVolumeBoxes.get(world).getVolumeBoxFromId(addonBoxId);
            var linked = volume != null && volume.addons.get(addonSlot) instanceof AddonFillerPlanner planner ? planner : null;
            if (addon != linked) { addon = linked; updateBuildingInfo(); finished = false; sendNetworkUpdate(NET_RENDER_DATA); }
        }
        if (addon != null && activeInfo != addon.buildingInfo) {
            activeInfo = addon.buildingInfo;
            builder.updateSnapshot();
            finished = false;
        }
        if (mode == Mode.OFF || (mode == Mode.ON && finished) || getTemplateBuildingInfo() == null) { publishWork(List.of()); return; }
        boolean previous = finished;
        finished = builder.tick();
        publishWork(builder.visibleWork().stream().map(work -> new RenderWork(
            work.pos(), work.breaking(), (float)((double)work.power()/work.target()), work.item())).toList());
        if (previous != finished) markDirty();
    }

    private void publishWork(List<RenderWork> work) {
        if (work.isEmpty() && renderWork.isEmpty()) return;
        renderWork = work;
        sendNetworkUpdate(NET_WORK);
    }

    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put("battery", battery.writeToNbt());
        nbt.putBoolean("canExcavate", canExcavate);
        nbt.putBoolean("inverted", inverted);
        nbt.putBoolean("finished", finished);
        nbt.putInt("lockedTicks", lockedTicks);
        nbt.put("mode", NBTUtilBC.writeEnum(mode));
        nbt.put("box", box.writeToNBT());
        nbt.putBoolean("markerBox", markerBox);
        nbt.put("patternStatement", patternStatement.writeToNbt());
        nbt.put("builder", savedWork != null ? savedWork : builder.serializeNBT());
        if (addonBoxId != null && addonSlot != null) {
            nbt.putUuid("addonVolumeBoxId", addonBoxId);
            nbt.put("addonSlot", NBTUtilBC.writeEnum(addonSlot));
        }
    }

    @Override public void readFromNBT(NbtCompound nbt) {
        activeInfo = buildingInfo = null;
        builder.deserializeNBT(new NbtCompound());
        battery.readFromNbt(nbt.getCompound("battery"));
        canExcavate = !nbt.contains("canExcavate") || nbt.getBoolean("canExcavate");
        inverted = nbt.getBoolean("inverted");
        finished = nbt.getBoolean("finished");
        lockedTicks = Math.max(0, Math.min(3, nbt.getInt("lockedTicks")));
        Mode readMode = NBTUtilBC.readEnum(nbt.get("mode"), Mode.class);
        mode = readMode == null ? Mode.ON : readMode;
        box.initialize(nbt.getCompound("box"));
        markerBox = !nbt.contains("markerBox") || nbt.getBoolean("markerBox");
        addon = null;
        addonBoxId = nbt.containsUuid("addonVolumeBoxId") ? nbt.getUuid("addonVolumeBoxId") : null;
        addonSlot = NBTUtilBC.readEnum(nbt.get("addonSlot"), EnumAddonSlot.class);
        patternStatement.readFromNbt(nbt.getCompound("patternStatement"));
        savedWork = nbt.getCompound("builder").copy();
    }

    @Override public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id, buffer, side);
        if (side != NetSide.SERVER) return;
        if (id == NET_WORK) {
            buffer.writeVarInt(renderWork.size());
            for (var work : renderWork) {
                buffer.writeBlockPos(work.pos()); buffer.writeBoolean(work.breaking());
                buffer.writeFloat(work.progress()); buffer.writeItemStack(work.item());
            }
        }
        if (id == NET_RENDER_DATA || id == NET_GUI_TICK) {
            (addon != null ? addon.volumeBox.box : box).writeData(buffer);
            buffer.writeBoolean(markerBox);
            buffer.writeBoolean(canExcavate);
            buffer.writeBoolean(addon != null ? addon.inverted : inverted);
            buffer.writeBoolean(finished);
            buffer.writeBoolean(isLocked());
            buffer.writeEnumConstant(mode);
            (addon != null ? addon.patternStatement : patternStatement).writeToBuffer(buffer);
            buffer.writeVarInt(builder.leftToPlace);
            buffer.writeVarInt(builder.leftToBreak);
        }
    }

    @Override public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object ctx) throws IOException {
        // The base GUI packet embeds render data followed by owner and delta data.
        // Only the outer packet owns the end-of-buffer boundary.
        if (id == NET_GUI_DATA) {
            boolean previous = readingGuiData;
            readingGuiData = true;
            try { super.readPayload(id, buffer, side, ctx); }
            finally { readingGuiData = previous; }
            if (side == NetSide.CLIENT && buffer.isReadable()) throw new IOException("Trailing filler GUI data");
            return;
        }
        super.readPayload(id, buffer, side, ctx);
        if (side == NetSide.CLIENT && (id == NET_RENDER_DATA || id == NET_GUI_TICK)) {
            var nextBox = new Box(); nextBox.readData(buffer);
            boolean nextMarker=buffer.readBoolean(), nextExcavate=buffer.readBoolean(), nextInverted=buffer.readBoolean(),
                nextFinished=buffer.readBoolean(), nextLocked=buffer.readBoolean();
            Mode nextMode=buffer.readEnumConstant(Mode.class);
            var nextPattern = new FullStatement<IFillerPattern>(FillerType.INSTANCE,4,null); nextPattern.readFromBuffer(buffer);
            int toPlace=buffer.readVarInt(), toBreak=buffer.readVarInt();
            if ((!readingGuiData && buffer.isReadable()) || toPlace<0 || toBreak<0 || toPlace>1_048_576 || toBreak>1_048_576)
                throw new IOException("Invalid filler status");
            if(nextBox.isInitialized())try { buildcraft.builders.snapshot.Snapshot.getDataSize(nextBox.size()); }
            catch(IllegalArgumentException invalid){throw new IOException("Invalid filler volume",invalid);}
            box.reset(); if(nextBox.isInitialized())box.initialize(nextBox);
            markerBox=nextMarker;canExcavate=nextExcavate;inverted=nextInverted;finished=nextFinished;
            lockedTicks=nextLocked?1:0;mode=nextMode;patternStatement.takePreparedConfiguration(nextPattern);
            clientToPlace=toPlace;clientToBreak=toBreak;
        } else if (side == NetSide.CLIENT && id == NET_WORK) {
            int count=buffer.readVarInt();
            if(count<0 || count>16)throw new IOException("Invalid filler work count");
            var work=new java.util.ArrayList<RenderWork>();
            for(int i=0;i<count;i++){
                var target=buffer.readBlockPos();boolean breaking=buffer.readBoolean();float progress=buffer.readFloat();
                var item=buffer.readItemStack();
                if(!Float.isFinite(progress) || progress<0 || progress>1 || !hasBox() || !getBox().contains(net.minecraft.util.math.Vec3d.ofCenter(target))
                    || !item.isEmpty() && (!(item.getItem() instanceof BlockItem) || item.getCount()!=1))throw new IOException("Invalid filler work");
                work.add(new RenderWork(target,breaking,progress,item));
            }
            if(buffer.isReadable())throw new IOException("Trailing filler work data");
            builder.receiveRenderWork(work);
        } else if (side == NetSide.SERVER && id == NET_CAN_EXCAVATE) {
            boolean value = buffer.readBoolean();
            if(buffer.isReadable())throw new IOException("Trailing filler setting data");
            if (!isLocked()) {
                canExcavate = value;
                onStatementChange();
            }
        }
    }

    public void sendCanExcavate(boolean value) {
        MessageManager.sendToServer(createMessage(NET_CAN_EXCAVATE, buffer -> buffer.writeBoolean(value)));
    }
    public int getCountToPlace() { return world != null && world.isClient ? clientToPlace : builder.leftToPlace; }
    public int getCountToBreak() { return world != null && world.isClient ? clientToBreak : builder.leftToBreak; }
    public boolean isFinished() { return mode != Mode.LOOP && finished; }
    public boolean isLocked() { return lockedTicks > 0; }
    @Override public World getWorldBC() { return world; }
    @Override public World getFillerWorld() { return world; }
    @Override public net.minecraft.block.entity.BlockEntity getTile() { return this; }
    @Override public MjBattery getBattery() { return battery; }
    @Override public BlockPos getBuilderPos() { return pos; }
    @Override public boolean canExcavate() { return canExcavate; }
    @Override public TemplateBuilder getBuilder() { return builder; }
    @Override public Template.BuildingInfo getTemplateBuildingInfo() { return activeInfo; }
    @Override public IItemTransactor getInvResources() { return resources; }
    @Override public boolean hasBox() { return addon != null || box.isInitialized(); }
    @Override public IBox getBox() { return addon != null ? addon.volumeBox.box : box; }
    @Override public void setPattern(IFillerPattern pattern, IStatementParameter[] params) {
        var prepared=new FullStatement<IFillerPattern>(FillerType.INSTANCE,4,null);
        prepared.set(pattern==null?FillerType.INSTANCE.defaultStatement:pattern);
        if(params!=null)for(int i=0;i<Math.min(4,params.length);i++)if(prepared.canSet(i,params[i]))prepared.set(i,params[i]);
        var current=addon!=null?addon.patternStatement:patternStatement;
        lockedTicks=3;
        if(current.writeToNbt().equals(prepared.writeToNbt()))return;
        if (addon != null) {
            addon.setPattern(prepared.get(), prepared.getParameters());
            WorldSavedDataVolumeBoxes.get(world).markDirty();
        } else {
            patternStatement.takePreparedConfiguration(prepared);
        }
        onStatementChange();
        lockedTicks = 3;
        finished = false;
        markDirty();
    }
    @Override public Mode getControlMode() { return mode; }
    @Override public void setControlMode(Mode mode) {
        if (mode != null && mode != this.mode) { this.mode = mode; finished = false; markDirty(); }
    }
    @Override public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("box = " + getBox());
        left.add("pattern = " + (addon != null ? addon.patternStatement.get() : patternStatement.get()));
        left.add("mode = " + mode + ", finished = " + finished);
        left.add("remaining = " + getCountToBreak() + " break, " + getCountToPlace() + " place");
    }
}
