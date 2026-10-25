/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.api.transport.pipe.*;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.inventory.ItemTransactorHelper;
import buildcraft.transport.*;
import buildcraft.transport.pipe.behaviour.PipeBehaviourObsidian;
import buildcraft.transport.plug.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import team.reborn.energy.api.EnergyStorage;

public final class ObsidianGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,4,3);
    private static TilePipeHolder pipe(TestContext c,Direction output){
        c.setBlockState(POS,Blocks.AIR);for(var face:Direction.values())c.setBlockState(POS.offset(face),Blocks.AIR);
        c.setBlockState(POS,BCTransportBlocks.pipeHolder);var tile=(TilePipeHolder)c.getBlockEntity(POS);tile.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemObsidian));
        if(output!=null)c.setBlockState(POS.offset(output),Blocks.BARREL);tile.tick();return tile;
    }
    private static PipeBehaviourObsidian behavior(TilePipeHolder tile){return (PipeBehaviourObsidian)tile.getPipe().getBehaviour();}
    private static ItemStack sample(int count){var stack=new ItemStack(Items.DIAMOND,count);stack.setCustomName(Text.literal("Exact obsidian sample"));return stack;}
    private static ItemEntity item(TestContext c,TilePipeHolder tile,Vec3d offset,int count,boolean spawn){
        var p=Vec3d.ofCenter(tile.getPos()).add(offset);var entity=new ItemEntity(c.getWorld(),p.x,p.y,p.z,sample(count));entity.setNoGravity(true);entity.setVelocity(Vec3d.ZERO);if(spawn)c.getWorld().spawnEntity(entity);return entity;
    }
    private static int contents(TilePipeHolder tile){var list=DefaultedList.<ItemStack>of();tile.getPipe().flow.addDrops(list,0);return list.stream().mapToInt(ItemStack::getCount).sum();}
    private static void collide(TestContext c,TilePipeHolder tile,Entity entity){tile.getCachedState().getBlock().onEntityCollision(tile.getCachedState(),c.getWorld(),tile.getPos(),entity);}
    private static long cost(double distance){return (long)(Math.max(1,distance)*MjAPI.MJ/4+MjAPI.MJ/2);}

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeCollisionCollectsAllSixOrientationsWithNbtAndNoMj(TestContext c){
        for(var side:Direction.values()){
            var tile=pipe(c,side);var entity=item(c,tile,Vec3d.ZERO,7,false);collide(c,tile,entity);
            c.assertTrue(entity.isRemoved() && contents(tile)==7,"Collision failed toward "+side);collide(c,tile,entity);c.assertTrue(contents(tile)==7,"Repeated collision duplicated items");
            var drops=DefaultedList.<ItemStack>of();tile.getPipe().flow.addDrops(drops,0);c.assertTrue(drops.stream().allMatch(s->ItemStack.canCombine(s,sample(1))),"Collision lost exact NBT");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void singleConnectionAndBlockedMouthAreRequired(TestContext c){
        var tile=pipe(c,null);var entity=item(c,tile,Vec3d.ZERO,2,false);collide(c,tile,entity);
        c.assertTrue(contents(tile)==0 && behavior(tile).receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Disconnected collector worked");
        c.setBlockState(POS.west(),Blocks.BARREL);c.setBlockState(POS.north(),Blocks.BARREL);tile.tick();collide(c,tile,entity);
        c.assertTrue(contents(tile)==0 && behavior(tile).getPowerRequested()==0,"Ambiguous collector worked");
        c.setBlockState(POS.north(),Blocks.AIR);tile.tick();tile.replacePluggable(Direction.EAST,new PluggableBlocker(BCTransportPlugs.blocker,tile,Direction.EAST));collide(c,tile,entity);
        c.assertTrue(contents(tile)==0 && behavior(tile).getPowerRequested()==0,"Blocked mouth collected through a plug");tile.replacePluggable(Direction.EAST,null);tile.tick();collide(c,tile,entity);c.assertTrue(contents(tile)==2,"Unblocked collector did not resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void obsidianPipesDoNotJoinEachOtherButExposeNativeMj(TestContext c){
        var tile=pipe(c,Direction.WEST);c.setBlockState(POS.east(),BCTransportBlocks.pipeHolder);var other=(TilePipeHolder)c.getBlockEntity(POS.east());other.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemObsidian));tile.tick();other.tick();
        c.assertTrue(!tile.getPipe().isConnected(Direction.EAST) && !other.getPipe().isConnected(Direction.WEST),"Obsidian collectors joined");
        for(var side:Direction.values())c.assertTrue(MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),side) instanceof IMjReceiver,"Missing MJ side "+side);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void poweredRangeAndPerItemDistanceCostWorkInAllDirections(TestContext c){
        for(var output:Direction.values()){
            var tile=pipe(c,output);var face=output.getOpposite();var entity=item(c,tile,new Vec3d(face.getOffsetX()*2,face.getOffsetY()*2,face.getOffsetZ()*2),5,true);var receiver=behavior(tile);long offered=cost(2)*2+123;
            c.assertTrue(receiver.receivePower(offered,true)==123 && entity.getStack().getCount()==5 && contents(tile)==0,"Simulation changed source or price");
            c.assertTrue(receiver.getPowerRequested()==5*cost(2) && entity.getStack().getCount()==5,"Demand query mutated items");
            c.assertTrue(receiver.receivePower(offered,false)==123 && entity.getStack().getCount()==3 && contents(tile)==2,"Distance/item price wrong on "+face);entity.discard();
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void forwardConeIncludesLateralItemsButExcludesRearAndBeyondFourPlanes(TestContext c){
        var tile=pipe(c,Direction.WEST);var far=item(c,tile,new Vec3d(5.2,0,0),4,true);var rear=item(c,tile,new Vec3d(-2,0,0),4,true);var lateral=item(c,tile,new Vec3d(4,4,4),2,true);
        long expected=cost(Math.sqrt(48));c.assertTrue(behavior(tile).receivePower(expected,false)==0 && lateral.getStack().getCount()==1 && contents(tile)==1,"Original expanding search plane lost lateral range");
        lateral.discard();c.assertTrue(behavior(tile).receivePower(2*MjAPI.MJ,false)==MjAPI.MJ && far.getStack().getCount()==4 && rear.getStack().getCount()==4,"Search reached beyond the mouth/range");far.discard();rear.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void emptyScansAndSmallOffersNeverInventOrOverdrawEnergy(TestContext c){
        var tile=pipe(c,Direction.WEST);var receiver=behavior(tile);
        c.assertTrue(receiver.getPowerRequested()==MjAPI.MJ,"Original idle scan cost changed");
        for(long offered:new long[]{-1,0,1,MjAPI.MJ-1,MjAPI.MJ,Long.MAX_VALUE})c.assertTrue(receiver.receivePower(offered,false)==offered-Math.min(Math.max(0,offered),MjAPI.MJ),"Invalid idle energy remainder");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void itemExtractionAndNestedAbortRestoreTheEntityAndPipeTogether(TestContext c){
        var tile=pipe(c,Direction.WEST);var entity=item(c,tile,new Vec3d(2,0,0),5,true);var receiver=behavior(tile);
        try(var outer=Transaction.openOuter()){
            c.assertTrue(receiver.receivePower(2*cost(2),outer)==0 && entity.getStack().getCount()==3 && contents(tile)==0,"Pending extraction wrong");
            try(var inner=outer.openNested()){receiver.receivePower(2*cost(2),inner);}
            c.assertTrue(entity.getStack().getCount()==3 && contents(tile)==0,"Nested abort lost source");
        }
        c.assertTrue(entity.getStack().getCount()==5 && contents(tile)==0 && !entity.isRemoved(),"Outer abort lost items");
        try(var tx=Transaction.openOuter()){receiver.receivePower(5*cost(2),tx);c.assertTrue(!entity.isRemoved() && entity.getStack().isEmpty(),"Entity removal must await final commit");tx.commit();}
        c.assertTrue(entity.isRemoved() && contents(tile)==5,"Final commit duplicated or lost collected items");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void projectileReservationsAreSharedAndPickupPermissionsArePreserved(TestContext c){
        var tile=pipe(c,Direction.WEST);var point=Vec3d.ofCenter(tile.getPos()).add(2,0,0);var arrow=new ArrowEntity(c.getWorld(),point.x,point.y,point.z);arrow.pickupType=PersistentProjectileEntity.PickupPermission.ALLOWED;arrow.setNoGravity(true);c.getWorld().spawnEntity(arrow);
        var first=ItemTransactorHelper.getTransactorForEntity(arrow,Direction.WEST);var second=ItemTransactorHelper.getTransactorForEntity(arrow,Direction.NORTH);
        try(var tx=Transaction.openOuter()){c.assertTrue(!first.extract(null,1,1,false).isEmpty() && second.extract(null,1,1,false).isEmpty() && !arrow.isRemoved(),"Two extractors reserved the same projectile");}
        c.assertTrue(!arrow.isRemoved() && !first.extract(null,1,1,true).isEmpty(),"Aborted reservation persisted");
        for(var permission:new PersistentProjectileEntity.PickupPermission[]{PersistentProjectileEntity.PickupPermission.DISALLOWED,PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY}){arrow.pickupType=permission;behavior(tile).receivePower(2*MjAPI.MJ,false);c.assertTrue(!arrow.isRemoved() && contents(tile)==0,"Denied projectile collected");}
        arrow.pickupType=PersistentProjectileEntity.PickupPermission.ALLOWED;behavior(tile).receivePower(cost(2),false);c.assertTrue(arrow.isRemoved() && contents(tile)==1,"Allowed projectile not collected");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMinecartInventoryExtractionRollsBackAndKeepsExactNbt(TestContext c){
        var tile=pipe(c,Direction.WEST);var p=Vec3d.ofCenter(tile.getPos()).add(2,0,0);var cart=new ChestMinecartEntity(c.getWorld(),p.x,p.y,p.z);cart.setNoGravity(true);cart.setStack(0,sample(3));cart.setStack(1,sample(2));c.getWorld().spawnEntity(cart);
        try(var tx=Transaction.openOuter()){behavior(tile).receivePower(4*cost(2),tx);}
        c.assertTrue(cart.getStack(0).getCount()==3 && cart.getStack(1).getCount()==2 && contents(tile)==0,"Minecart abort lost source");
        behavior(tile).receivePower(4*cost(2),false);c.assertTrue(cart.getStack(0).isEmpty() && cart.getStack(1).getCount()==1 && contents(tile)==4 && !cart.isRemoved(),"Minecart extraction count wrong");cart.clear();cart.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void tippedSpectralArrowsAndDamagedEnchantedTridentsKeepNativePickupStacks(TestContext c){
        var tile=pipe(c,Direction.WEST);var world=c.getWorld();var player=c.createMockCreativeServerPlayerInWorld();
        var tippedStack=net.minecraft.potion.PotionUtil.setPotion(new ItemStack(Items.TIPPED_ARROW),net.minecraft.potion.Potions.POISON);
        var tipped=new ArrowEntity(world,0,0,0);tipped.initFromStack(tippedStack);
        var tridentStack=new ItemStack(Items.TRIDENT);tridentStack.setDamage(41);tridentStack.setCustomName(Text.literal("Recovered trident"));tridentStack.addEnchantment(net.minecraft.enchantment.Enchantments.UNBREAKING,2);
        List<PersistentProjectileEntity> projectiles=List.of(tipped,new SpectralArrowEntity(world,0,0,0),new TridentEntity(world,player,tridentStack));
        var expected=List.of(tippedStack,new ItemStack(Items.SPECTRAL_ARROW),tridentStack);
        for(var projectile:projectiles){
            projectile.setPosition(Vec3d.ofCenter(tile.getPos()));projectile.pickupType=PersistentProjectileEntity.PickupPermission.ALLOWED;
            var service=ItemTransactorHelper.getTransactorForEntity(projectile,Direction.EAST);
            c.assertTrue(service.extract(s->false,1,1,false).isEmpty() && service.extract(null,2,2,false).isEmpty() && !projectile.isRemoved(),"Denied projectile extraction mutated entity");
            collide(c,tile,projectile);c.assertTrue(projectile.isRemoved(),"Native projectile was not collected");
        }
        var drops=DefaultedList.<ItemStack>of();tile.getPipe().flow.addDrops(drops,0);
        c.assertTrue(contents(tile)==3 && expected.stream().allMatch(e->drops.stream().anyMatch(s->ItemStack.canCombine(e,s))) && drops.stream().anyMatch(s->ItemStack.canCombine(tridentStack,s)),"Projectile subtype, potion or trident NBT lost");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void livingEntitiesAndRemovedSourcesAreIgnored(TestContext c){
        var tile=pipe(c,Direction.WEST);var player=c.createMockCreativeServerPlayerInWorld();player.getInventory().setStack(0,sample(4));collide(c,tile,player);c.assertTrue(contents(tile)==0 && player.getInventory().getStack(0).getCount()==4,"Collected living inventory");
        var entity=item(c,tile,Vec3d.ZERO,3,false);entity.discard();collide(c,tile,entity);c.assertTrue(contents(tile)==0,"Collected removed item");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=60)
    public void ownDroppedItemsWaitTwentyTicksBeforeRecollection(TestContext c){
        var tile=pipe(c,Direction.WEST);var entity=item(c,tile,Vec3d.ZERO,3,false);tile.fireEvent(new PipeEventItem.Drop(tile,(IFlowItems)tile.getPipe().flow,entity));collide(c,tile,entity);
        c.assertTrue(contents(tile)==0,"Immediate drop loop");c.runAtTick(10,()->{collide(c,tile,entity);c.assertTrue(contents(tile)==0,"Cooldown ended too early");});
        c.runAtTick(22,()->{collide(c,tile,entity);c.assertTrue(contents(tile)==3 && entity.isRemoved(),"Cooldown never expired");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void reloadDelayBlocksBothCollectionPathsAndSavedItemsSurvive(TestContext c){
        var tile=pipe(c,Direction.WEST);var first=item(c,tile,Vec3d.ZERO,2,false);collide(c,tile,first);var saved=tile.createNbt();var stale=behavior(tile);
        var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());copy.readNbt(saved);c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();
        var entity=item(c,copy,Vec3d.ZERO,3,false);collide(c,copy,entity);c.assertTrue(contents(copy)==2 && behavior(copy).getPowerRequested()==0,"Reload skipped cooldown or lost travelling items");
        for(int i=0;i<19;i++)behavior(copy).onTick();collide(c,copy,entity);c.assertTrue(contents(copy)==2,"Reload cooldown too short");behavior(copy).onTick();copy.tick();collide(c,copy,entity);
        c.assertTrue(contents(copy)==5 && stale.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Reload failed to resume or stale receiver survived");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pipeRemovalDuringOuterTransactionRecoversPendingContents(TestContext c){
        var tile=pipe(c,Direction.WEST);var entity=item(c,tile,new Vec3d(2,0,0),3,true);var receiver=behavior(tile);
        c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(tile.getPos()).expand(1),e->true).forEach(Entity::discard);
        try(var tx=Transaction.openOuter()){receiver.receivePower(3*cost(2),tx);c.getWorld().removeBlock(tile.getPos(),false);tx.commit();}
        int total=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(tile.getPos()).expand(1),e->ItemStack.canCombine(e.getStack(),sample(1))).stream().mapToInt(e->e.getStack().getCount()).sum();
        c.assertTrue(entity.isRemoved() && total==3 && receiver.getPowerRequested()==0,"Removal lost pending extraction or retained service");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void collectedItemsReachRealNativeBarrel(TestContext c){
        var tile=pipe(c,Direction.WEST);var entity=item(c,tile,new Vec3d(2,0,0),6,true);behavior(tile).receivePower(6*cost(2),false);
        c.runAtTick(55,()->{var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.west());c.assertTrue(entity.isRemoved() && barrel.getStack(0).getCount()==6 && ItemStack.canCombine(barrel.getStack(0),sample(1)) && contents(tile)==0,"Real transport lost collected items/NBT");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void naturallyFallingItemTriggersVanillaCollisionAndDelivery(TestContext c){
        var tile=pipe(c,Direction.WEST);var entity=item(c,tile,new Vec3d(0,1,0),3,true);entity.setNoGravity(false);entity.setVelocity(0,-.1,0);
        c.runAtTick(60,()->{var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.west());c.assertTrue(entity.isRemoved() && barrel.getStack(0).getCount()==3 && ItemStack.canCombine(barrel.getStack(0),sample(1)),"Vanilla falling item was not collected/delivered");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeRfAdaptorRollsBackItemsAndFractionalEnergyCredit(TestContext c){
        var oldMode=BCLibConfig.powerMode;var oldRate=BCLibConfig.mjRfConversion;
        try{
            BCLibConfig.powerMode=BCLibConfig.PowerMode.MJ_AUTOCONVERT_RF;BCLibConfig.mjRfConversion=MjRfConversion.createDefault();
            var tile=pipe(c,Direction.WEST);var entity=item(c,tile,new Vec3d(2,0,0),3,true);var plug=new PluggablePowerAdaptor(BCTransportPlugs.powerAdaptor,tile,Direction.NORTH);tile.replacePluggable(Direction.NORTH,plug);var rf=EnergyStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);
            c.assertTrue(rf!=null,"Obsidian RF adaptor missing");long perRf=BCLibConfig.mjRfConversion.mjPerRf;long offered=(cost(2)+perRf-1)/perRf;
            try(var tx=Transaction.openOuter()){c.assertTrue(rf.insert(offered,tx)==offered,"RF did not pay exact extraction");}
            c.assertTrue(entity.getStack().getCount()==3 && contents(tile)==0 && plug.getStoredMicroMj()==0,"Aborted RF extraction leaked state");
            try(var tx=Transaction.openOuter()){rf.insert(offered,tx);tx.commit();}
            c.assertTrue(entity.getStack().getCount()==2 && contents(tile)==1 && plug.getStoredMicroMj()==offered*perRf-cost(2),"RF extraction or remainder wrong");entity.discard();
        }finally{BCLibConfig.powerMode=oldMode;BCLibConfig.mjRfConversion=oldRate;}c.complete();
    }
}
