/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.transport.pipe.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileChute;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.transport.*;
import buildcraft.transport.pipe.flow.PipeFlowPower;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public final class PowerPipeGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3,3,3);
    private record Endpoint(Direction face, IMjConnector connector) {}
    private static final Map<ServerWorld,Map<BlockPos,Endpoint>> ENDPOINTS = new WeakHashMap<>();
    static {
        MjAPI.CONNECTOR.registerForBlocks((world,pos,state,tile,face) -> {
            var endpoints = ENDPOINTS.get(world); var endpoint = endpoints == null ? null : endpoints.get(pos);
            return endpoint != null && endpoint.face == face ? endpoint.connector : null;
        }, Blocks.DIAMOND_BLOCK);
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMaterialsRetainRatesReceiversAndCorrectLossUnits(TestContext c) {
        var defs = new PipeDefinition[]{BCTransportPipes.cobblePower,BCTransportPipes.stonePower,BCTransportPipes.woodPower,
            BCTransportPipes.sandstonePower,BCTransportPipes.quartzPower,BCTransportPipes.ironPower,BCTransportPipes.goldPower,
            BCTransportPipes.diamondPower,BCTransportPipes.diaWoodPower};
        int[] factors={1,2,4,4,8,8,32,64,64};
        for(int i=0;i<defs.length;i++) {
            var info=PipeApi.getPowerTransferInfo(defs[i]); long rate=BCTransportConfig.basePowerRate*factors[i]*MjAPI.MJ;
            c.assertTrue(info.transferPerTick==rate && info.isReceiver==(i==2||i==8), "Wrong native MJ material parameters");
            long resistance=MjAPI.MJ/(i==0?16:i==2?128:32);
            c.assertTrue(info.resistancePerTick==resistance && info.lossPerTick==rate*resistance/MjAPI.MJ,"MJ resistance and absolute loss swapped");
        }
        var info=PipeApi.PowerTransferInfo.createFromLoss(16*MjAPI.MJ,1000,true);
        c.assertTrue(info.lossPerTick==1000 && info.resistancePerTick==62,"Absolute MJ loss conversion wrong");
        var huge=PipeApi.PowerTransferInfo.createFromResistance(Long.MAX_VALUE,MjAPI.MJ/2,true);
        c.assertTrue(huge.lossPerTick==Long.MAX_VALUE/2,"API power configuration overflowed");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeInputSimulationBoundsAndRemovedReferencesConserveEnergy(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipePowerWood); var flow=flow(tile);
        var receiver=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.WEST);
        long rate=PipeApi.getPowerTransferInfo(BCTransportPipes.woodPower).transferPerTick;
        var before=tile.createNbt();
        c.assertTrue(receiver.canReceive() && receiver.receivePower(Long.MAX_VALUE,true)==Long.MAX_VALUE-rate
            && before.equals(tile.createNbt()),"Simulation changed state or ignored native rate");
        c.assertTrue(receiver.receivePower(rate+37,false)==37 && flow.getStoredPower()==rate,"Native MJ input lost overflow");
        c.assertTrue(receiver.receivePower(1,false)==1 && receiver.receivePower(-1,false)==-1,"Full/negative MJ input changed state");
        c.assertTrue(MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),null)==null,"Null side became an arbitrary pipe input");
        c.getWorld().removeBlock(tile.getPos(),false);
        c.assertTrue(!receiver.canReceive() && receiver.receivePower(1,false)==1 && receiver.getPowerRequested()==0,"Removed pipe retained live MJ input");
        var stone=pipe(c,POS,BCTransportItems.pipePowerStone);
        var stoneReceiver=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),stone.getPos(),Direction.WEST);
        c.assertTrue(!stoneReceiver.canReceive() && stoneReceiver.receivePower(rate,false)==rate,"Non-wood pipe accepted direct engine input");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void nativeSidedProviderAndReloadDeliverExactMicroMjAcrossPipes(TestContext c) {
        var sink=sink(c,POS.east(2),Direction.WEST,100*MjAPI.MJ,100*MjAPI.MJ);
        var wood=pipe(c,POS,BCTransportItems.pipePowerWood); var stone=pipe(c,POS.east(),BCTransportItems.pipePowerStone);
        long amount=15*MjAPI.MJ+37;
        var old=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),wood.getPos(),Direction.WEST);
        c.assertTrue(old.receivePower(amount,false)==0,"Fixture input rejected");
        var copy=BCTransportBlocks.pipeHolderTile.instantiate(wood.getPos(),wood.getCachedState()); copy.readNbt(wood.createNbt());
        c.getWorld().removeBlockEntity(wood.getPos()); c.getWorld().addBlockEntity(copy);
        c.assertTrue(flow(copy).getStoredPower()==amount && old.receivePower(1,false)==1,"Reload lost incoming MJ or retained old receiver");
        c.waitAndRun(35,()->{
            c.assertTrue(stone.getPipe().isConnected(Direction.EAST),"Native provider without a block entity was not connected");
            c.assertTrue(sink.amount==amount && flow(copy).getStoredPower()==0 && flow(stone).getStoredPower()==0,"Native MJ pipeline lost/duplicated energy across reload");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=120)
    public void backpressureAndReplacementKeepUndeliveredEnergy(TestContext c) {
        var sink=sink(c,POS.east(2),Direction.WEST,3*MjAPI.MJ,100*MjAPI.MJ);
        var wood=pipe(c,POS,BCTransportItems.pipePowerWood); var stone=pipe(c,POS.east(),BCTransportItems.pipePowerStone);
        ((IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),wood.getPos(),Direction.WEST)).receivePower(8*MjAPI.MJ,false);
        c.waitAndRun(35,()->{
            c.assertTrue(sink.amount==3*MjAPI.MJ && flow(wood).getStoredPower()+flow(stone).getStoredPower()==5*MjAPI.MJ,"Full sink destroyed or duplicated MJ");
            c.setBlockState(POS.east(2),Blocks.AIR);
            var replacement=sink(c,POS.east(2),Direction.WEST,100*MjAPI.MJ,100*MjAPI.MJ);
            c.waitAndRun(35,()->{
                c.assertTrue(replacement.amount==5*MjAPI.MJ && flow(wood).getStoredPower()+flow(stone).getStoredPower()==0,"Replacement did not receive exact retained energy");
                c.complete();
            });
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=80)
    public void weightedBranchesRespectActualDemand(TestContext c) {
        var east=sink(c,POS.east(),Direction.WEST,100*MjAPI.MJ,MjAPI.MJ);
        var north=sink(c,POS.north(),Direction.SOUTH,100*MjAPI.MJ,3*MjAPI.MJ);
        var wood=pipe(c,POS,BCTransportItems.pipePowerWood);
        c.waitAndRun(5,()->{
            ((IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),wood.getPos(),Direction.WEST)).receivePower(4*MjAPI.MJ,false);
            c.waitAndRun(15,()->{
                c.assertTrue(east.amount==MjAPI.MJ && north.amount==3*MjAPI.MJ && flow(wood).getStoredPower()==0,"Weighted demand split was not 1:3");
                c.complete();
            });
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=130)
    public void gateDisabledAndRateLimitedPipesHoldThenDeliverWithoutLosingBuffers(TestContext c) {
        var sink=sink(c,POS.east(2),Direction.WEST,100*MjAPI.MJ,100*MjAPI.MJ);
        var wood=pipe(c,POS,BCTransportItems.pipePowerWood); var iron=pipe(c,POS.east(),BCTransportItems.pipePowerIron);
        limit(iron,6);
        ((IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),wood.getPos(),Direction.WEST)).receivePower(8*MjAPI.MJ,false);
        c.waitAndRun(25,()->{
            c.assertTrue(sink.amount==0 && flow(wood).getStoredPower()+flow(iron).getStoredPower()==8*MjAPI.MJ,"Disabled limiter passed or destroyed MJ");
            limit(iron,5);
            c.waitAndRun(55,()->{
                c.assertTrue(sink.amount==8*MjAPI.MJ && flow(wood).getStoredPower()+flow(iron).getStoredPower()==0,"Limiter failed to resume exact delivery");
                c.assertTrue(sink.maxTick<=MjAPI.MJ,"Iron pipe exceeded its 1 MJ/t limit"); c.complete();
            });
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void passiveProviderExtractionIsSidedBoundedAndSimulatedBeforeCommit(TestContext c) {
        var source=new Source(20*MjAPI.MJ); endpoint(c,POS.west(),Direction.EAST,source);
        var tile=pipe(c,POS,BCTransportItems.pipePowerWood); tile.tick(); var flow=flow(tile);
        c.assertTrue(flow.tryExtractPower(Long.MAX_VALUE,Direction.WEST)==16*MjAPI.MJ && source.amount==4*MjAPI.MJ
            && source.simulations==1 && source.commits==1,"Passive extraction did not honor exact native capacity/simulation");
        c.assertTrue(flow.tryExtractPower(1,Direction.WEST)==0 && flow.tryExtractPower(1,Direction.NORTH)==0,"Passive extraction ignored capacity/face");
        c.assertTrue(tile.getPipe().behaviour.getTextureIndex(Direction.WEST)==1,"Wood extraction face lost its filled texture");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void savedBuffersAndAtomicClientPacketsRejectMalformedValues(TestContext c) throws java.io.IOException {
        var tile=pipe(c,POS,BCTransportItems.pipePowerWood); var flow=flow(tile);
        var detached=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState()); detached.readNbt(tile.createNbt());
        c.assertTrue(detached.getPipe().getConnectedPipe(Direction.NORTH)==null && detached.getPipe().getConnectedTile(Direction.NORTH)==null,
            "An unattached saved pipe attempted a world lookup");
        var saved=new NbtCompound(); var west=new NbtCompound(); west.putLong("stored",Long.MAX_VALUE); west.putLong("incoming",Long.MAX_VALUE); saved.put("west",west);
        var north=new NbtCompound(); north.putLong("stored",-1); north.putLong("incoming",-1); saved.put("north",north);
        var copy=new PipeFlowPower(tile.getPipe(),saved);
        c.assertTrue(copy.getStoredPower()==16*MjAPI.MJ,"Saved MJ amounts overflowed or accepted negatives");
        for(var side:Direction.values()) { flow.getSection(side).displayPower=123; flow.getSection(side).displayFlow=PipeFlowPower.EnumFlow.IN; }
        var packet=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            flow.writePayload(PipeFlowPower.NET_POWER_AMOUNTS,packet,EnvType.SERVER);
            copy.readPayload(PipeFlowPower.NET_POWER_AMOUNTS,packet,EnvType.CLIENT);
            c.assertTrue(copy.getSection(Direction.WEST).displayPower==123 && !packet.isReadable(),"MJ render packet roundtrip failed");
            packet.clear();
            for(int i=0;i<6;i++) { packet.writeInt(i==5?(int)MjAPI.MJ+1:456); packet.writeEnumValue(PipeFlowPower.EnumFlow.OUT); }
            boolean rejected=false;
            try { copy.readPayload(PipeFlowPower.NET_POWER_AMOUNTS,packet,EnvType.CLIENT); } catch(java.io.IOException expected) { rejected=true; }
            c.assertTrue(rejected && copy.getSection(Direction.DOWN).displayPower==123,"Malformed MJ snapshot partially applied");
        } finally { packet.release(); }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void configuredLossModesUseExactUnitsOncePerWorldTick(TestContext c) {
        var previous=BCTransportConfig.lossMode;
        try {
            for(var mode:BCTransportConfig.PowerLossMode.values()) {
                c.setBlockState(POS,Blocks.AIR); var tile=pipe(c,POS,BCTransportItems.pipePowerWood);
                var saved=tile.createNbt(); var pipeTag=saved.getCompound("pipe"); var flowTag=pipeTag.getCompound("flow");
                var north=new NbtCompound(); north.putLong("stored",MjAPI.MJ); flowTag.put("north",north);
                tile.readNbt(saved); BCTransportConfig.lossMode=mode; var flow=flow(tile);
                long expected=MjAPI.MJ-switch(mode) {
                    case LOSSLESS -> 0L;
                    case ABSOLUTE -> PipeApi.getPowerTransferInfo(BCTransportPipes.woodPower).lossPerTick;
                    case PERCENTAGE -> PipeApi.getPowerTransferInfo(BCTransportPipes.woodPower).resistancePerTick;
                };
                flow.onTick(); flow.onTick();
                c.assertTrue(flow.getStoredPower()==expected,"Wrong loss units or repeated tick charged twice: "+mode+" actual="+flow.getStoredPower());
            }
        } finally { BCTransportConfig.lossMode=previous; }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=130)
    public void realEnginePowersChuteThroughWoodAndStonePipes(TestContext c) {
        c.setBlockState(POS.east(2),BCFactoryBlocks.chute.getDefaultState().with(BCFactoryBlocks.chute.getFacingProperty(),Direction.NORTH));
        var wood=pipe(c,POS,BCTransportItems.pipePowerWood); pipe(c,POS.east(),BCTransportItems.pipePowerStone);
        c.setBlockState(POS.west().down(),Blocks.REDSTONE_BLOCK);
        c.setBlockState(POS.west(),BCCoreBlocks.engine.getDefaultState().with(BuildCraftProperties.ENGINE_TYPE,EnumEngineType.CREATIVE));
        var chute=(TileChute)c.getBlockEntity(POS.east(2)); var pos=chute.getPos();
        var entity=new ItemEntity(c.getWorld(),pos.getX()+.5,pos.getY()+.5,pos.getZ()-.2,new ItemStack(Items.DIAMOND,3));
        entity.setNoGravity(true); entity.setVelocity(Vec3d.ZERO); c.getWorld().spawnEntity(entity);
        c.waitAndRun(80,()->{
            c.assertTrue(chute.inv.getStackInSlot(0).isOf(Items.DIAMOND) && chute.inv.getStackInSlot(0).getCount()==3,
                "Native engine/wood/stone/chute chain did not collect items");
            c.assertTrue(wood.getPipe().isConnected(Direction.WEST) && wood.getPipe().behaviour.getTextureIndex(Direction.WEST)==1,
                "Engine input connection/texture missing"); c.complete();
        });
    }

    private static void limit(TilePipeHolder pipe,int shift) { pipe.fireEvent(new PipeEventActionActivate(pipe,BCTransportStatements.ACTION_IRON_POWER_LIMIT[shift],new buildcraft.api.statements.IStatementParameter[0],buildcraft.api.core.EnumPipePart.CENTER)); }
    private static TilePipeHolder pipe(TestContext c,BlockPos pos,Item item) { c.setBlockState(pos,BCTransportBlocks.pipeHolder); var tile=(TilePipeHolder)c.getBlockEntity(pos); tile.onPlacedBy(null,new ItemStack(item)); return tile; }
    private static PipeFlowPower flow(TilePipeHolder tile) { return (PipeFlowPower)tile.getPipe().flow; }
    private static void endpoint(TestContext c,BlockPos pos,Direction side,IMjConnector connector) { c.setBlockState(pos,Blocks.DIAMOND_BLOCK); ENDPOINTS.computeIfAbsent(c.getWorld(),ignored->new HashMap<>()).put(c.getAbsolutePos(pos),new Endpoint(side,connector)); }
    private static Sink sink(TestContext c,BlockPos pos,Direction side,long capacity,long demand) { var sink=new Sink(c.getWorld(),capacity,demand); endpoint(c,pos,side,sink); return sink; }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allNineMjUpgradesAndUndoPreserveDyesThroughNativeCrafting(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld(); player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var handler=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        var colours=Arrays.copyOf(net.minecraft.util.DyeColor.values(),17); int pairs=0;
        for(var def:buildcraft.transport.pipe.PipeRegistry.INSTANCE.getAllRegisteredPipes()) {
            if(!def.identifier.getPath().endsWith("_power")) continue;
            var target=(Item)buildcraft.transport.pipe.PipeRegistry.INSTANCE.getItemForPipe(def);
            var source=net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier("buildcrafttransport","pipe_"+def.identifier.getPath().replace("_power","_item"))); pairs++;
            for(var colour:colours) for(boolean undo:new boolean[]{false,true}) {
                handler.setCursorStack(ItemStack.EMPTY); for(int i=1;i<=9;i++)handler.getSlot(i).setStack(ItemStack.EMPTY);
                handler.getSlot(1).setStack(buildcraft.transport.item.ItemPipeHolder.withColour(new ItemStack(undo?target:source,2),colour));
                if(!undo)handler.getSlot(9).setStack(new ItemStack(Items.GLOWSTONE_DUST,2));
                handler.onContentChanged(handler.getSlot(1).inventory); var result=handler.getSlot(0).getStack();
                c.assertTrue(result.isOf(undo?source:target) && result.getCount()==1 && buildcraft.transport.item.ItemPipeHolder.getColour(result)==colour,"MJ crafting lost type/dye: "+def.identifier);
                handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
                c.assertTrue(handler.getSlot(1).getStack().getCount()==1 && (undo||handler.getSlot(9).getStack().getCount()==1),"MJ crafting consumed wrong ingredient amount");
            }
        }
        c.assertTrue(pairs==9 && c.getWorld().getServer().getAdvancementLoader().get(new net.minecraft.util.Identifier("buildcrafttransport:conducting_mj"))!=null,"Missing MJ recipe family or advancement"); c.complete();
    }

    private static final class Sink implements IMjReceiver {
        final ServerWorld world; final long capacity,demand; long amount,lastTick=Long.MIN_VALUE,inTick,maxTick;
        Sink(ServerWorld world,long capacity,long demand) { this.world=world;this.capacity=capacity;this.demand=demand; }
        @Override public boolean canConnect(IMjConnector other) { return true; }
        @Override public boolean canReceive() { return true; }
        @Override public long getPowerRequested() { return Math.min(demand,capacity-amount); }
        @Override public long receivePower(long sent,boolean simulate) {
            long accepted=Math.max(0,Math.min(sent,capacity-amount));
            if(!simulate) { if(lastTick!=world.getTime()){lastTick=world.getTime();inTick=0;} amount+=accepted;inTick+=accepted;maxTick=Math.max(maxTick,inTick); }
            return sent-accepted;
        }
    }
    private static final class Source implements IMjPassiveProvider {
        long amount; int simulations,commits; Source(long amount){this.amount=amount;}
        @Override public boolean canConnect(IMjConnector other){return true;}
        @Override public long extractPower(long min,long max,boolean simulate){ long taken=Math.min(amount,max);if(taken<min)return 0;if(simulate)simulations++;else{commits++;amount-=taken;}return taken;}
    }
}
