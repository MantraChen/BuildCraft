/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native multiblock heat exchange. Remake by FelixMantra.
 */
package buildcraft.factory.tile;

import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import buildcraft.api.recipes.*;
import buildcraft.api.recipes.IRefineryRecipeManager.*;
import buildcraft.api.tiles.*;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.block.BlockHeatExchange.EnumExchangePart;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.fluid.*;
import buildcraft.lib.misc.*;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.*;

/** Three to five aligned blocks exchange equal volumes through four transactional tanks. */
public class TileHeatExchange extends TileBC_Neptune implements TickingBlockEntity, IDebuggable, IHasWork {
    public static final IdAllocator IDS = TileBC_Neptune.IDS.makeChild("HeatExchanger");
    public static final int WARMUP_TICKS = 120;
    private static final long[] RATES = {405, 810, 1620}; // Original 5/10/20 mB per tick.
    private ExchangeSection section;
    private boolean formed, checkNeighbours = true, changed = true;
    private List<BlockPos> structure = List.of();
    private long nextCheck, revision;
    private int structureSize;

    public TileHeatExchange(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type,pos,state); }
    @Override public IdAllocator getIdAllocator() { return IDS; }
    public ExchangeSection getSection() { return section; }
    public boolean isStart() { return formed && section instanceof ExchangeSectionStart; }
    public boolean isEnd() { return formed && section instanceof ExchangeSectionEnd; }
    public boolean isFormed() { return formed; }
    public int getStructureSize() { return structureSize; }
    public Direction getFacing() { return getCachedState().get(BlockBCBase_Neptune.PROP_FACING); }
    public TileHeatExchange getClientEnd() {
        if(world==null || !isStart() || structureSize<3 || structureSize>5)return null;
        BlockPos at=pos.offset(getFacing().rotateYCounterclockwise(),structureSize-1);
        return world.isChunkLoaded(at) && world.getBlockEntity(at) instanceof TileHeatExchange end
            && end.isEnd() && end.getFacing()==getFacing()?end:null;
    }

    private record Scan(List<TileHeatExchange> tiles, boolean complete) {}
    private Scan scan() {
        Deque<TileHeatExchange> found = new ArrayDeque<>(); found.add(this);
        boolean complete = true;
        Direction facing = getFacing();
        for (Direction direction : List.of(facing.rotateYClockwise(),facing.rotateYCounterclockwise())) {
            for (int i=1;i<=6;i++) {
                BlockPos at=pos.offset(direction,i);
                if (!world.isChunkLoaded(at)) { complete=false; break; }
                if (!(world.getBlockEntity(at) instanceof TileHeatExchange other) || other.getFacing()!=facing) break;
                if(!other.isStorageAvailable()) {complete=false;break;}
                if (direction==facing.rotateYClockwise()) found.addFirst(other); else found.addLast(other);
            }
        }
        return new Scan(List.copyOf(found),complete);
    }
    /** Does not load chunks; retained native views also validate the complete current structure. */
    private boolean structureLive() {
        if (!formed || !isStorageAvailable() || structure.size()<3 || structure.size()>5) return false;
        Direction facing=getFacing();
        for(BlockPos at:structure) {
            if(!world.isChunkLoaded(at) || !(world.getBlockEntity(at) instanceof TileHeatExchange other)
                || !other.isStorageAvailable() || other.getFacing()!=facing || !other.formed || !other.structure.equals(structure)) return false;
        }
        // A newly attached same-facing block changes the endpoint even before its first scheduled tick.
        for(BlockPos at:List.of(structure.get(0).offset(facing.rotateYClockwise()),
                structure.get(structure.size()-1).offset(facing.rotateYCounterclockwise()))) {
            if(!world.isChunkLoaded(at))return false;
            if(world.getBlockEntity(at) instanceof TileHeatExchange other && other.getFacing()==facing)return false;
        }
        return true;
    }
    private void bind(ExchangeSection value) {
        section=value; revision++; changed=true;
        if(value==null)tankManager.replace();
        else { value.attach(this); tankManager.replace(value.tankOutput,value.tankInput); }
        markDirty();
    }
    private void reconcile() {
        Scan scan=scan();var tiles=scan.tiles;
        if(!scan.complete || tiles.size()<3 || tiles.size()>5) {
            for(var tile:tiles) {
                if(tile.formed || tile.structureSize!=tiles.size()) { tile.revision++; tile.changed=true; tile.markDirty(); }
                tile.formed=false;tile.structure=List.of();tile.structureSize=tiles.size();
                tile.checkNeighbours=false;tile.nextCheck=world.getTime()+20;
                if(tile.section instanceof ExchangeSectionStart start)start.endSection=null;
                tile.updateModel();
            }
            return;
        }
        var positions=tiles.stream().map(tile->tile.getPos().toImmutable()).toList();
        boolean unchanged=tiles.stream().allMatch(tile->tile.formed && tile.structure.equals(positions))
            && tiles.get(0).section instanceof ExchangeSectionStart
            && tiles.get(tiles.size()-1).section instanceof ExchangeSectionEnd
            && tiles.subList(1,tiles.size()-1).stream().allMatch(tile->tile.section==null);
        if(!unchanged) {
            ExchangeSectionStart start=null; ExchangeSectionEnd end=null;
            for(var tile:tiles) {
                if(start==null && tile.section instanceof ExchangeSectionStart found)start=found;
                if(tile.section instanceof ExchangeSectionEnd found)end=found;
            }
            if(start==null)start=new ExchangeSectionStart(tiles.get(0));
            if(end==null)end=new ExchangeSectionEnd(tiles.get(tiles.size()-1));
            for(var tile:tiles) {
                ExchangeSection extra=tile.section;
                if(extra!=null && extra!=start && extra!=end)mergeOrRecover(extra,extra instanceof ExchangeSectionStart?start:end);
            }
            // Detach every old ownership binding before moving the two surviving sections.
            for(var tile:tiles) {tile.formed=false;tile.bind(null);}
            tiles.get(0).bind(start);tiles.get(tiles.size()-1).bind(end);
            start.endSection=end;start.middleCount=tiles.size()-2;
            for(var tile:tiles) {tile.structure=positions;tile.structureSize=tiles.size();tile.formed=true;tile.changed=true;}
        }
        for(var tile:tiles) {
            tile.checkNeighbours=false;tile.nextCheck=world.getTime()+20;tile.updateModel();
        }
    }
    /** Merged structures keep compatible contents; excess/incompatible contents become exact recoverable shards. */
    private static void mergeOrRecover(ExchangeSection extra, ExchangeSection target) {
        var drops=DefaultedList.<ItemStack>of();
        try(Transaction tx=Transaction.openOuter()) {
            for(int i=0;i<2;i++) {
                var from=(i==0?extra.tankInput:extra.tankOutput).getStorage();
                var to=(i==0?target.tankInput:target.tankOutput).getStorage();
                if(from.isResourceBlank())continue;
                var fluid=from.getResource();long amount=from.getAmount();
                long moved=to.insert(fluid,amount,tx);
                if(from.extract(fluid,amount,tx)!=amount)throw new IllegalStateException("Heat section changed during merge");
                buildcraft.api.items.FluidItemDrops.addFluidDrops(drops,FluidStackBC.of(fluid,amount-moved));
            }
            tx.commit();
        }
        InventoryUtil.dropAll(extra.tile.world,extra.tile.pos,drops);
    }
    private void updateModel() {
        var state=getCachedState();Direction facing=getFacing();
        EnumExchangePart part=isStart()?EnumExchangePart.START:isEnd()?EnumExchangePart.END:EnumExchangePart.MIDDLE;
        boolean vertical=false;
        if(formed && section!=null) {
            Direction side=isStart()?Direction.DOWN:Direction.UP;BlockPos neighbor=pos.offset(side);
            vertical=world.isChunkLoaded(neighbor) && FluidStorage.SIDED.find(world,neighbor,side.getOpposite())!=null;
        }
        var updated=state.with(BlockHeatExchange.PROP_PART,part)
            .with(BlockHeatExchange.PROP_CONNECTED_LEFT,aligned(pos.offset(facing.rotateYClockwise()),facing))
            .with(BlockHeatExchange.PROP_CONNECTED_RIGHT,aligned(pos.offset(facing.rotateYCounterclockwise()),facing))
            .with(BlockHeatExchange.PROP_CONNECTED_Y,vertical);
        if(!state.equals(updated)) {
            world.setBlockState(pos,updated,Block.NOTIFY_LISTENERS);
            world.updateNeighborsAlways(pos,state.getBlock());
        }
    }
    private boolean aligned(BlockPos at,Direction facing) {
        return world.isChunkLoaded(at) && world.getBlockState(at).isOf(BCFactoryBlocks.heatExchange)
            && world.getBlockState(at).get(BlockBCBase_Neptune.PROP_FACING)==facing;
    }
    @Override public void onLoad() {super.onLoad();checkNeighbours=true;changed=true;}
    @Override public void onNeighbourBlockChanged(Block block,BlockPos neighbor) {super.onNeighbourBlockChanged(block,neighbor);checkNeighbours=true;}
    @Override public void tick() {
        if(world==null || !isStorageAvailable())return;
        if(world.isClient) {
            if(section instanceof ExchangeSectionStart start) {
                start.animate();
                if(start.progressState==EnumProgressState.RUNNING && world.getTime()%5==0) {
                    var end=getClientEnd();
                    if(end!=null && end.section!=null) {
                        var hot=end.section.tankInput.getFluidForRender();
                        var cold=start.tankInput.getFluidForRender();
                        if(hot!=null && hot.getFluid()==net.minecraft.fluid.Fluids.LAVA) {
                            Direction out=getFacing().rotateYClockwise();
                            world.addParticle(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,pos.getX()+.5+out.getOffsetX()*.6,
                                pos.getY()+.5,pos.getZ()+.5+out.getOffsetZ()*.6,out.getOffsetX()*.1,.03,out.getOffsetZ()*.1);
                        }
                        if(cold!=null && cold.getFluid()==net.minecraft.fluid.Fluids.WATER)
                            world.addParticle(net.minecraft.particle.ParticleTypes.CLOUD,end.pos.getX()+.5,end.pos.getY()+1,
                                end.pos.getZ()+.5,0,.1,0);
                    }
                }
            }
            return;
        }
        if(checkNeighbours || world.getTime()>=nextCheck || formed && !structureLive())reconcile();
        if(section instanceof ExchangeSectionStart start) {
            start.tick(structureLive());
            if(structureLive()) {
                output(start,start.getTile().getFacing().rotateYClockwise());
                if(start.endSection!=null)output(start.endSection,Direction.UP);
            }
        }
        if(changed && world.getTime()%10==0) {changed=false;sendNetworkUpdate(NET_RENDER_DATA);}
    }
    private static void output(ExchangeSection section,Direction direction) {
        BlockPos neighbor=section.tile.pos.offset(direction);
        if(!section.tile.world.isChunkLoaded(neighbor))return;
        var destination=FluidStorage.SIDED.find(section.tile.world,neighbor,direction.getOpposite());
        if(destination!=null)StorageUtil.move(section.tankOutput,destination,variant->true,FluidConstants.BUCKET,null);
    }
    @Override public Storage<FluidVariant> getFluidStorage(Direction side) {
        if(world==null || world.isClient || section==null || !structureLive())return null;
        ExchangeSection captured=section;long epoch=revision;Direction facing=getFacing();
        Storage<FluidVariant> access=null;
        if(side==null)access=tankManager;
        else if(section instanceof ExchangeSectionStart) {
            if(side==Direction.DOWN)access=section.tankInput;
            else if(side==facing.rotateYClockwise())access=section.tankOutput;
        } else {
            if(side==Direction.UP)access=section.tankOutput;
            else if(side==facing.rotateYCounterclockwise())access=section.tankInput;
        }
        return LiveTileStorage.of(access,FluidVariant.blank(),()->revision==epoch && section==captured && structureLive());
    }
    @Override public boolean hasWork() {
        if(world==null || world.isClient || !structureLive())return false;
        var controller=(TileHeatExchange)world.getBlockEntity(structure.get(0));
        return controller!=null && controller.section instanceof ExchangeSectionStart start && start.exchange(false)>0;
    }
    @Override public boolean onActivated(PlayerEntity player,Hand hand,Direction face,float x,float y,float z) {
        if(!canInteractWith(player) || section==null || player.getStackInHand(hand).getItem() instanceof buildcraft.api.tools.IToolWrench)return false;
        var context=ContainerItemContext.forPlayerInteraction(player,hand);
        if(world.isClient)return context.find(FluidStorage.ITEM)!=null;
        Storage<FluidVariant> access=structureLive()?tankManager:new FluidStorageAccess(tankManager,false,true);
        return FluidTransfers.interact(access,context,player);
    }
    @Override public ActionResult attemptRotation(PlayerEntity player,Direction side) {
        if(!canInteractWith(player))return ActionResult.FAIL;
        Scan scan=scan();
        if(!scan.complete || scan.tiles.size()>5)return ActionResult.FAIL;
        for(var tile:scan.tiles)if(!world.canPlayerModifyAt(player,tile.pos) || !tile.canPlayerEdit(player))return ActionResult.FAIL;
        if(world.isClient)return ActionResult.SUCCESS;
        Direction direction=scan.tiles.size()==1?getFacing().rotateYClockwise():getFacing().getOpposite();
        for(var tile:scan.tiles) {tile.formed=false;tile.revision++;tile.checkNeighbours=true;}
        for(var tile:scan.tiles)world.setBlockState(tile.pos,tile.getCachedState().with(BlockBCBase_Neptune.PROP_FACING,direction));
        reconcile();
        return ActionResult.CONSUME;
    }
    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if(section!=null)nbt.put("section",section.writeNbt());
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);
        var data=nbt.getCompound("section");
        if(data.isEmpty())bind(null);
        else {
            ExchangeSection restored=data.getBoolean("start")?new ExchangeSectionStart(this):new ExchangeSectionEnd(this);
            if(restored instanceof ExchangeSectionStart start) {
                start.progress=Math.max(0,Math.min(WARMUP_TICKS,data.getInt("progress")));
                start.progressLast=start.progress;
                start.progressState=start.progress==0?EnumProgressState.OFF:EnumProgressState.STOPPING;
            }
            bind(restored);
            // Read actual old section tanks when no native root tank manager was saved.
            if(!nbt.contains("tanks")) {
                restored.tankInput.readFromNBT(data.getCompound("input"));
                restored.tankOutput.readFromNBT(data.getCompound("output"));
            }
        }
        formed=false;structure=List.of();checkNeighbours=true;
    }

    public enum EnumProgressState {OFF,PREPARING,RUNNING,STOPPING}
    public abstract static class ExchangeSection {
        private TileHeatExchange tile;
        public final ExchangeTank tankInput,tankOutput;
        ExchangeSection(TileHeatExchange tile,boolean heating) {
            this.tile=tile;
            tankInput=new ExchangeTank("input",tile,false);
            tankInput.setFilter(fluid->lookup(fluid,heating)!=null);
            tankOutput=new ExchangeTank("output",tile,true);
        }
        private void attach(TileHeatExchange tile) {
            this.tile=tile;tankInput.attach(tile);tankOutput.attach(tile);
        }
        public TileHeatExchange getTile() {return tile;}
        NbtCompound writeNbt() {
            NbtCompound tag=new NbtCompound();tag.putBoolean("start",this instanceof ExchangeSectionStart);return tag;
        }
    }
    public static final class ExchangeSectionEnd extends ExchangeSection {
        ExchangeSectionEnd(TileHeatExchange tile) {super(tile,false);}
    }
    public static final class ExchangeSectionStart extends ExchangeSection {
        private ExchangeSectionEnd endSection;
        public int middleCount;
        private int progress,progressLast;
        private EnumProgressState progressState=EnumProgressState.OFF;
        ExchangeSectionStart(TileHeatExchange tile) {super(tile,true);}
        public ExchangeSectionEnd getEndSection() {return endSection;}
        public EnumProgressState getProgressState() {return progressState;}
        public double getProgress(float partial) {return (progressLast+(progress-progressLast)*partial)/WARMUP_TICKS;}
        public int getWarmupTicks() {return progress;}
        @Override NbtCompound writeNbt() {var tag=super.writeNbt();tag.putInt("progress",progress);return tag;}
        private long exchange(boolean commit) {
            if(endSection==null || middleCount<1 || middleCount>3)return 0;
            var hot=lookup(endSection.tankInput.getFluid(),false);
            var cold=lookup(tankInput.getFluid(),true);
            if(hot==null || cold==null || hot.heatFrom()<=cold.heatFrom())return 0;
            long count=Math.min(RATES[middleCount-1],Math.min(tankInput.getStorage().getAmount(),endSection.tankInput.getStorage().getAmount()));
            count=Math.min(count,room(tankOutput,hot.out()));
            count=Math.min(count,room(endSection.tankOutput,cold.out()));
            if(count<=0)return 0;
            try(Transaction tx=Transaction.openOuter()) {
                if(tankInput.getStorage().extract(cold.in().getFluidVariant(),count,tx)!=count
                    || endSection.tankInput.getStorage().extract(hot.in().getFluidVariant(),count,tx)!=count
                    || !put(tankOutput,hot.out(),count,tx) || !put(endSection.tankOutput,cold.out(),count,tx))return 0;
                if(commit)tx.commit();return count;
            }
        }
        private static long room(Tank tank,FluidStackBC output) {
            if(output==null)return Long.MAX_VALUE;
            var storage=tank.getStorage();
            return storage.isResourceBlank() || storage.getResource().equals(output.getFluidVariant())?storage.getCapacity()-storage.getAmount():0;
        }
        private static boolean put(Tank tank,FluidStackBC output,long count,Transaction tx) {
            return output==null || tank.getStorage().insert(output.getFluidVariant(),count,tx)==count;
        }
        private void tick(boolean valid) {
            progressLast=progress;EnumProgressState before=progressState;
            if(!valid || exchange(false)==0) {
                progress=Math.max(0,progress-1);progressState=progress==0?EnumProgressState.OFF:EnumProgressState.STOPPING;
            } else if(progressState==EnumProgressState.OFF) {
                progressState=EnumProgressState.PREPARING;
            } else {
                progress=Math.min(WARMUP_TICKS,progress+1);
                progressState=progress==WARMUP_TICKS?EnumProgressState.RUNNING:EnumProgressState.PREPARING;
                if(progressState==EnumProgressState.RUNNING && exchange(true)>0)
                    AdvancementUtil.unlockAdvancement((ServerWorld)getTile().world,getTile().getOwner().getId(),
                        new Identifier("buildcraftfactory","heat_exchange"));
            }
            if(progress!=progressLast || before!=progressState) {getTile().changed=true;getTile().markDirty();}
        }
        private void animate() {
            progressLast=progress;
            if(progressState==EnumProgressState.PREPARING)progress=Math.min(WARMUP_TICKS,progress+1);
            if(progressState==EnumProgressState.STOPPING)progress=Math.max(0,progress-1);
        }
    }
    private static IHeatExchangerRecipe lookup(FluidStackBC fluid,boolean heating) {
        var manager=BuildcraftRecipeRegistry.refineryRecipes;
        if(manager==null || fluid==null || fluid.isEmpty())return null;
        IHeatExchangerRecipe recipe=heating?manager.getHeatableRegistry().getRecipeForInput(fluid):manager.getCoolableRegistry().getRecipeForInput(fluid);
        if(recipe==null || recipe.in()==null || recipe.in().isEmpty() || recipe.out()!=null && recipe.out().isEmpty())return null;
        return (heating?recipe.heatTo()>recipe.heatFrom():recipe.heatFrom()>recipe.heatTo())?recipe:null;
    }
    public static final class ExchangeTank extends Tank {
        private TileHeatExchange owner;
        private final boolean output;
        private FluidStackBC visible=FluidStackBC.EMPTY;
        ExchangeTank(String name,TileHeatExchange tile,boolean output) {super(name,2000,tile);this.owner=tile;this.output=output;}
        private void attach(TileHeatExchange owner) {this.owner=owner;this.tile=owner;}
        @Override protected void onContentsChanged() {super.onContentsChanged();owner.changed=true;}
        @Override public long insert(FluidVariant variant,long max,TransactionContext tx) {
            StoragePreconditions.notBlankNotNegative(variant,max);return output?0:super.insert(variant,max,tx);
        }
        @Override public boolean supportsInsertion() {return !output && super.supportsInsertion();}
        @Override public FluidStackBC getFluidForRender() {return visible.isEmpty()?null:visible;}
        @Override public int getClientAmount() {return (int)visible.getAmountMB();}
    }
    @Override public void writePayload(int id,PacketBufferBC buffer,NetSide side) {
        super.writePayload(id,buffer,side);
        if(side==NetSide.SERVER && (id==NET_RENDER_DATA || id==NET_GUI_TICK)) {
            buffer.writeBoolean(formed);buffer.writeByte(Math.min(13,structureSize));
            buffer.writeBoolean(section!=null);
            if(section!=null) {
                buffer.writeBoolean(section instanceof ExchangeSectionStart);
                for(Tank tank:List.of(section.tankInput,section.tankOutput)) {
                    tank.getStorage().getResource().toPacket(buffer);buffer.writeLong(tank.getStorage().getAmount());
                }
                if(section instanceof ExchangeSectionStart start) {
                    buffer.writeByte(start.middleCount);buffer.writeByte(start.progress);buffer.writeByte(start.progressState.ordinal());
                }
            }
        }
    }
    @Override public void readPayload(int id,PacketBufferBC buffer,NetSide side,Object ctx) throws IOException {
        super.readPayload(id,buffer,side,ctx);
        if(side==NetSide.CLIENT && (id==NET_RENDER_DATA || id==NET_GUI_TICK)) {
            boolean active=buffer.readBoolean();int size=buffer.readUnsignedByte();
            if(size>13 || active && (size<3 || size>5))throw new IOException("Invalid heat exchanger structure");
            ExchangeSection value=null;
            if(buffer.readBoolean()) {
                boolean start=buffer.readBoolean();
                FluidStackBC[] fluids=new FluidStackBC[2];
                for(int i=0;i<2;i++) {
                    FluidVariant fluid=FluidVariant.fromPacket(buffer);long amount=buffer.readLong();
                    if(amount<0 || amount>2*FluidConstants.BUCKET || fluid.isBlank()!=(amount==0))throw new IOException("Invalid heat exchanger tank");
                    fluids[i]=FluidStackBC.of(fluid,amount);
                }
                if(start) {
                    int middles=buffer.readUnsignedByte(),progress=buffer.readUnsignedByte(),state=buffer.readUnsignedByte();
                    if(middles>3 || progress>WARMUP_TICKS || state>=EnumProgressState.values().length
                        || active && middles!=size-2)throw new IOException("Invalid heat exchanger progress");
                    var first=new ExchangeSectionStart(this);first.middleCount=middles;first.progress=first.progressLast=progress;
                    first.progressState=EnumProgressState.values()[state];value=first;
                } else value=new ExchangeSectionEnd(this);
                value.tankInput.visible=fluids[0];value.tankOutput.visible=fluids[1];
            }
            formed=active;structureSize=size;section=value;
        }
    }
    @Override public void getDebugInfo(List<String> left,List<String> right,Direction side) {
        left.add("formed = "+formed+"; blocks = "+structureSize);
        if(section!=null) {
            left.add("section = "+(section instanceof ExchangeSectionStart?"start":"end"));
            left.add("input = "+section.tankInput.getDebugString());left.add("output = "+section.tankOutput.getDebugString());
            if(section instanceof ExchangeSectionStart start)left.add("warmup = "+start.progress+"; state = "+start.progressState);
        }
    }
}
