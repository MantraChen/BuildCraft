/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.core.BCCoreConfig;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.*;
import buildcraft.factory.tile.*;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;

public final class PumpGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,6,3), SOURCE=POS.down(3);
    private static final long BUCKET=FluidConstants.BUCKET;
    private static final Map<ServerWorld,Set<BlockPos>> PROTECTED=new WeakHashMap<>();
    static {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->
            PROTECTED.getOrDefault(world,Set.of()).contains(hit.getBlockPos())?ActionResult.FAIL:ActionResult.PASS);
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void finiteSourceCostsTenMjAndLeavesNoTubeOrDuplicateFluid(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.WATER);power(tile,4*MjAPI.MJ);tile.tick();
        c.assertTrue(tile.getPaidWork()==4*MjAPI.MJ && tile.getFluidAmount()==0 && tile.getWantedLength()==3,"Unpowered pump/shaft or partial payment is wrong");
        power(tile,6*MjAPI.MJ);tile.tick();
        c.assertTrue(tile.getFluidAmount()==BUCKET && tile.getFluidVariant().isOf(Fluids.WATER)
            && c.getBlockState(SOURCE).isAir() && tile.getStoredPower()==0 && tile.isComplete(),"Finite source was not collected exactly once for 10 MJ");
        for(int i=1;i<3;i++)c.assertTrue(c.getBlockState(POS.down(i)).isAir(),"Completed pump left a shaft");
        for(int i=0;i<40;i++)tile.tick();
        c.assertTrue(tile.getFluidAmount()==BUCKET,"Empty source generated a second bucket");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void renewableWaterChecksRealNeighborsSupportConfigAndGameRule(TestContext c) {
        var tile=pump(c);pool(c);boolean old=BCCoreConfig.pumpsConsumeWater;
        boolean oldRule=c.getWorld().getGameRules().getBoolean(GameRules.WATER_SOURCE_CONVERSION);
        try {
            BCCoreConfig.pumpsConsumeWater=false;c.getWorld().getGameRules().get(GameRules.WATER_SOURCE_CONVERSION).set(true,c.getWorld().getServer());
            power(tile,30*MjAPI.MJ);for(int i=0;i<3;i++)tile.tick();
            c.assertTrue(tile.getFluidAmount()==3*BUCKET && countSources(c)==9,"Renewable pool was consumed or gave free fluid");
            BCCoreConfig.pumpsConsumeWater=true;power(tile,10*MjAPI.MJ);tile.tick();
            c.assertTrue(countSources(c)==8 && tile.getFluidAmount()==4*BUCKET,"Consume-water setting was ignored");
            BCCoreConfig.pumpsConsumeWater=false;c.getWorld().getGameRules().get(GameRules.WATER_SOURCE_CONVERSION).set(false,c.getWorld().getServer());
            power(tile,10*MjAPI.MJ);tile.tick();
            c.assertTrue(countSources(c)==7 && tile.getFluidAmount()==5*BUCKET,"Native source-conversion rule was ignored");
        } finally {BCCoreConfig.pumpsConsumeWater=old;c.getWorld().getGameRules().get(GameRules.WATER_SOURCE_CONVERSION).set(oldRule,c.getWorld().getServer());}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void flowingNeighborsAndUnsupportedWaterCannotCreateInfiniteBuckets(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.WATER);
        c.setBlockState(SOURCE.east(),Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL,1));
        c.setBlockState(SOURCE.west(),Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL,1));
        c.setBlockState(SOURCE.down(),Blocks.STONE);power(tile,10*MjAPI.MJ);tile.tick();
        c.assertTrue(c.getBlockState(SOURCE).isAir() && tile.getFluidAmount()==BUCKET,"Flowing neighbors counted as infinite sources");
        c.setBlockState(SOURCE,Blocks.WATER);c.setBlockState(SOURCE.east(),Blocks.WATER);c.setBlockState(SOURCE.west(),Blocks.WATER);
        c.setBlockState(SOURCE.down(),Blocks.AIR);power(tile,30*MjAPI.MJ);for(int i=0;i<40;i++)tile.tick();
        c.assertTrue(tile.getFluidAmount()==4*BUCKET,"Unsupported source line became an infinite source");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void paidWorkAndTankSurviveReloadAndOldHandlesBecomeInert(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.LAVA);power(tile,4*MjAPI.MJ);tile.tick();
        var staleMj=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP);
        var staleFluid=lookup(tile);var copy=BlockEntityRegistry.create(TilePump.class,tile.getPos(),tile.getCachedState());
        copy.readNbt(tile.createNbt());c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();
        c.assertTrue(staleMj.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Removed pump accepted cached energy");
        power(copy,6*MjAPI.MJ);copy.tick();
        c.assertTrue(copy.getFluidAmount()==BUCKET && copy.getPaidWork()==0,"Reload lost or duplicated paid fluid");
        try(var tx=Transaction.openOuter()) {c.assertTrue(staleFluid.extract(FluidVariant.of(Fluids.LAVA),BUCKET,tx)==0,"Old fluid handle remained live");}
        var saved=copy.createNbt();copy.readNbt(saved);for(int i=0;i<35;i++)copy.tick();
        c.assertTrue(copy.getFluidAmount()==BUCKET,"Tank NBT reload duplicated fluid");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void redstonePausesCollectionAndForeignSourceCannotReusePaidWork(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.WATER);power(tile,4*MjAPI.MJ);tile.tick();
        c.setBlockState(POS.east(),Blocks.REDSTONE_BLOCK);power(tile,6*MjAPI.MJ);tile.tick();
        c.assertTrue(tile.getPaidWork()==4*MjAPI.MJ && tile.getStoredPower()==6*MjAPI.MJ && tile.getFluidAmount()==0,"Redstone spent MJ or drained water");
        c.setBlockState(SOURCE,Blocks.LAVA);c.setBlockState(POS.east(),Blocks.AIR);for(int i=0;i<35;i++)tile.tick();
        c.assertTrue(tile.getPaidWork()==6*MjAPI.MJ && tile.getFluidAmount()==0,"Different fluid reused old source payment");
        power(tile,4*MjAPI.MJ);tile.tick();c.assertTrue(tile.getFluidVariant().isOf(Fluids.LAVA) && tile.getFluidAmount()==BUCKET,"Retargeted pump did not resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protectedDrainsAndShaftPlacementNeverEraseWorldOrLosePaidWork(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.WATER);power(tile,10*MjAPI.MJ);
        var blocked=PROTECTED.computeIfAbsent(c.getWorld(),w->new HashSet<>());
        blocked.add(c.getAbsolutePos(POS.down()));
        try {tile.tick();c.assertTrue(tile.getStoredPower()==10*MjAPI.MJ && tile.getFluidAmount()==0,"Pump bypassed protected shaft");}
        finally {blocked.remove(c.getAbsolutePos(POS.down()));}
        blocked.add(c.getAbsolutePos(SOURCE));
        try {for(int i=0;i<25;i++)tile.tick();c.assertTrue(tile.getPaidWork()==10*MjAPI.MJ && tile.getFluidAmount()==0
            && c.getBlockState(SOURCE).isOf(Blocks.WATER),"Drain bypassed native protection or discarded payment");}
        finally {blocked.remove(c.getAbsolutePos(SOURCE));}
        for(int i=0;i<25;i++)tile.tick();c.assertTrue(tile.getFluidAmount()==BUCKET,"Pump did not resume after protection removal");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void detachedSourcesAndNewSolidShaftObstructionsCannotBePumped(TestContext c) {
        var tile=pump(c);
        c.setBlockState(SOURCE,Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL,1));
        c.setBlockState(SOURCE.east(),Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL,1));
        c.setBlockState(SOURCE.east(2),Blocks.WATER);tile.tick();c.setBlockState(SOURCE.east(),Blocks.STONE);
        power(tile,10*MjAPI.MJ);for(int i=0;i<35;i++)tile.tick();
        c.assertTrue(tile.getFluidAmount()==0 && c.getBlockState(SOURCE.east(2)).isOf(Blocks.WATER),"Pump drew fluid across a broken connection");
        c.setBlockState(SOURCE,Blocks.WATER);for(int i=0;i<35;i++)tile.tick();
        c.assertTrue(tile.getFluidAmount()==BUCKET,"Pump did not rediscover a reachable source");
        c.setBlockState(SOURCE,Blocks.WATER);c.setBlockState(POS.down(),Blocks.STONE);power(tile,10*MjAPI.MJ);
        for(int i=0;i<35;i++)tile.tick();
        c.assertTrue(tile.getFluidAmount()==BUCKET && c.getBlockState(POS.down()).isOf(Blocks.STONE),"Shaft obstruction was erased or bypassed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void waterloggedHostsAreDrainedWithoutBeingDestroyed(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.OAK_FENCE.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED,true));
        power(tile,10*MjAPI.MJ);tile.tick();
        c.assertTrue(c.getBlockState(SOURCE).isOf(Blocks.OAK_FENCE) && !c.getBlockState(SOURCE).get(net.minecraft.state.property.Properties.WATERLOGGED)
            && tile.getFluidAmount()==BUCKET,"Pump destroyed a waterlogged host or failed to collect its water");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oilAndGasUseNativeBucketsAndOppositeVerticalSearch(TestContext c) {
        var tile=pump(c);var oil=BCEnergyFluids.crudeOil[0];c.setBlockState(SOURCE,oil.getBlock());c.setBlockState(SOURCE.east().up(),oil.getBlock());
        c.setBlockState(SOURCE.east(),oil.getBlock());power(tile,30*MjAPI.MJ);for(int i=0;i<3;i++)tile.tick();
        c.assertTrue(tile.getFluidAmount()==3*BUCKET && tile.getFluidVariant().isOf(oil),"Native oil search/drain failed");
        extract(tile,3*BUCKET,true);
        var gas=BCEnergyFluids.fuelGaseous[0];c.setBlockState(SOURCE,gas.getBlock());c.setBlockState(SOURCE.east(),gas.getBlock());c.setBlockState(SOURCE.east().down(),gas.getBlock());
        power(tile,30*MjAPI.MJ);for(int i=0;i<36;i++)tile.tick();
        c.assertTrue(tile.getFluidAmount()==3*BUCKET && tile.getFluidVariant().isOf(gas),"Gaseous fluid did not search downward");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullTankAndIncompatibleFluidPreserveSourceAndExactFractionalRemainder(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.WATER);
        var nbt=tile.createNbt();var tank=new NbtCompound();tank.putInt("storageVersion",1);tank.put("variant",FluidVariant.of(Fluids.WATER).toNbt());tank.putLong("amount",16*BUCKET-1);
        var tanks=new NbtCompound();tanks.put("tank",tank);nbt.put("tanks",tanks);tile.readNbt(nbt);
        power(tile,10*MjAPI.MJ);tile.tick();
        c.assertTrue(tile.getFluidAmount()==16*BUCKET-1 && tile.getStoredPower()==10*MjAPI.MJ && c.getBlockState(SOURCE).isOf(Blocks.WATER),"Almost full tank swallowed fluid or energy");
        c.assertTrue(extract(tile,BUCKET-1,false)==BUCKET-1 && tile.getFluidAmount()==16*BUCKET-1,"Aborted extraction mutated storage");
        extract(tile,BUCKET-1,true);tile.tick();c.assertTrue(tile.getFluidAmount()==16*BUCKET,"Capacity reservation lost a droplet");
        c.setBlockState(SOURCE,Blocks.LAVA);extract(tile,BUCKET,true);power(tile,10*MjAPI.MJ);for(int i=0;i<35;i++)tile.tick();
        c.assertTrue(c.getBlockState(SOURCE).isOf(Blocks.LAVA) && tile.getFluidAmount()==15*BUCKET,"Incompatible fluid was consumed");
        try(var tx=Transaction.openOuter()) {c.assertTrue(lookup(tile).insert(FluidVariant.of(Fluids.WATER),1,tx)==0,"Pump accepted external fluid insertion");}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=200)
    public void nativeFluidPipeDeliversExactBucketWithoutExtractionEngine(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.WATER);c.setBlockState(POS.west(),BCTransportBlocks.pipeHolder);
        ((TilePipeHolder)c.getBlockEntity(POS.west())).onPlacedBy(null,new ItemStack(BCTransportItems.pipeFluidStone));
        c.setBlockState(POS.west(2),BCFactoryBlocks.tank);((TilePipeHolder)c.getBlockEntity(POS.west())).tick();
        power(tile,10*MjAPI.MJ);tile.tick();
        c.waitAndRun(130,()->{
            var bank=(TileTank)c.getBlockEntity(POS.west(2));
            c.assertTrue(bank.tank.getStorage().getAmount()==BUCKET && tile.getFluidAmount()==0,"Pump-to-native-pipe output lost fluid");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void liveDepthRadiusAndMalformedPacketsCannotBypassLimits(TestContext c) throws Exception {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL,1));
        c.setBlockState(SOURCE.east(),Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL,1));c.setBlockState(SOURCE.east(2),Blocks.WATER);
        int oldDepth=BCCoreConfig.miningMaxDepth,oldRadius=BCCoreConfig.pumpMaxDistance;
        try {
            BCCoreConfig.miningMaxDepth=2;power(tile,10*MjAPI.MJ);tile.tick();
            c.assertTrue(tile.isComplete() && tile.getStoredPower()==10*MjAPI.MJ,"Pump ignored configured shaft depth");
            BCCoreConfig.miningMaxDepth=3;BCCoreConfig.pumpMaxDistance=1;for(int i=0;i<35;i++)tile.tick();
            c.assertTrue(tile.isComplete() && tile.getFluidAmount()==0,"Pump exceeded radius");
            BCCoreConfig.pumpMaxDistance=2;for(int i=0;i<35;i++)tile.tick();
            c.assertTrue(tile.getFluidAmount()==BUCKET,"Pump did not respond to live radius change");
        } finally {BCCoreConfig.miningMaxDepth=oldDepth;BCCoreConfig.pumpMaxDistance=oldRadius;}
        var saved=tile.createNbt();
        for(int id:new int[]{TileMiner.NET_RENDER_DATA,TileMiner.NET_LED_STATUS}) {
            var packet=PacketBufferBC.write(b->tile.writePayload(id,b,TileBC_Neptune.NetSide.SERVER));
            try {
                packet.writerIndex(packet.writerIndex()-1);boolean rejected=false;
                try {tile.readPayload(id,packet,TileBC_Neptune.NetSide.CLIENT,null);}catch(IndexOutOfBoundsException | java.io.IOException expected){rejected=true;}
                c.assertTrue(rejected && saved.equals(tile.createNbt()),"Truncated pump state partially applied");
            } finally {packet.release();}
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void survivalRecipeAndAdvancementDefinitionsLoad(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var handler=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        var gear=net.minecraft.registry.Registries.ITEM.get(new Identifier("buildcraftcore:gear_iron"));
        Item[] ingredients={Items.IRON_INGOT,Items.REDSTONE,Items.IRON_INGOT,Items.IRON_INGOT,gear,Items.IRON_INGOT,BCFactoryBlocks.tank.asItem(),Items.BUCKET,BCFactoryBlocks.tank.asItem()};
        for(int i=0;i<9;i++)handler.getSlot(i+1).setStack(new ItemStack(ingredients[i]));handler.onContentChanged(handler.getSlot(1).inventory);
        c.assertTrue(handler.getSlot(0).getStack().isOf(BCFactoryBlocks.pump.asItem()),"Native pump recipe missing");
        handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        for(int i=1;i<=9;i++)c.assertTrue(handler.getSlot(i).getStack().isEmpty(),"Pump recipe left an ingredient behind");
        for(String name:new String[]{"draining_the_world","oil_platform","black_gold"})
            c.assertTrue(c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory",name))!=null,"Missing pump advancement "+name);
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void largeConnectedReservoirSearchSpansTicksWithoutSpendingPowerEarly(TestContext c) {
        var tile=pump(c);var oil=BCEnergyFluids.crudeOil[0];
        try {
            for(int x=-1;x<=6;x++)for(int z=-1;z<=6;z++)for(int y=0;y<35;y++)
                if(x!=0 || z!=0 || y==0)c.setBlockState(SOURCE.add(x,y,z),oil.getBlock());
            power(tile,MjAPI.MJ);tile.tick();
            c.assertTrue(tile.getCurrentPos()==null && !tile.isComplete() && tile.getStoredPower()==MjAPI.MJ,
                "Large search exceeded its per-tick budget or spent energy before selecting a source");
            tile.tick();
            c.assertTrue(tile.getCurrentPos()!=null && tile.getPaidWork()==MjAPI.MJ && tile.getFluidAmount()==0,
                "Incremental reservoir search did not finish and select connected work");
        } finally {
            c.setBlockState(POS,Blocks.AIR);
            for(int x=-1;x<=6;x++)for(int z=-1;z<=6;z++)for(int y=0;y<35;y++)
                if(x!=0 || z!=0 || y==0)c.setBlockState(SOURCE.add(x,y,z),Blocks.AIR);
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void manualBucketAndRemovalRecoverOnlyTheRemainingFluid(TestContext c) {
        var tile=pump(c);c.setBlockState(SOURCE,Blocks.LAVA);c.setBlockState(SOURCE.east(),Blocks.LAVA);
        power(tile,20*MjAPI.MJ);tile.tick();tile.tick();
        var player=c.createMockCreativeServerPlayerInWorld();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var center=Vec3d.ofCenter(tile.getPos());player.refreshPositionAndAngles(center.x,center.y,center.z-2,0,0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.BUCKET));
        var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(tile.getPos()),Direction.UP,tile.getPos(),false);
        c.assertTrue(player.interactionManager.interactBlock(player,c.getWorld(),player.getMainHandStack(),Hand.MAIN_HAND,hit).isAccepted()
            && player.getMainHandStack().isOf(Items.LAVA_BUCKET) && tile.getFluidAmount()==BUCKET,"Native bucket interaction lost or duplicated fluid");
        var stale=lookup(tile);var view=stale.iterator().next();
        c.setBlockState(POS,Blocks.AIR);tile.onRemove();
        long recovered=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->true).stream()
            .map(e->buildcraft.core.item.ItemFragileFluidContainer.getFluid(e.getStack()))
            .filter(f->!f.isEmpty() && f.getFluidVariant().isOf(Fluids.LAVA)).mapToLong(f->f.amount).sum();
        c.assertTrue(recovered==BUCKET && view.getAmount()==0,"Removal omitted output-only pump fluid or duplicated bucket contents");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeOilSpringRecordsOwnerAndAwardsBlackGoldAtWorldBottom(TestContext c) {
        var tile=pump(c);BlockPos springPos=new BlockPos(tile.getPos().getX(),c.getWorld().getBottomY(),tile.getPos().getZ());
        for(int y=tile.getPos().getY()-1;y>springPos.getY();y--)c.getWorld().setBlockState(new BlockPos(tile.getPos().getX(),y,tile.getPos().getZ()),Blocks.AIR.getDefaultState());
        // The bottom marker is below the GameTest structure's reset bounds. Replace any previous run's tile.
        c.getWorld().setBlockState(springPos,Blocks.AIR.getDefaultState());
        c.getWorld().setBlockState(springPos,buildcraft.core.BCCoreBlocks.spring.getDefaultState()
            .with(buildcraft.core.block.BlockSpring.SPRING_TYPE,buildcraft.api.enums.EnumSpring.OIL));
        var spring=(buildcraft.energy.tile.TileSpringOil)c.getWorld().getBlockEntity(springPos);spring.totalSources=1;
        c.getWorld().setBlockState(springPos.up(),BCEnergyFluids.crudeOil[0].getBlock().getDefaultState());power(tile,10*MjAPI.MJ);tile.tick();
        var progress=spring.createNbt().getList("pumpProgress",NbtElement.COMPOUND_TYPE);
        c.assertTrue(progress.size()==1 && progress.getCompound(0).getInt("sourcesPumped")==1,"Oil spring did not record pump owner: "+progress);
        var player=c.getWorld().getServer().getPlayerManager().getPlayer(tile.getOwner().getId());
        var advancement=c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftfactory:black_gold"));
        c.assertTrue(player!=null && player.getAdvancementTracker().getProgress(advancement).isDone(),"World-bottom oil spring did not award Black Gold");c.complete();
    }

    private static TilePump pump(TestContext c) {
        for(int i=1;i<=6;i++)if(c.getWorld().isInBuildLimit(c.getAbsolutePos(POS.down(i))))c.setBlockState(POS.down(i),Blocks.AIR);
        c.setBlockState(POS,BCFactoryBlocks.pump);var tile=(TilePump)c.getBlockEntity(POS);tile.onLoad();
        tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCFactoryBlocks.pump));return tile;
    }
    private static void power(TilePump tile,long amount) {
        var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);
        if(receiver==null || receiver.receivePower(amount,false)!=0)throw new AssertionError("Pump rejected fixture MJ");
    }
    private static Storage<FluidVariant> lookup(TilePump tile) {return FluidStorage.SIDED.find(tile.getWorld(),tile.getPos(),Direction.UP);}
    private static long extract(TilePump tile,long amount,boolean commit) {
        try(var tx=Transaction.openOuter()) {long moved=lookup(tile).extract(tile.getFluidVariant(),amount,tx);if(commit)tx.commit();return moved;}
    }
    private static void pool(TestContext c) {
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {c.setBlockState(SOURCE.add(x,-1,z),Blocks.STONE);c.setBlockState(SOURCE.add(x,0,z),Blocks.WATER);}
    }
    private static int countSources(TestContext c) {
        int result=0;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(c.getBlockState(SOURCE.add(x,0,z)).getFluidState().isStill())result++;return result;
    }
}
