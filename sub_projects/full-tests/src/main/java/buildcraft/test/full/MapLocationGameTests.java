/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;
import java.util.*;
import buildcraft.api.items.IMapLocation.MapLocationType;
import buildcraft.core.*;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.core.marker.*;
import buildcraft.core.tile.*;
import buildcraft.robotics.zone.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import io.netty.buffer.Unpooled;

public final class MapLocationGameTests implements FabricGameTest {
    private static final Map<ServerWorld,Set<BlockPos>> DENIED=new WeakHashMap<>();
    static {UseBlockCallback.EVENT.register((p,w,h,hit)->DENIED.getOrDefault(w,Set.of()).contains(hit.getBlockPos())?ActionResult.FAIL:ActionResult.PASS);}
    private static BlockPos base(TestContext c){return c.getAbsolutePos(new BlockPos(2,150,2));}
    private static ServerPlayerEntity player(TestContext c){var p=c.createMockCreativeServerPlayerInWorld();var at=base(c);p.getInventory().clear();p.refreshPositionAndAngles(at.getX(),at.getY()+1,at.getZ()+1,0,0);return p;}
    private static ActionResult use(ServerPlayerEntity p,BlockPos at){return p.interactionManager.interactBlock(p,p.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at),Direction.WEST,at,false));}
    private static ItemStack blank(int count){return new ItemStack(BCCoreItems.mapLocation,count);}
    private static ItemStack recorded(ServerPlayerEntity p){return p.getInventory().main.stream().filter(ItemMapLocation::isRecorded).findFirst().orElseThrow();}
    private static void check(TestContext c,boolean ok,String message){c.assertTrue(ok,message);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeCaptureSplitsOneRecordAndDoesNotOpenChest(TestContext c){
        var at=base(c);c.getWorld().setBlockState(at,Blocks.CHEST.getDefaultState());var p=player(c);var input=blank(3);input.setCustomName(Text.literal("Keep display"));p.setStackInHand(Hand.MAIN_HAND,input);
        check(c,use(p,at).isAccepted()&&p.currentScreenHandler==p.playerScreenHandler,"Capture did not precede chest interaction");var result=recorded(p);
        check(c,p.getMainHandStack().getCount()==2&&!ItemMapLocation.isRecorded(p.getMainHandStack())&&p.getMainHandStack().getMaxCount()==16&&result.getCount()==1&&result.getMaxCount()==1,"Split capture lost stacks or limits");
        check(c,at.equals(BCCoreItems.mapLocation.getPoint(result))&&BCCoreItems.mapLocation.getPointSide(result)==Direction.WEST&&result.hasCustomName()&&c.getWorld().getRegistryKey().getValue().equals(ItemMapLocation.getDimension(result)),"Point/side/name/dimension lost");
        var loaded=ItemStack.fromNbt(result.writeNbt(new NbtCompound()));check(c,ItemStack.areEqual(loaded,result)&&BCCoreItems.mapLocation.getBox(loaded).min().equals(at),"Saved native record lost data");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullInventoryDropsExactlyOneRecordWithoutLosingBlank(TestContext c){
        var p=player(c);var at=base(c);c.getWorld().setBlockState(at,Blocks.STONE.getDefaultState());for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.COBBLESTONE,64));p.setStackInHand(Hand.MAIN_HAND,blank(2));
        var before=c.getWorld().getEntitiesByClass(ItemEntity.class,p.getBoundingBox().expand(5),e->ItemMapLocation.isRecorded(e.getStack())).size();
        check(c,use(p,at).isAccepted()&&p.getMainHandStack().getCount()==1,"Full inventory consumed wrong blanks");var drops=c.getWorld().getEntitiesByClass(ItemEntity.class,p.getBoundingBox().expand(5),e->ItemMapLocation.isRecorded(e.getStack()));check(c,drops.size()==before+1,"Recorded item lost at full inventory");drops.forEach(ItemEntity::discard);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void clearingPreservesUnrelatedNbtAndReadingDoesNotCreateTags(TestContext c){
        var fresh=blank(1);check(c,BCCoreItems.mapLocation.getZone(fresh)==null&&BCCoreItems.mapLocation.getPoint(fresh)==null&&BCCoreItems.mapLocation.getLabel(fresh).isEmpty()&&!fresh.hasNbt(),"Read-only map API mutated blank");
        var p=player(c);var at=base(c);c.getWorld().setBlockState(at,Blocks.STONE.getDefaultState());p.setStackInHand(Hand.MAIN_HAND,fresh);use(p,at);var stack=p.getMainHandStack();stack.setCustomName(Text.literal("Keep"));stack.getOrCreateNbt().putString("addon","preserved");BCCoreItems.mapLocation.setName(stack,"Remove label");
        check(c,use(p,at)==ActionResult.FAIL,"Recorded map overwrote itself");p.setSneaking(true);check(c,use(p,at).isAccepted()&&!ItemMapLocation.isRecorded(stack)&&stack.hasCustomName()&&stack.getNbt().getString("addon").equals("preserved")&&BCCoreItems.mapLocation.getLabel(stack).isEmpty(),"Clear lost unrelated NBT or retained record");
        stack.getOrCreateNbt().putString("chunkMapping","malformed");BCCoreItems.mapLocation.use(c.getWorld(),p,Hand.MAIN_HAND);check(c,!ItemMapLocation.isRecorded(stack),"Malformed map could not be cleared");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeProtectionTransactionsAndSpectatorsRejectRecording(TestContext c){
        var p=player(c);var at=base(c);c.getWorld().setBlockState(at,Blocks.STONE.getDefaultState());p.setStackInHand(Hand.MAIN_HAND,blank(2));DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(at);try{check(c,use(p,at)==ActionResult.FAIL&&!ItemMapLocation.isRecorded(p.getMainHandStack()),"Protection bypassed");}finally{DENIED.get(c.getWorld()).remove(at);}
        try(var tx=Transaction.openOuter()){check(c,use(p,at)==ActionResult.FAIL&&p.getMainHandStack().getCount()==2,"Transaction mutated map inventory");}
        var spectator=new ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"map-spectator"));spectator.networkHandler=p.networkHandler;spectator.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);spectator.setStackInHand(Hand.MAIN_HAND,blank(1));
        check(c,ItemMapLocation.useFirst(spectator,c.getWorld(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at),Direction.UP,at,false))==ActionResult.FAIL,"Spectator recorded map");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void areaMarkersAndLoopingPathCaptureWithoutChangingConnections(TestContext c){
        var at=base(c);var p=player(c);var end=at.east(3);
        for(var pos:List.of(at,end)){c.getWorld().setBlockState(pos.down(),Blocks.STONE.getDefaultState());c.getWorld().setBlockState(pos,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));((TileMarkerVolume)c.getWorld().getBlockEntity(pos)).onLoad();}
        check(c,VolumeCache.INSTANCE.getSubCache(c.getWorld()).tryConnect(at,end),"Area fixture did not connect");p.setStackInHand(Hand.MAIN_HAND,blank(1));check(c,use(p,at).isAccepted(),"Area capture failed");var area=BCCoreItems.mapLocation.getBox(p.getMainHandStack());check(c,area!=null&&area.min().equals(at)&&area.max().equals(end),"Area bounds lost");
        for(var pos:List.of(at,end))c.getWorld().breakBlock(pos,false);
        var path=List.of(at,at.east(3),at.east(3).south(3));for(var pos:path){c.getWorld().setBlockState(pos.down(),Blocks.STONE.getDefaultState());c.getWorld().setBlockState(pos,BCCoreBlocks.markerPath.getDefaultState().with(BCCoreBlocks.markerPath.getFacingProperty(),Direction.UP));((TileMarkerPath)c.getWorld().getBlockEntity(pos)).onLoad();}
        var cache=PathCache.INSTANCE.getSubCache(c.getWorld());check(c,cache.tryConnect(path.get(0),path.get(1))&&cache.tryConnect(path.get(1),path.get(2)),"Path fixture did not connect");var marker=(TileMarkerPath)c.getWorld().getBlockEntity(at);var connection=marker.getCurrentConnection();check(c,connection.addMarker(path.get(0),path.get(2)),"Path loop fixture failed");var expected=marker.getPath();p.setStackInHand(Hand.MAIN_HAND,blank(2));check(c,use(p,at).isAccepted(),"Path capture failed");var result=recorded(p);check(c,MapLocationType.getFromStack(result)==MapLocationType.PATH_REPEATING&&BCCoreItems.mapLocation.getPath(result).equals(expected)&&marker.getPath().equals(expected)&&!ItemMapLocation.isRecorded(p.getMainHandStack()),"Loop order/source/blank remainder changed");for(var pos:path)c.getWorld().breakBlock(pos,false);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedMapDataDoesNotCrashOrInventCoordinates(TestContext c){
        var stack=blank(1);var n=stack.getOrCreateNbt();MapLocationType.SPOT.setToStack(stack);n.putInt("x",1);n.putByte("side",(byte)-1);check(c,BCCoreItems.mapLocation.getPoint(stack)==null&&BCCoreItems.mapLocation.getPointSide(stack)==null,"Malformed point accepted");MapLocationType.PATH.setToStack(stack);n.putString("path","wrong");check(c,BCCoreItems.mapLocation.getPath(stack)==null,"Malformed path accepted");MapLocationType.ZONE.setToStack(stack);n.putString("chunkMapping","wrong");check(c,BCCoreItems.mapLocation.getZone(stack)==null,"Malformed zone accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullZoneCopyEraseAndOffsetRetainEveryColumn(TestContext c){
        var plan=new ZonePlan();for(int x=-16;x<0;x++)for(int z=0;z<16;z++)plan.set(x,z,true);var copy=new ZonePlan(plan);check(c,copy.getAll().size()==256,"Full chunk copy became empty");copy.set(-16,0,false);check(c,copy.getAll().size()==255&&copy.get(-1,15)&&plan.get(-16,0),"Erasing one cell erased corner or original");var shifted=copy.getWithOffset(32,-32);check(c,shifted.get(31,-17)&&!shifted.get(16,-32),"Negative chunk offset corrupted selection");
        var stack=blank(1);ItemMapLocation.setZone(stack,copy);var zone=BCCoreItems.mapLocation.getZone(ItemStack.fromNbt(stack.writeNbt(new NbtCompound())));check(c,zone!=null&&zone.contains(new Vec3d(-.5,-64,15.5))&&!zone.contains(new Vec3d(-15.5,300,.5)),"Saved zone lost columns");check(c,copy.distanceToSquared(new BlockPos(-16,50,0))==1&&copy.distanceToSquared(new BlockPos(-1,-64,15))==0,"Zone distance uses chunk center or wrong shift");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void zoneDeserializationReplacesStateAndMalformedInputIsAtomic(TestContext c){
        var old=new ZonePlan();old.set(0,0,true);var next=new ZonePlan();next.set(-17,-18,true);var tag=new NbtCompound();next.writeToNBT(tag);old.readFromNBT(tag);check(c,!old.get(0,0)&&old.get(-17,-18),"NBT merged stale region");
        var bad=tag.copy();bad.getList("chunkMapping",10).getCompound(0).putByteArray("bits",new byte[33]);boolean rejected=false;try{old.readFromNBT(bad);}catch(IllegalArgumentException expected){rejected=true;}check(c,rejected&&old.get(-17,-18),"Malformed NBT erased valid region");
        var buf=new PacketByteBuf(Unpooled.buffer());try{buf.writeInt(1);buf.writeInt(0);buf.writeInt(0);buf.writeByte(1);buf.writeByteArray(new byte[33]);rejected=false;try{old.readFromByteBuf(buf);}catch(RuntimeException expected){rejected=true;}check(c,rejected&&old.get(-17,-18),"Malformed packet partially replaced region");buf.clear();new ZonePlan().writeToByteBuf(buf);old.readFromByteBuf(buf);check(c,old.getAll().isEmpty(),"Empty packet retained old cells");}finally{buf.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void emptyAndReusedZoneChunksRemainSafe(TestContext c){
        var chunk=new ZoneChunk();check(c,chunk.isEmpty()&&chunk.getRandomBlockPos(new Random(0))==null,"Empty chunk failed");chunk.set(15,15,true);chunk.readFromNBT(new NbtCompound());check(c,chunk.isEmpty(),"NBT retained old bits");for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.set(x,z,true);var buf=new PacketByteBuf(Unpooled.buffer());try{buf.writeByte(0);chunk.readFromByteBuf(buf);check(c,chunk.isEmpty(),"Packet retained full chunk");}finally{buf.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeSurvivalMapRecipeIsRegistered(TestContext c){
        var r=(net.minecraft.recipe.CraftingRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcraftcore:map_location")).orElseThrow();
        var grid=new buildcraft.lib.tile.craft.WorkbenchCrafting.Grid(2,2);grid.setStack(0,new ItemStack(Items.PAPER));check(c,!r.matches(grid,c.getWorld()),"Map recipe accepted missing redstone");grid.setStack(3,new ItemStack(Items.REDSTONE));
        check(c,r.matches(grid,c.getWorld()),"Native survival ingredients rejected");var out=r.craft(grid,c.getWorld().getRegistryManager());check(c,out.isOf(BCCoreItems.mapLocation)&&out.getCount()==4&&!ItemMapLocation.isRecorded(out),"Map recipe output wrong");c.complete();
    }
}
