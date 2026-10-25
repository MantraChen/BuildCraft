/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.ArrayList;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.statements.*;
import buildcraft.api.transport.pipe.*;
import buildcraft.lib.net.*;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerGate;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.*;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.behaviour.*;
import buildcraft.transport.pipe.flow.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.*;

public final class PipeGateActionGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3,3,3);

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 420)
    public void gatePaintAndDirectionRouteRealItemsAcrossReload(TestContext context) {
        var lapis = place(context,POS,BCTransportItems.pipeItemLapis);
        var daizuli = place(context,POS.east(),BCTransportItems.pipeItemDaizuli);
        context.setBlockState(POS.west(), Blocks.BARREL);
        context.setBlockState(POS.east(2), Blocks.BARREL);
        context.setBlockState(POS.east().north(), Blocks.BARREL);
        lapis.tick(); daizuli.tick();
        var player = context.createMockCreativeServerPlayerInWorld(); player.refreshPositionAndAngles(lapis.getPos(),0,0);
        var lapisGate = gate(lapis,EnumGateModifier.NO_MODIFIER);
        var routeGate = gate(daizuli,EnumGateModifier.NO_MODIFIER);
        var raw = new ArrayList<IActionInternal>();
        daizuli.fireEvent(new PipeEventStatement.AddActionInternal(daizuli, raw));
        context.assertTrue(raw.size()==22 && new java.util.HashSet<>(raw).size()==22, "Inherited direction/colour providers ran twice or lost an action");
        var paint = open(player,lapisGate);
        choose(context,paint,0,false,"buildcraftcore:trigger.true"); choose(context,paint,0,true,"buildcraft:pipe.color.blue");
        player.closeHandledScreen();
        var route = open(player,routeGate);
        choose(context,route,0,false,"buildcraftcore:trigger.true"); choose(context,route,0,true,"buildcraft:pipe.color.blue");
        choose(context,route,1,false,"buildcraftcore:trigger.true"); choose(context,route,1,true,"buildcraft:pipe.dir.east");
        player.closeHandledScreen(); lapis.tick(); daizuli.tick();
        insert(context,lapis,3);
        context.waitAndRun(180, () -> {
            context.assertTrue(count(context,POS.east(2))==3 && count(context,POS.east().north())==0, "Gate-painted blue items missed selected east output");
            var copy=BCTransportBlocks.pipeHolderTile.instantiate(daizuli.getPos(),daizuli.getCachedState()); copy.readNbt(daizuli.createNbt());
            context.getWorld().removeBlockEntity(daizuli.getPos()); context.getWorld().addBlockEntity(copy); copy.tick();
            context.assertTrue(copy.getPipe().behaviour.getTextureIndex(Direction.EAST)==DyeColor.BLUE.getId(),"Reload lost gate-selected filter colour/direction");
            var change = open(player,lapisGate); choose(context,change,0,true,"buildcraft:pipe.color.red"); player.closeHandledScreen(); lapis.tick();
            insert(context,lapis,5);
        });
        context.waitAndRun(360, () -> {
            context.assertTrue(count(context,POS.east(2))==3 && count(context,POS.east().north())==5, "Changed gate paint did not reroute items after reload");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gateDirectionChoicesFollowConnectionsAndRejectRemovedDestinations(TestContext context) {
        var iron = place(context,POS,BCTransportItems.pipeItemIron);
        context.setBlockState(POS.east(),Blocks.BARREL); context.setBlockState(POS.west(),Blocks.BARREL);
        iron.tick();
        var gate=gate(iron,EnumGateModifier.NO_MODIFIER);
        var player=context.createMockCreativeServerPlayerInWorld(); player.refreshPositionAndAngles(iron.getPos(),0,0);
        var screen=open(player,gate);
        context.assertTrue(screen.actions.stream().filter(a->a.delegate instanceof buildcraft.transport.statements.ActionPipeDirection).count()==2,"Iron gate offered disconnected directions");
        choose(context,screen,0,false,"buildcraftcore:trigger.true"); choose(context,screen,0,true,"buildcraft:pipe.dir.east"); iron.tick();
        context.assertTrue(iron.getPipe().behaviour.getTextureIndex(Direction.EAST)==0,"Gate did not set iron output east");
        context.setBlockState(POS.east(),Blocks.AIR); iron.getPipe().markForUpdate(); iron.tick();
        int oldEast=-1; for(int i=0;i<screen.actions.size();i++) if(screen.actions.get(i).getUniqueTag().equals("buildcraft:pipe.dir.east"))oldEast=i;
        context.assertTrue(oldEast>=0 && !screen.onButtonClick(player,ContainerGate.selectionId(0,true,oldEast)),"Stale screen offered removed destination");
        context.assertTrue(iron.getPipe().behaviour.getTextureIndex(Direction.WEST)==0,"Disconnected output did not recover to remaining connection");
        var raw=new ArrayList<IActionInternal>(); iron.fireEvent(new PipeEventStatement.AddActionInternal(iron,raw));
        context.assertTrue(raw.size()==1,"Direction provider did not refresh after neighbour removal");
        player.closeHandledScreen(); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gateLimitActionsReconfigureAllPowerPipeVariantsAndPersist(TestContext context) throws java.io.IOException {
        var player=context.createMockCreativeServerPlayerInWorld();
        var definitions=new PipeDefinition[]{BCTransportPipes.ironPower,BCTransportPipes.diamondPower,BCTransportPipes.ironRf,BCTransportPipes.diamondRf};
        for(var def:definitions) {
            context.setBlockState(POS,Blocks.AIR);
            var pipe=place(context,POS,(ItemPipeHolder)buildcraft.transport.pipe.PipeRegistry.INSTANCE.getItemForPipe(def));
            player.refreshPositionAndAngles(pipe.getPos(),0,0); var gate=gate(pipe,EnumGateModifier.NO_MODIFIER); var screen=open(player,gate);
            var options=screen.actions.stream().filter(a->a.delegate instanceof buildcraft.transport.statements.ActionPowerLimit).toList();
            context.assertTrue(options.size()==7,"Gate limit choices missing for "+def.identifier);
            choose(context,screen,0,false,"buildcraftcore:trigger.true");
            for(int shift=0;shift<=6;shift++) {
                final int targetShift=shift;
                var action=options.stream().filter(a->((buildcraft.transport.statements.ActionPowerLimit)a.delegate).limitShift==targetShift).findFirst().orElseThrow();
                choose(context,screen,0,true,action.getUniqueTag()); pipe.tick();
                context.assertTrue(pipe.getPipe().behaviour.writeToNbt().getInt("limitShift")==shift,"Gate did not apply requested limit");
                if(pipe.getPipe().flow instanceof PipeFlowPower flow) {
                    var configure=new PipeEventPower.Configure(pipe,flow); configure.setMaxPower(256*MjAPI.MJ); pipe.fireEvent(configure);
                    context.assertTrue(configure.isTransferDisabled()==(shift==6) && (shift==6 || configure.getMaxPower()==(256*MjAPI.MJ>>shift)),"MJ limiter event ignored setting");
                } else {
                    var configure=new PipeEventRedstoneFlux.Configure(pipe,(PipeFlowRedstoneFlux)pipe.getPipe().flow); configure.setMaxPower(2560); pipe.fireEvent(configure);
                    context.assertTrue(configure.isTransferDisabled()==(shift==6) && (shift==6 || configure.getMaxPower()==(2560>>shift)),"RF limiter event ignored setting");
                }
                var packet=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());
                try {
                    pipe.getPipe().behaviour.writePayload(packet,net.fabricmc.api.EnvType.SERVER);
                    var remote=new PipeBehaviourLimiter(pipe.getPipe()); remote.readPayload(packet,net.fabricmc.api.EnvType.CLIENT,null);
                    context.assertTrue(remote.writeToNbt().getInt("limitShift")==shift && !packet.isReadable(),"Limit packet lost state");
                } finally { packet.release(); }
            }
            var foreign = def==BCTransportPipes.ironPower ? BCTransportStatements.ACTION_DIAMOND_POWER_LIMIT[0] : BCTransportStatements.ACTION_IRON_POWER_LIMIT[0];
            pipe.fireEvent(new PipeEventActionActivate(pipe,foreign,new IStatementParameter[0],EnumPipePart.CENTER));
            context.assertTrue(pipe.getPipe().behaviour.writeToNbt().getInt("limitShift")==6,"A foreign gate limit changed this pipe type");
            var copy=BCTransportBlocks.pipeHolderTile.instantiate(pipe.getPos(),pipe.getCachedState()); copy.readNbt(pipe.createNbt());
            context.assertTrue(copy.getPipe().behaviour.writeToNbt().getInt("limitShift")==6,"Saved limit lost after unattached reconstruction");
            player.closeHandledScreen();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gateItemConditionCopiesSampleAndTracksActualTraffic(TestContext context) {
        var tile=place(context,POS,BCTransportItems.pipeItemStone);
        context.setBlockState(POS.west(),Blocks.BARREL);
        context.setBlockState(POS.east(),Blocks.BARREL); tile.tick();
        var gate=gate(tile,EnumGateModifier.LAPIS);
        var player=context.createMockCreativeServerPlayerInWorld(); player.refreshPositionAndAngles(tile.getPos(),0,0);
        var screen=open(player,gate);
        choose(context,screen,0,false,"buildcraft:pipe_contains_items"); choose(context,screen,0,true,"buildcraft:redstone.output");
        var sample=new ItemStack(Items.DIAMOND,7); sample.setCustomName(net.minecraft.text.Text.literal("Gate sample"));
        screen.setCursorStack(sample);
        context.assertTrue(screen.onButtonClick(player,ContainerGate.parameterId(0,false,0,0,false)),"Gate sample click rejected");
        context.assertTrue(screen.getCursorStack().getCount()==7 && gate.logic.statements[0].trigger.get(0).getItemStack().getCount()==1,"Condition sample consumed real items");
        tile.tick(); context.assertTrue(!gate.logic.isOn,"Empty pipe activated items condition");
        try(var transaction=Transaction.openOuter()) {
            context.assertTrue(ItemStorage.SIDED.find(context.getWorld(),tile.getPos(),Direction.WEST).insert(ItemVariant.of(sample),2,transaction)==2,"Pipe rejected sampled input"); transaction.commit();
        }
        tile.tick(); context.assertTrue(gate.logic.isOn && tile.getRedstoneOutput(Direction.SOUTH)==15,"Matching traffic did not activate gate");
        screen.setCursorStack(new ItemStack(Items.EMERALD,4)); screen.onButtonClick(player,ContainerGate.parameterId(0,false,0,0,false));
        tile.tick(); context.assertTrue(!gate.logic.isOn && tile.getRedstoneOutput(Direction.SOUTH)==0,"Mismatched traffic still activated gate");
        screen.setCursorStack(ItemStack.EMPTY); screen.onButtonClick(player,ContainerGate.parameterId(0,false,0,0,false));
        tile.tick(); context.assertTrue(gate.logic.isOn,"Cleared sample should match any item traffic");
        player.closeHandledScreen(); context.complete();
    }

    private static TilePipeHolder place(TestContext c,BlockPos pos,ItemPipeHolder item) {
        c.setBlockState(pos,BCTransportBlocks.pipeHolder); var tile=(TilePipeHolder)c.getBlockEntity(pos); tile.onPlacedBy(null,new ItemStack(item)); return tile;
    }
    private static PluggableGate gate(TilePipeHolder tile,EnumGateModifier modifier) {
        var gate=new PluggableGate(BCSiliconPlugs.gate,tile,Direction.UP,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.NETHER_BRICK,modifier)); tile.replacePluggable(Direction.UP,gate); return gate;
    }
    private static ContainerGate open(PlayerEntity player,PluggableGate gate) { BCSiliconScreens.open(player,gate); return (ContainerGate)player.currentScreenHandler; }
    private static void choose(TestContext c,ContainerGate screen,int row,boolean action,String id) {
        var choices=screen.choices(action);
        for(int i=0;i<choices.size();i++) if(choices.get(i).getUniqueTag().equals(id)) {
            final int index=i;
            var message=MessageContainer.of(screen.syncId,ContainerGate.NET_EDIT,b->b.writeVarInt(ContainerGate.selectionId(row,action,index)));
            var payload=message.getPayload();
            try { screen.readMessage(message.getMessageId(),payload,false,null); }
            catch(java.io.IOException e){throw new IllegalStateException(e);} finally{payload.release();}
            c.assertTrue(screen.statement(row,action).get()!=null && screen.statement(row,action).get().getUniqueTag().equals(id),"Rejected "+id); return;
        }
        throw new IllegalStateException("Missing statement "+id);
    }
    private static void insert(TestContext c,TilePipeHolder tile,int count) {
        try(var tx=Transaction.openOuter()) { c.assertTrue(ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.WEST).insert(ItemVariant.of(Items.DIAMOND),count,tx)==count,"Pipe rejected input"); tx.commit(); }
    }
    private static int count(TestContext c,BlockPos pos) { var inventory=(Inventory)c.getBlockEntity(pos); int result=0; for(int i=0;i<inventory.size();i++)result+=inventory.getStack(i).getCount(); return result; }
}
