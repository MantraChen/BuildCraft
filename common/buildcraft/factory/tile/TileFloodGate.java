/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native Fabric flood gate. Remake by FelixMantra.
 */
package buildcraft.factory.tile;

import java.io.IOException;
import java.util.*;
import it.unimi.dsi.fastutil.longs.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.lib.fluid.Tank;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.FakePlayerProvider;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;

/** Places one bucket every sixteen active ticks through connected open outlets, without an MJ cost. */
public class TileFloodGate extends TileBC_Neptune implements buildcraft.lib.tile.TickingBlockEntity, IDebuggable {
    private static final int[] REBUILD_DELAYS={16,32,64,128,256};
    private static final int SEARCH_BUDGET=1024, MAX_TARGETS=4096, RADIUS=64;
    private static final int SIDE_MASK=63 & ~(1<<Direction.UP.ordinal());
    public final Tank tank;
    public final Set<Direction> openSides=EnumSet.noneOf(Direction.class);
    public final Deque<BlockPos> queue=new ArrayDeque<>();
    private final Long2LongOpenHashMap parents=new Long2LongOpenHashMap();
    private final LongArrayFIFOQueue frontier=new LongArrayFIFOQueue();
    private FluidVariant searchFluid=FluidVariant.blank(), clientFluid=FluidVariant.blank();
    private long clientAmount;
    private boolean searching, contentsDirty=true;
    private int delayIndex,tick,lastSides=-1;

