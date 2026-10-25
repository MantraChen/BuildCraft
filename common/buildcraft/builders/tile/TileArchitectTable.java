/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.tile;

import java.io.IOException;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import buildcraft.api.core.*;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.schematics.*;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.builders.*;
import buildcraft.builders.block.BlockArchitectTable;
import buildcraft.builders.client.ClientArchitectTables;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.*;
import buildcraft.core.marker.volume.*;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.data.*;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

public class TileArchitectTable extends TileBC_Neptune implements buildcraft.lib.tile.TickingBlockEntity, IDebuggable {
    public static final IdAllocator IDS = TileBC_Neptune.IDS.makeChild("architect");
    public static final int NET_BOX = IDS.allocId("BOX"), NET_SCAN = IDS.allocId("SCAN");
    public static final int MAX_NAME_LENGTH = 64;
    private static final Identifier ADVANCEMENT = new Identifier("buildcraftbuilders:architect");
    public final ItemHandlerSimple invSnapshotIn = itemManager.addInvHandler("in", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSnapshot, EnumAccess.INSERT, EnumPipePart.VALUES);
    public final ItemHandlerSimple invSnapshotOut = itemManager.addInvHandler("out", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES);
    public final Box box = new Box();
    public boolean markerBox;
    public String name = "";
    private boolean valid;
    private Snapshot scanning;
    private final Map<ISchematicBlock,Integer> paletteIndices = new HashMap<>();
    private ItemStack scanInput = ItemStack.EMPTY;
    private int scanned, total, retryTicks;
    // 0: no area, 1: waiting for input, 2: scanning, 3: unloaded chunk, 4: save failed, 5: output ready.
    private int status;
    private boolean readingGuiData;

