/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.transport.pipe.*;
import buildcraft.core.*;
import buildcraft.energy.*;
import buildcraft.energy.tile.*;
import buildcraft.factory.*;
import buildcraft.factory.tile.TileChute;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.transport.*;
import buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import team.reborn.energy.api.EnergyStorage;

public final class RfPipeGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private record Endpoint(Direction face,EnergyStorage storage) {}
    private static final Map<ServerWorld,Map<BlockPos,Endpoint>> ENDPOINTS=new WeakHashMap<>();
    static {
        EnergyStorage.SIDED.registerForBlocks((world,pos,state,tile,face)->{
            var entries=ENDPOINTS.get(world);var value=entries==null?null:entries.get(pos);
            return value!=null && value.face==face?value.storage:null;
        },Blocks.DIAMOND_BLOCK);
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMaterialsAndPipeFamiliesRemainDistinct(TestContext c) {
        var defs=new PipeDefinition[]{BCTransportPipes.cobbleRf,BCTransportPipes.stoneRf,BCTransportPipes.woodRf,
            BCTransportPipes.sandstoneRf,BCTransportPipes.quartzRf,BCTransportPipes.ironRf,BCTransportPipes.goldRf,
            BCTransportPipes.diamondRf,BCTransportPipes.diaWoodRf};
        int[] factors={1,2,4,4,8,8,32,64,64};
        for(int i=0;i<defs.length;i++){
            var info=PipeApi.getRfTransferInfo(defs[i]);
            c.assertTrue(info.transferPerTick==BCTransportConfig.baseRfRate*factors[i] && info.isReceiver==(i==2||i==8),"Wrong RF material rate/receiver");
        }
        var wood=pipe(c,POS,BCTransportItems.pipeRfWood);
        pipe(c,POS.east(),BCTransportItems.pipePowerWood);
        var stone=pipe(c,POS.north(),BCTransportItems.pipeRfStone);
        wood.tick();stone.tick();
        c.assertTrue(!wood.getPipe().isConnected(Direction.EAST) && wood.getPipe().isConnected(Direction.NORTH),"MJ/RF pipe families connected incorrectly");
        try(var tx=Transaction.openOuter()){
            c.assertTrue(input(stone,Direction.WEST).insert(1,tx)==0,"Ordinary RF pipe accepted direct input");
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nestedRollbackRestoresAllFacesAndPerTickInputBudget(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeRfWood);var west=input(tile,Direction.WEST);var north=input(tile,Direction.NORTH);
        var before=tile.createNbt();
        try(var outer=Transaction.openOuter()){
            c.assertTrue(west.insert(100,outer)==100,"Initial RF insert failed");
            try(var nested=outer.openNested()){c.assertTrue(west.insert(100,nested)==60,"Nested insert ignored tick limit");north.insert(23,nested);nested.commit();}
        }
        c.assertTrue(before.equals(tile.createNbt()) && flow(tile).getStoredPower()==0,"Outer abort did not restore nested commits/all sides");
        try(var outer=Transaction.openOuter()){
            c.assertTrue(west.insert(Long.MAX_VALUE,outer)==160,"Rollback consumed RF quota");
            try(var nested=outer.openNested()){north.insert(17,nested);}
            c.assertTrue(north.getAmount()==0,"Nested abort retained energy");outer.commit();
        }
        try(var tx=Transaction.openOuter()){c.assertTrue(west.insert(1,tx)==0,"Separate transaction bypassed input rate");tx.commit();}
        c.assertTrue(flow(tile).getStoredPower()==160 && EnergyStorage.SIDED.find(c.getWorld(),tile.getPos(),null)==null,"Wrong committed RF or null-sided input");
        c.getWorld().removeBlock(tile.getPos(),false);
        try(var tx=Transaction.openOuter()){c.assertTrue(!west.supportsInsertion() && west.insert(1,tx)==0 && west.getAmount()==0 && west.getCapacity()==0,"Removed RF pipe retained a usable native handle");}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=90)
    public void nativeNoBlockEntitySinkReceivesExactRfAfterReload(TestContext c) {
        var sink=sink(c,POS.east(2),Direction.WEST,1000,1000);
        var wood=pipe(c,POS,BCTransportItems.pipeRfWood);var stone=pipe(c,POS.east(),BCTransportItems.pipeRfStone);
        var old=input(wood,Direction.WEST);put(old,137);
        var copy=BCTransportBlocks.pipeHolderTile.instantiate(wood.getPos(),wood.getCachedState());copy.readNbt(wood.createNbt());
        c.getWorld().removeBlockEntity(wood.getPos());c.getWorld().addBlockEntity(copy);
        try(var tx=Transaction.openOuter()){c.assertTrue(old.insert(1,tx)==0 && flow(copy).getStoredPower()==137,"Reload lost RF or retained old handle");}
        c.waitAndRun(30,()->{
            c.assertTrue(stone.getPipe().isConnected(Direction.EAST) && sink.amount==137 && flow(copy).getStoredPower()+flow(stone).getStoredPower()==0,"RF transfer lost/duplicated energy");
            c.assertTrue(sink.maxTick<=80,"Stone RF exceeded 80 RF/t");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void backpressureAndReplacementKeepEveryUndeliveredRf(TestContext c) {
        var sink=sink(c,POS.east(2),Direction.WEST,3,1000);
        var wood=pipe(c,POS,BCTransportItems.pipeRfWood);var stone=pipe(c,POS.east(),BCTransportItems.pipeRfStone);put(input(wood,Direction.WEST),137);
        c.waitAndRun(30,()->{
            c.assertTrue(sink.amount==3 && flow(wood).getStoredPower()+flow(stone).getStoredPower()==134,"Full sink destroyed/duplicated RF");
            c.setBlockState(POS.east(2),Blocks.AIR);var replacement=sink(c,POS.east(2),Direction.WEST,1000,1000);
            c.waitAndRun(30,()->{c.assertTrue(replacement.amount==134 && flow(wood).getStoredPower()+flow(stone).getStoredPower()==0,"Replacement did not receive retained RF");c.complete();});
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=90)
    public void nativeTransactionalDemandSplitsBranchesWithoutSimulationEnergy(TestContext c) {
        var east=sink(c,POS.east(),Direction.WEST,1000,10);var north=sink(c,POS.north(),Direction.SOUTH,1000,30);
        var wood=pipe(c,POS,BCTransportItems.pipeRfWood);
        c.waitAndRun(5,()->{
            c.assertTrue(east.amount+north.amount==0,"Demand simulations created RF");put(input(wood,Direction.WEST),40);
            c.waitAndRun(20,()->{c.assertTrue(east.amount==10 && north.amount==30 && flow(wood).getStoredPower()==0,"Native RF demand split was not 1:3");c.complete();});
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=120)
    public void disabledIronRetainsBuffersAndResumesAtTenRfPerTick(TestContext c) {
        var sink=sink(c,POS.east(2),Direction.WEST,1000,1000);
        var wood=pipe(c,POS,BCTransportItems.pipeRfWood);var iron=pipe(c,POS.east(),BCTransportItems.pipeRfIron);
        limit(iron,6);put(input(wood,Direction.WEST),155);
        c.waitAndRun(25,()->{
            c.assertTrue(sink.amount==0 && flow(wood).getStoredPower()+flow(iron).getStoredPower()==155,"Disabled RF limiter lost/passed energy");limit(iron,5);
            c.waitAndRun(55,()->{c.assertTrue(sink.amount==155 && sink.maxTick<=10 && flow(wood).getStoredPower()+flow(iron).getStoredPower()==0,"RF limiter failed to resume at 10 RF/t");c.complete();});
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void passiveExtractionUsesOneTransactionAndHonoursSides(TestContext c) {
        var source=new Storage(c.getWorld(),1000,1000,true);source.amount=1000;endpoint(c,POS.west(),Direction.EAST,source);
        var wood=pipe(c,POS,BCTransportItems.pipeRfWood);wood.tick();
        c.assertTrue(source.amount==1000,"RF pipe unexpectedly pulled without an extraction request");
        c.assertTrue(flow(wood).tryExtractPower(Integer.MAX_VALUE,Direction.WEST)==160 && source.amount==840 && flow(wood).getStoredPower()==160,"Passive RF extraction lost energy or exceeded capacity");
        c.assertTrue(flow(wood).tryExtractPower(1,Direction.WEST)==0 && flow(wood).tryExtractPower(1,Direction.NORTH)==0,"Passive RF extraction ignored capacity/side");
        c.assertTrue(wood.getPipe().behaviour.getTextureIndex(Direction.WEST)==1,"RF wooden input face texture missing");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedSavedBuffersAndAtomicPacketsAreBounded(TestContext c) throws java.io.IOException {
        var wood=pipe(c,POS,BCTransportItems.pipeRfWood);var flow=flow(wood);
        var nbt=new NbtCompound();var west=new NbtCompound();west.putLong("stored",Long.MAX_VALUE);west.putLong("incoming",Long.MAX_VALUE);nbt.put("west",west);
        var north=new NbtCompound();north.putInt("stored",-1);north.putInt("incoming",-2);nbt.put("north",north);
        var copy=new PipeFlowRedstoneFlux(wood.getPipe(),nbt);
        c.assertTrue(copy.getStoredPower()==160,"Saved RF overflowed or accepted negatives");
        var packet=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            for(var side:Direction.values()){flow.getSection(side).displayPower=123;flow.getSection(side).displayFlow=PipeFlowRedstoneFlux.EnumFlow.IN;}
            flow.writePayload(PipeFlowRedstoneFlux.NET_POWER_AMOUNTS,packet,EnvType.SERVER);
            copy.readPayload(PipeFlowRedstoneFlux.NET_POWER_AMOUNTS,packet,EnvType.CLIENT);
            c.assertTrue(copy.getSection(Direction.WEST).displayPower==123 && !packet.isReadable(),"RF packet roundtrip failed");
            packet.clear();
            for(int i=0;i<6;i++){packet.writeInt(i==5?-1:456);packet.writeEnumValue(PipeFlowRedstoneFlux.EnumFlow.OUT);}
            boolean rejected=false;try{copy.readPayload(PipeFlowRedstoneFlux.NET_POWER_AMOUNTS,packet,EnvType.CLIENT);}catch(java.io.IOException expected){rejected=true;}
            c.assertTrue(rejected && copy.getSection(Direction.DOWN).displayPower==123,"Malformed RF packet partially applied");
        } finally {packet.release();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void rfEngineInputRollbackConversionExactFitAndRemovedHandle(TestContext c) {
        var engine=engine(c,POS);var input=EnergyStorage.SIDED.find(c.getWorld(),engine.getPos(),Direction.WEST);
        try(var outer=Transaction.openOuter()){
            input.insert(50,outer);try(var nested=outer.openNested()){input.insert(50,nested);nested.commit();}
        }
        c.assertTrue(engine.getCurrentRF()==0,"RF engine simulation/rollback created energy");put(input,100);
        var previous=BCLibConfig.mjRfConversion;
        try {
            BCLibConfig.mjRfConversion=MjRfConversion.createRaw(200_000);
            c.assertTrue(engine.getRfConsumptionRate()==20,"RF engine ignored configured conversion ratio");
            engine.invUpgrades.setStackInSlot(0,new ItemStack(BCCoreItems.gearIron));engine.invUpgrades.setStackInSlot(1,new ItemStack(BCCoreItems.gearGold));
            c.assertTrue(engine.getMjPerTick()==9*MjAPI.MJ && engine.getRfConsumptionRate()==45,"Native RF engine gear upgrades missing");
            var saved=engine.createNbt();saved.putLong("power",engine.getMaxPower()-3*200_000);engine.readNbt(saved);
            engine.isRedstonePowered=true;engine.tick();
            c.assertTrue(engine.getCurrentRF()==97 && engine.getMjStored()==engine.getMaxPower() && engine.getCurrentOutput()==600_000,"Exact-fit RF conversion overfilled or stalled");
            engine.tick();c.assertTrue(engine.getCurrentRF()==97 && engine.getCurrentOutput()==0,"Full engine consumed RF or claimed output");
        } finally {BCLibConfig.mjRfConversion=previous;}
        var data=engine.createNbt();data.putLong("currentRF",Long.MAX_VALUE);engine.readNbt(data);
        c.assertTrue(engine.getCurrentRF()==TileEngineRF.MAX_RF,"Engine RF NBT overflowed");
        c.getWorld().removeBlock(engine.getPos(),false);
        try(var tx=Transaction.openOuter()){c.assertTrue(input.insert(1,tx)==0 && input.getAmount()==0 && input.getCapacity()==0,"Removed RF engine retained native input");}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=130)
    public void dynamoPipesAndRfEngineConserveFractionalMjEndToEnd(TestContext c) {
        var dynamo=dynamo(c,POS.west());var wood=pipe(c,POS,BCTransportItems.pipeRfWood);var stone=pipe(c,POS.east(),BCTransportItems.pipeRfStone);
        var engine=engine(c,POS.east(2));long ratio=BCLibConfig.mjRfConversion.mjPerRf;dynamo.battery.addPower(503*ratio+7,false);
        c.waitAndRun(90,()->{
            c.assertTrue(dynamo.battery.getStored()==7 && flow(wood).getStoredPower()+flow(stone).getStoredPower()==0 && engine.getCurrentRF()==0 && engine.getMjStored()==503*ratio,"Dynamo/pipe/engine round trip lost or duplicated MJ/RF");
            var stale=EnergyStorage.SIDED.find(c.getWorld(),dynamo.getPos(),Direction.EAST);dynamo.battery.addPower(MjAPI.MJ,false);c.getWorld().removeBlock(dynamo.getPos(),false);
            try(var tx=Transaction.openOuter()){c.assertTrue(stale.extract(1,tx)==0 && !stale.supportsExtraction() && stale.getAmount()==0,"Removed dynamo retained native output");}
            c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=140)
    public void realRfChainPowersNativeChuteAndCollectsItems(TestContext c) {
        var dynamo=dynamo(c,POS.west());pipe(c,POS,BCTransportItems.pipeRfWood);pipe(c,POS.east(),BCTransportItems.pipeRfStone);
        c.setBlockState(POS.east(3),BCFactoryBlocks.chute.getDefaultState().with(BCFactoryBlocks.chute.getFacingProperty(),Direction.NORTH));
        engine(c,POS.east(2));dynamo.battery.addPower(300*MjAPI.MJ,false);
        var chute=(TileChute)c.getBlockEntity(POS.east(3));var pos=chute.getPos();
        var item=new ItemEntity(c.getWorld(),pos.getX()+.5,pos.getY()+.5,pos.getZ()-.2,new ItemStack(Items.DIAMOND,3));item.setNoGravity(true);item.setVelocity(Vec3d.ZERO);c.getWorld().spawnEntity(item);
        c.waitAndRun(100,()->{c.assertTrue(chute.inv.getStackInSlot(0).isOf(Items.DIAMOND) && chute.inv.getStackInSlot(0).getCount()==3,"Native dynamo/RF/engine/chute chain failed");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeRfEngineScreenUpgradesAndDistanceChecks(TestContext c) {
        var engine=engine(c,POS);var player=c.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(engine.getPos(),0,0);
        var screen=new buildcraft.energy.container.ContainerEngineRF(player,37,engine);
        c.assertTrue(screen.getType()==BCEnergyScreens.RF && screen.syncId==37 && screen.canUse(player),"RF screen lost native type/id");
        player.getInventory().setStack(9,new ItemStack(BCCoreItems.gearGold,6));screen.quickMove(player,0);
        int count=0;for(int i=0;i<4;i++)count+=engine.invUpgrades.getStackInSlot(i).getCount();
        c.assertTrue(count==4 && player.getInventory().getStack(9).getCount()==2,"RF upgrade slots failed one-item insertion limit");
        player.refreshPositionAndAngles(engine.getPos().add(30,0,0),0,0);
        c.assertTrue(!screen.canUse(player) && screen.quickMove(player,36).isEmpty(),"Distant player retained RF screen access");
        screen.onClosed(player);
        var dynamo=dynamo(c,POS.north(2));player.refreshPositionAndAngles(dynamo.getPos(),0,0);
        var dynamoScreen=new buildcraft.energy.container.ContainerDynamoMJ(player,38,dynamo);
        player.getInventory().setStack(9,new ItemStack(BCCoreItems.gearIron,6));dynamoScreen.quickMove(player,0);
        count=0;for(int i=0;i<4;i++)count+=dynamo.invUpgrades.getStackInSlot(i).getCount();
        c.assertTrue(count==4 && player.getInventory().getStack(9).getCount()==2,"Dynamo upgrade screen ignored one-item slot capacity");
        dynamoScreen.onClosed(player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nineRfUpgradeUndoPairsPreserveDyesAndNativeCraftingConsumption(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var handler=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        var colours=Arrays.copyOf(net.minecraft.util.DyeColor.values(),17);int pairs=0;
        for(var def:buildcraft.transport.pipe.PipeRegistry.INSTANCE.getAllRegisteredPipes()){
            if(!def.identifier.getPath().endsWith("_rf"))continue;
            var target=(Item)buildcraft.transport.pipe.PipeRegistry.INSTANCE.getItemForPipe(def);
            var source=net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier("buildcrafttransport","pipe_"+def.identifier.getPath().replace("_rf","_power")));pairs++;
            for(var colour:colours)for(boolean undo:new boolean[]{false,true}){
                handler.setCursorStack(ItemStack.EMPTY);for(int i=1;i<=9;i++)handler.getSlot(i).setStack(ItemStack.EMPTY);
                handler.getSlot(1).setStack(buildcraft.transport.item.ItemPipeHolder.withColour(new ItemStack(undo?target:source,2),colour));
                if(!undo)handler.getSlot(9).setStack(new ItemStack(Items.REDSTONE,2));
                handler.onContentChanged(handler.getSlot(1).inventory);var result=handler.getSlot(0).getStack();
                c.assertTrue(result.isOf(undo?source:target) && result.getCount()==1 && buildcraft.transport.item.ItemPipeHolder.getColour(result)==colour,"RF recipe lost type/dye: "+def.identifier);
                handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
                c.assertTrue(handler.getSlot(1).getStack().getCount()==1 && (undo||handler.getSlot(9).getStack().getCount()==1),"RF crafting consumed wrong amount");
            }
        }
        c.assertTrue(pairs==9 && c.getWorld().getServer().getAdvancementLoader().get(new net.minecraft.util.Identifier("buildcrafttransport:conducting_rf"))!=null,"RF recipe family/advancement missing");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeTilePacketsIgnoreDifferentMachineTypesAfterReplacement(TestContext c) throws java.io.IOException {
        var engine=engine(c,POS);
        var message=engine.createNetworkUpdate(buildcraft.lib.tile.TileBC_Neptune.NET_RENDER_DATA);
        c.setBlockState(POS,BCFactoryBlocks.chute);
        var chute=(TileChute)c.getBlockEntity(POS);var before=chute.createNbt();
        var player=c.createMockCreativeServerPlayerInWorld();
        var wire=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            message.write(wire);var decoded=buildcraft.lib.net.MessageUpdateTile.read(wire);var payload=decoded.getPayload();
            try {
                chute.receivePayload(new buildcraft.lib.net.NetworkContext(player,reply->{throw new AssertionError("Wrong-type packet produced reply");}),payload);
                c.assertTrue(!payload.isReadable() && before.equals(chute.createNbt()),"Replacement parsed the previous machine's payload");
            } finally {payload.release();}
        } finally {wire.release();}
        c.complete();
    }
    private static final class Storage extends SnapshotParticipant<long[]> implements EnergyStorage {
        final ServerWorld world;final long capacity,rate;final boolean source;long amount,tick=Long.MIN_VALUE,used,maxTick;
        Storage(ServerWorld world,long capacity,long rate,boolean source){this.world=world;this.capacity=capacity;this.rate=rate;this.source=source;}
        @Override public boolean supportsInsertion(){return !source;}
        @Override public boolean supportsExtraction(){return source;}
        private long transfer(long maximum,TransactionContext tx,boolean extract){
            if(maximum<0)throw new IllegalArgumentException();
            long available=rate-(tick==world.getTime()?used:0);long accepted=Math.min(maximum,Math.min(available,extract?amount:capacity-amount));
            if(accepted>0){updateSnapshots(tx);if(tick!=world.getTime()){tick=world.getTime();used=0;}used+=accepted;maxTick=Math.max(maxTick,used);amount+=extract?-accepted:accepted;}
            return accepted;
        }
        @Override public long insert(long max,TransactionContext tx){return source?0:transfer(max,tx,false);}
        @Override public long extract(long max,TransactionContext tx){return source?transfer(max,tx,true):0;}
        @Override public long getAmount(){return amount;}
        @Override public long getCapacity(){return capacity;}
        @Override protected long[] createSnapshot(){return new long[]{amount,tick,used,maxTick};}
        @Override protected void readSnapshot(long[] s){amount=s[0];tick=s[1];used=s[2];maxTick=s[3];}
    }
    private static void limit(TilePipeHolder tile,int shift){tile.fireEvent(new PipeEventActionActivate(tile,BCTransportStatements.ACTION_IRON_RF_LIMIT[shift],new buildcraft.api.statements.IStatementParameter[0],buildcraft.api.core.EnumPipePart.CENTER));}
    private static void put(EnergyStorage storage,long amount){try(var tx=Transaction.openOuter()){if(storage.insert(amount,tx)!=amount)throw new AssertionError("Fixture energy rejected");tx.commit();}}
    private static EnergyStorage input(TilePipeHolder tile,Direction side){return EnergyStorage.SIDED.find(tile.getWorld(),tile.getPos(),side);}
    private static TilePipeHolder pipe(TestContext c,BlockPos pos,Item item){c.setBlockState(pos,BCTransportBlocks.pipeHolder);var tile=(TilePipeHolder)c.getBlockEntity(pos);tile.onPlacedBy(null,new ItemStack(item));return tile;}
    private static PipeFlowRedstoneFlux flow(TilePipeHolder tile){return (PipeFlowRedstoneFlux)tile.getPipe().flow;}
    private static void endpoint(TestContext c,BlockPos pos,Direction face,EnergyStorage storage){c.setBlockState(pos,Blocks.DIAMOND_BLOCK);ENDPOINTS.computeIfAbsent(c.getWorld(),ignored->new HashMap<>()).put(c.getAbsolutePos(pos),new Endpoint(face,storage));}
    private static Storage sink(TestContext c,BlockPos pos,Direction face,long capacity,long rate){var sink=new Storage(c.getWorld(),capacity,rate,false);endpoint(c,pos,face,sink);return sink;}
    private static TileEngineRF engine(TestContext c,BlockPos pos){c.setBlockState(pos.down(),Blocks.REDSTONE_BLOCK);c.setBlockState(pos,BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.RF));return (TileEngineRF)c.getBlockEntity(pos);}
    private static TileDynamoMJ dynamo(TestContext c,BlockPos pos){c.setBlockState(pos,BCEnergyBlocks.mjDynamo.getDefaultState().with(buildcraft.lib.block.BlockBCBase_Neptune.BLOCK_FACING_6,Direction.EAST));return (TileDynamoMJ)c.getBlockEntity(pos);}
}
