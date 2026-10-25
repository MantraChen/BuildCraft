/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.*;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.transport.pipe.*;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.transport.*;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Actual packaged fluid pipes, native providers and real world ticks. */
public final class FluidPipeGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(2,3,3);
    private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER), LAVA = FluidVariant.of(Fluids.LAVA);
    private record Endpoint(Direction side, SingleFluidStorage storage) {}
    private static final Map<World,Map<BlockPos,Endpoint>> ENDPOINTS = new WeakHashMap<>();
    static {
        FluidStorage.SIDED.registerForBlocks((world,pos,state,tile,side) -> {
            var endpoint = ENDPOINTS.getOrDefault(world,Map.of()).get(pos);
            return endpoint != null && endpoint.side() == side ? endpoint.storage() : null;
        }, Blocks.DIAMOND_BLOCK);
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeFluidTransactionsShareIdentityBudgetsAndRollbackAcrossFaces(TestContext c) {
        var tile = pipe(c,POS,BCTransportItems.pipeFluidStone);
        endpoint(c,POS.north(),Direction.SOUTH,FluidConstants.BUCKET);
        endpoint(c,POS.south(),Direction.NORTH,FluidConstants.BUCKET);
        tile.tick(); var flow = flow(tile);
        var north = FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.NORTH);
        var south = FluidStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.SOUTH);
        c.assertTrue(north != null && south != null && tile.getPipe().isConnected(Direction.NORTH),"Native provider without block entity did not connect");
        var before = flow.writeToNbt();
        try (var outer = Transaction.openOuter()) {
            c.assertTrue(north.insert(WATER,101,outer)==101 && south.insert(LAVA,17,outer)==0,"Transaction mixed fluid identities");
            try (var nested = outer.openNested()) { c.assertTrue(south.insert(WATER,53,nested)==53,"Second face did not share pending identity"); nested.commit(); }
        }
        c.assertTrue(flow.writeToNbt().equals(before),"Aborted outer transaction retained nested amounts/delay/direction");
        insert(north,WATER,17);
        int rate = PipeApi.getFluidTransferInfo(tile.getPipe().definition).transferPerTick*81;
        try (var tx = Transaction.openOuter()) {
            c.assertTrue(north.insert(WATER,Long.MAX_VALUE,tx)==rate-17 && north.insert(WATER,1,tx)==0,"Per-tick budget was bypassed");
            c.assertTrue(north.extract(WATER,1,tx)==0 && !north.supportsExtraction(),"Automation stole travelling fluid");
        }
        c.assertTrue(flow.getAmount()==17 && flow.getFluid().equals(WATER),"Droplet remainder was rounded or rollback failed");
        var nbt = new NbtCompound(); nbt.putString("batch","different");
        try (var tx = Transaction.openOuter()) { c.assertTrue(south.insert(FluidVariant.of(Fluids.WATER,nbt),81,tx)==0,"Variants with different NBT mixed"); }
        reload(c,tile);
        try (var tx = Transaction.openOuter()) { c.assertTrue(north.insert(WATER,81,tx)==0,"Stale pipe storage remained writable"); }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=300)
    public void woodenFluidExtractionConservesExactNbtAndMjAcrossReload(TestContext c) {
        var source = tank(c,POS.west()); var destination = tank(c,POS.east(2));
        var wood = pipe(c,POS,BCTransportItems.pipeFluidWood); var stone = pipe(c,POS.east(),BCTransportItems.pipeFluidStone);
        var tag = new NbtCompound(); tag.putString("batch","Remake by FelixMantra");
        var fluid = FluidVariant.of(Fluids.WATER,tag); long total = FluidConstants.BUCKET+37;
        insert(source,fluid,total); wood.tick(); stone.tick();
        var receiver = (IMjReceiver) MjAPI.CONNECTOR.find(c.getWorld(),wood.getPos(),Direction.UP);
        c.assertTrue(receiver != null,"Wooden fluid pipe did not expose native MJ");
        long power = 2*MjAPI.MJ, cost = (total*1000+80)/81;
        var before = flow(wood).writeToNbt();
        c.assertTrue(receiver.receivePower(power,true)==power-cost && amount(source)==total && flow(wood).writeToNbt().equals(before),"MJ simulation changed fluid or charged the wrong amount");
        c.assertTrue(receiver.receivePower(power,false)==power-cost && amount(source)==0 && flow(wood).getAmount()==total,"Wood extraction lost droplets or MJ");
        TilePipeHolder[] pipes = {wood,stone};
        c.runAtTick(45,() -> {
            for(int i=0;i<pipes.length;i++) pipes[i]=reload(c,pipes[i]);
            c.assertTrue(amount(destination)+flow(pipes[0]).getAmount()+flow(pipes[1]).getAmount()==total,"Native reconstruction lost travelling fluid");
        });
        c.runAtTick(230,() -> {
            c.assertTrue(amount(destination)==total && flow(pipes[0]).getAmount()==0 && flow(pipes[1]).getAmount()==0,"Tank-to-wood-to-stone delivery lost or stranded fluid");
            c.assertTrue(destination.nonEmptyViews().iterator().next().getResource().equals(fluid),"Transport lost fluid NBT"); c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=280)
    public void backpressurePartialDropletsAndReplacedSinkDoNotLoseFluid(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeFluidStone);
        var small=endpoint(c,POS.east(),Direction.WEST,13); tile.tick();
        c.assertTrue(flow(tile).insertFluidsForce(WATER,997,null,false)==997,"Force input rounded droplets");
        c.runAtTick(70,() -> {
            c.assertTrue(small.amount==13 && flow(tile).getAmount()==984,"Partly full receiver lost fluid");
            var large=endpoint(c,POS.east(),Direction.WEST,10000); insert(large,WATER,small.amount);
        });
        c.runAtTick(230,() -> {
            var sink=FluidStorage.SIDED.find(c.getWorld(),tile.getPos().east(),Direction.WEST);
            c.assertTrue(amount(sink)==997 && flow(tile).getAmount()==0,"Replacement sink did not receive remaining fluid"); c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=160)
    public void ironOutputRejectsInjectionAndRoutesOnlySelectedFace(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeFluidIron);
        var east=endpoint(c,POS.east(),Direction.WEST,10000);
        var west=endpoint(c,POS.west(),Direction.EAST,10000); tile.tick();
        tile.fireEvent(new PipeEventActionActivate(tile,BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.EAST.ordinal()],new IStatementParameter[0],EnumPipePart.CENTER));
        try(var tx=Transaction.openOuter()) { c.assertTrue(flow(tile).getStorage(Direction.EAST).insert(WATER,81,tx)==0,"Iron output accepted incoming fluid"); }
        insert(flow(tile).getStorage(Direction.WEST),WATER,503);
        c.runAtTick(120,() -> { c.assertTrue(east.amount==503 && west.amount==0 && flow(tile).getAmount()==0,"Iron pipe ignored selected direction"); c.complete(); });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=220)
    public void clayPrefersAvailableTankAndFallsBackToPipeWhenFull(TestContext c) {
        var clay=pipe(c,POS,BCTransportItems.pipeFluidClay); var stone=pipe(c,POS.east(),BCTransportItems.pipeFluidStone);
        var preferred=endpoint(c,POS.north(),Direction.SOUTH,997); var fallback=endpoint(c,POS.east(2),Direction.WEST,10000);
        clay.tick(); stone.tick(); flow(clay).insertFluidsForce(WATER,997,null,false);
        c.runAtTick(70,() -> { c.assertTrue(preferred.amount==997 && fallback.amount==0,"Clay ignored preferred tank"); flow(clay).insertFluidsForce(WATER,503,null,false); });
        c.runAtTick(190,() -> { c.assertTrue(preferred.amount==997 && fallback.amount==503,"Clay failed to route around a full tank"); c.complete(); });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void voidPipeConsumesOnlyCommittedInputsAfterTravel(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeFluidVoid); endpoint(c,POS.west(),Direction.EAST,10000); tile.tick();
        var input=flow(tile).getStorage(Direction.WEST);
        try(var tx=Transaction.openOuter()) { c.assertTrue(input.insert(WATER,101,tx)==101,"Void simulation rejected fluid"); }
        c.assertTrue(flow(tile).getAmount()==0,"Aborted insertion fed void pipe"); insert(input,WATER,101);
        c.assertTrue(flow(tile).getAmount()==101,"Void skipped section travel delay");
        c.runAtTick(40,() -> { c.assertTrue(flow(tile).getAmount()==0,"Void did not consume fluid at centre"); c.complete(); });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void forcedExtractionAndLegacyNbtPreserveAmountAndDelayInvariants(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeFluidStone); tile.tick(); var flow=flow(tile);
        c.assertTrue(flow.insertFluidsForce(WATER,1001,null,true)==1001 && flow.getAmount()==0,"Force insertion simulation mutated fluid");
        flow.insertFluidsForce(WATER,1001,null,false);
        c.assertTrue(flow.extractFluidsForce(1002,Long.MAX_VALUE,null,false)==null,"Minimum extraction ignored");
        var before=flow.writeToNbt(); var sample=flow.extractFluidsForce(1,500,null,true);
        c.assertTrue(sample.amount()==500 && sample.resource().equals(WATER) && flow.writeToNbt().equals(before),"Force extraction lost amount or mutated during simulation");
        c.assertTrue(flow.extractFluidsForce(500,500,null,false).amount()==500 && flow.getAmount()==501,"Force extraction double-drained fluid");
        var copy=reload(c,tile); for(int i=0;i<30;i++) copy.tick();
        c.assertTrue(flow(copy).getAmount()==501,"Forced extraction left invalid travelling credits after reload");
        var legacy=new NbtCompound(); legacy.put("fluid",WATER.toNbt()); var section=new NbtCompound(); section.putShort("capacity",(short)777); section.putShort("in[0]",(short)13); legacy.put("tank[6]",section);
        var restored=new PipeFlowFluids(copy.getPipe(),legacy); c.assertTrue(restored.getAmount()==777*81L,"Legacy millibuckets were not converted once");
        var nativeCopy=new PipeFlowFluids(copy.getPipe(),restored.writeToNbt()); c.assertTrue(nativeCopy.getAmount()==restored.getAmount(),"Native reload multiplied droplets again");
        section.putLong("capacity",Long.MAX_VALUE); section.putInt("in[0]",Integer.MAX_VALUE); section.putInt("ticksInDirection",Integer.MIN_VALUE);
        var malformed=new PipeFlowFluids(copy.getPipe(),legacy); c.assertTrue(malformed.getAmount()==malformed.capacity,"Malformed saved amount not bounded");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fluidSnapshotsCarryExactAmountsAndRejectInvalidSections(TestContext c) throws java.io.IOException {
        var tile=pipe(c,POS,BCTransportItems.pipeFluidStone); tile.tick(); var flow=flow(tile); flow.insertFluidsForce(WATER,1001,null,false);
        var packet=new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            flow.writePayload(PipeFlow.NET_ID_FULL_STATE,packet,EnvType.SERVER);
            c.assertTrue(packet.readBoolean(),"Fluid identity omitted"); c.assertTrue(packet.readVarInt()>=0,"Invalid fluid cache ID");
            long sum=0; for(int i=0;i<7;i++) { sum+=packet.readVarInt(); c.assertTrue(packet.readUnsignedByte()<=2,"Invalid direction"); }
            c.assertTrue(sum==1001 && !packet.isReadable(),"Snapshot lost exact droplets or message boundary");
            packet.clear(); packet.writeBoolean(false); packet.writeVarInt(1); packet.writeByte(1);
            boolean rejected=false; try { flow.readPayload(PipeFlow.NET_ID_FULL_STATE,packet,EnvType.CLIENT); } catch(java.io.IOException expected){rejected=true;}
            c.assertTrue(rejected && flow.getAmount()==1001,"Empty identity accepted nonempty section");
            packet.clear(); packet.writeBoolean(false); flow.readPayload(PipeFlow.NET_ID_FULL_STATE,packet,EnvType.SERVER);
            c.assertTrue(packet.readerIndex()==0 && flow.getAmount()==1001,"Server accepted client fluid snapshot");
        } finally { packet.release(); }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=230)
    public void diamondFluidFiltersPreferMatchingBucketsAndFallbackAroundFullTanks(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeFluidDiamond);
        var preferred=endpoint(c,POS.east(),Direction.WEST,997);
        var forbidden=endpoint(c,POS.north(),Direction.SOUTH,10000);
        var fallback=endpoint(c,POS.south(),Direction.NORTH,10000);
        var behaviour=(buildcraft.transport.pipe.behaviour.PipeBehaviourDiamondFluid)tile.getPipe().behaviour;
        behaviour.filters.setStackInSlot(Direction.EAST.ordinal()*9,new ItemStack(Items.WATER_BUCKET));
        behaviour.filters.setStackInSlot(Direction.NORTH.ordinal()*9,new ItemStack(Items.LAVA_BUCKET));
        behaviour.filters.setStackInSlot(Direction.SOUTH.ordinal()*9,new ItemStack(Items.STONE));
        tile.tick(); flow(tile).insertFluidsForce(WATER,997,null,false);
        c.runAtTick(70,() -> {
            c.assertTrue(preferred.amount==997 && forbidden.amount==0 && fallback.amount==0,"Diamond ignored fluid sample priority");
            flow(tile).insertFluidsForce(WATER,503,null,false);
        });
        c.runAtTick(190,() -> {
            c.assertTrue(preferred.amount==997 && forbidden.amount==0 && fallback.amount==503,"Full filtered tank blocked fallback or sent water to lava filter");
            c.assertTrue(behaviour.filters.getStackInSlot(Direction.EAST.ordinal()*9).isOf(Items.WATER_BUCKET),"Filter sample was consumed");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void diamondWoodFluidWhitelistBlacklistAndSimulationRespectSamples(TestContext c) {
        var source=tank(c,POS.west()); var tile=pipe(c,POS,BCTransportItems.pipeFluidDiaWood); tile.tick();
        var behaviour=(buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond)tile.getPipe().behaviour;
        var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP);
        behaviour.filters.setStackInSlot(0,new ItemStack(Items.LAVA_BUCKET)); insert(source,WATER,17);
        c.assertTrue(receiver.receivePower(MjAPI.MJ,false)==MjAPI.MJ && amount(source)==17,"Fluid whitelist extracted nonmatching water");
        behaviour.filters.setStackInSlot(0,new ItemStack(Items.WATER_BUCKET));
        var before=tile.createNbt(); long cost=(17*1000+80)/81;
        c.assertTrue(receiver.receivePower(MjAPI.MJ,true)==MjAPI.MJ-cost && before.equals(tile.createNbt()) && amount(source)==17,"Filtered simulation changed pipe or sample");
        behaviour.setFilterMode(buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode.BLACK_LIST);
        c.assertTrue(receiver.receivePower(MjAPI.MJ,false)==MjAPI.MJ && amount(source)==17,"Blacklist extracted matching fluid");
        behaviour.filters.setStackInSlot(0,new ItemStack(Items.LAVA_BUCKET));
        c.assertTrue(receiver.receivePower(MjAPI.MJ,false)==MjAPI.MJ-cost && amount(source)==0 && flow(tile).getAmount()==17,"Blacklist failed to extract permitted exact amount");
        c.assertTrue(behaviour.filters.getStackInSlot(0).isOf(Items.LAVA_BUCKET),"Extraction consumed the ghost bucket");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fluidMaterialsRetainTheirDistinctRatesAndGoldDelay(TestContext c) {
        int base=BCTransportConfig.baseFlowRate;
        c.assertTrue(PipeApi.getFluidTransferInfo(BCTransportPipes.woodFluid).transferPerTick==base
            && PipeApi.getFluidTransferInfo(BCTransportPipes.stoneFluid).transferPerTick==2*base
            && PipeApi.getFluidTransferInfo(BCTransportPipes.ironFluid).transferPerTick==4*base
            && PipeApi.getFluidTransferInfo(BCTransportPipes.diamondFluid).transferPerTick==8*base
            && PipeApi.getFluidTransferInfo(BCTransportPipes.goldFluid).transferDelayMultiplier==2,"Native materials lost original rates/delay");
        c.complete();
    }

    private static TilePipeHolder pipe(TestContext c,BlockPos pos,Item item) { c.setBlockState(pos,BCTransportBlocks.pipeHolder); var tile=(TilePipeHolder)c.getBlockEntity(pos); tile.onPlacedBy(null,new ItemStack(item)); return tile; }
    private static PipeFlowFluids flow(TilePipeHolder tile) { return (PipeFlowFluids)tile.getPipe().flow; }
    private static SingleFluidStorage endpoint(TestContext c,BlockPos pos,Direction side,long capacity) {
        c.setBlockState(pos,Blocks.DIAMOND_BLOCK); var storage=SingleFluidStorage.withFixedCapacity(capacity,()->{});
        ENDPOINTS.computeIfAbsent(c.getWorld(),ignored->new HashMap<>()).put(c.getAbsolutePos(pos),new Endpoint(side,storage)); return storage;
    }
    private static Storage<FluidVariant> tank(TestContext c,BlockPos pos) { c.setBlockState(pos,BCFactoryBlocks.tank); return FluidStorage.SIDED.find(c.getWorld(),c.getAbsolutePos(pos),Direction.WEST); }
    private static long amount(Storage<FluidVariant> storage) { long amount=0; for(var view:storage)amount+=view.getAmount(); return amount; }
    private static void insert(Storage<FluidVariant> storage,FluidVariant fluid,long amount) { try(var tx=Transaction.openOuter()){ if(storage.insert(fluid,amount,tx)!=amount)throw new IllegalStateException("Fixture insertion failed"); tx.commit(); } }
    private static TilePipeHolder reload(TestContext c,TilePipeHolder tile) { var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState()); copy.readNbt(tile.createNbt()); c.getWorld().removeBlockEntity(tile.getPos()); c.getWorld().addBlockEntity(copy); return copy; }
}
