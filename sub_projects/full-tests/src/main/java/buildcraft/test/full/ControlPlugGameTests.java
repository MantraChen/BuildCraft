/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.mj.*;
import buildcraft.api.transport.pipe.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerGate;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.item.*;
import buildcraft.silicon.plug.*;
import buildcraft.silicon.recipe.NativeAssemblyRecipe;
import buildcraft.transport.*;
import buildcraft.transport.pipe.behaviour.*;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class ControlPlugGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static TilePipeHolder pipe(TestContext c,BlockPos pos,Item item) {
        c.setBlockState(pos,BCTransportBlocks.pipeHolder);var tile=(TilePipeHolder)c.getBlockEntity(pos);tile.onPlacedBy(null,new ItemStack(item));return tile;
    }
    private static PluggablePulsar pulsar(TilePipeHolder tile) {
        var pulsar=new PluggablePulsar(BCSiliconPlugs.pulsar,tile,Direction.UP);tile.replacePluggable(Direction.UP,pulsar);return pulsar;
    }
    private static BarrelBlockEntity source(TestContext c,TilePipeHolder tile,int count) {
        c.setBlockState(POS.west(),Blocks.BARREL);var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.west());barrel.setStack(0,new ItemStack(Items.DIAMOND,count));tile.tick();return barrel;
    }
    private static net.minecraft.server.network.ServerPlayerEntity near(TestContext c,TilePipeHolder tile) {
        var player=c.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(tile.getPos(),0,0);return player;
    }
    private static void tick(PluggablePulsar pulsar,int count){for(int i=0;i<count;i++)pulsar.onTick();}
    private static TilePipeHolder reload(TestContext c,TilePipeHolder tile) {
        var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);return copy;
    }
    private static PluggableGate gate(TilePipeHolder tile) {
        var gate=new PluggableGate(BCSiliconPlugs.gate,tile,Direction.NORTH,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.NETHER_BRICK,EnumGateModifier.NO_MODIFIER));tile.replacePluggable(Direction.NORTH,gate);return gate;
    }
    private static int choose(TestContext c,ContainerGate menu,boolean action,int row,String id,Direction face) {
        var choices=menu.choices(action);for(int i=0;i<choices.size();i++)if(choices.get(i).getUniqueTag().equals(id) && choices.get(i).sourcePart.face==face) {
            c.assertTrue(menu.onButtonClick(menu.player,ContainerGate.selectionId(row,action,i)),"Rejected control statement "+id);return i;
        }
        throw new IllegalStateException("Missing "+id+" / "+face);
    }
    private static Storage<FluidVariant> tank(TestContext c,BlockPos pos) {
        c.setBlockState(pos,BCFactoryBlocks.tank);return FluidStorage.SIDED.find(c.getWorld(),c.getAbsolutePos(pos),Direction.EAST);
    }
    private static void water(Storage<FluidVariant> storage,long amount) {
        try(var tx=Transaction.openOuter()){if(storage.insert(FluidVariant.of(net.minecraft.fluid.Fluids.WATER),amount,tx)!=amount)throw new IllegalStateException("Tank full");tx.commit();}
    }
    private static long amount(Storage<FluidVariant> storage){long amount=0;for(var view:storage)amount+=view.getAmount();return amount;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allThreeControlsPlaceOnSixFacesAndRoundTripNbt(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var player=near(c,tile);player.getAbilities().creativeMode=false;
        for(var item:List.of(BCSiliconItems.plugPulsar,BCSiliconItems.plugTimer,BCSiliconItems.plugLightSensor))for(var side:Direction.values()) {
            var look=Vec3d.of(side.getVector()).multiply(-1);var eye=Vec3d.ofCenter(tile.getPos()).subtract(look.multiply(3));
            player.refreshPositionAndAngles(eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());
            var stack=new ItemStack(item,2);player.setStackInHand(Hand.MAIN_HAND,stack);
            var result=BCTransportBlocks.pipeHolder.onUse(tile.getCachedState(),c.getWorld(),tile.getPos(),player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(tile.getPos()),side,tile.getPos(),false));
            c.assertTrue(result.isAccepted() && tile.getPluggable(side) instanceof PluggableSidedPlate && stack.getCount()==1,"Native control placement/count failed "+item+" "+side);
            var plug=(PluggableSidedPlate)tile.getPluggable(side);c.assertTrue(plug.isBlocking() && plug.getPickStack().isOf(item) && !plug.getBoundingBox().contains(.5,.5,.5),"Wrong plate shape/pick/blocking");
            var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());c.assertTrue(copy.getPluggable(side).getPickStack().isOf(item),"Control attachment lost on NBT load");tile.replacePluggable(side,null);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pulsarRejectsUnsupportedPipesOccupiedFacesAndDistantPlayers(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemStone);var player=near(c,tile);
        c.assertTrue(BCSiliconItems.plugPulsar.onPlace(new ItemStack(BCSiliconItems.plugPulsar),tile,Direction.UP,player,Hand.MAIN_HAND)==null,"Pulsar attached to non-extractor");
        var timer=BCSiliconItems.plugTimer.onPlace(new ItemStack(BCSiliconItems.plugTimer),tile,Direction.UP,player,Hand.MAIN_HAND);tile.replacePluggable(Direction.UP,timer);
        c.assertTrue(BCSiliconItems.plugLightSensor.onPlace(new ItemStack(BCSiliconItems.plugLightSensor),tile,Direction.UP,player,Hand.MAIN_HAND)==null,"Overwrote occupied plate");
        player.refreshPositionAndAngles(player.getX()+100,player.getY(),player.getZ(),0,0);
        c.assertTrue(BCSiliconItems.plugTimer.onPlace(new ItemStack(BCSiliconItems.plugTimer),tile,Direction.DOWN,player,Hand.MAIN_HAND)==null,"Distant attachment placement");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void manualPulsesExtractOncePerTwentyTicksAndToggleOff(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,8);var pulse=pulsar(tile);var player=near(c,tile);
        tick(pulse,25);c.assertTrue(source.getStack(0).getCount()==8,"Idle pulsar extracted");
        c.assertTrue(pulse.onPluggableActivate(player,null,0,0,0),"Manual activation rejected");tick(pulse,19);c.assertTrue(source.getStack(0).getCount()==8,"Pulsar extracted before 20 ticks");tick(pulse,1);
        c.assertTrue(source.getStack(0).getCount()==7 && pulse.getPulseStage()==0,"Manual extraction amount/cadence wrong");tick(pulse,20);c.assertTrue(source.getStack(0).getCount()==6,"Continuous second pulse missing");
        pulse.onPluggableActivate(player,null,0,0,0);tick(pulse,50);c.assertTrue(source.getStack(0).getCount()==6 && !pulse.isPulsing() && pulse.getPulseStage()==0,"Manual off retained motion/extraction");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void queuedSinglePulseWaitsForInputThenConsumesExactlyOneRequest(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,0);var pulse=pulsar(tile);pulse.addSinglePulse();tick(pulse,40);
        c.assertTrue(pulse.getPendingPulses()==1 && pulse.isPulsing(),"Failed extraction discarded single request");
        source.setStack(0,new ItemStack(Items.DIAMOND,5));tick(pulse,19);c.assertTrue(source.getStack(0).getCount()==5,"Retry extracted early");tick(pulse,1);tick(pulse,40);
        c.assertTrue(pulse.getPendingPulses()==0 && source.getStack(0).getCount()==4 && !pulse.isPulsing(),"Single request duplicated or extracted multiple items");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void singleFluidPulseRequiresWholeBucketAndKeepsExactDroplets(TestContext c) {
        var storage=tank(c,POS.west());water(storage,FluidConstants.BUCKET/2+7);var tile=pipe(c,POS,BCTransportItems.pipeFluidWood);tile.tick();var pulse=pulsar(tile);pulse.addSinglePulse();tick(pulse,20);
        c.assertTrue(amount(storage)==FluidConstants.BUCKET/2+7 && pulse.getPendingPulses()==1,"Partial bucket consumed on failed single pulse");
        water(storage,FluidConstants.BUCKET/2);tick(pulse,20);
        c.assertTrue(amount(storage)==7 && ((PipeFlowFluids)tile.getPipe().getFlow()).getAmount()==FluidConstants.BUCKET && pulse.getPendingPulses()==0,"Fluid pulse lost droplets or bucket accounting");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void configuredExtractionCostsMatchSingleItemAndFluidPulses(TestContext c) {
        long oldItem=BCTransportConfig.mjPerItem,oldFluid=BCTransportConfig.mjPerMillibucket;
        try {
            BCTransportConfig.mjPerItem=2*MjAPI.MJ;BCTransportConfig.mjPerMillibucket=2000;
            var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,3);var pulse=pulsar(tile);pulse.addSinglePulse();tick(pulse,20);c.assertTrue(source.getStack(0).getCount()==2 && pulse.getPendingPulses()==0,"Single item ignored configured cost");
            c.getWorld().breakBlock(tile.getPos(),false);c.setBlockState(POS.west(),Blocks.AIR);var storage=tank(c,POS.west());water(storage,FluidConstants.BUCKET*2);
            tile=pipe(c,POS,BCTransportItems.pipeFluidWood);tile.tick();pulse=pulsar(tile);pulse.addSinglePulse();tick(pulse,20);c.assertTrue(amount(storage)==FluidConstants.BUCKET && pulse.getPendingPulses()==0,"Single fluid ignored configured cost");
        }finally{BCTransportConfig.mjPerItem=oldItem;BCTransportConfig.mjPerMillibucket=oldFluid;}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gateConstantLeaseExpiresAndSingleActionsAreEdgeTriggered(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,8);var pulse=pulsar(tile);
        pulse.enablePulsar();tick(pulse,20);c.assertTrue(source.getStack(0).getCount()==8 && !pulse.isPulsing(),"Expired ten-tick gate lease continued");
        for(int i=0;i<40;i++){pulse.enablePulsar();pulse.onTick();}c.assertTrue(source.getStack(0).getCount()==6,"Continuous gate did not refresh lease");tick(pulse,30);
        var gate=gate(tile);var player=near(c,tile);var menu=new ContainerGate(player,1,gate);choose(c,menu,false,0,"buildcraftcore:trigger.true",null);choose(c,menu,true,0,"buildcraft:pulsar.single",Direction.UP);
        for(int i=0;i<80;i++){gate.logic.resolveActions();pulse.onTick();}
        c.assertTrue(source.getStack(0).getCount()==5 && pulse.getPendingPulses()==0,"Single gate action repeated while high");
        gate.logic.statements[0].trigger.set(null);gate.logic.resolveActions();choose(c,menu,false,0,"buildcraftcore:trigger.true",null);
        for(int i=0;i<20;i++){gate.logic.resolveActions();pulse.onTick();}c.assertTrue(source.getStack(0).getCount()==4,"New rising edge did not pulse");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void independentGateRowsAndReloadKeepSingleActionLatches(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,8);var pulse=pulsar(tile);var gate=gate(tile);var menu=new ContainerGate(near(c,tile),1,gate);
        for(int row=0;row<2;row++){choose(c,menu,false,row,"buildcraftcore:trigger.true",null);choose(c,menu,true,row,"buildcraft:pulsar.single",Direction.UP);}
        gate.logic.resolveActions();tick(pulse,7);c.assertTrue(pulse.getPendingPulses()==2,"Two gate rows shared an activation latch");
        var copy=reload(c,tile);var restored=(PluggablePulsar)copy.getPluggable(Direction.UP);var restoredGate=(PluggableGate)copy.getPluggable(Direction.NORTH);
        c.assertTrue(restored.getPendingPulses()==2 && restored.getPulseStage()==7,"Saved pulse queue/stage missing");
        copy.getPipe().onTick();for(int i=0;i<100;i++){restoredGate.logic.resolveActions();restored.onTick();}
        c.assertTrue(source.getStack(0).getCount()==6 && restored.getPendingPulses()==0,"Reload retriggered a high single action or lost queued work");
        pulse.addSinglePulse();pulse.enablePulsar();tick(pulse,40);c.assertTrue(source.getStack(0).getCount()==6,"Stale pulsar extracted after replacement");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void availableGateStatementsFollowAttachmentAndFace(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var pulse=pulsar(tile);var gate=gate(tile);var timer=new PluggableTimer(BCSiliconPlugs.timer,tile,Direction.SOUTH);tile.replacePluggable(Direction.SOUTH,timer);
        var sensor=new PluggableLightSensor(BCSiliconPlugs.lightSensor,tile,Direction.DOWN);tile.replacePluggable(Direction.DOWN,sensor);
        var triggers=gate.logic.getAllValidTriggers();var actions=gate.logic.getAllValidActions();
        c.assertTrue(triggers.stream().filter(w->w.delegate instanceof buildcraft.silicon.statement.TriggerTimer).count()==3
            && triggers.stream().filter(w->w.delegate instanceof buildcraft.silicon.statement.TriggerLightSensor && w.sourcePart.face==Direction.DOWN).count()==2
            && actions.stream().filter(w->w.delegate instanceof buildcraft.silicon.statement.ActionPowerPulsar && w.sourcePart.face==Direction.UP).count()==2,"Missing/facing-wrong control choices");
        var player=near(c,tile);var menu=new ContainerGate(player,1,gate);int choice=-1;for(int i=0;i<menu.actions.size();i++)if(menu.actions.get(i).delegate==BCSiliconStatements.ACTION_PULSAR_SINGLE)choice=i;
        tile.replacePluggable(Direction.UP,null);c.assertTrue(!menu.onButtonClick(player,ContainerGate.selectionId(0,true,choice)),"Stale menu selected removed pulsar");
        tile.replacePluggable(Direction.SOUTH,null);tile.replacePluggable(Direction.DOWN,null);
        c.assertTrue(gate.logic.getAllValidTriggers().stream().noneMatch(w->w.delegate instanceof buildcraft.silicon.statement.TriggerTimer || w.delegate instanceof buildcraft.silicon.statement.TriggerLightSensor)
            && !BCSiliconStatements.TRIGGER_LIGHT_HIGH.isTriggerActive(Direction.DOWN,gate.logic,new buildcraft.api.statements.IStatementParameter[0]),"Removed controls retained choices/active triggers");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=650)
    public void timerPeriodsUseWorldTicksAndRemovalDisablesExistingTrigger(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemStone);tile.replacePluggable(Direction.UP,new PluggableTimer(BCSiliconPlugs.timer,tile,Direction.UP));var gate=gate(tile);
        var times=List.of(new ArrayList<Long>(),new ArrayList<Long>(),new ArrayList<Long>());
        for(int tick=1;tick<=610;tick++)c.runAtTick(tick,()->{
            for(int i=0;i<3;i++)if(BCSiliconStatements.TRIGGER_TIMER[i].isTriggerActive(gate.logic,new buildcraft.api.statements.IStatementParameter[0]))times.get(i).add(c.getWorld().getTime());
        });
        c.runAtTick(611,()->{
            for(int i=0;i<3;i++){var list=times.get(i);c.assertTrue(list.size()>=2,"Timer interval never fired");for(int n=1;n<list.size();n++)c.assertTrue(list.get(n)-list.get(n-1)==100*(i+1),"Timer period is not 5/10/15 seconds");}
            tile.replacePluggable(Direction.UP,null);c.assertTrue(!BCSiliconStatements.TRIGGER_TIMER_SHORT.isTriggerActive(gate.logic,new buildcraft.api.statements.IStatementParameter[0]),"Removed timer remained active");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void daylightSensorDistinguishesDayNightAndItsMountedFace(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemStone);tile.replacePluggable(Direction.UP,new PluggableLightSensor(BCSiliconPlugs.lightSensor,tile,Direction.UP));var gate=gate(tile);long old=c.getWorld().getTimeOfDay();c.getWorld().setTimeOfDay(1000);
        c.runAtTick(20,()->{
            c.assertTrue(BCSiliconStatements.TRIGGER_LIGHT_HIGH.isTriggerActive(Direction.UP,gate.logic,new buildcraft.api.statements.IStatementParameter[0])
                && !BCSiliconStatements.TRIGGER_LIGHT_HIGH.isTriggerActive(Direction.DOWN,gate.logic,new buildcraft.api.statements.IStatementParameter[0]),"Daylight or sensor mounting side incorrect");c.getWorld().setTimeOfDay(18000);
        });
        c.runAtTick(40,()->{try{c.assertTrue(BCSiliconStatements.TRIGGER_LIGHT_LOW.isTriggerActive(Direction.UP,gate.logic,new buildcraft.api.statements.IStatementParameter[0])
                && !BCSiliconStatements.TRIGGER_LIGHT_HIGH.isTriggerActive(Direction.UP,gate.logic,new buildcraft.api.statements.IStatementParameter[0]),"Night was not dark");}finally{c.getWorld().setTimeOfDay(old);}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void savedAndNetworkPulseStateIsBoundedAndServerIgnoresClientMutation(TestContext c) throws Exception {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var nbt=new NbtCompound();nbt.putInt("pulseStage",Integer.MAX_VALUE);nbt.putInt("gateSinglePulses",Integer.MAX_VALUE);nbt.putInt("gateEnabledTicks",-4);
        var pulse=new PluggablePulsar(BCSiliconPlugs.pulsar,tile,Direction.UP,nbt);tile.replacePluggable(Direction.UP,pulse);
        c.assertTrue(pulse.getPulseStage()==19 && pulse.getPendingPulses()==PluggablePulsar.MAX_PENDING,"Unbounded NBT pulse state");
        var buffer=new PacketByteBuf(io.netty.buffer.Unpooled.buffer());try {
            pulse.writeCreationPayload(buffer);var copy=new PluggablePulsar(BCSiliconPlugs.pulsar,tile,Direction.UP,buffer);c.assertTrue(copy.getPulseStage()==19 && !copy.isManual(),"Creation packet lost phase/manual state");
            buffer.clear();buffer.writeBoolean(true);buffer.writeBoolean(false);buffer.writeBoolean(true);buffer.writeByte(2);pulse.readPayload(buffer,EnvType.SERVER,null);c.assertTrue(!pulse.isManual(),"Client packet enabled pulsar");
            buffer.clear();buffer.writeBoolean(true);buffer.writeBoolean(false);buffer.writeBoolean(true);buffer.writeByte(255);
            boolean failed=false;try{pulse.readPayload(buffer,EnvType.CLIENT,null);}catch(java.io.IOException expected){failed=true;}c.assertTrue(failed && !pulse.isManual() && pulse.getPulseStage()==19,"Invalid packet partially changed pulse state");
        }finally{buffer.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void originalAssemblyRecipesAndEngineVariantIngredientSurvivePackets(TestContext c) {
        for(var name:List.of("plug_pulsar","light_sensor","timer")) {
            var id=new Identifier("buildcraftsilicon:assembly/"+name);var recipe=(NativeAssemblyRecipe)c.getWorld().getRecipeManager().get(id).orElseThrow();
            c.assertTrue(recipe.cost()==(name.equals("plug_pulsar")?1000:500)*MjAPI.MJ,"Wrong original control recipe cost");
            var buffer=new PacketByteBuf(io.netty.buffer.Unpooled.buffer());try {
                BCSiliconRecipes.ASSEMBLY_SERIALIZER.write(buffer,recipe);var copy=BCSiliconRecipes.ASSEMBLY_SERIALIZER.read(id,buffer);
                if(name.equals("plug_pulsar")) {
                    for(var type:EnumEngineType.values())c.assertTrue(copy.inputs().get(0).test(BCCoreBlocks.engine.getStack(type))==(type==EnumEngineType.WOOD),"Pulsar recipe accepted wrong engine variant");
                    var plain=new ItemStack(BCCoreBlocks.engine);plain.setCustomName(Text.literal("Felix redstone engine"));c.assertTrue(copy.inputs().get(0).test(plain),"Default named redstone engine rejected");
                    var legacy=new ItemStack(BCCoreBlocks.engine);legacy.setDamage(EnumEngineType.CREATIVE.ordinal());c.assertTrue(!copy.inputs().get(0).test(legacy),"Legacy creative engine matched redstone input");
                }
            }finally{buffer.release();}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=170)
    public void nativePulsarFeedsWoodStonePipeDeliveryAndStopsAfterRemoval(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var source=source(c,tile,6);pipe(c,POS.east(),BCTransportItems.pipeItemStone);c.setBlockState(POS.east(2),Blocks.BARREL);tile.tick();var pulse=pulsar(tile);pulse.addSinglePulse();pulse.addSinglePulse();
        c.waitAndRun(130,()->{
            var target=(BarrelBlockEntity)c.getBlockEntity(POS.east(2));int count=0;for(int i=0;i<target.size();i++)count+=target.getStack(i).getCount();c.assertTrue(count==2 && source.getStack(0).getCount()==4,"Live pulsar pipeline lost/duplicated output");
            tile.replacePluggable(Direction.UP,null);pulse.addSinglePulse();pulse.enablePulsar();tick(pulse,60);c.assertTrue(source.getStack(0).getCount()==4,"Removed pulsar still extracted");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void breakingPipeDropsEachControlAttachmentExactlyOnce(TestContext c) {
        var tile=pipe(c,POS,BCTransportItems.pipeItemWood);var pulse=pulsar(tile);tile.replacePluggable(Direction.NORTH,new PluggableLightSensor(BCSiliconPlugs.lightSensor,tile,Direction.NORTH));tile.replacePluggable(Direction.SOUTH,new PluggableTimer(BCSiliconPlugs.timer,tile,Direction.SOUTH));c.getWorld().breakBlock(tile.getPos(),true);
        var drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->true);
        for(var item:List.of(BCSiliconItems.plugPulsar,BCSiliconItems.plugLightSensor,BCSiliconItems.plugTimer))c.assertTrue(drops.stream().filter(e->e.getStack().isOf(item)).mapToInt(e->e.getStack().getCount()).sum()==1,"Lost/duplicated attachment on pipe break");
        pulse.addSinglePulse();tick(pulse,20);c.assertTrue(!pulse.isAttached(),"Removed pipe retained active attachment");c.complete();
    }
}
