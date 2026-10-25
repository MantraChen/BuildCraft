/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import buildcraft.api.mj.*;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.transport.*;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.PipeRegistry;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.recipe.PipeUpgradeRecipe;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class FluidRecoveryGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,3,3);
    private static FluidVariant taggedWater() { var tag=new NbtCompound(); tag.putString("batch","Remake by FelixMantra"); return FluidVariant.of(Fluids.WATER,tag); }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void shardDropsSplitExactAmountsPreserveNbtAndBoundMalformedData(TestContext c) {
        var fluid=taggedWater(); var source=FluidStackBC.of(fluid,81037);
        var drops=DefaultedList.<ItemStack>of(); BCCoreItems.fragileFluidShard.addFluidDrops(drops,source);
        c.assertTrue(drops.size()==3 && source.getAmount()==81037,"Shard splitting mutated or rounded source fluid");
        long total=0;
        for(var stack:drops) {
            var saved=ItemStack.fromNbt(stack.writeNbt(new NbtCompound())); var contents=ItemFragileFluidContainer.getFluid(saved);
            c.assertTrue(stack.getCount()==1 && stack.getMaxCount()==1 && contents.getFluidVariant().equals(fluid)
                && contents.getAmount()<=40500,"Shard item or serialization lost variant/capacity");
            total+=contents.getAmount();
        }
        c.assertTrue(total==81037 && ItemFragileFluidContainer.getFluid(drops.get(2)).getAmount()==37,"Shard remainder was lost");
        var bad=drops.get(0).copy(); bad.getSubNbt("fluid").putLong("Amount",Long.MAX_VALUE);
        c.assertTrue(ItemFragileFluidContainer.getFluid(bad).getAmount()==40500,"Oversized shard amount was not bounded");
        bad.getSubNbt("fluid").putLong("Amount",-1);
        c.assertTrue(ItemFragileFluidContainer.getFluid(bad).isEmpty(),"Negative shard amount accepted");
        var legacy=new NbtCompound(); legacy.putString("FluidName","water"); legacy.putInt("Amount",123); legacy.put("Tag",fluid.copyNbt());
        bad.getOrCreateNbt().put("fluid",legacy);
        c.assertTrue(ItemFragileFluidContainer.getFluid(bad).getAmount()==123*81 && ItemFragileFluidContainer.getFluid(bad).getFluidVariant().equals(fluid),"Legacy shard did not convert mB/NBT");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeShardExtractionRollsBackReplacementAndConsumesOnlyWhenEmpty(TestContext c) {
        var fluid=taggedWater(); var slot=new Slot(BCCoreItems.fragileFluidShard.stack(fluid,1001));
        slot.stack.setCustomName(Text.literal("Recovered sample")); var original=slot.stack.copy();
        var context=ContainerItemContext.ofSingleSlot(slot); var storage=context.find(FluidStorage.ITEM);
        c.assertTrue(storage!=null && !storage.supportsInsertion(),"Shard native item storage missing or fillable");
        try(var outer=Transaction.openOuter()) {
            c.assertTrue(storage.insert(fluid,1,outer)==0 && storage.extract(FluidVariant.of(Fluids.WATER),1,outer)==0,"Shard accepted refill or mismatched NBT");
            c.assertTrue(storage.extract(fluid,17,outer)==17,"Shard could not extract fractional mB");
            try(var inner=outer.openNested()) { c.assertTrue(storage.extract(fluid,984,inner)==984 && slot.stack.isEmpty(),"Last drop did not destroy shard"); inner.commit(); }
        }
        c.assertTrue(ItemStack.areEqual(original,slot.stack),"Outer rollback lost the destroyed shard or custom name");
        try(var tx=Transaction.openOuter()) { c.assertTrue(storage.extract(fluid,17,tx)==17,"Committed partial extraction failed"); tx.commit(); }
        c.assertTrue(ItemFragileFluidContainer.getFluid(slot.stack).getAmount()==984 && slot.stack.getName().getString().equals("Recovered sample"),"Partial extraction lost remaining fluid or custom NBT");
        try(var tx=Transaction.openOuter()) { c.assertTrue(storage.extract(fluid,Long.MAX_VALUE,tx)==984,"Final extraction returned the wrong amount"); tx.commit(); }
        c.assertTrue(slot.stack.isEmpty(),"Consumed shard left an empty reusable container");
        slot.stack=new ItemStack(Items.DIAMOND);
        try(var tx=Transaction.openOuter()) { c.assertTrue(storage.extract(fluid,1,tx)==0 && storage.iterator().next().getAmount()==0,"Stale shard storage altered a replacement item"); }
        var blocked=new Slot(original) { @Override protected boolean canInsert(ItemVariant variant) { return false; } };
        var blockedStorage=ContainerItemContext.ofSingleSlot(blocked).find(FluidStorage.ITEM);
        try(var tx=Transaction.openOuter()) { c.assertTrue(blockedStorage.extract(fluid,17,tx)==0 && ItemStack.areEqual(original,blocked.stack),"Rejected item replacement lost fluid"); tx.commit(); }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeSurvivalPipeBreakAndPlayerTankRecoveryConserveEveryDroplet(TestContext c) {
        var fluid=taggedWater();
        c.setBlockState(POS.west(),BCFactoryBlocks.tank); var world=c.getWorld();
        var tankPos=c.getAbsolutePos(POS.west()); var tank=FluidStorage.SIDED.find(world,tankPos,Direction.EAST);
        try(var tx=Transaction.openOuter()) { c.assertTrue(tank.insert(fluid,81037,tx)==81037,"Source fixture fill failed"); tx.commit(); }
        var tile=wood(c); var mj=(IMjReceiver)MjAPI.CONNECTOR.find(world,tile.getPos(),Direction.UP);
        mj.receivePower(2*MjAPI.MJ,false);
        c.assertTrue(((PipeFlowFluids)tile.getPipe().flow).getAmount()==81037,"Fixture extraction failed");
        var player=c.createMockCreativeServerPlayerInWorld(); player.changeGameMode(GameMode.SURVIVAL); player.getInventory().clear();
        var center=Vec3d.ofCenter(tile.getPos()); player.refreshPositionAndAngles(center.x,center.y-player.getEyeHeight(player.getPose()),center.z-2,0,0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
        c.assertTrue(player.interactionManager.tryBreakBlock(tile.getPos()),"Native survival break rejected");
        var entities=world.getEntitiesByClass(ItemEntity.class,new Box(tile.getPos()).expand(1),e->true);
        long total=0; int pipes=0,shards=0;
        for(var entity:entities) {
            var stack=entity.getStack();
            if(stack.isOf(BCTransportItems.pipeFluidWood)) pipes+=stack.getCount();
            if(stack.isOf(BCCoreItems.fragileFluidShard)) { shards++; total+=ItemFragileFluidContainer.getFluid(stack).getAmount(); }
            entity.setPickupDelay(0); entity.onPlayerCollision(player);
        }
        c.assertTrue(pipes==1 && shards==3 && total==81037,"Native pipe break lost or duplicated fluid shards/pipe");
        int recovered=0;
        for(int slot=0;slot<9;slot++) if(player.getInventory().getStack(slot).isOf(BCCoreItems.fragileFluidShard)) {
            player.getInventory().selectedSlot=slot;
            var state=world.getBlockState(tankPos);
            state.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(tankPos),Direction.EAST,tankPos,false));
            c.assertTrue(player.getMainHandStack().isEmpty(),"Native tank use did not consume recovered shard"); recovered++;
        }
        long delivered=0; for(var view:tank.nonEmptyViews()) { c.assertTrue(view.getResource().equals(fluid),"Player recovery lost fluid NBT"); delivered+=view.getAmount(); }
        c.assertTrue(recovered==3 && delivered==81037,"Player tank recovery lost droplets");
        tile=wood(c); ((IMjReceiver)MjAPI.CONNECTOR.find(world,tile.getPos(),Direction.UP)).receivePower(2*MjAPI.MJ,false);
        player.changeGameMode(GameMode.CREATIVE); player.interactionManager.tryBreakBlock(tile.getPos());
        c.assertTrue(world.isAir(tile.getPos()) && world.getEntitiesByClass(ItemEntity.class,new Box(tile.getPos()).expand(1),e->true).isEmpty(),"Creative pipe break produced recoverable fluid loot");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allElevenFluidUpgradesAndUndoPreserveEveryPipeDyeAndConsumeIngredients(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld(); player.changeGameMode(GameMode.SURVIVAL);
        var handler=new CraftingScreenHandler(1,player.getInventory(),ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        var colours=java.util.Arrays.copyOf(DyeColor.values(),17); int pairs=0;
        for(var def:PipeRegistry.INSTANCE.getAllRegisteredPipes()) {
            if(!def.identifier.getPath().endsWith("_fluid")) continue;
            var target=(ItemPipeHolder)PipeRegistry.INSTANCE.getItemForPipe(def);
            var itemId=new Identifier("buildcrafttransport","pipe_"+def.identifier.getPath().replace("_fluid","_item"));
            var source=net.minecraft.registry.Registries.ITEM.get(itemId); pairs++;
            for(var colour:colours) {
                reset(handler); handler.getSlot(1).setStack(ItemPipeHolder.withColour(new ItemStack(source,2),colour));
                handler.getSlot(5).setStack(new ItemStack(BCTransportItems.waterproof,2)); handler.onContentChanged(handler.getSlot(1).inventory);
                var result=handler.getSlot(0).getStack();
                c.assertTrue(result.isOf(target) && result.getCount()==1 && ItemPipeHolder.getColour(result)==colour,"Upgrade lost type/dye: "+def.identifier+" "+colour);
                handler.onSlotClick(0,0,SlotActionType.PICKUP,player);
                c.assertTrue(handler.getSlot(1).getStack().getCount()==1 && handler.getSlot(5).getStack().getCount()==1 && handler.getCursorStack().isOf(target),"Upgrade did not consume exactly one pipe/sealant");
                reset(handler); handler.getSlot(9).setStack(ItemPipeHolder.withColour(new ItemStack(target,2),colour)); handler.onContentChanged(handler.getSlot(9).inventory);
                result=handler.getSlot(0).getStack();
                c.assertTrue(result.isOf(source) && ItemPipeHolder.getColour(result)==colour,"Undo lost pipe material/dye");
                handler.onSlotClick(0,0,SlotActionType.PICKUP,player);
                c.assertTrue(handler.getSlot(9).getStack().getCount()==1 && handler.getCursorStack().getCount()==1,"Undo duplicated pipe ingredients");
            }
        }
        c.assertTrue(pairs==11,"Not all registered fluid pipes have upgrade/undo recipes"); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sealantRecipesConsumeDyeSlimeAndResidueAndRetainTheBucket(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld(); player.changeGameMode(GameMode.SURVIVAL);
        var handler=new CraftingScreenHandler(1,player.getInventory(),ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        for(var input:new Item[]{Items.GREEN_DYE,Items.SLIME_BALL,buildcraft.energy.BCEnergyFluids.oilResidue[0].getBucketItem()}) {
            reset(handler); handler.getSlot(1).setStack(new ItemStack(input)); handler.onContentChanged(handler.getSlot(1).inventory);
            int expected=input instanceof BucketItem?8:1;
            c.assertTrue(handler.getSlot(0).getStack().isOf(BCTransportItems.waterproof) && handler.getSlot(0).getStack().getCount()==expected,"Sealant recipe missing or wrong yield");
            handler.onSlotClick(0,0,SlotActionType.PICKUP,player);
            c.assertTrue(input instanceof BucketItem ? handler.getSlot(1).getStack().isOf(Items.BUCKET) : handler.getSlot(1).getStack().isEmpty(),"Sealant recipe consumed the empty bucket or retained ingredient");
        }
        c.assertTrue(c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcrafttransport:sealing_fluids"))!=null,"Sealant advancement missing");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeUpgradeRecipeNetworkRoundTripRetainsDyeAndRejectsExtraInput(TestContext c) {
        var recipe=(PipeUpgradeRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcrafttransport:pipe_gold_fluid")).orElseThrow();
        var buffer=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            BCTransportRecipes.PIPE_UPGRADE.write(buffer,recipe); var copy=BCTransportRecipes.PIPE_UPGRADE.read(recipe.getId(),buffer);
            var player=c.createMockCreativeServerPlayerInWorld(); var handler=new CraftingScreenHandler(1,player.getInventory());
            var inventory=(net.minecraft.inventory.RecipeInputInventory)handler.getSlot(1).inventory;
            inventory.setStack(0,ItemPipeHolder.withColour(new ItemStack(BCTransportItems.pipeItemGold),DyeColor.BLUE)); inventory.setStack(8,new ItemStack(BCTransportItems.waterproof));
            c.assertTrue(!buffer.isReadable() && copy.matches(inventory,c.getWorld()) && ItemPipeHolder.getColour(copy.craft(inventory,c.getWorld().getRegistryManager()))==DyeColor.BLUE,"Synced upgrade lost matching/output dye");
            inventory.setStack(4,new ItemStack(Items.STONE)); c.assertTrue(!copy.matches(inventory,c.getWorld()),"Upgrade consumed unrelated extra input");
        } finally { buffer.release(); }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void recoveredShardFiltersMatchExactFluidNbtWithoutConsumingSample(TestContext c) {
        var fluid=taggedWater(); c.setBlockState(POS.west(),BCFactoryBlocks.tank);
        var tank=FluidStorage.SIDED.find(c.getWorld(),c.getAbsolutePos(POS.west()),Direction.EAST);
        try(var tx=Transaction.openOuter()) { tank.insert(fluid,17,tx); tx.commit(); }
        c.setBlockState(POS,BCTransportBlocks.pipeHolder); var tile=(TilePipeHolder)c.getBlockEntity(POS);
        tile.onPlacedBy(null,new ItemStack(BCTransportItems.pipeFluidDiaWood)); tile.tick();
        var filters=((buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond)tile.getPipe().behaviour).filters;
        var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP);
        filters.setStackInSlot(0,new ItemStack(Items.WATER_BUCKET));
        c.assertTrue(receiver.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Plain bucket matched tagged fluid");
        var sample=BCCoreItems.fragileFluidShard.stack(fluid,1); filters.setStackInSlot(0,sample.copy());
        c.assertTrue(receiver.receivePower(MjAPI.MJ,true)<MjAPI.MJ && ((PipeFlowFluids)tile.getPipe().flow).getAmount()==0,"Shard filter simulation mutated pipe");
        c.assertTrue(receiver.receivePower(MjAPI.MJ,false)<MjAPI.MJ && ((PipeFlowFluids)tile.getPipe().flow).getAmount()==17,"Shard filter did not match exact tagged resource");
        c.assertTrue(ItemStack.areEqual(sample,filters.getStackInSlot(0)),"Fluid filter consumed the recovered sample");
        c.complete();
    }

    private static TilePipeHolder wood(TestContext c) { c.setBlockState(POS,BCTransportBlocks.pipeHolder); var tile=(TilePipeHolder)c.getBlockEntity(POS); tile.onPlacedBy(null,new ItemStack(BCTransportItems.pipeFluidWood)); tile.tick(); return tile; }
    private static void reset(CraftingScreenHandler handler) { handler.setCursorStack(ItemStack.EMPTY); for(int i=1;i<=9;i++)handler.getSlot(i).setStack(ItemStack.EMPTY); }
    private static class Slot extends SingleStackStorage {
        ItemStack stack; Slot(ItemStack stack) { this.stack=stack.copy(); }
        @Override protected ItemStack getStack() { return stack; }
        @Override protected void setStack(ItemStack stack) { this.stack=stack; }
    }
}
