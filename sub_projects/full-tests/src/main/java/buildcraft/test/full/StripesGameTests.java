/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.api.transport.pipe.*;
import buildcraft.lib.misc.*;
import buildcraft.transport.*;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.plug.*;
import buildcraft.transport.stripes.PipeExtensionManager;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;

public final class StripesGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,4,3);
    private static final Map<ServerWorld,Set<BlockPos>> USE_DENIED=new WeakHashMap<>(),BREAK_DENIED=new WeakHashMap<>();
    private static final Set<UUID> ENTITY_DENIED=new HashSet<>();
    private static final Map<ServerWorld,Map<BlockPos,Runnable>> BREAK_MUTATIONS=new WeakHashMap<>();
    static{
        UseBlockCallback.EVENT.register((player,world,hand,hit)->USE_DENIED.getOrDefault(world,Set.of()).contains(hit.getBlockPos())?ActionResult.FAIL:ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,tile)->{
            var action=BREAK_MUTATIONS.getOrDefault(world,new HashMap<>()).remove(pos);if(action!=null)action.run();
            return !BREAK_DENIED.getOrDefault(world,Set.of()).contains(pos);
        });
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->ENTITY_DENIED.contains(entity.getUuid())?ActionResult.FAIL:ActionResult.PASS);
    }
    private static void deny(Map<ServerWorld,Set<BlockPos>> map,ServerWorld world,BlockPos pos){map.computeIfAbsent(world,w->new HashSet<>()).add(pos);}
    private static void allow(Map<ServerWorld,Set<BlockPos>> map,ServerWorld world,BlockPos pos){map.getOrDefault(world,new HashSet<>()).remove(pos);}
    private static TilePipeHolder place(ServerWorld world,BlockPos pos,ItemStack stack){
        world.setBlockState(pos,Blocks.AIR.getDefaultState());world.setBlockState(pos,BCTransportBlocks.pipeHolder.getDefaultState());var tile=(TilePipeHolder)world.getBlockEntity(pos);tile.onPlacedBy(null,stack);return tile;
    }
    private static TilePipeHolder pipe(TestContext c){
        c.setBlockState(POS,Blocks.AIR);for(var side:Direction.values())c.setBlockState(POS.offset(side),Blocks.AIR);
        var tile=place(c.getWorld(),c.getAbsolutePos(POS),new ItemStack(BCTransportItems.pipeItemStripes));c.setBlockState(POS.west(),Blocks.BARREL);tile.tick();return tile;
    }
    private static PipeBehaviourStripes head(TilePipeHolder tile){return (PipeBehaviourStripes)tile.getPipe().getBehaviour();}
    private static void ticks(TilePipeHolder tile,int count){for(int i=0;i<count;i++)tile.tick();}
    private static List<ItemStack> contents(TilePipeHolder tile){var drops=DefaultedList.<ItemStack>of();tile.getPipe().flow.addDrops(drops,0);return drops;}
    private static int count(TilePipeHolder tile,Item item){return contents(tile).stream().filter(s->s.isOf(item)).mapToInt(ItemStack::getCount).sum();}
    private static ItemStack send(TilePipeHolder tile,ItemStack stack){
        var point=Vec3d.ofCenter(tile.getPos());var entity=new ItemEntity(tile.getWorld(),point.x,point.y,point.z,stack.copy());
        var event=new PipeEventItem.Drop(tile,(IFlowItems)tile.getPipe().flow,entity);tile.fireEvent(event);return event.getStack();
    }
    private static TilePipeHolder reload(TilePipeHolder tile){
        var world=(ServerWorld)tile.getWorld();var data=tile.createNbtWithIdentifyingData();var pos=tile.getPos();var state=tile.getCachedState();world.removeBlockEntity(pos);
        var fresh=(TilePipeHolder)BlockEntity.createFromNbt(pos,state,data);world.addBlockEntity(fresh);fresh.onLoad();return fresh;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void boundedBatterySimulatesAndRollsBackNestedTransactions(TestContext c){
        var tile=pipe(c);var h=head(tile);c.assertTrue(h.receivePower(300*MjAPI.MJ,true)==44*MjAPI.MJ && h.getStoredMicroMj()==0,"Simulation/capacity wrong");
        try(var outer=Transaction.openOuter()){
            h.receivePower(200*MjAPI.MJ,outer);
            try(var inner=outer.openNested()){h.receivePower(100*MjAPI.MJ,inner);inner.commit();}
            c.assertTrue(h.getStoredMicroMj()==256*MjAPI.MJ,"Battery exceeded hard capacity");
        }
        c.assertTrue(h.getStoredMicroMj()==0,"Outer abort leaked energy");
        try(var tx=Transaction.openOuter()){h.receivePower(80*MjAPI.MJ,tx);tx.commit();}
        c.assertTrue(h.getStoredMicroMj()==80*MjAPI.MJ && h.receivePower(-1,false)==-1,"Committed energy wrong");
        for(var side:Direction.values())c.assertTrue(MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),side)==h,"Native MJ connector missing");
        c.setBlockState(POS,Blocks.AIR);c.assertTrue(h.getPowerRequested()==0 && h.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed battery remained live");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void miningUsesExactHardnessCostAndReturnsNativeLootOnSixFaces(TestContext c){
        for(var side:Direction.values()){
            var tile=pipe(c);c.setBlockState(POS.west(),Blocks.AIR);c.setBlockState(POS.offset(side.getOpposite()),Blocks.BARREL);tile.getPipe().refreshConnections();head(tile).setDirection(side);
            var target=tile.getPos().offset(side);c.getWorld().setBlockState(target,Blocks.STONE.getDefaultState());long cost=BlockUtil.computeBlockBreakPower(c.getWorld(),target);head(tile).receivePower(cost,false);ticks(tile,40);
            c.assertTrue(c.getWorld().isAir(target) && head(tile).getStoredMicroMj()==0 && count(tile,Items.COBBLESTONE)==1,"Native mining failed on "+side);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void miningPaidProgressSurvivesFreshHolderReload(TestContext c){
        var tile=pipe(c);c.setBlockState(POS.east(),Blocks.STONE);head(tile).receivePower(80*MjAPI.MJ,false);ticks(tile,3);
        c.assertTrue(head(tile).getProgressMicroMj()==30*MjAPI.MJ,"Per-tick mining limit wrong");var stale=head(tile);tile=reload(tile);
        c.assertTrue(head(tile).getStoredMicroMj()==50*MjAPI.MJ && head(tile).getProgressMicroMj()==30*MjAPI.MJ && !stale.isLive(),"Save/load lost work or retained old service");
        ticks(tile,6);c.assertTrue(c.getWorld().isAir(tile.getPos().east()) && count(tile,Items.COBBLESTONE)==1,"Restored work did not finish exactly");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unbreakableAndChangedTargetsNeverReusePaidMiningWork(TestContext c){
        var tile=pipe(c);head(tile).receivePower(200*MjAPI.MJ,false);c.setBlockState(POS.east(),Blocks.BEDROCK);ticks(tile,5);
        c.assertTrue(head(tile).getStoredMicroMj()==200*MjAPI.MJ && head(tile).getProgressMicroMj()==0,"Unbreakable block consumed MJ");
        c.setBlockState(POS.east(),Blocks.STONE);ticks(tile,3);c.setBlockState(POS.east(),Blocks.DIRT);tile.tick();
        c.assertTrue(head(tile).getProgressMicroMj()==10*MjAPI.MJ,"Changed target inherited paid work");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void deniedMiningRetainsPaidWorkAndResumesWithoutAnotherCharge(TestContext c){
        var tile=pipe(c);var target=tile.getPos().east();c.setBlockState(POS.east(),Blocks.STONE);deny(BREAK_DENIED,c.getWorld(),target);
        try{head(tile).receivePower(80*MjAPI.MJ,false);ticks(tile,30);c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.STONE) && head(tile).getProgressMicroMj()==80*MjAPI.MJ && head(tile).getStoredMicroMj()==0,"Denied break consumed/lost paid work");}
        finally{allow(BREAK_DENIED,c.getWorld(),target);}
        ticks(tile,22);c.assertTrue(c.getWorld().isAir(target) && count(tile,Items.COBBLESTONE)==1,"Paid protected work did not resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedAndAmbiguousDirectionsDoNotMine(TestContext c){
        var tile=pipe(c);c.assertTrue(head(tile).direction==Direction.EAST,"Single connection did not pick opposite face");
        tile.replacePluggable(Direction.EAST,new PluggableBlocker(BCTransportPlugs.blocker,tile,Direction.EAST));c.setBlockState(POS.east(),Blocks.STONE);head(tile).receivePower(80*MjAPI.MJ,false);ticks(tile,12);
        c.assertTrue(head(tile).direction==null && head(tile).getStoredMicroMj()==80*MjAPI.MJ,"Blocked face consumed work");
        tile.replacePluggable(Direction.EAST,null);c.setBlockState(POS.north(),Blocks.BARREL);tile.tick();c.assertTrue(head(tile).direction==null,"Ambiguous auto direction selected");
        head(tile).setDirection(Direction.EAST);ticks(tile,9);c.assertTrue(c.getWorld().isAir(tile.getPos().east()),"Explicit free direction failed with multiple connections");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void itemBlockPlacementConsumesOneAndPreservesNamedRemainder(TestContext c){
        var tile=pipe(c);var stack=new ItemStack(Items.STONE,4);stack.setCustomName(Text.literal("Stripes sample"));
        c.assertTrue(send(tile,stack).isEmpty() && c.getWorld().getBlockState(tile.getPos().east()).isOf(Blocks.STONE),"Native placement failed");
        c.assertTrue(count(tile,Items.STONE)==3 && contents(tile).stream().allMatch(s->s.getName().getString().equals("Stripes sample")),"Placement lost remainder NBT");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hoeAndSeedsUseNativeToolsAndFarmland(TestContext c){
        var tile=pipe(c);c.setBlockState(POS.east(),Blocks.DIRT);c.assertTrue(send(tile,new ItemStack(Items.IRON_HOE)).isEmpty(),"Hoe not handled");
        c.assertTrue(c.getWorld().getBlockState(tile.getPos().east()).isOf(Blocks.FARMLAND) && contents(tile).get(0).getDamage()==1,"Hoe did not till/damage");
        tile=pipe(c);c.setBlockState(POS.east().down(),Blocks.FARMLAND);c.assertTrue(send(tile,new ItemStack(Items.WHEAT_SEEDS,3)).isEmpty(),"Seeds unhandled");
        c.assertTrue(c.getWorld().getBlockState(tile.getPos().east()).isOf(Blocks.WHEAT) && count(tile,Items.WHEAT_SEEDS)==2,"Native crop placement/consumption failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void shearsHarvestNativeLeavesAndReturnToolOnce(TestContext c){
        var tile=pipe(c);c.setBlockState(POS.east(),Blocks.OAK_LEAVES);c.assertTrue(send(tile,new ItemStack(Items.SHEARS)).isEmpty(),"Shears unhandled");
        c.assertTrue(c.getWorld().isAir(tile.getPos().east()) && count(tile,Items.OAK_LEAVES)==1 && count(tile,Items.SHEARS)==1,"Sheared drops lost/duplicated");
        c.assertTrue(contents(tile).stream().filter(s->s.isOf(Items.SHEARS)).findFirst().orElseThrow().getDamage()==1,"Shears wear wrong");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeDispenserUsesVirtualInventoryForBucketsAndProjectiles(TestContext c){
        var tile=pipe(c);c.assertTrue(send(tile,new ItemStack(Items.WATER_BUCKET)).isEmpty() && c.getWorld().getBlockState(tile.getPos().east()).isOf(Blocks.WATER) && count(tile,Items.BUCKET)==1,"Water dispenser failed");
        tile=pipe(c);c.setBlockState(POS.east(),Blocks.WATER);send(tile,new ItemStack(Items.BUCKET,3));
        c.assertTrue(count(tile,Items.BUCKET)==2 && count(tile,Items.WATER_BUCKET)==1 && c.getWorld().isAir(tile.getPos().east()),"Virtual dispenser inventory lost filled bucket");
        tile=pipe(c);send(tile,new ItemStack(Items.ARROW,3));
        c.assertTrue(count(tile,Items.ARROW)==2 && c.getWorld().getEntitiesByClass(net.minecraft.entity.projectile.ArrowEntity.class,new Box(tile.getPos()).expand(2),e->true).size()==1,"Arrow behavior did not fire exactly once");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void interactionProtectionReturnsItemsWithoutFallingThrough(TestContext c){
        var tile=pipe(c);var target=tile.getPos().east();deny(USE_DENIED,c.getWorld(),target);
        try{send(tile,new ItemStack(Items.WATER_BUCKET));c.assertTrue(c.getWorld().isAir(target) && count(tile,Items.WATER_BUCKET)==1,"Denied use fell through to dispenser");}
        finally{allow(USE_DENIED,c.getWorld(),target);}
        c.setBlockState(POS.east(),Blocks.OAK_LEAVES);deny(BREAK_DENIED,c.getWorld(),target);
        try{send(tile,new ItemStack(Items.SHEARS));c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.OAK_LEAVES) && count(tile,Items.SHEARS)==1,"Denied shearing lost item");}
        finally{allow(BREAK_DENIED,c.getWorld(),target);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void livingEntityInteractionHonorsNativeEntityProtection(TestContext c){
        var tile=pipe(c);var sheep=new SheepEntity(EntityType.SHEEP,c.getWorld());sheep.setPosition(Vec3d.ofCenter(tile.getPos().east()));c.getWorld().spawnEntity(sheep);ENTITY_DENIED.add(sheep.getUuid());
        try{send(tile,new ItemStack(Items.SHEARS));c.assertTrue(!sheep.isSheared() && count(tile,Items.SHEARS)==1,"Entity protection bypassed");}
        finally{ENTITY_DENIED.remove(sheep.getUuid());}
        send(tile,new ItemStack(Items.SHEARS));c.assertTrue(sheep.isSheared() && count(tile,Items.SHEARS)==2,"Native entity interaction failed");sheep.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fakePlayerInventoryIsRestoredAndUnknownItemsRemainUnhandled(TestContext c){
        var tile=pipe(c);var player=FakePlayerProvider.INSTANCE.getFakePlayer(c.getWorld(),tile.getOwner(),tile.getPos());player.getInventory().clear();player.getInventory().selectedSlot=4;player.getInventory().setStack(4,new ItemStack(Items.DIAMOND,7));player.getInventory().setStack(9,new ItemStack(Items.EMERALD,3));
        send(tile,new ItemStack(Items.STONE,2));
        c.assertTrue(player.getInventory().selectedSlot==4 && player.getMainHandStack().getCount()==7 && player.getInventory().getStack(9).getCount()==3 && count(tile,Items.DIAMOND)==0,"Shared fake player inventory leaked");
        var unknown=send(tile,new ItemStack(Items.STICK,5));c.assertTrue(unknown.getCount()==5 && count(tile,Items.STICK)==0,"Unknown stack swallowed");
        player.getInventory().clear();player.getInventory().selectedSlot=0;c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void extensionConsumesOnePipeAndPreservesHeadStateAndStaleViews(TestContext c){
        var tile=pipe(c);head(tile).receivePower(77*MjAPI.MJ,false);tile.replacePluggable(Direction.UP,new PluggableBlocker(BCTransportPlugs.blocker,tile,Direction.UP));
        ((IFlowItems)tile.getPipe().flow).insertItemsForce(new ItemStack(Items.DIAMOND,5),Direction.EAST,null,.02);
        var stale=head(tile);var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.EAST);var stack=ItemPipeHolder.withColour(new ItemStack(BCTransportItems.pipeItemStone,4),DyeColor.BLUE);
        c.assertTrue(send(tile,stack).isEmpty() && c.getWorld().isAir(tile.getPos().east()),"Extension must be deferred");PipeExtensionManager.INSTANCE.tick(c.getWorld());
        var base=(TilePipeHolder)c.getWorld().getBlockEntity(tile.getPos());var moved=(TilePipeHolder)c.getWorld().getBlockEntity(tile.getPos().east());
        c.assertTrue(base.getPipe().getDefinition()==BCTransportPipes.stoneItem && base.getPipe().getColour()==DyeColor.BLUE,"Base pipe lost type/colour");
        c.assertTrue(head(moved).getStoredMicroMj()==77*MjAPI.MJ && moved.getPluggable(Direction.UP) instanceof PluggableBlocker && count(moved,Items.DIAMOND)==5 && count(moved,BCTransportItems.pipeItemStone)==3,"Moved head lost state/remainder");
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(ItemVariant.of(Items.GOLD_INGOT),1,tx)==0 && !stale.isLive(),"Old head retained service");}
        c.assertTrue(contents(moved).stream().filter(s->s.isOf(BCTransportItems.pipeItemStone)).allMatch(s->ItemPipeHolder.getColour(s)==DyeColor.BLUE),"Remainder lost colour");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedAndProtectedExtensionsRefundEveryItem(TestContext c){
        var tile=pipe(c);c.setBlockState(POS.east(),Blocks.STONE);send(tile,new ItemStack(BCTransportItems.pipeItemStone,3));PipeExtensionManager.INSTANCE.tick(c.getWorld());
        c.assertTrue(head(tile).isLive() && count(tile,BCTransportItems.pipeItemStone)==3 && c.getWorld().getBlockState(tile.getPos().east()).isOf(Blocks.STONE),"Obstructed extension lost items");
        tile=pipe(c);var target=tile.getPos().east();deny(BREAK_DENIED,c.getWorld(),target);
        try{send(tile,new ItemStack(BCTransportItems.pipeItemStone,4));PipeExtensionManager.INSTANCE.tick(c.getWorld());c.assertTrue(head(tile).isLive() && count(tile,BCTransportItems.pipeItemStone)==4 && c.getWorld().isAir(target),"Protected move partially executed");}
        finally{allow(BREAK_DENIED,c.getWorld(),target);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void duplicateRequestsAndRemovedHeadsNeverDuplicateOrLoseQueuedStacks(TestContext c){
        var tile=pipe(c);send(tile,new ItemStack(BCTransportItems.pipeItemStone,2));send(tile,new ItemStack(BCTransportItems.pipeItemStone,3));PipeExtensionManager.INSTANCE.tick(c.getWorld());
        var moved=(TilePipeHolder)c.getWorld().getBlockEntity(tile.getPos().east());c.assertTrue(count(moved,BCTransportItems.pipeItemStone)==4,"Two same-tick requests duplicated/lost remainder");
        tile=pipe(c);var pos=tile.getPos();send(tile,new ItemStack(BCTransportItems.pipeItemStone,6));c.getWorld().setBlockState(pos,Blocks.AIR.getDefaultState());PipeExtensionManager.INSTANCE.tick(c.getWorld());
        int recovered=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(pos).expand(1),e->e.getStack().isOf(BCTransportItems.pipeItemStone)).stream().mapToInt(e->e.getStack().getCount()).sum();
        c.assertTrue(recovered==6,"Removed queued head lost refund");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void retractionReturnsCatalystNeighborContentsAndPartsExactlyOnce(TestContext c){
        var tile=pipe(c);var back=place(c.getWorld(),tile.getPos().west(),ItemPipeHolder.withColour(new ItemStack(BCTransportItems.pipeItemStone),DyeColor.RED));
        back.replacePluggable(Direction.UP,new PluggableBlocker(BCTransportPlugs.blocker,back,Direction.UP));((IFlowItems)back.getPipe().flow).insertItemsForce(new ItemStack(Items.EMERALD,3),Direction.WEST,null,.02);back.tick();tile.tick();
        send(tile,new ItemStack(BCTransportItems.pipeItemVoid,2));PipeExtensionManager.INSTANCE.tick(c.getWorld());
        var moved=(TilePipeHolder)c.getWorld().getBlockEntity(tile.getPos().west());c.assertTrue(c.getWorld().isAir(tile.getPos()) && head(moved).direction==Direction.EAST,"Retraction did not relocate head");
        c.assertTrue(count(moved,BCTransportItems.pipeItemVoid)==2 && count(moved,BCTransportItems.pipeItemStone)==1 && count(moved,Items.EMERALD)==3,"Retraction lost catalyst/neighbor contents");
        c.assertTrue(contents(moved).stream().anyMatch(s->s.isOf(BCTransportItems.pipeItemStone) && ItemPipeHolder.getColour(s)==DyeColor.RED),"Retraction lost pipe colour");
        c.assertTrue(moved.getPluggable(Direction.UP)==null && contents(moved).size()==4,"Neighbor plug was not returned separately");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void retractionRefusesMachineConnectionsAndBranches(TestContext c){
        var tile=pipe(c);var back=place(c.getWorld(),tile.getPos().west(),new ItemStack(BCTransportItems.pipeItemStone));c.setBlockState(POS.west(2),Blocks.BARREL);back.tick();tile.tick();
        send(tile,new ItemStack(BCTransportItems.pipeItemVoid));PipeExtensionManager.INSTANCE.tick(c.getWorld());c.assertTrue(head(tile).isLive() && count(tile,BCTransportItems.pipeItemVoid)==1,"Retraction consumed a machine endpoint");
        c.setBlockState(POS.west(2),Blocks.AIR);place(c.getWorld(),back.getPos().north(),new ItemStack(BCTransportItems.pipeItemStone));place(c.getWorld(),back.getPos().south(),new ItemStack(BCTransportItems.pipeItemStone));back.tick();
        send(tile,new ItemStack(BCTransportItems.pipeItemVoid));PipeExtensionManager.INSTANCE.tick(c.getWorld());c.assertTrue(head(tile).isLive() && count(tile,BCTransportItems.pipeItemVoid)==2,"Retraction consumed a branch");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void directionGateActionsAndPayloadsRejectBlockedOrMalformedFaces(TestContext c) throws Exception {
        var tile=pipe(c);tile.replacePluggable(Direction.UP,new PluggableBlocker(BCTransportPlugs.blocker,tile,Direction.UP));
        var actions=new ArrayList<buildcraft.api.statements.IActionInternal>();tile.fireEvent(new PipeEventStatement.AddActionInternal(tile,actions));
        c.assertTrue(actions.contains(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.NORTH.ordinal()]) && !actions.contains(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.WEST.ordinal()]) && !actions.contains(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.UP.ordinal()]),"Gate advertised blocked/connected face");
        tile.fireEvent(new PipeEventActionActivate(tile,BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.NORTH.ordinal()],new buildcraft.api.statements.IStatementParameter[0],buildcraft.api.core.EnumPipePart.CENTER));
        c.assertTrue(head(tile).direction==Direction.NORTH,"Gate did not select free face");
        tile.fireEvent(new PipeEventActionActivate(tile,BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.WEST.ordinal()],new buildcraft.api.statements.IStatementParameter[0],buildcraft.api.core.EnumPipePart.CENTER));
        c.assertTrue(head(tile).direction==Direction.NORTH,"Gate selected connected face");
        var packet=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            head(tile).writePayload(packet,net.fabricmc.api.EnvType.SERVER);
            var copy=new PipeBehaviourStripes(tile.getPipe());copy.readPayload(packet,net.fabricmc.api.EnvType.CLIENT,null);c.assertTrue(copy.direction==Direction.NORTH,"Direction packet lost state");
            packet.clear();packet.writeByte(255);boolean rejected=false;try{copy.readPayload(packet,net.fabricmc.api.EnvType.CLIENT,null);}catch(java.io.IOException expected){rejected=true;}
            c.assertTrue(rejected && copy.direction==Direction.NORTH,"Malformed direction packet changed state");
        }finally{packet.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void savedWorkKeepsLongPrecisionAndClampsLegacyBattery(TestContext c){
        var tile=pipe(c);var tag=new NbtCompound();var battery=new NbtCompound();battery.putLong("stored",Long.MAX_VALUE);tag.put("battery",battery);tag.putLong("workPos",tile.getPos().east().asLong());tag.put("workState",NbtHelper.fromBlockState(Blocks.OBSIDIAN.getDefaultState()));tag.putLong("progress",4_000_000_123L);
        var loaded=new PipeBehaviourStripes(tile.getPipe(),tag);c.assertTrue(loaded.getStoredMicroMj()==256*MjAPI.MJ && loaded.writeToNbt().getLong("progress")==4_000_000_123L,"Long work truncated or old battery overflowed");
        battery.putLong("stored",-1);c.assertTrue(new PipeBehaviourStripes(tile.getPipe(),tag).getStoredMicroMj()==0,"Negative legacy energy survived");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void extensionKeepsOwnerAndWirePartsThroughMoveAndReload(TestContext c){
        var tile=pipe(c);var owner=new com.mojang.authlib.GameProfile(UUID.fromString("395169c0-3d54-47eb-b425-a7dc2b804754"),"StripesOwner");var tag=tile.createNbt();tag.put("owner",NbtHelper.writeGameProfile(new NbtCompound(),owner));tile.readNbt(tag);tile.onLoad();
        var part=buildcraft.api.transport.EnumWirePart.EAST_UP_NORTH;tile.wireManager.addPart(part,DyeColor.LIME);send(tile,new ItemStack(BCTransportItems.pipeItemStone));PipeExtensionManager.INSTANCE.tick(c.getWorld());
        var base=(TilePipeHolder)c.getWorld().getBlockEntity(tile.getPos());var moved=reload((TilePipeHolder)c.getWorld().getBlockEntity(tile.getPos().east()));
        c.assertTrue(base.getOwner().equals(owner) && moved.getOwner().equals(owner) && base.wireManager.parts.isEmpty() && moved.wireManager.getColorOfPart(part)==DyeColor.LIME,"Owner/wire state lost or duplicated");
        moved.tick();c.assertTrue(buildcraft.transport.wire.WorldSavedDataWireSystems.get(c.getWorld()).getLoadedHolder(moved.getPos())==moved,"Wire graph retained moved holder");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void incompatibleExtensionPipesReturnInsteadOfSeveringTheHead(TestContext c){
        var tile=pipe(c);send(tile,new ItemStack(BCTransportItems.pipeItemStripes,2));PipeExtensionManager.INSTANCE.tick(c.getWorld());
        c.assertTrue(head(tile).isLive() && count(tile,BCTransportItems.pipeItemStripes)==2 && c.getWorld().isAir(tile.getPos().east()),"Extension created disconnected stripes pair");
        tile=pipe(c);head(tile).setDirection(Direction.NORTH);tile.replacePluggable(Direction.SOUTH,new PluggableBlocker(BCTransportPlugs.blocker,tile,Direction.SOUTH));
        send(tile,new ItemStack(BCTransportItems.pipeItemStone));PipeExtensionManager.INSTANCE.tick(c.getWorld());
        c.assertTrue(head(tile).isLive() && count(tile,BCTransportItems.pipeItemStone)==1,"Extension created blocked back face");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void retractionCanFollowAPerpendicularSinglePipePath(TestContext c){
        var tile=pipe(c);c.setBlockState(POS.west(),Blocks.AIR);var north=place(c.getWorld(),tile.getPos().north(),new ItemStack(BCTransportItems.pipeItemStone));north.tick();tile.tick();head(tile).setDirection(Direction.EAST);
        send(tile,new ItemStack(BCTransportItems.pipeItemVoid));PipeExtensionManager.INSTANCE.tick(c.getWorld());
        var moved=(TilePipeHolder)c.getWorld().getBlockEntity(north.getPos());c.assertTrue(c.getWorld().isAir(tile.getPos()) && head(moved).direction==Direction.SOUTH && count(moved,BCTransportItems.pipeItemVoid)==1 && count(moved,BCTransportItems.pipeItemStone)==1,"Perpendicular retraction failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void relocationRevalidatesWorldAndHeadNbtAfterProtectionCallbacks(TestContext c){
        var tile=pipe(c);var target=tile.getPos().east();send(tile,new ItemStack(BCTransportItems.pipeItemStone,2));
        BREAK_MUTATIONS.computeIfAbsent(c.getWorld(),w->new HashMap<>()).put(target,()->c.getWorld().setBlockState(target,Blocks.OBSIDIAN.getDefaultState()));
        PipeExtensionManager.INSTANCE.tick(c.getWorld());c.assertTrue(head(tile).isLive() && count(tile,BCTransportItems.pipeItemStone)==2 && c.getWorld().getBlockState(target).isOf(Blocks.OBSIDIAN),"Callback replacement was overwritten");
        tile=pipe(c);var changed=head(tile);send(tile,new ItemStack(BCTransportItems.pipeItemStone,3));
        BREAK_MUTATIONS.get(c.getWorld()).put(tile.getPos(),()->changed.receivePower(12*MjAPI.MJ,false));PipeExtensionManager.INSTANCE.tick(c.getWorld());
        c.assertTrue(changed.isLive() && changed.getStoredMicroMj()==12*MjAPI.MJ && count(tile,BCTransportItems.pipeItemStone)==3,"Callback changed NBT was discarded");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void insertionBeforeFirstReloadTickDoesNotAddWorldTimeTwice(TestContext c){
        var tile=pipe(c);((IFlowItems)tile.getPipe().flow).insertItemsForce(new ItemStack(Items.DIAMOND,2),Direction.EAST,null,.02);tile=reload(tile);
        ((IFlowItems)tile.getPipe().flow).insertItemsForce(new ItemStack(Items.EMERALD,3),Direction.EAST,null,.02);
        var saved=tile.getPipe().flow.writeToNbt().getList("items",NbtElement.COMPOUND_TYPE);
        c.assertTrue(saved.size()==2,"Reloaded/new stacks missing");for(int i=0;i<saved.size();i++)c.assertTrue(saved.getCompound(i).getInt("tickFinished")==0,"New item incorrectly inherited another world-time offset");
        tile.tick();var after=tile.getPipe().flow.writeToNbt().getList("items",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<after.size();i++)c.assertTrue(after.getCompound(i).getInt("tickFinished")<=50,"First tick delayed newly inserted items by world age");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=240)
    public void relocatedHeadReturnsItemsThroughNativeWorldTicking(TestContext c){
        var tile=pipe(c);send(tile,new ItemStack(BCTransportItems.pipeItemStone,2));var barrelPos=tile.getPos().west();
        c.runAtTick(180,()->{
            var barrel=(net.minecraft.block.entity.BarrelBlockEntity)c.getWorld().getBlockEntity(barrelPos);int delivered=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(BCTransportItems.pipeItemStone))delivered+=barrel.getStack(i).getCount();
            c.assertTrue(delivered==1,"Relocated pipe's returned item did not advance with native world ticks");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeTickHookProcessesQueuedRelocation(TestContext c){
        var tile=pipe(c);send(tile,new ItemStack(BCTransportItems.pipeItemStone));var target=tile.getPos().east();
        c.runAtTick(3,()->{c.assertTrue(c.getWorld().getBlockEntity(target) instanceof TilePipeHolder moved && moved.getPipe().getBehaviour() instanceof PipeBehaviourStripes,"End-world-tick relocation hook absent");c.complete();});
    }
}