    public TileArchitectTable(net.minecraft.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type,pos,state); invSnapshotIn.setLimitedInsertor(1); invSnapshotOut.setLimitedInsertor(1);
    }
    @Override public IdAllocator getIdAllocator() { return IDS; }
    public int getScanned() { return scanned; }
    public int getScanTotal() { return total; }
    public int getScanStatus() { return status; }
    public boolean hasArea() { return valid && validBox(box); }
    private boolean validBox(Box candidate) {
        if (!candidate.isInitialized()) return false;
        try { Snapshot.getDataSize(candidate.size()); }
        catch (IllegalArgumentException invalid) { return false; }
        return world == null || (!world.isOutOfHeightLimit(candidate.min()) && !world.isOutOfHeightLimit(candidate.max())
            && world.getWorldBorder().contains(candidate.min()) && world.getWorldBorder().contains(candidate.max()));
    }
    @Override public void onPlacedBy(LivingEntity placer, ItemStack stack) {
        super.onPlacedBy(placer,stack);
        if (!isStorageAvailable() || world.isClient) return;
        var state = getCachedState();
        var target = pos.offset(state.get(BlockArchitectTable.PROP_FACING).getOpposite());
        if (!world.isChunkLoaded(target)) return;
        var boxes = WorldSavedDataVolumeBoxes.get(world);
        var volume = boxes.getVolumeBoxAt(target);
        if (volume != null && validBox(volume.box) && !volume.box.contains(pos)) {
            box.setMin(volume.box.min()); box.setMax(volume.box.max()); valid = true;
            volume.locks.add(new Lock(new Lock.Cause.CauseBlock(pos,state.getBlock()), new Lock.Target.TargetRemove(),
                new Lock.Target.TargetResize(),new Lock.Target.TargetUsedByMachine(Lock.Target.TargetUsedByMachine.EnumType.STRIPES_READ)));
            boxes.markDirty();
        } else if (world.getBlockEntity(target) instanceof IAreaProvider provider) {
            var candidate = new Box(); candidate.setMin(provider.min()); candidate.setMax(provider.max());
            if (validBox(candidate) && !candidate.contains(pos)
                && (!(provider instanceof buildcraft.api.tiles.ITileAreaProvider area) || area.isValidFromLocation(pos))
                && BuildersAreaHelper.canConsumeMarkers(this,provider)) {
                box.setMin(candidate.min()); box.setMax(candidate.max()); markerBox = true; valid = true;
                provider.removeFromWorld();
            }
        }
        world.setBlockState(pos, state.with(BlockArchitectTable.PROP_VALID, valid), 3);
        status = valid ? 1 : 0;
        markDirty(); sendNetworkUpdate(NET_RENDER_DATA);
    }
    private void resetScan() { scanning = null; paletteIndices.clear(); scanInput = ItemStack.EMPTY; scanned = 0; total = 0; retryTicks = 0; }
    @Override protected void onSlotChange(Object handler, int slot, ItemStack before, ItemStack after) {
        super.onSlotChange(handler,slot,before,after);
        if (handler == invSnapshotIn && scanning != null && !ItemStack.areEqual(before,after)) resetScan();
    }
    @Override public void tick() {
        if (!isStorageAvailable()) return;
        if (world.isClient) {
            if (box.isInitialized()) ClientArchitectTables.BOXES.put(box.getBoundingBox(), ClientArchitectTables.START_BOX_VALUE);
            return;
        }
        if (Transaction.getCurrentUnsafe() != null) return;
        int oldStatus = status, oldProgress = scanned;
        if (!hasArea()) { resetScan(); status = 0; }
        else if (!invSnapshotOut.getStackInSlot(0).isEmpty()) { status = 5; }
        else if (!(invSnapshotIn.getStackInSlot(0).getItem() instanceof ItemSnapshot)) { resetScan(); status = 1; }
        else {
            var input = invSnapshotIn.getStackInSlot(0);
            if (scanning != null && !ItemStack.areEqual(input,scanInput)) resetScan();
            if (scanning == null) {
                scanning = Snapshot.create(ItemSnapshot.EnumItemSnapshotType.getFromStack(input).snapshotType);
                scanning.size = box.size(); scanning.facing = getCachedState().get(BlockArchitectTable.PROP_FACING);
                scanning.offset = box.min().subtract(pos.offset(scanning.facing.getOpposite()));
                total = scanning.getDataSize(); scanInput = input.copy();
                if (scanning instanceof Template template) template.data = new BitSet(total);
                if (scanning instanceof Blueprint blueprint) blueprint.data = new int[total];
            }
            if (retryTicks > 0) retryTicks--;
            else {
                status = 2;
                List<BlockPos> highlights = new ArrayList<>();
                for (int i=0; i<scanning.getType().maxPerTick && scanned<total; i++) {
                    var target = box.min().add(scanning.indexToPos(scanned));
                    if (!world.isChunkLoaded(target)) { status = 3; break; }
                    if (scanning instanceof Template template) template.data.set(scanned, !world.isAir(target));
                    else if (scanning instanceof Blueprint blueprint) {
                        var state = world.getBlockState(target);
                        var schematic = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(world,box.min(),target,state,state.getBlock()));
                        int index = paletteIndices.computeIfAbsent(schematic, key -> {
                            int next=blueprint.palette.size(); blueprint.palette.add(key); return next;
                        });
                        blueprint.data[scanned] = index;
                    }
                    if (highlights.size()<16) highlights.add(target);
                    scanned++;
                }
                if (!highlights.isEmpty()) createAndSendMessage(NET_SCAN,buffer -> {
                    buffer.writeVarInt(highlights.size()); for (var target:highlights) buffer.writeBlockPos(target);
                });
                if (scanned==total) finishScanning();
            }
        }
        if (status!=oldStatus || scanned!=oldProgress) { markDirty(); sendNetworkUpdate(NET_RENDER_DATA); }
    }
    private void finishScanning() {
        // A completed scan never consumes the item until its content-addressed file is durable.
        if (scanning instanceof Blueprint blueprint) {
            for(int x=box.min().getX()>>4;x<=box.max().getX()>>4;x++)for(int z=box.min().getZ()>>4;z<=box.max().getZ()>>4;z++)if(!world.isChunkLoaded(x,z) || world instanceof net.minecraft.server.world.ServerWorld server && !server.isChunkLoaded(ChunkPos.toLong(x,z))){status=3;return;}
            blueprint.entities.clear();
            world.getEntitiesByClass(Entity.class,box.getBoundingBox(),entity -> box.getBoundingBox().contains(entity.getPos())).stream()
                .map(entity -> SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(world,box.min(),entity)))
                .filter(Objects::nonNull).limit(Blueprint.MAX_ENTITIES+1).forEach(blueprint.entities::add);
            if(blueprint.entities.size()>Blueprint.MAX_ENTITIES){status=4;retryTicks=20;return;}
        }
        scanning.computeKey();
        if (!GlobalSavedDataSnapshots.get(world).addSnapshot(scanning)) { status=4; retryTicks=20; return; }
        if (!isStorageAvailable() || !ItemStack.areEqual(invSnapshotIn.getStackInSlot(0),scanInput)
            || !invSnapshotOut.getStackInSlot(0).isEmpty()) return;
        var output = BCBuildersItems.snapshot.getUsed(scanning.getType(),
            new Snapshot.Header(scanning.key,getOwner().getId(),new Date(),name));
        var remaining=invSnapshotIn.getStackInSlot(0).copy(); remaining.decrement(1);
        resetScan();
        invSnapshotIn.setStackInSlot(0,remaining);
        invSnapshotOut.setStackInSlot(0,output);
        status=5; markDirty();
        if(world instanceof net.minecraft.server.world.ServerWorld server)AdvancementUtil.unlockAdvancement(server,getOwner().getId(),ADVANCEMENT);
    }
    @Override public void writePayload(int id, PacketBufferBC buffer, NetSide side) {
        super.writePayload(id,buffer,side);
        if (side==NetSide.SERVER && (id==NET_RENDER_DATA || id==NET_BOX)) {
            box.writeData(buffer); buffer.writeBoolean(markerBox);
            if (id==NET_RENDER_DATA) { buffer.writeString(name); buffer.writeByte(status); buffer.writeVarInt(scanned); buffer.writeVarInt(total); }
        }
    }
    @Override public void readPayload(int id, PacketBufferBC buffer, NetSide side, Object ctx) throws IOException {
        if (side==NetSide.CLIENT && id==NET_GUI_DATA) {
            readingGuiData=true;
            try { super.readPayload(id,buffer,side,ctx); if (buffer.isReadable()) throw new IOException("Trailing architect GUI data"); }
            finally { readingGuiData=false; }
            return;
        }
        super.readPayload(id,buffer,side,ctx);
        if (side!=NetSide.CLIENT) return;
        if (id==NET_RENDER_DATA || id==NET_BOX) {
            var area=new Box(); area.readData(buffer); boolean markers=buffer.readBoolean();
            if (area.isInitialized() && !validBox(area)) throw new IOException("Invalid architect area");
            String newName=name; int newStatus=status,newScanned=scanned,newTotal=total;
            if (id==NET_RENDER_DATA) {
                newName=buffer.readString(MAX_NAME_LENGTH); newStatus=buffer.readUnsignedByte(); newScanned=buffer.readVarInt(); newTotal=buffer.readVarInt();
                if (newStatus>5 || newScanned<0 || newTotal<newScanned || newTotal>BuildingExecutor.MAX_VOLUME)
                    throw new IOException("Invalid architect progress");
            }
            if (!readingGuiData && buffer.isReadable()) throw new IOException("Trailing architect data");
            box.reset(); if (area.isInitialized()) { box.setMin(area.min()); box.setMax(area.max()); }
            markerBox=markers; name=newName; status=newStatus; scanned=newScanned; total=newTotal; valid=area.isInitialized();
        } else if (id==NET_SCAN) {
            int count=buffer.readVarInt(); if (count<0 || count>16) throw new IOException("Invalid scan count");
            List<BlockPos> positions=new ArrayList<>();
            for (int i=0;i<count;i++) { var target=buffer.readBlockPos(); if (!box.isInitialized() || !box.contains(target)) throw new IOException("Scan outside architect area"); positions.add(target); }
            if (buffer.isReadable()) throw new IOException("Trailing scan data");
            for (var target:positions) ClientArchitectTables.SCANNED_BLOCKS.put(target,ClientArchitectTables.START_SCANNED_BLOCK_VALUE);
        }
    }
    @Override public NbtCompound writeToNBT(NbtCompound nbt) {
        super.writeToNBT(nbt); nbt.put("box",box.writeToNBT()); nbt.putBoolean("markerBox",markerBox);
        nbt.putBoolean("isValid",valid); nbt.putString("name",name); return nbt;
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt); box.initialize(nbt.getCompound("box")); markerBox=nbt.getBoolean("markerBox");
        valid=nbt.getBoolean("isValid") && validBox(box);
        name=nbt.getString("name"); if (name.length()>MAX_NAME_LENGTH) name=name.substring(0,MAX_NAME_LENGTH);
        // Partial scans contain observations, not paid work: reload starts a fresh coherent capture.
        resetScan(); status=valid?1:0;
    }
    @Override public void getDebugInfo(List<String> left,List<String> right,Direction side) {
        left.add("box: "+box.min()+" - "+box.max()); left.add("scan: "+scanned+"/"+total+", status="+status);
    }
}
