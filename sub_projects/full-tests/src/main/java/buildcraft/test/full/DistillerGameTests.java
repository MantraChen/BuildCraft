/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import buildcraft.api.mj.*;
import buildcraft.api.recipes.*;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.*;
import buildcraft.factory.tile.*;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.registry.BlockEntityRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.*;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class DistillerGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3,3,3);
    private static final long MB = FluidConstants.BUCKET / 1000;
    private static FluidVariant oil() { return FluidVariant.of(BCEnergyFluids.crudeOil[0]); }
    private static FluidVariant gas() { return FluidVariant.of(BCEnergyFluids.fuelGaseous[0]); }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exactOriginalOilRatioAndThirtyTwoMjCost(TestContext c) {
        var tile = machine(c); fill(tile.tankIn, oil(), 8*MB); power(tile,32*MjAPI.MJ);
        finish(tile);
        c.assertTrue(tile.tankIn.getStorage().getAmount()==0 && tile.tankGasOut.getStorage().getAmount()==16*MB
            && tile.tankLiquidOut.getStorage().getAmount()==3*MB && tile.getStoredPower()==0 && tile.getDistillPower()==0,
            "Cold oil ratio or exact 32 MJ payment incorrect");
        var player=c.getWorld().getServer().getPlayerManager().getPlayer(tile.getOwner().getId());
        var adv=c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:refining"));
        c.assertTrue(adv!=null && player.getAdvancementTracker().getProgress(adv).isDone(),"Owner refining advancement missing");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allTenRecipesKeepOriginalHeatAndReducedRatios(TestContext c) {
        var registry=BuildcraftRecipeRegistry.refineryRecipes.getDistillationRegistry();
        c.assertTrue(registry.getAllRecipes().size()==10,"Expected ten native distillation recipes");
        var tile=machine(c);
        for(var recipe:List.copyOf(registry.getAllRecipes())) {
            clear(tile.tankIn);clear(tile.tankGasOut);clear(tile.tankLiquidOut);
            fill(tile.tankIn,recipe.in().getFluidVariant(),recipe.in().amount);power(tile,recipe.powerRequired());finish(tile);
            c.assertTrue(tile.tankIn.getStorage().getAmount()==0
                && tile.tankGasOut.getStorage().getResource().equals(recipe.outGas().getFluidVariant())
                && tile.tankGasOut.getStorage().getAmount()==recipe.outGas().amount
                && tile.tankLiquidOut.getStorage().getResource().equals(recipe.outLiquid().getFluidVariant())
                && tile.tankLiquidOut.getStorage().getAmount()==recipe.outLiquid().amount && tile.getStoredPower()==0,
                "A heated recipe changed its product temperature, ratio or MJ cost");
        }
        var warm=registry.getRecipeForInput(new FluidStackBC(BCEnergyFluids.crudeOil[1],1));
        c.assertTrue(warm.in().amount==4*MB && warm.outGas().amount==5*MB && warm.outLiquid().amount==MB
            && warm.powerRequired()==8*MjAPI.MJ,"Warm oil common-factor reduction changed cost");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void insufficientInputAndEitherBlockedOutputAreAtomic(TestContext c) {
        var tile=machine(c);fill(tile.tankIn,oil(),8*MB-1);power(tile,32*MjAPI.MJ);tile.tick();
        c.assertTrue(!tile.hasWork() && tile.getStoredPower()==32*MjAPI.MJ && tile.tankIn.getStorage().getAmount()==8*MB-1,
            "Sub-droplet shortage consumed partial input/energy");
        fill(tile.tankIn,oil(),1);
        fill(tile.tankLiquidOut,FluidVariant.of(BCEnergyFluids.oilHeavy[0]),4000*MB-3*MB+1);tile.tick();
        c.assertTrue(!tile.hasWork() && tile.tankGasOut.getStorage().getAmount()==0 && tile.tankIn.getStorage().getAmount()==8*MB,
            "Liquid overflow partially committed gas/input");
        clear(tile.tankLiquidOut);fill(tile.tankGasOut,FluidVariant.of(net.minecraft.fluid.Fluids.WATER),MB);tile.tick();
        c.assertTrue(!tile.hasWork() && tile.getStoredPower()==32*MjAPI.MJ,"Mismatched gas consumed power");
        clear(tile.tankGasOut);finish(tile);
        c.assertTrue(tile.tankGasOut.getStorage().getAmount()==16*MB,"Unblocked distiller failed to resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sidedAccessFiltersVariantsAndAbortedTransfersRollback(TestContext c) {
        var tile=machine(c);
        var input=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);
        var up=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.UP);
        var down=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.DOWN);
        NbtCompound tag=new NbtCompound();tag.putString("grade","different");
        try(var tx=Transaction.openOuter()) {
            c.assertTrue(input.insert(FluidVariant.of(net.minecraft.fluid.Fluids.WATER),MB,tx)==0
                && input.insert(FluidVariant.of(BCEnergyFluids.crudeOil[0],tag),MB,tx)==0
                && input.insert(oil(),8*MB,tx)==8*MB && input.extract(oil(),MB,tx)==0
                && up.insert(gas(),MB,tx)==0 && down.insert(oil(),MB,tx)==0,"Sided refinery access or exact-NBT recipe filter failed");
        }
        c.assertTrue(tile.tankIn.getStorage().getAmount()==0,"Aborted input leaked");
        fill(tile.tankIn,oil(),8*MB);power(tile,32*MjAPI.MJ);finish(tile);
        try(var tx=Transaction.openOuter()) {
            c.assertTrue(up.extract(gas(),16*MB,tx)==16*MB && down.extract(gas(),16*MB,tx)==0,"Vertical output sides crossed");
        }
        c.assertTrue(tile.tankGasOut.getStorage().getAmount()==16*MB,"Aborted output leaked");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void reservoirIsBoundedSimulatedAndSixMjPerTickLimited(TestContext c) {
        var tile=machine(c);var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.EAST);
        c.assertTrue(receiver.receivePower(Long.MAX_VALUE,true)==Long.MAX_VALUE-TileDistiller_BC8.CAPACITY
            && tile.getStoredPower()==0,"Simulated MJ insertion mutated reservoir");
        c.assertTrue(receiver.receivePower(Long.MAX_VALUE,false)==Long.MAX_VALUE-TileDistiller_BC8.CAPACITY
            && receiver.getPowerRequested()==0,"Reservoir overflowed");
        fill(tile.tankIn,oil(),8*MB);tile.tick();
        c.assertTrue(tile.getDistillPower()==6*MjAPI.MJ && receiver.getPowerRequested()==0,
            "Throughput exceeded 6 MJ/t or exposed reserved work as free capacity");
        c.assertTrue(receiver instanceof IMjReadable readable && readable.getStored()==TileDistiller_BC8.CAPACITY
            && readable.getCapacity()==TileDistiller_BC8.CAPACITY && !(receiver instanceof IMjRedstoneReceiver),
            "Distiller lost readable MJ capacity or accepted cheap redstone-engine power");
        for(int i=0;i<5;i++)tile.tick();
        c.assertTrue(tile.getStoredPower()==(1024-32)*MjAPI.MJ && tile.getDistillPower()==0,"Batch cost/rate changed");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void savedPaymentSurvivesBeforeWorldAttachmentAndStaleHandlesStop(TestContext c) {
        var tile=machine(c);fill(tile.tankIn,oil(),8*MB);power(tile,4*MjAPI.MJ);finish(tile);
        c.assertTrue(tile.getDistillPower()==4*MjAPI.MJ,"Partial power fixture did not stop");
        var fluid=FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);
        var mj=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.NORTH);
        var nbt=tile.createNbt();var pos=tile.getPos();var state=tile.getCachedState();
        c.getWorld().removeBlockEntity(pos);
        var restored=BlockEntityRegistry.create(TileDistiller_BC8.class,pos,state);restored.readNbt(nbt);c.getWorld().addBlockEntity(restored);restored.onLoad();
        restored.tick();c.assertTrue(restored.getDistillPower()==4*MjAPI.MJ,"First attached tick lost paid work");
        try(var tx=Transaction.openOuter()){c.assertTrue(fluid.insert(oil(),MB,tx)==0,"Stale fluid handle changed replaced tile");}
        c.assertTrue(mj.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Stale MJ handle accepted power");
        power(restored,28*MjAPI.MJ);finish(restored);
        c.assertTrue(restored.tankGasOut.getStorage().getAmount()==16*MB && restored.getStoredPower()==0,"Reload duplicated/forgot payment");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void recipeReplacementRefundsEnergyAndCannotReuseObsoleteOutput(TestContext c) {
        var tile=machine(c);fill(tile.tankIn,oil(),8*MB);power(tile,4*MjAPI.MJ);finish(tile);
        var manager=BuildcraftRecipeRegistry.refineryRecipes;var registry=manager.getDistillationRegistry();
        var original=registry.getRecipeForInput(new FluidStackBC(BCEnergyFluids.crudeOil[0],8));
        try {
            registry.addRecipe(manager.createDistillationRecipe(original.in(),new FluidStackBC(BCEnergyFluids.fuelLight[0],5),
                original.outLiquid(),8*MjAPI.MJ));
            tile.tick();c.assertTrue(tile.getStoredPower()+tile.getDistillPower()==4*MjAPI.MJ,"Recipe replacement lost/refabricated energy");
            power(tile,4*MjAPI.MJ);finish(tile);
            c.assertTrue(tile.tankGasOut.getStorage().getResource().isOf(BCEnergyFluids.fuelLight[0])
                && tile.tankGasOut.getStorage().getAmount()==5*MB,"Obsolete recipe result survived replacement");
            fill(tile.tankIn,oil(),8*MB);power(tile,4*MjAPI.MJ);clear(tile.tankGasOut);finish(tile);
            registry.removeRecipes(r->r.in().getFluidVariant().equals(oil()));tile.tick();
            c.assertTrue(tile.getStoredPower()==4*MjAPI.MJ && tile.getDistillPower()==0 && !tile.hasWork(),
                "Removed recipe retained paid work or discarded power");
        } finally { registry.addRecipe(original); }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedPaidBatchRefundsWithoutExceedingCapacity(TestContext c) {
        var tile=machine(c);fill(tile.tankIn,oil(),8*MB);power(tile,TileDistiller_BC8.CAPACITY);tile.tick();
        fill(tile.tankGasOut,gas(),4000*MB);tile.tick();
        c.assertTrue(tile.getStoredPower()==TileDistiller_BC8.CAPACITY && tile.getDistillPower()==0
            && tile.tankIn.getStorage().getAmount()==8*MB && !tile.hasWork(),"Blocked partial work did not refund exactly");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeScreenShiftClickAndManualOutputsPreserveBuckets(TestContext c) {
        var tile=machine(c);
        // TestContext's creative mock overrides isCreative() even after changeGameMode.
        var player=new net.minecraft.server.network.ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),
            new com.mojang.authlib.GameProfile(UUID.randomUUID(),"distiller-survival"));
        c.getWorld().getServer().getPlayerManager().onPlayerConnect(
            new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),player);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var v=Vec3d.ofCenter(tile.getPos());player.refreshPositionAndAngles(v.x,v.y,v.z-2,0,0);
        player.getInventory().setStack(9,new ItemStack(BCEnergyFluids.crudeOil[0].getBucketItem()));
        var handler=new ContainerDistiller(player,1,tile);handler.quickMove(player,0);
        c.assertTrue(tile.tankIn.getStorage().getAmount()==FluidConstants.BUCKET && player.getInventory().getStack(9).isOf(Items.BUCKET),
            "Screen shift-click did not exchange exact oil bucket: amount="+tile.tankIn.getStorage().getAmount()+", slot="+player.getInventory().getStack(9)+", inventory="+player.getInventory().main);
        fill(tile.tankGasOut,gas(),FluidConstants.BUCKET);handler.setCursorStack(new ItemStack(Items.BUCKET));
        tile.tankGasOut.onGuiClicked(handler);
        c.assertTrue(handler.getCursorStack().isOf(BCEnergyFluids.fuelGaseous[0].getBucketItem()) && tile.tankGasOut.getStorage().getAmount()==0,
            "Output GUI failed to withdraw bucket");
        tile.tankGasOut.onGuiClicked(handler);
        c.assertTrue(tile.tankGasOut.getStorage().getAmount()==0 && handler.getCursorStack().isOf(BCEnergyFluids.fuelGaseous[0].getBucketItem()),
            "Output GUI allowed reverse filling");
        c.setBlockState(POS,Blocks.AIR);handler.quickMove(player,0);
        c.assertTrue(!handler.canUse(player),"Removed screen stayed usable");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void removalRecoversAllThreeExactFractionalTanksOnce(TestContext c) {
        var tile=machine(c);fill(tile.tankIn,oil(),MB+1);fill(tile.tankGasOut,gas(),2*MB+1);
        fill(tile.tankLiquidOut,FluidVariant.of(BCEnergyFluids.oilHeavy[0]),3*MB+1);
        c.setBlockState(POS,Blocks.AIR);tile.onRemove();
        long recovered=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->true).stream()
            .map(e->buildcraft.core.item.ItemFragileFluidContainer.getFluid(e.getStack())).mapToLong(f->f.amount).sum();
        c.assertTrue(recovered==6*MB+3,"Removal lost or duplicated one of three tank fractions");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void survivalDistillerRecipeUsesThreeTanksDiamondGearAndTwoTorches(TestContext c) {
        var recipe=c.getWorld().getRecipeManager().get(new Identifier("buildcraftfactory:distiller")).orElseThrow();
        var grid=new buildcraft.lib.tile.craft.WorkbenchCrafting.Grid(3,3);
        grid.setStack(0,new ItemStack(Items.REDSTONE_TORCH));grid.setStack(1,new ItemStack(BCFactoryBlocks.tank));grid.setStack(2,new ItemStack(Items.REDSTONE_TORCH));
        grid.setStack(3,new ItemStack(BCFactoryBlocks.tank));
        grid.setStack(4,new ItemStack(net.minecraft.registry.Registries.ITEM.get(new Identifier("buildcraftcore:gear_diamond"))));
        grid.setStack(5,new ItemStack(BCFactoryBlocks.tank));
        c.assertTrue(((CraftingRecipe)recipe).matches(grid,c.getWorld())
            && ((CraftingRecipe)recipe).craft(grid,c.getWorld().getRegistryManager()).isOf(BCFactoryBlocks.distiller.asItem()),
            "Native survival distillery recipe failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=240)
    public void poweredWoodenFluidPipeExtractsOnlyTheTopGasOutput(TestContext c) {
        var tile=machine(c);fill(tile.tankIn,oil(),8*MB);fill(tile.tankGasOut,gas(),FluidConstants.BUCKET);
        var pipePos=POS.up();c.setBlockState(pipePos,buildcraft.transport.BCTransportBlocks.pipeHolder);
        var pipe=(buildcraft.transport.tile.TilePipeHolder)c.getBlockEntity(pipePos);
        pipe.onPlacedBy(null,new ItemStack(buildcraft.transport.BCTransportItems.pipeFluidWood));
        c.setBlockState(pipePos.east(),BCFactoryBlocks.tank);
        pipe.tick();
        pipe.fireEvent(new buildcraft.api.transport.pipe.PipeEventActionActivate(pipe,
            buildcraft.transport.BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.DOWN.ordinal()],
            new buildcraft.api.statements.IStatementParameter[0],buildcraft.api.core.EnumPipePart.CENTER));
        var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),pipe.getPos(),Direction.NORTH);
        c.assertTrue(receiver.receivePower(MjAPI.MJ,false)==0 && tile.tankGasOut.getStorage().getAmount()==0
            && tile.tankIn.getStorage().getAmount()==8*MB,"Wooden extractor did not drain the gas port exactly");
        c.runAtTick(210,()->{
            var destination=(TileTank)c.getBlockEntity(pipePos.east());
            c.assertTrue(destination.tank.getStorage().getResource().equals(gas())
                && destination.tank.getStorage().getAmount()==FluidConstants.BUCKET,"Gas pipeline lost fluid or extracted input");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void truncatedTankPacketDoesNotPartiallyReplaceVisibleFluids(TestContext c) throws java.io.IOException {
        var tile=machine(c);fill(tile.tankIn,oil(),MB+1);fill(tile.tankGasOut,gas(),2*MB+1);
        var client=BlockEntityRegistry.create(TileDistiller_BC8.class,tile.getPos(),tile.getCachedState());
        var buffer=new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            tile.writePayload(TileDistiller_BC8.NET_RENDER_DATA,buffer,buildcraft.lib.tile.TileBC_Neptune.NetSide.SERVER);
            client.readPayload(TileDistiller_BC8.NET_RENDER_DATA,buffer,buildcraft.lib.tile.TileBC_Neptune.NetSide.CLIENT,null);
            c.assertTrue(client.tankIn.getFluidForRender().amount==MB+1 && client.tankGasOut.getFluidForRender().amount==2*MB+1,"Tank snapshot rounded native droplets");
            buffer.clear();fill(tile.tankIn,oil(),MB);
            tile.writePayload(TileDistiller_BC8.NET_RENDER_DATA,buffer,buildcraft.lib.tile.TileBC_Neptune.NetSide.SERVER);
            buffer.writerIndex(buffer.writerIndex()-1);
            boolean rejected=false;
            try {client.readPayload(TileDistiller_BC8.NET_RENDER_DATA,buffer,buildcraft.lib.tile.TileBC_Neptune.NetSide.CLIENT,null);}
            catch(IndexOutOfBoundsException | java.io.IOException expected){rejected=true;}
            c.assertTrue(rejected && client.tankIn.getFluidForRender().amount==MB+1,"Truncated snapshot partially changed visible tanks");
        } finally { buffer.release(); }
        c.complete();
    }
    private static TileDistiller_BC8 machine(TestContext c) {
        c.setBlockState(POS,BCFactoryBlocks.distiller);var tile=(TileDistiller_BC8)c.getBlockEntity(POS);tile.onLoad();
        tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCFactoryBlocks.distiller));return tile;
    }
    private static void fill(buildcraft.lib.fluid.Tank tank,FluidVariant fluid,long amount) {
        try(var tx=Transaction.openOuter()){if(tank.getStorage().insert(fluid,amount,tx)!=amount)throw new AssertionError("Fixture tank rejected "+amount);tx.commit();}
    }
    private static void clear(buildcraft.lib.fluid.Tank tank) {
        try(var tx=Transaction.openOuter()){if(!tank.getStorage().isResourceBlank())tank.getStorage().extract(tank.getStorage().getResource(),Long.MAX_VALUE,tx);tx.commit();}
    }
    private static void power(TileDistiller_BC8 tile,long amount) {
        if(tile.receivePower(amount,false)!=0)throw new AssertionError("Fixture MJ refused");
    }
    private static void finish(TileDistiller_BC8 tile) { for(int i=0;i<1000;i++)tile.tick(); }
}
