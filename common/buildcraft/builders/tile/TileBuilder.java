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
    public static final int NET_WORK = IDS.allocId("WORK");
    private static final int MAX_PATH_POINTS = 256, MAX_PATH_STEPS = 65536;
    public final ItemHandlerSimple invSnapshot = itemManager.addInvHandler("snapshot", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSnapshot item && item.getHeader(stack) != null, EnumAccess.BOTH, EnumPipePart.VALUES);
    public final ItemHandlerSimple invResources = itemManager.addInvHandler("resources", 27, EnumAccess.BOTH, EnumPipePart.VALUES);
    private final Storage<ItemVariant> resourceStorage = invResources.asStorage(true, true);
    private final IItemTransactor resources = new StorageItemTransactor(resourceStorage);
    private final MjBattery battery = new MjBattery(16000 * MjAPI.MJ);
    private final buildcraft.lib.mj.BoundedTileMjReceiver receiver = new buildcraft.lib.mj.BoundedTileMjReceiver(this,battery);
    public final TemplateBuilder templateBuilder = new TemplateBuilder(this);
    public final BlueprintBuilder blueprintBuilder = new BlueprintBuilder(this);
    public EnumSnapshotType snapshotType;
    public List<BlockPos> path = List.of();
    private List<BlockPos> basePositions = List.of();
    private int pathIndex;
    private boolean invalidSavedPath;
    private boolean canExcavate = true, finished, initialize = true, snapshotChanged;
    private Template.BuildingInfo templateInfo;
    private Blueprint.BuildingInfo blueprintInfo;
    private Snapshot snapshot;
    private final Box currentBox = new Box();
    private NbtCompound savedWork;
    private int clientToBreak, clientToPlace, clientEntities;
    private List<BlueprintBuilder.Material> clientMaterials = List.of();
    private int clientPathCount=1;
    private boolean readingGuiData;
    private List<SnapshotBuilder.RenderWork> renderWork=List.of();

    public TileBuilder(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        invSnapshot.setLimitedInsertor(1);
        for (int i = 1; i <= 4; i++) tankManager.add(new Tank("tank" + i, 8000, this));
    }
    @Override public IMjConnector getMjConnector(Direction side) { return isStorageAvailable()?receiver:null; }
    @Override public IdAllocator getIdAllocator() { return IDS; }
    @Override public void onLoad() {
        super.onLoad();
        if (isStorageAvailable() && !world.isClient) initializePlan();
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
        if (!isStorageAvailable() || world.isClient) return;
        BlockPos start = pos.offset(facing().getOpposite());
        if (world.isChunkLoaded(start) && world.getBlockEntity(start) instanceof IPathProvider provider) {
            List<BlockPos> candidate = provider.getPath();
            if (validPath(candidate) && candidate.stream().allMatch(p -> world.isInBuildLimit(p) && world.getWorldBorder().contains(p))
                && buildcraft.builders.BuildersAreaHelper.canConsumePath(this,provider) && candidate.equals(provider.getPath())) {
                path = candidate.stream().map(BlockPos::toImmutable).toList();
                provider.removeFromWorld();
            }
        }
        rebuildPath();
        loadSelected(true);
        initialize=false; snapshotChanged=false;
        markDirty();
        sendNetworkUpdate(NET_RENDER_DATA);
    }
    private Direction facing() { return getCachedState().get(BlockBCBase_Neptune.PROP_FACING); }
    private static boolean validPath(List<BlockPos> path) {
        if (path==null || path.size() < 2 || path.size() > MAX_PATH_POINTS || path.stream().anyMatch(java.util.Objects::isNull)) return false;
        long steps = 1;
        for (int i = 1; i < path.size(); i++) {
            BlockPos a = path.get(i - 1), b = path.get(i);
            if (a.equals(b)) return false;
            steps += Math.abs((long) a.getX() - b.getX()) + Math.abs((long) a.getY() - b.getY())
                + Math.abs((long) a.getZ() - b.getZ());
            if (steps > MAX_PATH_STEPS) return false;
        }
        return true;
    }
    private void rebuildPath() {
        if (invalidSavedPath) { path=List.of();basePositions=List.of();pathIndex=0;return; }
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
        if (snapshot!=null && snapshot.getType()!=ItemSnapshot.EnumItemSnapshotType.getFromStack(stack).snapshotType) snapshot=null;
        updatePlan();
    }
    private void updatePlan() {
        publishWork(List.of());
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
        if (!isStorageAvailable() || world.isClient || net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getCurrentUnsafe()!=null) return;
        initializePlan();
        // Paid work refunds remain owned energy even when temporarily above input capacity.
        if (snapshotChanged) {
            snapshotChanged = false;
            loadSelected(true);
            sendNetworkUpdate(NET_RENDER_DATA);
            markDirty();
        }
        var builder = getBuilder();
        if (builder == null || finished) { publishWork(List.of()); return; }
        boolean done=builder.tick();
        publishWork(builder.visibleWork().stream().map(work -> new SnapshotBuilder.RenderWork(work.pos(),work.breaking(),
            (float)((double)work.power()/work.target()),work.item())).toList());
        if (done) {
            if (pathIndex + 1 < basePositions.size()) { pathIndex++; updatePlan(); }
            else {
                finished = true;
                if (basePositions.size()>1 && world instanceof net.minecraft.server.world.ServerWorld server) buildcraft.lib.misc.AdvancementUtil.unlockAdvancement(server,getOwner().getId(),
                    new net.minecraft.util.Identifier("buildcraftbuilders:paving_the_way"));
            }
            markDirty();
            sendNetworkUpdate(NET_RENDER_DATA);
        }
    }
    private void publishWork(List<SnapshotBuilder.RenderWork> work) {
        if (work.isEmpty() && renderWork.isEmpty()) return;
        renderWork=work;
        if (world!=null && !world.isClient) sendNetworkUpdate(NET_WORK);
    }
    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put("battery", battery.writeToNbt());
        nbt.putBoolean("canExcavate", canExcavate);
        nbt.putBoolean("finished", finished);
        nbt.putInt("pathIndex", pathIndex);
        nbt.putBoolean("invalidSavedPath", invalidSavedPath);
        nbt.putLongArray("pathPositions", path.stream().mapToLong(BlockPos::asLong).toArray());
        if (savedWork != null) nbt.put("builder", savedWork.copy());
        else if (getBuilder() != null) nbt.put("builder", getBuilder().serializeNBT());
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        initialize = true;
        renderWork=List.of(); currentBox.reset();
        snapshot = null; snapshotType = null; templateInfo = null; blueprintInfo = null;
        templateBuilder.deserializeNBT(new NbtCompound());
        blueprintBuilder.deserializeNBT(new NbtCompound());
        battery.readFromNbt(nbt.getCompound("battery"));
        canExcavate = !nbt.contains("canExcavate") || nbt.getBoolean("canExcavate");
        finished = nbt.getBoolean("finished");
        pathIndex = Math.max(0, nbt.getInt("pathIndex"));
        long[] positions = nbt.getLongArray("pathPositions");
        path = positions.length <= MAX_PATH_POINTS ? Arrays.stream(positions).mapToObj(BlockPos::fromLong).toList() : List.of();
        invalidSavedPath=nbt.getBoolean("invalidSavedPath") || positions.length>0 && !validPath(path);
        savedWork = nbt.contains("builder") ? nbt.getCompound("builder").copy() : null;
    }
    @Override public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id,buffer,side);
        if (side!=NetSide.SERVER) return;
        if (id==NET_WORK) {
            buffer.writeVarInt(renderWork.size());
            for (var work:renderWork) { buffer.writeBlockPos(work.pos()); buffer.writeBoolean(work.breaking()); buffer.writeFloat(work.progress()); buffer.writeItemStack(work.item()); }
        }
        if (id==NET_RENDER_DATA || id==NET_GUI_TICK) {
            currentBox.writeData(buffer);
            buffer.writeEnumConstant(EnumOptionalSnapshotType.fromNullable(snapshotType));
            buffer.writeBoolean(canExcavate); buffer.writeBoolean(finished);
            buffer.writeVarInt(getCountToBreak()); buffer.writeVarInt(getCountToPlace()); buffer.writeVarInt(getPendingEntities());
            buffer.writeVarInt(pathIndex); buffer.writeVarInt(basePositions.size());
            buffer.writeVarInt(path.size()); for (var point:path) buffer.writeBlockPos(point);
            tankManager.writeToBuffer(buffer);
            var materials=getRemainingMaterials(); buffer.writeVarInt(materials.size());
            for (var material:materials) { buffer.writeItemStack(material.stack()); buffer.writeVarLong(material.count()); }
        }
    }
    @Override public void readPayload(int id,PacketBufferBC buffer,NetSide side,Object context)throws IOException {
        if (id==NET_GUI_DATA) {
            boolean previous=readingGuiData; readingGuiData=true;
            try {super.readPayload(id,buffer,side,context);} finally {readingGuiData=previous;}
            if (side==NetSide.CLIENT && buffer.isReadable()) throw new IOException("Trailing builder GUI data");
            return;
        }
        super.readPayload(id,buffer,side,context);
        if (side==NetSide.CLIENT && id==NET_WORK) {
            int count=buffer.readVarInt(); if (count<0 || count>16) throw new IOException("Invalid builder work count");
            List<SnapshotBuilder.RenderWork> work=new ArrayList<>();
            for (int i=0;i<count;i++) {
                var target=buffer.readBlockPos(); boolean breaking=buffer.readBoolean(); float progress=buffer.readFloat(); var item=buffer.readItemStack();
                if (!currentBox.isInitialized() || !currentBox.contains(target) || !Float.isFinite(progress) || progress<0 || progress>1
                    || !item.isEmpty() && item.getCount()!=1) throw new IOException("Invalid builder work");
                work.add(new SnapshotBuilder.RenderWork(target,breaking,progress,item));
            }
            if (buffer.isReadable() || !work.isEmpty() && getBuilder()==null) throw new IOException("Invalid builder work payload");
            templateBuilder.receiveRenderWork(List.of()); blueprintBuilder.receiveRenderWork(List.of());
            if (getBuilder()!=null) getBuilder().receiveRenderWork(work);
        } else if (side==NetSide.CLIENT && (id==NET_RENDER_DATA || id==NET_GUI_TICK)) {
            var area=new Box(); area.readData(buffer);
            if (area.isInitialized()) try {Snapshot.getDataSize(area.size());} catch (IllegalArgumentException invalid) {throw new IOException("Invalid builder box",invalid);}
            var type=buffer.readEnumConstant(EnumOptionalSnapshotType.class).type;
            boolean excavate=buffer.readBoolean(), done=buffer.readBoolean();
            int breaking=buffer.readVarInt(), placing=buffer.readVarInt(), entities=buffer.readVarInt();
            int index=buffer.readVarInt(), steps=buffer.readVarInt(), points=buffer.readVarInt();
            if (breaking<0 || breaking>BuildingExecutor.MAX_VOLUME || placing<0 || placing>BuildingExecutor.MAX_VOLUME
                || entities<0 || entities>BuildingExecutor.MAX_VOLUME || steps<0 || steps>MAX_PATH_STEPS
                || index<0 || index>=Math.max(1,steps) || points<0 || points>MAX_PATH_POINTS) throw new IOException("Invalid builder counts");
            List<BlockPos> newPath=new ArrayList<>(); for (int i=0;i<points;i++) newPath.add(buffer.readBlockPos());
            if (!newPath.isEmpty() && !validPath(newPath)) throw new IOException("Invalid builder path");
            // Validate the full tank section before requesting client cache objects or replacing any UI state.
            int[] fluidIds=new int[4];long[] fluidAmounts=new long[4];
            for (int i=0;i<4;i++) {
                boolean present=buffer.readBoolean();
                int fluidId=present?buffer.readInt():-1;
                if (present && fluidId<0) throw new IOException("Invalid builder fluid cache id");
                long amount=buffer.readVarLong();
                if (amount<0 || amount>8*net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET || !present && amount!=0)
                    throw new IOException("Invalid builder fluid amount");
                fluidIds[i]=fluidId;fluidAmounts[i]=amount;
            }
            int count=buffer.readVarInt(); if (count<0 || count>24) throw new IOException("Too many builder material entries");
            List<BlueprintBuilder.Material> materials=new ArrayList<>();
            for (int i=0;i<count;i++) {
                var stack=buffer.readItemStack(); long amount=buffer.readVarLong();
                if (stack.isEmpty() || stack.getCount()!=1 || amount<=0) throw new IOException("Invalid builder material entry");
                materials.add(new BlueprintBuilder.Material(stack,amount));
            }
            if (!readingGuiData && buffer.isReadable()) throw new IOException("Trailing builder status");
            if (world!=null && world.isClient) {
                // Boolean flags share earlier bytes in PacketBufferBC, so a raw byte slice cannot preserve their state.
                var tanks=PacketBufferBC.write(out->{for(int i=0;i<4;i++){
                    out.writeBoolean(fluidIds[i]>=0);if(fluidIds[i]>=0)out.writeInt(fluidIds[i]);out.writeVarLong(fluidAmounts[i]);
                }});
                try {tankManager.readFromBuffer(tanks);} finally {tanks.release();}
            }
            currentBox.reset(); if (area.isInitialized()) {currentBox.setMin(area.min());currentBox.setMax(area.max());}
            snapshotType=type;canExcavate=excavate;finished=done;clientToBreak=breaking;clientToPlace=placing;clientEntities=entities;
            pathIndex=index;clientPathCount=steps;path=List.copyOf(newPath);clientMaterials=List.copyOf(materials);
        } else if (side==NetSide.SERVER && id==NET_CAN_EXCAVATE) {
            boolean value=buffer.readBoolean();if (buffer.isReadable())throw new IOException("Unexpected builder setting payload");
            if (!isStorageAvailable()) return;
            if (value!=canExcavate) {
                canExcavate=value; finished=false;
                if (getBuilder()!=null) getBuilder().updateSnapshot();
                publishWork(List.of()); markDirty();sendNetworkUpdate(NET_RENDER_DATA);
            }
        }
    }
    public int getPathIndex() { return pathIndex; }
    public int getPathCount() { return world!=null && world.isClient?clientPathCount:basePositions.size(); }
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
