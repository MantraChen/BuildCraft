/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.api.tiles.IControllable.Mode;
import buildcraft.builders.*;
import buildcraft.builders.container.*;
import buildcraft.builders.tile.TileFiller;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.mj.TransactionalMjReceiver;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class FillerGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,4,2);
    private static final Map<ServerWorld,Set<BlockPos>> USE_DENIED=new WeakHashMap<>(), BREAK_DENIED=new WeakHashMap<>();
    static {
        UseBlockCallback.EVENT.register((player,world,hand,hit)->USE_DENIED.getOrDefault(world,Set.of()).contains(hit.getBlockPos())?ActionResult.FAIL:ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,tile)->!BREAK_DENIED.getOrDefault(world,Set.of()).contains(pos));
    }
    private static TileFiller place(TestContext c) {
        c.setBlockState(POS,Blocks.AIR);
        c.setBlockState(POS,BCBuildersBlocks.filler.getDefaultState().with(BCBuildersBlocks.filler.getFacingProperty(),Direction.NORTH));
        var tile=(TileFiller)c.getBlockEntity(POS);tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.filler));return tile;
    }
    private static TileFiller small(TestContext c,int size) {
        for(var p:BlockPos.iterate(POS.south(),POS.add(size-1,size-1,size)))c.setBlockState(p,Blocks.AIR);
        var tile=place(c);tile.box.reset();tile.box.setMin(tile.getPos().south());tile.box.setMax(tile.getPos().add(size-1,size-1,size));return tile;
    }
    private static void pattern(TileFiller tile,buildcraft.api.filler.IFillerPattern pattern){tile.patternStatement.set(pattern);tile.onStatementChange();}
    private static void ticks(TileFiller tile,int n){for(int i=0;i<n;i++)tile.tick();}
    private static IMjReceiver input(TileFiller tile){return (IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);}
    private static void power(TileFiller tile){input(tile).receivePower(16_000*MjAPI.MJ,false);}
    private static int count(TileFiller tile,Item item){int total=0;for(int i=0;i<27;i++)if(tile.invResources.getStackInSlot(i).isOf(item))total+=tile.invResources.getStackInSlot(i).getCount();return total;}
    private static TileFiller reload(TileFiller tile){var world=tile.getWorld();var pos=tile.getPos();var state=tile.getCachedState();var nbt=tile.createNbtWithIdentifyingData();world.removeBlockEntity(pos);var fresh=(TileFiller)BlockEntity.createFromNbt(pos,state,nbt);world.addBlockEntity(fresh);fresh.onLoad();return fresh;}
    private static long paid(TileFiller tile){long sum=0;for(var tag:tile.builder.serializeNBT().getList("work",10))sum+=((NbtCompound)tag).getLong("power");return sum;}

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void receiverBoundsSimulationNestedRollbackAndStaleHandles(TestContext c){
        var tile=small(c,1);var r=input(tile);var txr=(TransactionalMjReceiver)r;
        c.assertTrue(r.receivePower(20_000*MjAPI.MJ,true)==4_000*MjAPI.MJ && tile.getBattery().getStored()==0,"Simulation changed buffer");
        try(var outer=Transaction.openOuter()){txr.receivePower(10_000*MjAPI.MJ,outer);try(var nested=outer.openNested()){txr.receivePower(10_000*MjAPI.MJ,nested);nested.commit();}c.assertTrue(tile.getBattery().getStored()==16_000*MjAPI.MJ,"MJ input exceeded capacity");}
        c.assertTrue(tile.getBattery().getStored()==0,"Aborted transfer leaked MJ");
        try(var tx=Transaction.openOuter()){txr.receivePower(90*MjAPI.MJ,tx);tx.commit();}
        for(var side:Direction.values())c.assertTrue(MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),side)==r,"Missing native MJ side");
        c.setBlockState(POS,Blocks.AIR);c.assertTrue(r.receivePower(MjAPI.MJ,false)==MjAPI.MJ && r.getPowerRequested()==0,"Stale receiver accepted MJ");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void itemTransferFiltersBlocksAndAbortsWithoutConstruction(TestContext c){
        var tile=small(c,1);pattern(tile,BCBuildersStatements.PATTERN_FILL);power(tile);
        for(var side:Direction.values()){
            var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),side);c.assertTrue(storage!=null,"Missing native item input");
            try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(ItemVariant.of(Items.DIAMOND),1,tx)==0,"Accepted non-block material");c.assertTrue(storage.insert(ItemVariant.of(Items.STONE),1,tx)==1,"Rejected native block input");tile.tick();c.assertTrue(c.getWorld().isAir(tile.box.min()),"Constructed during uncommitted item transfer");}
        }
        c.assertTrue(count(tile,Items.STONE)==0 && tile.getBattery().getStored()==16_000*MjAPI.MJ,"Aborted input changed materials or MJ");
        var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.UP);
        try(var tx=Transaction.openOuter()){storage.insert(ItemVariant.of(Items.STONE),2,tx);tx.commit();}
        ticks(tile,4);c.assertTrue(c.getWorld().getBlockState(tile.box.min()).isOf(Blocks.STONE) && count(tile,Items.STONE)==1,"Committed input did not place exactly one block");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fillAndClearConsumeExactMaterialsAndNativeLoot(TestContext c){
        var tile=small(c,2);pattern(tile,BCBuildersStatements.PATTERN_FILL);tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE,9));power(tile);ticks(tile,25);
        c.assertTrue(tile.isFinished() && count(tile,Items.STONE)==1,"Fill lost/duplicated material or failed to finish");
        for(var p:BlockPos.iterate(tile.box.min(),tile.box.max()))c.assertTrue(c.getWorld().getBlockState(p).isOf(Blocks.STONE),"Fill skipped a position");
        long before=tile.getBattery().getStored();pattern(tile,BCBuildersStatements.PATTERN_CLEAR);ticks(tile,25);
        c.assertTrue(tile.isFinished() && count(tile,Items.COBBLESTONE)==8 && before-tile.getBattery().getStored()==8*80*MjAPI.MJ,"Clear used wrong hardness cost or native loot");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void partialHardnessWorkReloadAndContinuousGateSignalFinish(TestContext c){
        var tile=small(c,1);var target=tile.box.min();c.getWorld().setBlockState(target,Blocks.OBSIDIAN.getDefaultState());pattern(tile,BCBuildersStatements.PATTERN_CLEAR);power(tile);tile.tick();
        c.assertTrue(paid(tile)==256*MjAPI.MJ,"Expected partially paid hard block");long balance=tile.getBattery().getStored()+paid(tile);var old=input(tile);tile=reload(tile);
        c.assertTrue(tile.getBattery().getStored()+paid(tile)==balance && paid(tile)==256*MjAPI.MJ && old.getPowerRequested()==0,"Reload discarded paid native hardness or left stale receiver");
        for(int i=0;i<12;i++){tile.setPattern(BCBuildersStatements.PATTERN_CLEAR,new buildcraft.api.statements.IStatementParameter[0]);tile.tick();}
        c.assertTrue(c.getWorld().isAir(target) && count(tile,Items.OBSIDIAN)==1 && tile.getBattery().getStored()==16_000*MjAPI.MJ-1632*MjAPI.MJ,"Continuous gate reset construction or charged twice");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cancelledPaidWorkIsRefundedAboveInputCapacityAndSurvivesReload(TestContext c){
        var tile=small(c,1);c.getWorld().setBlockState(tile.box.min(),Blocks.OBSIDIAN.getDefaultState());pattern(tile,BCBuildersStatements.PATTERN_CLEAR);power(tile);tile.tick();power(tile);
        pattern(tile,BCBuildersStatements.PATTERN_NONE);long balance=16_256*MjAPI.MJ;c.assertTrue(tile.getBattery().getStored()==balance,"Cancelled paid work was capped/lost");
        tile=reload(tile);ticks(tile,20);c.assertTrue(tile.getBattery().getStored()==balance && input(tile).receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Refund dissipated or allowed overfilling");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingMaterialsResumeAndOffOnLoopRespectExternalEdits(TestContext c){
        var tile=small(c,1);pattern(tile,BCBuildersStatements.PATTERN_FILL);power(tile);ticks(tile,5);c.assertTrue(!tile.isFinished() && tile.getBattery().getStored()==16_000*MjAPI.MJ,"Missing resource spent energy or completed");
        tile.setControlMode(Mode.OFF);tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE,3));ticks(tile,3);c.assertTrue(c.getWorld().isAir(tile.box.min()),"OFF still placed");
        tile.setControlMode(Mode.ON);ticks(tile,3);c.assertTrue(tile.isFinished(),"ON did not resume");c.getWorld().setBlockState(tile.box.min(),Blocks.AIR.getDefaultState());ticks(tile,3);c.assertTrue(c.getWorld().isAir(tile.box.min()),"Completed ON restarted by itself");
        tile.setControlMode(Mode.LOOP);ticks(tile,3);c.assertTrue(c.getWorld().getBlockState(tile.box.min()).isOf(Blocks.STONE) && !tile.isFinished() && count(tile,Items.STONE)==1,"LOOP did not repair external edits");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protectedPlacementRefundsAndResumesWithoutDuplication(TestContext c){
        var tile=small(c,1);var target=tile.box.min();pattern(tile,BCBuildersStatements.PATTERN_FILL);tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE));power(tile);
        USE_DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(target);
        try{ticks(tile,4);c.assertTrue(c.getWorld().isAir(target) && count(tile,Items.STONE)==1 && tile.getBattery().getStored()==16_000*MjAPI.MJ,"Protected place consumed resources");}finally{USE_DENIED.get(c.getWorld()).remove(target);}
        ticks(tile,3);c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.STONE) && count(tile,Items.STONE)==0 && tile.getBattery().getStored()==15_990*MjAPI.MJ,"Retry charged/lost placement");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protectedBreakRefundsAndResumesWithCorrectOwner(TestContext c){
        var tile=small(c,1);var target=tile.box.min();pattern(tile,BCBuildersStatements.PATTERN_CLEAR);c.getWorld().setBlockState(target,Blocks.STONE.getDefaultState());power(tile);
        BREAK_DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(target);
        try{ticks(tile,4);c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.STONE) && count(tile,Items.COBBLESTONE)==0 && tile.getBattery().getStored()==16_000*MjAPI.MJ,"Denied break lost MJ or loot");}finally{BREAK_DENIED.get(c.getWorld()).remove(target);}
        ticks(tile,3);c.assertTrue(c.getWorld().isAir(target) && count(tile,Items.COBBLESTONE)==1,"Break did not resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unbreakableFluidsAndExcavationDisabledDoNotDestroyWorld(TestContext c)throws Exception{
        var tile=small(c,1);var target=tile.box.min();pattern(tile,BCBuildersStatements.PATTERN_CLEAR);power(tile);
        for(var block:List.of(Blocks.BEDROCK,Blocks.WATER)){c.getWorld().setBlockState(target,block.getDefaultState());ticks(tile,3);c.assertTrue(!tile.isFinished() && c.getWorld().getBlockState(target).isOf(block) && tile.getBattery().getStored()==16_000*MjAPI.MJ,"Blocked target destroyed/charged");}
        c.getWorld().setBlockState(target,Blocks.STONE.getDefaultState());var b=PacketBufferBC.write(buf->buf.writeBoolean(false));try{tile.readPayload(TileFiller.NET_CAN_EXCAVATE,b,TileBC_Neptune.NetSide.SERVER,null);}finally{b.release();}ticks(tile,3);
        c.assertTrue(tile.isFinished() && c.getWorld().getBlockState(target).isOf(Blocks.STONE),"Disabled excavation removed block");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void everyRegisteredPatternBuildsItsVolumeAndInversionComplements(TestContext c){
        c.assertTrue(BCBuildersStatements.PATTERNS.length==19,"Unexpected registry fixture");
        for(var p:BCBuildersStatements.PATTERNS){
            var tile=small(c,3);pattern(tile,p);var info=tile.getTemplateBuildingInfo();
            if(p==BCBuildersStatements.PATTERN_NONE){c.assertTrue(info==null,"NONE generated construction");continue;}
            var bits=(BitSet)info.getSnapshot().data.clone();int n=bits.cardinality();
            if(p==BCBuildersStatements.PATTERN_FILL)c.assertTrue(n==27,"Solid fill has holes");
            if(p==BCBuildersStatements.PATTERN_CLEAR)c.assertTrue(n==0,"Clear has filled cells");
            if(p==BCBuildersStatements.PATTERN_BOX)c.assertTrue(n==26,"Box is not a hollow shell");
            if(p==BCBuildersStatements.PATTERN_FRAME)c.assertTrue(n==20,"Frame is not twelve edges");
            tile.invResources.setStackInSlot(0,new ItemStack(Items.STONE,64));power(tile);ticks(tile,80);
            c.assertTrue(tile.isFinished() && count(tile,Items.STONE)==64-n,"Pattern failed native construction: "+p.getUniqueTag());
            for(int i=0;i<27;i++)c.assertTrue(!c.getWorld().isAir(info.toWorld(info.getSnapshot().indexToPos(i)))==bits.get(i),"Pattern world mismatch: "+p.getUniqueTag());
            tile.inverted=true;tile.onStatementChange();var inverted=tile.getTemplateBuildingInfo().getSnapshot().data;
            bits.xor(inverted);c.assertTrue(bits.cardinality()==27,"Inversion did not complement entire volume: "+p.getUniqueTag());
        }c.complete();
    }
    private static TileMarkerVolume markers(TestContext c){
        var near=POS.south();for(var p:List.of(near,near.south(2),near.east(2))){c.setBlockState(p.down(),Blocks.STONE);c.setBlockState(p,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));((TileMarkerVolume)c.getBlockEntity(p)).onLoad();}
        var marker=(TileMarkerVolume)c.getBlockEntity(near);marker.onManualConnectionAttempt(c.createMockCreativeServerPlayerInWorld());c.assertTrue(marker.getCurrentConnection()!=null,"Markers did not connect");return marker;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMarkersAreAdoptedAndConsumedWithOwnerPersisted(TestContext c){
        var marker=markers(c);var min=marker.min();var max=marker.max();var tile=place(c);
        c.assertTrue(tile.hasBox() && tile.box.min().equals(min) && tile.box.max().equals(max) && c.getWorld().isAir(min),"Filler did not acquire native markers");
        var owner=tile.getOwner();tile=reload(tile);c.assertTrue(tile.box.min().equals(min) && tile.getOwner().equals(owner),"Selection/owner lost on reload");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protectedMarkerLeavesWholeConnectionAndMachineUnconfigured(TestContext c){
        var marker=markers(c);var protectedPos=marker.getPos().south(2);BREAK_DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(protectedPos);
        try{var tile=place(c);c.assertTrue(!tile.hasBox() && marker.getCurrentConnection()!=null && !c.getWorld().isAir(protectedPos) && c.getWorld().getBlockEntity(marker.getPos())==marker,"Denied marker was consumed or adopted");}
        finally{BREAK_DENIED.get(c.getWorld()).remove(protectedPos);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void disconnectedMarkerDoesNotBecomeOneBlockWorkArea(TestContext c){
        var near=POS.south();c.setBlockState(near.down(),Blocks.STONE);c.setBlockState(near,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));((TileMarkerVolume)c.getBlockEntity(near)).onLoad();
        var tile=place(c);c.assertTrue(!tile.hasBox() && c.getWorld().getBlockEntity(c.getAbsolutePos(near)) instanceof TileMarkerVolume,"Unconnected marker was accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedStatusAndWorkPacketsDoNotPartiallyReplaceState(TestContext c)throws Exception{
        var tile=small(c,1);pattern(tile,BCBuildersStatements.PATTERN_FILL);var before=tile.patternStatement.writeToNbt();
        for(int kind=0;kind<2;kind++){
            var b=PacketBufferBC.write(buf->tile.writePayload(TileBC_Neptune.NET_RENDER_DATA,buf,TileBC_Neptune.NetSide.SERVER));
            if(kind==0)b.writerIndex(b.writerIndex()-1);else b.writeByte(1);boolean rejected=false;
            try{tile.readPayload(TileBC_Neptune.NET_RENDER_DATA,b,TileBC_Neptune.NetSide.CLIENT,null);}catch(IndexOutOfBoundsException|java.io.IOException e){rejected=true;}finally{b.release();}
            c.assertTrue(rejected && tile.patternStatement.writeToNbt().equals(before),"Malformed status partially applied");
        }
        var b=PacketBufferBC.write(buf->{buf.writeVarInt(1);buf.writeBlockPos(tile.box.min());buf.writeBoolean(false);buf.writeFloat(Float.NaN);buf.writeItemStack(new ItemStack(Items.STONE));});boolean rejected=false;
        try{tile.readPayload(TileFiller.NET_WORK,b,TileBC_Neptune.NetSide.CLIENT,null);}catch(java.io.IOException e){rejected=true;}finally{b.release();}
        c.assertTrue(rejected && tile.builder.clientWork().isEmpty(),"Invalid work progress accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void workPacketsDescribePaidTargetsAndClearOnPause(TestContext c)throws Exception{
        var tile=small(c,1);c.getWorld().setBlockState(tile.box.min(),Blocks.OBSIDIAN.getDefaultState());pattern(tile,BCBuildersStatements.PATTERN_CLEAR);power(tile);tile.tick();
        var b=PacketBufferBC.write(buf->tile.writePayload(TileFiller.NET_WORK,buf,TileBC_Neptune.NetSide.SERVER));try{tile.readPayload(TileFiller.NET_WORK,b,TileBC_Neptune.NetSide.CLIENT,null);}finally{b.release();}
        var work=tile.builder.clientWork();c.assertTrue(work.size()==1 && work.get(0).breaking() && Math.abs(work.get(0).progress()-256f/1632)<.001,"Work animation not tied to paid work");
        tile.setControlMode(Mode.OFF);tile.tick();b=PacketBufferBC.write(buf->tile.writePayload(TileFiller.NET_WORK,buf,TileBC_Neptune.NetSide.SERVER));try{tile.readPayload(TileFiller.NET_WORK,b,TileBC_Neptune.NetSide.CLIENT,null);}finally{b.release();}
        c.assertTrue(tile.builder.clientWork().isEmpty(),"Paused work retained active animation");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void guiOpenPacketIncludesRenderStateOwnerAndDeltaTail(TestContext c)throws Exception{
        var tile=small(c,2);pattern(tile,BCBuildersStatements.PATTERN_BOX);var owner=tile.getOwner();
        var b=PacketBufferBC.write(buf->tile.writePayload(TileBC_Neptune.NET_GUI_DATA,buf,TileBC_Neptune.NetSide.SERVER));
        try{tile.readPayload(TileBC_Neptune.NET_GUI_DATA,b,TileBC_Neptune.NetSide.CLIENT,null);c.assertTrue(!b.isReadable() && tile.getOwner().equals(owner) && tile.patternStatement.get()==BCBuildersStatements.PATTERN_BOX && tile.box.size().equals(new BlockPos(2,2,2)),"GUI envelope lost render state or owner");}finally{b.release();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void survivalRecipeUsesOriginalMaterialsAndUnlockExists(TestContext c){
        var player=c.createMockCreativeServerPlayerInWorld();var handler=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        Item[] items={Items.BLACK_DYE,BCCoreBlocks.markerVolume.asItem(),Items.BLACK_DYE,Items.YELLOW_DYE,Items.CRAFTING_TABLE,Items.YELLOW_DYE,buildcraft.core.BCCoreItems.gearGold,Items.CHEST,buildcraft.core.BCCoreItems.gearGold};
        for(int i=0;i<9;i++)handler.getSlot(i+1).setStack(new ItemStack(items[i]));
        handler.onContentChanged(handler.getSlot(1).inventory);
        c.assertTrue(handler.getSlot(0).getStack().isOf(BCBuildersBlocks.filler.asItem()),"Native filler crafting recipe failed");
        handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        for(int i=1;i<=9;i++)c.assertTrue(handler.getSlot(i).getStack().isEmpty(),"Crafting retained ingredient");
        c.assertTrue(c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftbuilders:recipes/filler"))!=null,"Filler recipe unlock missing");c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeContainerShiftTransferGateLockAndStaleScreen(TestContext c)throws Exception{
        var tile=small(c,1);var player=c.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ()-2,0,0);
        var handler=new ContainerFiller(player,1,tile);player.currentScreenHandler=handler;
        player.getInventory().setStack(9,new ItemStack(Items.STONE,7));handler.quickMove(player,0);c.assertTrue(count(tile,Items.STONE)==7 && player.getInventory().getStack(9).isEmpty(),"Native shift transfer failed");
        player.getInventory().setStack(9,new ItemStack(Items.DIAMOND,2));handler.quickMove(player,0);c.assertTrue(player.getInventory().getStack(9).getCount()==2,"Shift accepted non-blocks");
        tile.setPattern(BCBuildersStatements.PATTERN_CLEAR,null);
        var incoming=new buildcraft.lib.statement.FullStatement<buildcraft.api.filler.IFillerPattern>(buildcraft.builders.filler.FillerType.INSTANCE,4,null);incoming.set(BCBuildersStatements.PATTERN_FILL);
        var b=PacketBufferBC.write(buf->{incoming.writeToBuffer(buf);buf.writeBoolean(true);});try{handler.readMessage(buildcraft.lib.gui.ContainerBC_Neptune.NET_DATA,b,false,null);}finally{b.release();}
        c.assertTrue(tile.patternStatement.get()==BCBuildersStatements.PATTERN_CLEAR && !tile.inverted,"GUI overwrote gate lock");ticks(tile,4);
        b=PacketBufferBC.write(buf->{incoming.writeToBuffer(buf);buf.writeBoolean(true);});try{handler.readMessage(buildcraft.lib.gui.ContainerBC_Neptune.NET_DATA,b,false,null);}finally{b.release();}
        c.assertTrue(tile.patternStatement.get()==BCBuildersStatements.PATTERN_FILL && tile.inverted,"Unlocked GUI failed to configure");
        var fresh=reload(tile);b=PacketBufferBC.write(buf->{incoming.writeToBuffer(buf);buf.writeBoolean(false);});try{handler.readMessage(buildcraft.lib.gui.ContainerBC_Neptune.NET_DATA,b,false,null);}finally{b.release();}
        c.assertTrue(!handler.canUse(player) && fresh.inverted,"Stale handler changed replacement filler");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void stairDirectionsAndVerticalParametersSurviveReload(TestContext c){
        var tile=small(c,3);
        for(var dir:buildcraft.builders.snapshot.pattern.parameter.PatternParameterXZDir.values())for(var vertical:buildcraft.builders.snapshot.pattern.parameter.PatternParameterYDir.values()){
            tile.setPattern(BCBuildersStatements.PATTERN_STAIRS,new buildcraft.api.statements.IStatementParameter[]{vertical,dir});var info=tile.getTemplateBuildingInfo();
            c.assertTrue(info.getSnapshot().data.cardinality()==18,"Stair volume incorrect");var expected=(BitSet)info.getSnapshot().data.clone();tile=reload(tile);
            c.assertTrue(tile.patternStatement.get(0)==vertical && tile.patternStatement.get(1)==dir && tile.getTemplateBuildingInfo().getSnapshot().data.equals(expected),"Parameterized plan changed after reload");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void volumePlannerReloadLocksAndRemovalCancelPaidWork(TestContext c){
        var data=buildcraft.core.marker.volume.WorldSavedDataVolumeBoxes.get(c.getWorld());var volume=new buildcraft.core.marker.volume.VolumeBox(c.getWorld(),c.getAbsolutePos(POS.south()));
        var planner=new buildcraft.builders.addon.AddonFillerPlanner();planner.volumeBox=volume;var slot=buildcraft.core.marker.volume.EnumAddonSlot.WEST_DOWN_NORTH;volume.addons.put(slot,planner);data.volumeBoxes.add(volume);planner.patternStatement.set(BCBuildersStatements.PATTERN_CLEAR);planner.updateBuildingInfo();
        try{
            var tile=place(c);c.assertTrue(tile.addon==planner && !tile.markerBox && volume.locks.size()==1,"Filler failed to link/lock volume planner");
            c.getWorld().setBlockState(volume.box.min(),Blocks.OBSIDIAN.getDefaultState());power(tile);tile.tick();tile=reload(tile);
            c.assertTrue(tile.addon==planner && paid(tile)==256*MjAPI.MJ,"Linked planner reload lost paid progress");
            volume.addons.remove(slot);tile.tick();c.assertTrue(tile.addon==null && tile.getTemplateBuildingInfo()==null && paid(tile)==0 && tile.getBattery().getStored()==16_000*MjAPI.MJ,"Removed planner continued stale construction or lost refund");
            c.setBlockState(POS,Blocks.AIR);data.tick();c.assertTrue(volume.locks.isEmpty(),"Broken filler left volume locked");
        }finally{data.volumeBoxes.remove(volume);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=180)
    public void nativeItemPipeFeedsMaterialsIntoWorkingFiller(TestContext c){
        var tile=small(c,1);pattern(tile,BCBuildersStatements.PATTERN_FILL);power(tile);var pos=tile.getPos().west();c.getWorld().setBlockState(pos,buildcraft.transport.BCTransportBlocks.pipeHolder.getDefaultState());
        var pipe=(buildcraft.transport.tile.TilePipeHolder)c.getWorld().getBlockEntity(pos);pipe.onPlacedBy(null,new ItemStack(buildcraft.transport.BCTransportItems.pipeItemStone));pipe.tick();
        ((buildcraft.api.transport.pipe.IFlowItems)pipe.getPipe().flow).insertItemsForce(new ItemStack(Items.STONE,3),Direction.WEST,null,.2);
        c.waitAndRun(100,()->{c.assertTrue(tile.isFinished() && c.getWorld().getBlockState(tile.box.min()).isOf(Blocks.STONE) && count(tile,Items.STONE)==2,"Native item pipe lost or duplicated construction input");c.complete();});
    }
}
