/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.api.transport.pipe.*;
import buildcraft.core.BCCoreItems;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.statement.*;
import buildcraft.silicon.*;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.*;
import buildcraft.transport.pipe.behaviour.*;
import buildcraft.transport.pipe.flow.*;
import buildcraft.transport.plug.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.recipe.*;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import team.reborn.energy.api.EnergyStorage;

public final class TransportPlugGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static TilePipeHolder pipe(TestContext c,BlockPos pos,Item item){
        c.setBlockState(pos,BCTransportBlocks.pipeHolder);var tile=(TilePipeHolder)c.getBlockEntity(pos);tile.onPlacedBy(null,new ItemStack(item));return tile;
    }
    private static BarrelBlockEntity source(TestContext c,TilePipeHolder tile,int count){
        c.setBlockState(POS.west(),Blocks.BARREL);var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.west());if(count>0)barrel.setStack(0,new ItemStack(Items.DIAMOND,count));tile.tick();return barrel;
    }
    private static PluggablePowerAdaptor adaptor(TilePipeHolder tile){
        var plug=new PluggablePowerAdaptor(BCTransportPlugs.powerAdaptor,tile,Direction.NORTH);tile.replacePluggable(Direction.NORTH,plug);return plug;
    }
    private static EnergyStorage rf(TestContext c,TilePipeHolder tile){var input=EnergyStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);c.assertTrue(input!=null,"RF input missing");return input;}
    private static int count(BarrelBlockEntity barrel){int sum=0;for(int i=0;i<barrel.size();i++)sum+=barrel.getStack(i).getCount();return sum;}
    private static int contents(TilePipeHolder tile){
        int sum=0;var entries=tile.getPipe().getFlow().writeToNbt().getList("items",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<entries.size();i++)sum+=ItemStack.fromNbt(entries.getCompound(i).getCompound("stack")).getCount();return sum;
    }
    private static void automatic(Runnable work){
        var oldMode=BCLibConfig.powerMode;var oldRate=BCLibConfig.mjRfConversion;
        try{BCLibConfig.powerMode=BCLibConfig.PowerMode.MJ_AUTOCONVERT_RF;BCLibConfig.mjRfConversion=MjRfConversion.createDefault();work.run();}
        finally{BCLibConfig.powerMode=oldMode;BCLibConfig.mjRfConversion=oldRate;}
    }
    private static TilePipeHolder reload(TestContext c,TilePipeHolder tile){
        var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.tick();return copy;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeBlockerAndAdaptorPlacementOnAllSixFaces(TestContext c){
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var player=c.createMockCreativeServerPlayerInWorld();player.getAbilities().creativeMode=false;
        for(var item:List.of(BCTransportItems.plugBlocker,BCTransportItems.plugPowerAdaptor))for(var side:Direction.values()){
            var look=Vec3d.of(side.getVector()).multiply(-1);var eye=Vec3d.ofCenter(tile.getPos()).subtract(look.multiply(3));
            player.refreshPositionAndAngles(eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());
            var stack=new ItemStack(item,2);player.setStackInHand(Hand.MAIN_HAND,stack);
            var result=BCTransportBlocks.pipeHolder.onUse(tile.getCachedState(),c.getWorld(),tile.getPos(),player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(tile.getPos()),side,tile.getPos(),false));
            var plug=tile.getPluggable(side);c.assertTrue(result.isAccepted() && stack.getCount()==1 && plug!=null && plug.getPickStack().isOf(item) && plug.isBlocking(),"Placement/pick failed "+side);
            var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());
            c.assertTrue(copy.getPluggable(side).getPickStack().isOf(item),"NBT lost transport attachment");
            c.assertTrue(item.onPlace(stack,tile,side,player,Hand.MAIN_HAND)==null,"Occupied attachment overwritten");tile.replacePluggable(side,null);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void adaptorRejectsUnsupportedPipesAndRemotePlayers(TestContext c){
        var tile=pipe(c,POS,BCTransportItems.pipePowerWood);var player=c.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(tile.getPos(),0,0);
        var stack=new ItemStack(BCTransportItems.plugPowerAdaptor);
        c.assertTrue(BCTransportItems.plugPowerAdaptor.onPlace(stack,tile,Direction.NORTH,player,Hand.MAIN_HAND)==null,"Adaptor accepted energy-flow pipe");
        c.assertTrue(BCTransportItems.plugBlocker.onPlace(new ItemStack(BCTransportItems.plugBlocker),tile,Direction.NORTH,player,Hand.MAIN_HAND)!=null,"Blocker rejected supported pipe");
        player.refreshPositionAndAngles(player.getX()+100,player.getY(),player.getZ(),0,0);
        c.assertTrue(BCTransportItems.plugBlocker.onPlace(new ItemStack(BCTransportItems.plugBlocker),tile,Direction.NORTH,player,Hand.MAIN_HAND)==null,"Remote attachment accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockerClosesNativeItemFluidMjAndRfFaces(TestContext c){
        for(var item:List.of(BCTransportItems.pipeItemWood,BCTransportItems.pipeFluidWood,BCTransportItems.pipePowerWood,BCTransportItems.pipeRfWood)){
            var tile=pipe(c,POS,item);tile.replacePluggable(Direction.NORTH,new PluggableBlocker(BCTransportPlugs.blocker,tile,Direction.NORTH));
            c.assertTrue(ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH)==null && FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH)==null
                && EnergyStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH)==null && MjAPI.findConnector(tile,Direction.NORTH)==null,"Blocker leaked a native endpoint");
            c.getWorld().removeBlock(tile.getPos(),false);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void mjSimulationAndCachedHandlesRespectRemoval(TestContext c){
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,4);var plug=adaptor(tile);var input=(IMjReceiver)MjAPI.findConnector(tile,Direction.NORTH);
        c.assertTrue(input!=null && input.receivePower(2*MjAPI.MJ,true)==0 && count(source)==4 && contents(tile)==0,"MJ simulation changed extraction");
        c.assertTrue(input.receivePower(2*MjAPI.MJ,false)==0 && count(source)==2 && contents(tile)==2,"MJ extraction failed");
        tile.replacePluggable(Direction.NORTH,null);c.assertTrue(!input.canReceive() && input.getPowerRequested()==0 && input.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed adaptor retained MJ input");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rfNestedAbortRestoresSourceCreditAndTravellingItems(TestContext c){automatic(()->{
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,8);var plug=adaptor(tile);var input=rf(c,tile);var before=tile.createNbt();
        try(var outer=Transaction.openOuter()){
            c.assertTrue(input.insert(10,outer)==10 && count(source)==7 && contents(tile)==0,"RF work did not remain transactional");
            try(var inner=outer.openNested()){c.assertTrue(input.insert(20,inner)==20,"Nested RF extraction failed");inner.commit();}
        }
        c.assertTrue(before.equals(tile.createNbt()) && count(source)==8,"Outer abort leaked nested extraction/credit");
        try(var outer=Transaction.openOuter()){
            c.assertTrue(input.insert(10,outer)==10,"RF rollback consumed demand");
            try(var inner=outer.openNested()){input.insert(10,inner);}
            c.assertTrue(count(source)==7,"Inner abort failed");outer.commit();
        }
        c.assertTrue(count(source)==7 && contents(tile)==1 && plug.getStoredMicroMj()==0,"Final RF commit lost/duplicated output");
    });c.complete();}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rfRoundingCreditSurvivesReloadAndUsesExactConfiguredCost(TestContext c){automatic(()->{
        long old=BCTransportConfig.mjPerItem;try{
            BCTransportConfig.mjPerItem=150000;var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,4);var plug=adaptor(tile);var input=rf(c,tile);
            try(var tx=Transaction.openOuter()){c.assertTrue(input.insert(2,tx)==2,"First rounded extraction failed");tx.commit();}
            c.assertTrue(plug.getStoredMicroMj()==50000 && count(source)==3,"Sub-RF credit incorrect");
            var copy=reload(c,tile);var restored=(PluggablePowerAdaptor)copy.getPluggable(Direction.NORTH);
            c.assertTrue(restored.getStoredMicroMj()==50000,"Saved RF credit lost");
            try(var tx=Transaction.openOuter()){c.assertTrue(rf(c,copy).insert(1,tx)==1,"Saved credit was not applied");tx.commit();}
            c.assertTrue(restored.getStoredMicroMj()==0 && count(source)==2 && contents(copy)==2,"Credit duplicated/lost after restart");
            try(var tx=Transaction.openOuter()){c.assertTrue(input.insert(100,tx)==0,"Stale adaptor survived tile reload");}
        }finally{BCTransportConfig.mjPerItem=old;}
    });c.complete();}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rfModeChangesEmptyInputAndLargeOffersRemainBounded(TestContext c){automatic(()->{
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,0);adaptor(tile);var input=rf(c,tile);
        try(var tx=Transaction.openOuter()){c.assertTrue(input.insert(Long.MAX_VALUE,tx)==0,"Empty source consumed RF");tx.commit();}
        source.setStack(0,new ItemStack(Items.DIAMOND,64));
        try(var tx=Transaction.openOuter()){c.assertTrue(input.insert(Long.MAX_VALUE,tx)==640 && input.extract(10,tx)==0,"Oversized RF offer overflowed");tx.commit();}
        c.assertTrue(source.isEmpty() && contents(tile)==64,"Large RF extraction lost items");
        BCLibConfig.powerMode=BCLibConfig.PowerMode.MJ_ONLY;
        c.assertTrue(EnergyStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH)==null && !input.supportsInsertion(),"MJ-only mode exposed RF");
        try(var tx=Transaction.openOuter()){c.assertTrue(input.insert(10,tx)==0,"Cached RF handle ignored power mode");}
    });c.complete();}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rfFluidRollbackIncludesTankPipeSectionsAndFractionalCredit(TestContext c){automatic(()->{
        c.setBlockState(POS.west(),BCFactoryBlocks.tank);var source=FluidStorage.SIDED.find(c.getWorld(),c.getAbsolutePos(POS.west()),Direction.EAST);
        var water=FluidVariant.of(net.minecraft.fluid.Fluids.WATER);try(var tx=Transaction.openOuter()){source.insert(water,FluidConstants.BUCKET+7,tx);tx.commit();}
        var tile=pipe(c,POS,BCTransportItems.pipeFluidWood);tile.tick();var plug=adaptor(tile);var input=rf(c,tile);var before=tile.createNbt();
        try(var outer=Transaction.openOuter()){input.insert(10,outer);try(var inner=outer.openNested()){input.insert(10,inner);inner.commit();}}
        c.assertTrue(before.equals(tile.createNbt()) && amount(source)==FluidConstants.BUCKET+7,"Fluid RF abort changed tank/pipe");
        try(var tx=Transaction.openOuter()){c.assertTrue(input.insert(11,tx)==11,"Fractional fluid RF acceptance wrong");tx.commit();}
        long used=( (FluidConstants.BUCKET+7)*BCTransportConfig.millibucketExtractionCost()+80)/81;
        c.assertTrue(amount(source)==0 && ((PipeFlowFluids)tile.getPipe().getFlow()).getAmount()==FluidConstants.BUCKET+7
            && plug.getStoredMicroMj()==11*100000-used,"Fluid RF commit lost droplets/credit");
    });c.complete();}
    private static long amount(Storage<FluidVariant> storage){long sum=0;for(var view:storage)sum+=view.getAmount();return sum;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void diamondWoodRoundRobinCursorRollsBackWithRfWork(TestContext c){automatic(()->{
        var tile=pipe(c,POS,BCTransportItems.pipeItemDiaWood);var source=source(c,tile,3);source.setStack(1,new ItemStack(Items.EMERALD,3));var behavior=(PipeBehaviourWoodDiamond)tile.getPipe().getBehaviour();
        behavior.filters.setStackInSlot(0,new ItemStack(Items.DIAMOND));behavior.filters.setStackInSlot(1,new ItemStack(Items.EMERALD));behavior.setFilterMode(PipeBehaviourWoodDiamond.FilterMode.ROUND_ROBIN);
        adaptor(tile);var input=rf(c,tile);var before=tile.createNbt();
        try(var tx=Transaction.openOuter()){input.insert(10,tx);c.assertTrue(behavior.currentFilter==1,"Transactional cursor did not advance");}
        c.assertTrue(before.equals(tile.createNbt()) && count(source)==6,"RF abort changed round-robin cursor");
        try(var tx=Transaction.openOuter()){input.insert(10,tx);input.insert(10,tx);tx.commit();}
        c.assertTrue(behavior.currentFilter==0 && count(source)==4 && contents(tile)==2,"Committed round-robin order wrong");
    });c.complete();}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void emzuliPresetCursorAndPaintJoinRfTransaction(TestContext c){automatic(()->{
        var tile=pipe(c,POS,BCTransportItems.pipeItemEmzuli);var source=source(c,tile,3);source.setStack(1,new ItemStack(Items.EMERALD,3));
        var behavior=(PipeBehaviourEmzuli)tile.getPipe().getBehaviour();behavior.invFilters.setStackInSlot(0,new ItemStack(Items.DIAMOND));behavior.invFilters.setStackInSlot(1,new ItemStack(Items.EMERALD));behavior.setColour(PipeBehaviourEmzuli.SlotIndex.SQUARE,DyeColor.BLUE);
        var gate=new PluggableGate(BCSiliconPlugs.gate,tile,Direction.UP,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.NETHER_BRICK,EnumGateModifier.NO_MODIFIER));
        for(int i=0;i<2;i++){gate.logic.statements[i].trigger.set(TriggerWrapper.wrap(buildcraft.api.statements.StatementManager.statements.get("buildcraftcore:trigger.true"),null));gate.logic.statements[i].action.set(ActionWrapper.wrap(BCTransportStatements.ACTION_EXTRACTION_PRESET[i],null));}
        tile.replacePluggable(Direction.UP,gate);gate.logic.resolveActions();adaptor(tile);var input=rf(c,tile);var before=tile.createNbt();
        try(var tx=Transaction.openOuter()){input.insert(10,tx);}
        c.assertTrue(before.equals(tile.createNbt()) && count(source)==6,"RF abort changed Emzuli preset");
        try(var tx=Transaction.openOuter()){input.insert(10,tx);tx.commit();}
        var entry=tile.getPipe().getFlow().writeToNbt().getList("items",NbtElement.COMPOUND_TYPE).getCompound(0);
        c.assertTrue(behavior.getCurrentSlot()==PipeBehaviourEmzuli.SlotIndex.CIRCLE && entry.getByte("colour")==DyeColor.BLUE.getId()+1,"Committed RF lost preset or paint");
    });c.complete();}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void onlyPowerFlowsConnectThroughAdaptorAndRemovalClosesThem(TestContext c){automatic(()->{
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);source(c,tile,3);adaptor(tile);
        for(var item:List.of(BCTransportItems.pipePowerStone,BCTransportItems.pipeRfStone,BCTransportItems.pipeItemStone,BCTransportItems.pipeFluidStone)){
            var other=pipe(c,POS.north(),item);other.tick();boolean energy=item==BCTransportItems.pipePowerStone || item==BCTransportItems.pipeRfStone;
            c.assertTrue(other.getPipe().isConnected(Direction.SOUTH)==energy,"Adaptor connection family wrong "+item);
            if(energy)c.assertTrue(other.getPipe().getConnectedType(Direction.SOUTH)==IPipe.ConnectedType.TILE && other.getPipe().getConnectedPipe(Direction.SOUTH)==null,"Adaptor appeared as pipe flow");
            c.getWorld().removeBlock(other.getPos(),false);
        }
        var other=pipe(c,POS.north(),BCTransportItems.pipePowerStone);other.tick();tile.replacePluggable(Direction.NORTH,new PluggableBlocker(BCTransportPlugs.blocker,tile,Direction.NORTH));other.getPipe().markForUpdate();other.tick();
        c.assertTrue(!other.getPipe().isConnected(Direction.SOUTH),"Blocker retained adaptor power connection");
    });c.complete();}
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=180)
    public void realMjPipeFeedsAdaptorThenItemPipelineWithoutMixingFlows(TestContext c){
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,3);adaptor(tile);pipe(c,POS.east(),BCTransportItems.pipeItemStone);c.setBlockState(POS.east(2),Blocks.BARREL);
        var relay=pipe(c,POS.north(),BCTransportItems.pipePowerStone);var input=pipe(c,POS.north(2),BCTransportItems.pipePowerWood);tile.tick();relay.tick();input.tick();
        var receiver=(IMjReceiver)MjAPI.findConnector(input,Direction.NORTH);c.assertTrue(receiver.receivePower(4*MjAPI.MJ,false)==0,"Wooden MJ pipe rejected input");
        c.waitAndRun(150,()->{c.assertTrue(source.isEmpty() && count((BarrelBlockEntity)c.getBlockEntity(POS.east(2)))==3,"MJ adaptor pipeline failed");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void boundedLegacyCreditAndExactNativeCraftingRecipes(TestContext c){
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var nbt=new NbtCompound();nbt.putLong("storedMJ",Long.MAX_VALUE);var plug=(PluggablePowerAdaptor)BCTransportPlugs.powerAdaptor.readFromNbt(tile,Direction.NORTH,nbt);
        c.assertTrue(plug.getStoredMicroMj()==MjRfConversion.MAX_MJ_PER_RF-1,"Oversized saved credit accepted");nbt.putLong("storedMJ",-7);
        c.assertTrue(((PluggablePowerAdaptor)BCTransportPlugs.powerAdaptor.readFromNbt(tile,Direction.NORTH,nbt)).getStoredMicroMj()==0,"Negative saved credit accepted");
        var recipe=(CraftingRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcrafttransport:plug_power_adaptor")).orElseThrow();
        var grid=new net.minecraft.inventory.CraftingInventory(new net.minecraft.screen.ScreenHandler(null,0){public ItemStack quickMove(net.minecraft.entity.player.PlayerEntity p,int slot){return ItemStack.EMPTY;}public boolean canUse(net.minecraft.entity.player.PlayerEntity p){return true;}},3,3);
        for(int i=0;i<9;i++)grid.setStack(i,new ItemStack(i%3==1?(i==1?Items.GOLD_INGOT:i==4?BCCoreItems.gearStone:Items.REDSTONE):BCTransportItems.pipeStructure));
        c.assertTrue(recipe.matches(grid,c.getWorld()) && recipe.craft(grid,c.getWorld().getRegistryManager()).getCount()==4,"Native adaptor recipe broken");
        var blocker=(CraftingRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcrafttransport:plug_blocker")).orElseThrow();grid.clear();grid.setStack(8,new ItemStack(BCTransportItems.pipeStructure));
        c.assertTrue(blocker.matches(grid,c.getWorld()) && blocker.craft(grid,c.getWorld().getRegistryManager()).getCount()==4,"Native blocker recipe broken");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pipeBreakRecoversBothAttachmentsAndInvalidatesRf(TestContext c){automatic(()->{
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);source(c,tile,3);adaptor(tile);tile.replacePluggable(Direction.UP,new PluggableBlocker(BCTransportPlugs.blocker,tile,Direction.UP));var input=rf(c,tile);c.getWorld().breakBlock(tile.getPos(),true);
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->true);
        for(var item:List.of(BCTransportItems.plugBlocker,BCTransportItems.plugPowerAdaptor))c.assertTrue(drops.stream().filter(e->e.getStack().isOf(item)).mapToInt(e->e.getStack().getCount()).sum()==1,"Attachment drop lost/duplicated");
        try(var tx=Transaction.openOuter()){c.assertTrue(!input.supportsInsertion() && input.insert(10,tx)==0,"Removed pipe retained RF endpoint");}
    });c.complete();}
}
