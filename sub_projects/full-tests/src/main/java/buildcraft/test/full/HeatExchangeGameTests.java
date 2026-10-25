/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.*;
import buildcraft.factory.tile.*;
import buildcraft.factory.tile.TileHeatExchange.*;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.fluid.Tank;
import buildcraft.lib.registry.BlockEntityRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class HeatExchangeGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,3,3);
    private static final long MB=81;
    private static FluidVariant cold(){return FluidVariant.of(BCEnergyFluids.fuelLight[0]);}
    private static FluidVariant hot(){return FluidVariant.of(BCEnergyFluids.crudeOil[2]);}

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void originalWarmupAndFourTankExchangeAreExact(TestContext c) {
        var line=line(c,3);fill(start(line).tankInput,cold(),100*MB);fill(end(line).tankInput,hot(),100*MB);
        tick(line,120);
        c.assertTrue(start(line).getWarmupTicks()==119 && amount(start(line).tankOutput)==0 && amount(end(line).tankOutput)==0,
            "Heat exchange consumed fluid before its 120-tick warmup");
        tick(line,1);
        c.assertTrue(start(line).getProgressState()==EnumProgressState.RUNNING
            && amount(start(line).tankInput)==95*MB && amount(end(line).tankInput)==95*MB
            && amount(start(line).tankOutput)==5*MB && start(line).tankOutput.getStorage().getResource().isOf(BCEnergyFluids.crudeOil[1])
            && amount(end(line).tankOutput)==5*MB && end(line).tankOutput.getStorage().getResource().isOf(BCEnergyFluids.fuelLight[1]),
            "Counter-flow products, temperatures or exact 5 mB rate changed");
        var owner=c.getWorld().getServer().getPlayerManager().getPlayer(line.get(0).getOwner().getId());
        var adv=c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:heat_exchange"));
        c.assertTrue(adv!=null && owner.getAdvancementTracker().getProgress(adv).isDone(),"Native heat-exchange owner advancement missing");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void twoMiddleSectionsProcessTenMillibucketsPerTick(TestContext c) {rate(c,4,10);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void threeMiddleSectionsProcessTwentyMillibucketsPerTick(TestContext c) {rate(c,5,20);}
    private static void rate(TestContext c,int count,int rate) {
        var line=line(c,count);fill(start(line).tankInput,cold(),100*MB);fill(end(line).tankInput,hot(),100*MB);
        tick(line,121);
        c.assertTrue(amount(start(line).tankOutput)==rate*MB && amount(end(line).tankOutput)==rate*MB,"Middle-section throughput incorrect");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void equalTemperatureInputsDoNotGenerateHeat(TestContext c) {
        var line=line(c,3);var same=FluidVariant.of(BCEnergyFluids.crudeOil[1]);
        fill(start(line).tankInput,same,100*MB);fill(end(line).tankInput,same,100*MB);tick(line,140);
        c.assertTrue(!line.get(0).hasWork() && start(line).getWarmupTicks()==0
            && amount(start(line).tankInput)==100*MB && amount(end(line).tankInput)==100*MB,"Equal temperatures generated work");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void eitherBlockedOutputRollsBackBothInputsAndTheOtherOutput(TestContext c) {
        var line=line(c,3);fill(start(line).tankInput,cold(),100*MB);fill(end(line).tankInput,hot(),100*MB);
        fill(start(line).tankOutput,FluidVariant.of(BCEnergyFluids.crudeOil[1]),2000*MB);tick(line,130);
        c.assertTrue(amount(start(line).tankInput)==100*MB && amount(end(line).tankInput)==100*MB
            && amount(end(line).tankOutput)==0 && !line.get(0).hasWork(),"Full cooled outlet consumed one side");
        clear(start(line).tankOutput);fill(end(line).tankOutput,FluidVariant.of(net.minecraft.fluid.Fluids.WATER),MB);tick(line,130);
        c.assertTrue(amount(start(line).tankInput)==100*MB && amount(start(line).tankOutput)==0,"Incompatible heated outlet partially committed");
        clear(end(line).tankOutput);tick(line,121);
        c.assertTrue(amount(start(line).tankOutput)==5*MB && amount(end(line).tankOutput)==5*MB,"Unblocked exchanger failed to restart");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fractionalRemaindersAndOutputCapacityStayExact(TestContext c) {
        var line=line(c,3);fill(start(line).tankInput,cold(),7);fill(end(line).tankInput,hot(),9);
        fill(start(line).tankOutput,FluidVariant.of(BCEnergyFluids.crudeOil[1]),2000*MB-3);
        tick(line,121);
        c.assertTrue(amount(start(line).tankInput)==4 && amount(end(line).tankInput)==6
            && amount(start(line).tankOutput)==2000*MB && amount(end(line).tankOutput)==3,"Sub-mB heat exchange rounded or overflowed");
        tick(line,5);c.assertTrue(amount(start(line).tankInput)==4,"Blocked fractional batch leaked input");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void waterAndLavaAreConsumedOnlyAlongsideAValidOtherFluid(TestContext c) {
        var line=line(c,3);var water=FluidVariant.of(net.minecraft.fluid.Fluids.WATER);
        fill(start(line).tankInput,water,25*MB);fill(end(line).tankInput,hot(),25*MB);tick(line,125);
        c.assertTrue(amount(start(line).tankInput)==0 && amount(end(line).tankInput)==0
            && amount(start(line).tankOutput)==25*MB && amount(end(line).tankOutput)==0,"Water evaporation created an item/fluid residue or changed cooling volume");
        clear(start(line).tankOutput);fill(start(line).tankInput,cold(),25*MB);
        fill(end(line).tankInput,FluidVariant.of(net.minecraft.fluid.Fluids.LAVA),25*MB);tick(line,5);
        c.assertTrue(amount(start(line).tankInput)==0 && amount(end(line).tankInput)==0
            && amount(start(line).tankOutput)==0 && amount(end(line).tankOutput)==25*MB,"Lava heating changed equal-volume consumption");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sidedPortsRejectInvalidVariantsAndRollbackNativeTransactions(TestContext c) {
        var line=line(c,3);var first=line.get(0);var last=line.get(2);
        var a=FluidStorage.SIDED.find(c.getWorld(),first.getPos(),Direction.DOWN);
        var b=FluidStorage.SIDED.find(c.getWorld(),last.getPos(),Direction.EAST);
        c.assertTrue(a!=null && b!=null && FluidStorage.SIDED.find(c.getWorld(),first.getPos(),Direction.UP)==null
            && FluidStorage.SIDED.find(c.getWorld(),line.get(1).getPos(),Direction.NORTH)==null,"Heat-exchange port directions incorrect");
        NbtCompound tag=new NbtCompound();tag.putString("grade","exact");
        try(var tx=Transaction.openOuter()) {
            c.assertTrue(a.insert(hot(),MB,tx)==0 && b.insert(cold(),MB,tx)==0
                && a.insert(FluidVariant.of(BCEnergyFluids.fuelLight[0],tag),MB,tx)==0
                && a.insert(cold(),MB+1,tx)==MB+1 && b.insert(hot(),MB+1,tx)==MB+1,"Thermal input filter accepted wrong temperature/NBT");
            var out=FluidStorage.SIDED.find(c.getWorld(),first.getPos(),Direction.WEST);
            c.assertTrue(out.insert(cold(),MB,tx)==0 && !out.supportsInsertion(),"Output port accepted insertion");
        }
        c.assertTrue(amount(start(line).tankInput)==0 && amount(end(line).tankInput)==0,"Aborted input transaction leaked");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void wholeStructureReloadKeepsAllTanksAndWarmupBeforeWorldAttachment(TestContext c) {
        var line=line(c,3);fill(start(line).tankInput,cold(),100*MB+1);fill(end(line).tankInput,hot(),100*MB+3);tick(line,61);
        int paid=start(line).getWarmupTicks();var stale=FluidStorage.SIDED.find(c.getWorld(),line.get(0).getPos(),Direction.DOWN);
        List<TileHeatExchange> restored=new ArrayList<>();
        for(var tile:line) {
            var data=tile.createNbt();var at=tile.getPos();var state=tile.getCachedState();c.getWorld().removeBlockEntity(at);
            var replacement=BlockEntityRegistry.create(TileHeatExchange.class,at,state);
            replacement.readNbt(data);c.getWorld().addBlockEntity(replacement);replacement.onLoad();restored.add(replacement);
        }
        tick(restored,1);
        c.assertTrue(start(restored).getWarmupTicks()==paid+1 && amount(start(restored).tankInput)==100*MB+1
            && amount(end(restored).tankInput)==100*MB+3,"Reattachment reset warmup or endpoint storage");
        try(var tx=Transaction.openOuter()){c.assertTrue(stale.insert(cold(),1,tx)==0,"Reloaded section accepted stale input");}
        tick(restored,60);
        c.assertTrue(amount(start(restored).tankOutput)>0 && amount(start(restored).tankInput)+amount(end(restored).tankOutput)==100*MB+1,
            "Restored exchanger lost or duplicated cold-side volume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingMiddleStopsProcessingPreservesTanksAndCanReform(TestContext c) {
        var line=line(c,3);fill(start(line).tankInput,cold(),100*MB);fill(end(line).tankInput,hot(),100*MB);tick(line,121);
        var oldStart=start(line);var oldEnd=end(line);
        var cached=FluidStorage.SIDED.find(c.getWorld(),line.get(0).getPos(),Direction.DOWN);
        c.setBlockState(POS.east(),Blocks.AIR);tick(List.of(line.get(0),line.get(2)),10);
        c.assertTrue(!line.get(0).isFormed() && !line.get(2).isFormed() && amount(oldStart.tankInput)==95*MB
            && amount(oldEnd.tankInput)==95*MB,"Broken structure kept processing or discarded endpoint fluids");
        try(var tx=Transaction.openOuter()){c.assertTrue(cached.insert(cold(),MB,tx)==0,"Broken structure retained a live port");}
        var middle=place(c,POS.east());line.set(1,middle);tick(line,121);
        c.assertTrue(line.get(0).isStart() && line.get(2).isEnd()
            && amount(start(line).tankInput)+amount(end(line).tankOutput)==100*MB,"Repair lost or duplicated saved endpoint contents");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void temporaryEndpointUnloadNeverProcessesAnIncompleteStructure(TestContext c) {
        var line=line(c,3);fill(start(line).tankInput,cold(),100*MB);fill(end(line).tankInput,hot(),100*MB);tick(line,70);
        var oldStart=start(line);var oldEnd=end(line);line.get(2).markRemoved();tick(List.of(line.get(0),line.get(1)),20);
        c.assertTrue(!line.get(0).hasWork() && amount(oldStart.tankInput)==100*MB && amount(oldEnd.tankInput)==100*MB,
            "A detached endpoint kept participating in heat exchange");
        line.get(2).cancelRemoval();line.get(2).onLoad();tick(line,121);
        c.assertTrue(line.get(0).isFormed() && amount(start(line).tankInput)+amount(end(line).tankOutput)==100*MB,
            "Reattached endpoint lost fluid or could not reform");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void extendingTheStartMovesContentsAndRemovalDoesNotDuplicateOldOwnership(TestContext c) {
        var line=line(c,3);var first=line.get(0);fill(start(line).tankInput,cold(),700*MB+1);fill(end(line).tankInput,hot(),600*MB+2);
        var stale=FluidStorage.SIDED.find(c.getWorld(),first.getPos(),Direction.DOWN);
        var extension=place(c,POS.west());var expanded=new ArrayList<TileHeatExchange>();expanded.add(extension);expanded.addAll(line);tick(expanded,1);
        c.assertTrue(extension.isStart() && first.getSection()==null && amount(start(expanded).tankInput)==700*MB+1,
            "Extending the structure left its input at the old endpoint");
        try(var tx=Transaction.openOuter()){c.assertTrue(stale.extract(cold(),MB,tx)==0,"Moved section left an extractable old view");}
        c.setBlockState(POS,Blocks.AIR);first.onRemove();
        c.assertTrue(recovered(c,first.getPos())==0 && amount(start(expanded).tankInput)==700*MB+1,
            "Breaking the former endpoint duplicated moved fluid");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeWrenchReversesAllBlocksAndBothLogicalSections(TestContext c) {
        var line=line(c,4);fill(start(line).tankInput,cold(),700*MB+1);fill(end(line).tankInput,hot(),600*MB+2);
        tick(line,31);int warm=start(line).getWarmupTicks();var old=line.get(0);
        var cached=FluidStorage.SIDED.find(c.getWorld(),old.getPos(),Direction.DOWN);
        var player=c.createMockCreativeServerPlayerInWorld();var at=Vec3d.ofCenter(line.get(1).getPos());
        player.refreshPositionAndAngles(at.x,at.y,at.z-2,0,0);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCCoreItems.wrench));
        var hit=new net.minecraft.util.hit.BlockHitResult(at,Direction.UP,line.get(1).getPos(),false);
        c.assertTrue(player.interactionManager.interactBlock(player,c.getWorld(),player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted(),"Native wrench did not rotate exchanger");
        Collections.reverse(line);
        c.assertTrue(line.stream().allMatch(t->t.getFacing()==Direction.NORTH) && line.get(0).isStart() && line.get(3).isEnd()
            && amount(start(line).tankInput)==700*MB+1 && amount(end(line).tankInput)==600*MB+2 && start(line).getWarmupTicks()==warm,
            "Wrench split the structure, swapped fluid roles or lost warmth");
        try(var tx=Transaction.openOuter()){c.assertTrue(cached.insert(cold(),1,tx)==0,"Reversed structure kept its old input port");}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oversizedStructureStaysInactiveAndRecoversAfterShortening(TestContext c) {
        var line=line(c,5);fill(start(line).tankInput,cold(),100*MB);fill(end(line).tankInput,hot(),100*MB);
        var extra=place(c,POS.east(5));line.add(extra);tick(line,130);
        c.assertTrue(line.stream().noneMatch(TileHeatExchange::isFormed) && !line.get(0).hasWork()
            && amount(((ExchangeSectionStart)line.get(0).getSection()).tankInput)==100*MB,"Six-block structure processed or lost input");
        c.setBlockState(POS.east(5),Blocks.AIR);line.remove(5);tick(line,1);
        c.assertTrue(line.get(0).isFormed(),"Endpoint removal did not reconstruct the shortened group");tick(line,121);
        c.assertTrue(line.get(0).isStart() && line.get(4).isEnd() && amount(start(line).tankOutput)==20*MB,"Shortened structure did not resume original rate");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void mergingDormantStartsRecoversOverflowAsExactShards(TestContext c) {
        var a=line(c,3);fill(start(a).tankInput,cold(),1500*MB+1);c.setBlockState(POS.east(2),Blocks.AIR);tick(a.subList(0,2),1);
        List<TileHeatExchange> b=new ArrayList<>();for(int i=3;i<6;i++)b.add(place(c,POS.east(i)));tick(b,1);
        fill(start(b).tankInput,cold(),1000*MB+1);c.setBlockState(POS.east(5),Blocks.AIR);tick(b.subList(0,2),1);
        var connector=place(c,POS.east(2));
        var joined=new ArrayList<TileHeatExchange>();joined.addAll(a.subList(0,2));joined.add(connector);joined.addAll(b.subList(0,2));tick(joined,1);
        c.assertTrue(joined.get(0).isStart() && joined.get(4).isEnd() && amount(start(joined).tankInput)==2000*MB
            && recovered(c,b.get(0).getPos())==500*MB+2,"Merged sections duplicated/lost overflow or ignored tank capacity");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void bothOutputsAutomaticallyEnterAdjacentNativeTanks(TestContext c) {
        var line=line(c,3);c.setBlockState(POS.west(),BCFactoryBlocks.tank);c.setBlockState(POS.east(2).up(),BCFactoryBlocks.tank);
        fill(start(line).tankInput,cold(),10*MB);fill(end(line).tankInput,hot(),10*MB);tick(line,122);
        var cool=(TileTank)c.getBlockEntity(POS.west());var warm=(TileTank)c.getBlockEntity(POS.east(2).up());
        c.assertTrue(amount(cool.tank)==10*MB && amount(warm.tank)==10*MB
            && cool.tank.getStorage().getResource().isOf(BCEnergyFluids.crudeOil[1])
            && warm.tank.getStorage().getResource().isOf(BCEnergyFluids.fuelLight[1])
            && amount(start(line).tankOutput)==0 && amount(end(line).tankOutput)==0,"Native automatic outputs crossed/lost their products");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeBucketInputsConsumeItemsAndOutputsCanBeRecovered(TestContext c) {
        var line=line(c,3);
        var player=new net.minecraft.server.network.ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),
            new com.mojang.authlib.GameProfile(UUID.randomUUID(),"heat-survival"));
        c.getWorld().getServer().getPlayerManager().onPlayerConnect(new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),player);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var at=Vec3d.ofCenter(line.get(0).getPos());player.refreshPositionAndAngles(at.x,at.y,at.z-2,0,0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCEnergyFluids.fuelLight[0].getBucketItem()));
        c.assertTrue(line.get(0).onActivated(player,Hand.MAIN_HAND,Direction.NORTH,.5f,.5f,.5f)
            && player.getMainHandStack().isOf(Items.BUCKET) && amount(start(line).tankInput)==FluidConstants.BUCKET,
            "Native cold bucket did not consume its filled item");
        fill(start(line).tankOutput,FluidVariant.of(BCEnergyFluids.crudeOil[1]),FluidConstants.BUCKET);
        c.assertTrue(line.get(0).onActivated(player,Hand.MAIN_HAND,Direction.NORTH,.5f,.5f,.5f)
            && player.getMainHandStack().isOf(BCEnergyFluids.crudeOil[1].getBucketItem()) && amount(start(line).tankOutput)==0,
            "Manual output bucket did not take priority over recovering input");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void heatRecipesAndNativeSurvivalCraftingAreRegistered(TestContext c) {
        var registry=BuildcraftRecipeRegistry.refineryRecipes;
        c.assertTrue(registry.getHeatableRegistry().getAllRecipes().size()==21 && registry.getCoolableRegistry().getAllRecipes().size()==21,
            "Expected two heat transitions for ten oils/fuels plus water/lava");
        var recipe=(CraftingRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcraftfactory:heat_exchange")).orElseThrow();
        var grid=new buildcraft.lib.tile.craft.WorkbenchCrafting.Grid(3,3);
        for(int i:new int[]{0,2,6,8})grid.setStack(i,new ItemStack(Items.IRON_INGOT));
        for(int i:new int[]{1,7})grid.setStack(i,new ItemStack(BCCoreItems.gearIron));
        for(int i=3;i<6;i++)grid.setStack(i,new ItemStack(Items.GLASS));
        c.assertTrue(recipe.matches(grid,c.getWorld()) && recipe.craft(grid,c.getWorld().getRegistryManager()).isOf(BCFactoryBlocks.heatExchange.asItem()),
            "Native heat exchanger recipe failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedSnapshotCannotReplaceAnyVisibleSectionData(TestContext c) throws java.io.IOException {
        var line=line(c,3);fill(start(line).tankInput,cold(),MB+1);var server=line.get(0);
        var client=BlockEntityRegistry.create(TileHeatExchange.class,server.getPos(),server.getCachedState());
        var data=new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            server.writePayload(TileHeatExchange.NET_RENDER_DATA,data,buildcraft.lib.tile.TileBC_Neptune.NetSide.SERVER);
            client.readPayload(TileHeatExchange.NET_RENDER_DATA,data,buildcraft.lib.tile.TileBC_Neptune.NetSide.CLIENT,null);
            var original=client.getSection();c.assertTrue(client.isStart() && original.tankInput.getFluidForRender().amount==MB+1,"Native snapshot lost exact tank quantity");
            data.clear();fill(start(line).tankInput,cold(),MB);
            server.writePayload(TileHeatExchange.NET_RENDER_DATA,data,buildcraft.lib.tile.TileBC_Neptune.NetSide.SERVER);data.writerIndex(data.writerIndex()-1);
            boolean rejected=false;
            try{client.readPayload(TileHeatExchange.NET_RENDER_DATA,data,buildcraft.lib.tile.TileBC_Neptune.NetSide.CLIENT,null);}
            catch(IndexOutOfBoundsException | java.io.IOException expected){rejected=true;}
            c.assertTrue(rejected && client.getSection()==original && original.tankInput.getFluidForRender().amount==MB+1,"Truncated snapshot partially replaced a section");
        } finally {data.release();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void everyHeatingAndCoolingTransitionUsesItsNativeProductVariant(TestContext c) {
        var line=line(c,3);var manager=BuildcraftRecipeRegistry.refineryRecipes;
        for(var recipe:List.copyOf(manager.getHeatableRegistry().getAllRecipes())) {
            for(Tank tank:List.of(start(line).tankInput,start(line).tankOutput,end(line).tankInput,end(line).tankOutput))clear(tank);
            fill(start(line).tankInput,recipe.in().getFluidVariant(),MB+1);
            fill(end(line).tankInput,FluidVariant.of(net.minecraft.fluid.Fluids.LAVA),MB+1);tick(line,121);
            c.assertTrue(amount(start(line).tankInput)==0 && amount(end(line).tankInput)==0 && amount(start(line).tankOutput)==0,
                "Heating transition left input or produced a lava residue");
            c.assertTrue(recipe.out()==null?amount(end(line).tankOutput)==0
                :amount(end(line).tankOutput)==MB+1 && end(line).tankOutput.getStorage().getResource().equals(recipe.out().getFluidVariant()),
                "Heating transition lost exact droplets or used the wrong native product");
        }
        for(var recipe:List.copyOf(manager.getCoolableRegistry().getAllRecipes())) {
            for(Tank tank:List.of(start(line).tankInput,start(line).tankOutput,end(line).tankInput,end(line).tankOutput))clear(tank);
            fill(start(line).tankInput,FluidVariant.of(net.minecraft.fluid.Fluids.WATER),MB+1);
            fill(end(line).tankInput,recipe.in().getFluidVariant(),MB+1);tick(line,121);
            c.assertTrue(amount(start(line).tankInput)==0 && amount(end(line).tankInput)==0 && amount(end(line).tankOutput)==0,
                "Cooling transition left input or produced a water residue");
            c.assertTrue(recipe.out()==null?amount(start(line).tankOutput)==0
                :amount(start(line).tankOutput)==MB+1 && start(line).tankOutput.getStorage().getResource().equals(recipe.out().getFluidVariant()),
                "Cooling transition lost exact droplets or used the wrong native product");
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void replacingOrRemovingARecipeCannotUseAnObsoleteProduct(TestContext c) {
        var line=line(c,3);fill(start(line).tankInput,cold(),100*MB);fill(end(line).tankInput,hot(),100*MB);tick(line,120);
        var manager=BuildcraftRecipeRegistry.refineryRecipes;var registry=manager.getHeatableRegistry();
        var original=registry.getRecipeForInput(start(line).tankInput.getFluid());
        try {
            registry.addRecipe(manager.createHeatingRecipe(original.in(),new buildcraft.lib.compat.FluidStackBC(BCEnergyFluids.fuelDense[1],10),0,1));
            tick(line,1);
            c.assertTrue(end(line).tankOutput.getStorage().getResource().isOf(BCEnergyFluids.fuelDense[1])
                && amount(end(line).tankOutput)==5*MB,"Running exchanger used a cached obsolete recipe product");
            registry.removeRecipes(recipe->recipe.in().getFluidVariant().equals(cold()));tick(line,10);
            c.assertTrue(!line.get(0).hasWork() && amount(start(line).tankInput)==95*MB && amount(end(line).tankInput)==95*MB,
                "Removed heating recipe kept consuming input");
        } finally {registry.addRecipe(original);}
        c.complete();
    }
    private static List<TileHeatExchange> line(TestContext c,int size) {
        List<TileHeatExchange> line=new ArrayList<>();for(int i=0;i<size;i++)line.add(place(c,POS.east(i)));tick(line,1);
        c.assertTrue(line.get(0).isStart() && line.get(size-1).isEnd(),"Native multiblock did not form");return line;
    }
    private static TileHeatExchange place(TestContext c,BlockPos at) {
        c.setBlockState(at,BCFactoryBlocks.heatExchange.getDefaultState().with(BlockBCBase_Neptune.PROP_FACING,Direction.SOUTH));
        var tile=(TileHeatExchange)c.getBlockEntity(at);tile.onLoad();
        tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCFactoryBlocks.heatExchange));return tile;
    }
    private static ExchangeSectionStart start(List<TileHeatExchange> line){return (ExchangeSectionStart)line.get(0).getSection();}
    private static ExchangeSectionEnd end(List<TileHeatExchange> line){return (ExchangeSectionEnd)line.get(line.size()-1).getSection();}
    private static void tick(List<TileHeatExchange> line,int times){for(int t=0;t<times;t++)for(var tile:line)tile.tick();}
    private static long amount(Tank tank){return tank.getStorage().getAmount();}
    private static void fill(Tank tank,FluidVariant fluid,long amount){
        try(var tx=Transaction.openOuter()){if(tank.getStorage().insert(fluid,amount,tx)!=amount)throw new AssertionError("Fixture fluid rejected");tx.commit();}
    }
    private static void clear(Tank tank){
        try(var tx=Transaction.openOuter()){if(!tank.getStorage().isResourceBlank())tank.getStorage().extract(tank.getStorage().getResource(),Long.MAX_VALUE,tx);tx.commit();}
    }
    private static long recovered(TestContext c,BlockPos at) {
        return c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(at).expand(1),e->true).stream()
            .map(e->buildcraft.core.item.ItemFragileFluidContainer.getFluid(e.getStack())).mapToLong(f->f.amount).sum();
    }
}