    public TileFloodGate(BlockEntityType<?> type,BlockPos pos,BlockState state){
        super(type,pos,state);
        tank=tankManager.add(new Tank("tank",2000,this));
        for(var side:BlockFloodGate.CONNECTED_MAP.keySet())if(state.get(BlockFloodGate.CONNECTED_MAP.get(side)))openSides.add(side);
    }
    public long getFluidAmount(){return world!=null && world.isClient?clientAmount:tank.getStorage().getAmount();}
    public FluidVariant getFluidVariant(){return world!=null && world.isClient?clientFluid:tank.getStorage().getResource();}
    public boolean isSearching(){return searching;}
    public int getVisitedCount(){return parents.size();}
    private int mask(){int mask=0;for(var side:openSides)mask|=1<<side.ordinal();return mask&SIDE_MASK;}
    private void setMask(int mask){openSides.clear();for(var side:BlockFloodGate.CONNECTED_MAP.keySet())if((mask&(1<<side.ordinal()))!=0)openSides.add(side);}
    @Override public void markDirty(){super.markDirty();contentsDirty=true;}
    @Override public void onLoad(){super.onLoad();resetSearch();contentsDirty=true;syncBlockState();}
    @Override public void onChunkUnload(){resetSearch();super.onChunkUnload();}
    private void resetSearch(){queue.clear();parents.clear();frontier.clear();searching=false;tick=0;}
    private void syncBlockState(){
        if(world==null || world.isClient || !isStorageAvailable())return;
        var before=getCachedState();var next=before;
        for(var side:BlockFloodGate.CONNECTED_MAP.keySet())next=next.with(BlockFloodGate.CONNECTED_MAP.get(side),openSides.contains(side));
        // Outlet properties change the model; fluid capabilities remain available on every face.
        // Avoid neighbor notifications that could load a chunk beyond an unloaded outlet.
        if(next!=before)world.setBlockState(pos,next,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
    }
    public boolean toggleSide(PlayerEntity player,Direction side){
        if(!BlockFloodGate.CONNECTED_MAP.containsKey(side) || !canInteractWith(player))return false;
        if(world.isClient)return true;
        if(!openSides.remove(side))openSides.add(side);
        resetSearch();delayIndex=0;lastSides=mask();markDirty();syncBlockState();sendNetworkUpdate(NET_RENDER_DATA);return true;
    }
    private boolean loaded(BlockPos cell){return world.isInBuildLimit(cell) && world.getWorldBorder().contains(cell) && world.isChunkLoaded(cell);}
    private boolean matches(BlockPos cell){
        return loaded(cell) && !searchFluid.isBlank() && world.getBlockState(cell).getBlock() instanceof FluidBlock
            && searchFluid.getFluid().matchesType(world.getFluidState(cell).getFluid());
    }
    private boolean canFill(BlockPos cell){return loaded(cell) && (world.isAir(cell) || matches(cell) && !world.getFluidState(cell).isStill());}
    private boolean canSearch(BlockPos cell){return loaded(cell) && (world.isAir(cell) || matches(cell));}
    private static BlockState sourceState(FluidVariant variant){
        if(variant.isBlank())return Blocks.AIR.getDefaultState();
        var fluid=variant.getFluid();if(fluid instanceof FlowableFluid flowing)fluid=flowing.getStill();
        return fluid.getDefaultState().getBlockState();
    }
    private void enqueue(BlockPos cell,long parent){
        if(cell.equals(pos) || cell.getSquaredDistance(pos)>(long)RADIUS*RADIUS || parents.containsKey(cell.asLong()) || !canSearch(cell))return;
        parents.put(cell.asLong(),parent);frontier.enqueue(cell.asLong());
    }
    private void startSearch(){
        resetSearch();searching=true;
        for(var face:Direction.values())if(openSides.contains(face) && face!=Direction.UP)enqueue(pos.offset(face),pos.asLong());
    }
    private void searchStep(){
        Direction vertical=FluidVariantAttributes.isLighterThanAir(searchFluid)?Direction.UP:Direction.DOWN;
        Direction[] directions={vertical,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
        for(int budget=SEARCH_BUDGET;budget>0 && !frontier.isEmpty();budget--){
            var cell=BlockPos.fromLong(frontier.dequeueLong());if(!canSearch(cell))continue;
            if(canFill(cell)){
                queue.addLast(cell);
                if(queue.size()>=MAX_TARGETS){frontier.clear();break;}
            }
            for(var direction:directions)enqueue(cell.offset(direction),cell.asLong());
        }
        if(frontier.isEmpty())searching=false;
    }
    private boolean pathValid(BlockPos target){
        long key=target.asLong();
        for(int left=parents.size();left>0;left--){
            if(!parents.containsKey(key))return false;
            long parent=parents.get(key);var here=BlockPos.fromLong(key);
            if(parent==pos.asLong()){
                var side=Direction.fromVector(here.getX()-pos.getX(),here.getY()-pos.getY(),here.getZ()-pos.getZ());
                return side!=null && side!=Direction.UP && openSides.contains(side);
            }
            var previous=BlockPos.fromLong(parent);if(!matches(previous))return false;key=parent;
        }
        return false;
    }
    private void place(BlockPos target){
        if(!canFill(target) || !pathValid(target)){resetSearch();return;}
        var player=FakePlayerProvider.INSTANCE.getFakePlayer((ServerWorld)world,getOwner(),target);
        if(!world.canPlayerModifyAt(player,target))return;
        var before=world.getBlockState(target);var item=searchFluid.getFluid().getBucketItem();
        var held=player.getMainHandStack();player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item==null || item==Items.AIR?Items.BUCKET:item));
        try{
            var hit=new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false);
            if(UseBlockCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,hit)!=ActionResult.PASS
                || !isStorageAvailable() || world.getBlockState(target)!=before || !canFill(target) || !pathValid(target))return;
            try(var transaction=Transaction.openOuter()){
                if(tank.extract(searchFluid,FluidConstants.BUCKET,transaction)!=FluidConstants.BUCKET)return;
                // Native buckets preserve dimension behavior, including water evaporation in the Nether.
                // Placeable add-on fluids without a bucket still use their native source block state.
                boolean placed=item instanceof BucketItem bucket?bucket.placeFluid(player,world,target,hit)
                    :world.setBlockState(target,sourceState(searchFluid),Block.NOTIFY_ALL);
                if(!placed)return;
                transaction.commit();
            }
        }finally{player.setStackInHand(Hand.MAIN_HAND,held);}
        delayIndex=0;markDirty();
        if(getOwner()!=null)AdvancementUtil.unlockAdvancement((ServerWorld)world,getOwner().getId(),new Identifier("buildcraftfactory","flooding_the_world"));
    }
    @Override public void tick(){
        if(world==null || world.isClient || !isStorageAvailable())return;
        if(contentsDirty){contentsDirty=false;sendNetworkUpdate(NET_RENDER_DATA);}
        var current=tank.getStorage().getResource();int sides=mask();
        if(!current.equals(searchFluid) || sides!=lastSides){resetSearch();searchFluid=current;lastSides=sides;delayIndex=0;syncBlockState();}
        if(tank.getStorage().getAmount()<FluidConstants.BUCKET || sides==0 || !(sourceState(current).getBlock() instanceof FluidBlock))return;
        tick++;
        if(searching){searchStep();if(searching)return;}
        if(queue.isEmpty()){
            if(tick>=REBUILD_DELAYS[delayIndex]){startSearch();delayIndex=Math.min(delayIndex+1,REBUILD_DELAYS.length-1);}
            return;
        }
        if(tick>=16){tick=0;place(queue.removeFirst());}
    }
    @Override public void writeNbt(NbtCompound nbt){super.writeNbt(nbt);nbt.putByte("openSides",(byte)mask());}
    @Override public void readFromNBT(NbtCompound nbt){
        super.readFromNBT(nbt);
        if(nbt.contains("openSides",NbtElement.NUMBER_TYPE))setMask(nbt.getByte("openSides"));
        else if(nbt.contains("openSides",NbtElement.BYTE_ARRAY_TYPE)){
            var bits=BitSet.valueOf(nbt.getByteArray("openSides"));int mask=0;
            for(var side:BlockFloodGate.CONNECTED_MAP.keySet())if(bits.get(side.ordinal()))mask|=1<<side.ordinal();setMask(mask);
        }
        resetSearch();searchFluid=FluidVariant.blank();lastSides=-1;delayIndex=0;contentsDirty=true;
    }
    @Override public void writePayload(int id,PacketBufferBC buffer,NetSide side){
        super.writePayload(id,buffer,side);
        if(side==NetSide.SERVER && id==NET_RENDER_DATA){buffer.writeByte(mask());tank.getStorage().getResource().toPacket(buffer);buffer.writeLong(tank.getStorage().getAmount());}
    }
    @Override public void readPayload(int id,PacketBufferBC buffer,NetSide side,Object ctx) throws IOException {
        super.readPayload(id,buffer,side,ctx);
        if(side==NetSide.CLIENT && id==NET_RENDER_DATA){
            int mask=buffer.readUnsignedByte();var fluid=FluidVariant.fromPacket(buffer);long amount=buffer.readLong();
            if((mask&~SIDE_MASK)!=0 || amount<0 || amount>2*FluidConstants.BUCKET || fluid.isBlank()!=(amount==0))throw new IOException("Invalid flood gate state");
            setMask(mask);clientFluid=fluid;clientAmount=amount;redrawBlock();
        }
    }
    @Override public void getDebugInfo(List<String> left,List<String> right,Direction side){
        left.add("fluid = "+getFluidAmount()+" droplets of "+getFluidVariant());left.add("open sides = "+openSides);
        left.add("search = "+searching+", visited = "+parents.size()+", queued = "+queue.size());left.add("delay = "+REBUILD_DELAYS[delayIndex]+", tick = "+tick);
    }
}
