/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.enums.EnumRedstoneChipset;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.statements.*;
import buildcraft.core.*;
import buildcraft.core.statements.StatementParamGateSideOnly;
import buildcraft.lib.statement.*;
import buildcraft.silicon.*;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.item.ItemGateCopier;
import buildcraft.silicon.plug.*;
import buildcraft.silicon.recipe.NativeAssemblyRecipe;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class GateCopierGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static final GateVariant FULL=new GateVariant(EnumGateLogic.AND,EnumGateMaterial.GOLD,EnumGateModifier.DIAMOND);
    private static final Set<BlockPos> DENIED=new HashSet<>();
    static {net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->DENIED.contains(hit.getBlockPos())?ActionResult.FAIL:ActionResult.PASS);}
    private static TilePipeHolder pipe(TestContext c,BlockPos pos){
        c.setBlockState(pos,BCTransportBlocks.pipeHolder);var tile=(TilePipeHolder)c.getBlockEntity(pos);tile.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));return tile;
    }
    private static PluggableGate gate(TilePipeHolder tile,Direction face,GateVariant variant){
        var gate=new PluggableGate(BCSiliconPlugs.gate,tile,face,variant);tile.replacePluggable(face,gate);return gate;
    }
    private static void configure(PluggableGate gate){
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_INVENTORY_CONTAINS,Direction.WEST));
        var sample=new ItemStack(Items.DIAMOND,7);sample.setCustomName(net.minecraft.text.Text.literal("copier sample"));
        gate.logic.statements[0].trigger.set(0,new StatementParameterItemStack(sample));
        gate.logic.statements[0].action.set(ActionWrapper.wrap(BCCoreStatements.ACTION_REDSTONE,null));
        gate.logic.statements[0].action.set(0,StatementParamGateSideOnly.SPECIFIC);
        gate.logic.statements[1].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE,null));
        gate.logic.statements[1].action.set(ActionWrapper.wrap(BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()],null));
        gate.logic.statements[1].action.set(0,buildcraft.transport.statements.ActionParameterSignal.get(DyeColor.BLUE));
        gate.logic.statements[2].trigger.set(TriggerWrapper.wrap(BCTransportStatements.TRIGGER_PIPE_SIGNAL[DyeColor.BLUE.ordinal()*2+1],null));
        gate.logic.statements[2].trigger.set(0,buildcraft.transport.statements.TriggerParameterSignal.get(false,DyeColor.GREEN));
        gate.logic.connections[0]=true;
    }
    private static void aim(ServerPlayerEntity player,PluggableGate gate){
        var eye=Vec3d.ofCenter(gate.holder.getPipePos()).add(Vec3d.of(gate.side.getVector()).multiply(3));var look=Vec3d.of(gate.side.getVector()).multiply(-1);
        player.refreshPositionAndAngles(eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y)));player.setHeadYaw(player.getYaw());
    }
    private static ActionResult click(TestContext c,ServerPlayerEntity player,PluggableGate gate,Hand hand){
        aim(player,gate);return player.interactionManager.interactBlock(player,c.getWorld(),player.getStackInHand(hand),hand,new BlockHitResult(gate.getBoundingBox().getCenter().add(Vec3d.of(gate.holder.getPipePos())),gate.side,gate.holder.getPipePos(),false));
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeCopyUsesBothHandsAndExactAttachmentOnEveryFace(TestContext c){
        var tile=pipe(c,POS);var player=c.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);
        for(var face:Direction.values())for(var hand:Hand.values()){
            var gate=gate(tile,face,FULL);configure(gate);
            var stack=new ItemStack(BCSiliconItems.gateCopier);player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);player.setStackInHand(hand,stack);
            c.assertTrue(click(c,player,gate,hand).isAccepted(),"Native copier click failed");
            c.assertTrue(stack.getCount()==1 && stack.getMaxCount()==1 && ItemGateCopier.getCopiedGateData(stack).equals(gate.logic.writeConfigData()),"Copy lost source configuration "+face+" "+hand);
            c.assertTrue(player.currentScreenHandler==player.playerScreenHandler,"Copy opened gate GUI");tile.replacePluggable(face,null);
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void everyWireParameterRetainsColourAndPolarityThroughNbtAndPackets(TestContext c) throws java.io.IOException {
        var params=new ArrayList<IStatementParameter>();
        params.add(buildcraft.transport.statements.ActionParameterSignal.EMPTY);params.add(buildcraft.transport.statements.TriggerParameterSignal.EMPTY);
        for(var colour:DyeColor.values()){
            params.add(buildcraft.transport.statements.ActionParameterSignal.get(colour));
            params.add(buildcraft.transport.statements.TriggerParameterSignal.get(false,colour));
            params.add(buildcraft.transport.statements.TriggerParameterSignal.get(true,colour));
        }
        for(var param:params){
            var nbt=StatementTypeParam.INSTANCE.writeToNbt(param);
            c.assertTrue(StatementTypeParam.INSTANCE.writeToNbt(StatementTypeParam.INSTANCE.readFromNbt(nbt)).equals(nbt),"Wire NBT reader lost colour/polarity "+nbt);
            var buffer=new buildcraft.lib.net.PacketBufferBC(io.netty.buffer.Unpooled.buffer());
            try{StatementTypeParam.INSTANCE.writeToBuffer(buffer,param);var restored=StatementTypeParam.INSTANCE.readFromBuffer(buffer);c.assertTrue(StatementTypeParam.INSTANCE.writeToNbt(restored).equals(nbt) && buffer.readableBytes()==0,"Wire packet reader lost colour/polarity "+nbt);}finally{buffer.release();}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exactPasteRetainsParametersSidesConnectionsAndTargetVariant(TestContext c){
        var tile=pipe(c,POS);var source=gate(tile,Direction.NORTH,FULL);var target=gate(tile,Direction.SOUTH,FULL);configure(source);
        var player=c.createMockCreativeServerPlayerInWorld();var copier=new ItemStack(BCSiliconItems.gateCopier);player.setStackInHand(Hand.MAIN_HAND,copier);click(c,player,source,Hand.MAIN_HAND);
        var stored=ItemGateCopier.getCopiedGateData(copier);c.assertTrue(click(c,player,target,Hand.MAIN_HAND).isAccepted(),"Native paste failed");
        c.assertTrue(target.logic.variant.equals(FULL) && target.logic.writeConfigData().equals(source.logic.writeConfigData()) && stored.equals(ItemGateCopier.getCopiedGateData(copier)),"Paste changed variant or data");
        var sample=target.logic.statements[0].trigger.get(0).getItemStack();
        c.assertTrue(sample.getCount()==7 && sample.getName().getString().equals("copier sample") && target.logic.statements[0].trigger.get().sourcePart==EnumPipePart.WEST,"Sample NBT or absolute side lost");
        source.logic.statements[0].trigger.get(0).getItemStack().setCount(1);c.assertTrue(sample.getCount()==7,"Target aliases source sample");
        sample.setCount(2);c.assertTrue(stored.equals(ItemGateCopier.getCopiedGateData(copier)),"Target aliases copied sample");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void mainHandCopierWinsWithoutOverwritingOffhandData(TestContext c){
        var tile=pipe(c,POS);var gate=gate(tile,Direction.UP,FULL);configure(gate);var player=c.createMockCreativeServerPlayerInWorld();
        var main=new ItemStack(BCSiliconItems.gateCopier);var off=new ItemStack(BCSiliconItems.gateCopier);var marker=new NbtCompound();marker.putString("marker","offhand");ItemGateCopier.setCopiedGateData(off,marker);
        player.setStackInHand(Hand.MAIN_HAND,main);player.setStackInHand(Hand.OFF_HAND,off);var before=gate.logic.writeConfigData();click(c,player,gate,Hand.MAIN_HAND);
        c.assertTrue(ItemGateCopier.getCopiedGateData(main).equals(before) && ItemGateCopier.getCopiedGateData(off).equals(marker) && gate.logic.writeConfigData().equals(before),"Hand priority pasted/overwrote wrong data");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void smallerAndDifferentLogicGatesReportLossAndKeepTheirHardware(TestContext c){
        var tile=pipe(c,POS);var source=gate(tile,Direction.NORTH,FULL);configure(source);
        var large=source.logic.writeConfigData();
        var target=gate(tile,Direction.SOUTH,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.IRON,EnumGateModifier.NO_MODIFIER));
        var result=GateConfigCopier.paste(target.logic,large);
        c.assertTrue(result.applied() && result.warnings().containsAll(Set.of(GateConfigCopier.Warning.LOGIC,GateConfigCopier.Warning.SLOTS,GateConfigCopier.Warning.TRIGGER_PARAMETERS,GateConfigCopier.Warning.ACTION_PARAMETERS)),"Missing reduced-target diagnostics");
        c.assertTrue(target.logic.variant.material==EnumGateMaterial.IRON && target.logic.variant.logic==EnumGateLogic.OR && target.logic.statements.length==2 && target.logic.connections[0],"Paste upgraded target or lost fitting group");
        c.assertTrue(target.logic.statements[0].trigger.getParamCount()==0 && target.logic.statements[0].action.getParamCount()==0,"Paste exceeded target parameter capacity");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blankConfigurationClearsEveryDestinationRowAndConnection(TestContext c){
        var tile=pipe(c,POS);var blank=gate(tile,Direction.UP,new GateVariant(EnumGateLogic.AND,EnumGateMaterial.CLAY_BRICK,EnumGateModifier.NO_MODIFIER));
        var target=gate(tile,Direction.NORTH,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.GOLD,EnumGateModifier.NO_MODIFIER));
        for(var row:target.logic.statements){row.trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE,null));row.action.set(ActionWrapper.wrap(BCCoreStatements.ACTION_REDSTONE,null));}Arrays.fill(target.logic.connections,true);
        c.assertTrue(GateConfigCopier.paste(target.logic,blank.logic.writeConfigData()).applied(),"Blank configuration rejected");
        for(var row:target.logic.statements)c.assertTrue(row.trigger.get()==null && row.action.get()==null,"Old target statement retained");
        for(boolean connection:target.logic.connections)c.assertTrue(!connection,"Old target group retained");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyStringEntriesLoadWithoutMutatingStoredData(TestContext c){
        var tile=pipe(c,POS);var target=gate(tile,Direction.UP,FULL);var legacy=new NbtCompound();legacy.put("variant",FULL.writeToNBT());
        legacy.putString("trigger[0]",BCCoreStatements.TRIGGER_TRUE.getUniqueTag());legacy.putString("action[0]",BCCoreStatements.ACTION_REDSTONE.getUniqueTag());legacy.putShort("connections",(short)1);
        var before=legacy.copy();c.assertTrue(GateConfigCopier.paste(target.logic,legacy).applied(),"Legacy string copier rejected");
        c.assertTrue(target.logic.statements[0].trigger.get().delegate==BCCoreStatements.TRIGGER_TRUE && target.logic.statements[0].action.get().getDelegate()==BCCoreStatements.ACTION_REDSTONE && legacy.equals(before),"Legacy string source changed or decoded empty");
        var loaded=new PluggableGate(BCSiliconPlugs.gate,tile,Direction.UP,new NbtCompound());loaded.logic.readConfigData(legacy);
        c.assertTrue(loaded.logic.statements[0].trigger.get()!=null && legacy.equals(before),"Legacy saved gate lost string entry");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedLaterRowCannotPartiallyReplaceGate(TestContext c){
        var tile=pipe(c,POS);var target=gate(tile,Direction.UP,FULL);configure(target);var before=target.logic.writeConfigData();
        var invalid=before.copy();invalid.getCompound("trigger[0]").getCompound("s").putString("kind",BCCoreStatements.TRIGGER_TRUE.getUniqueTag());invalid.getCompound("trigger[0]").getCompound("s").putByte("side",(byte)6);
        var bad=new NbtCompound();var statement=new NbtCompound();statement.putString("kind",BCCoreStatements.TRIGGER_INVENTORY_CONTAINS.getUniqueTag());statement.putByte("side",(byte)6);bad.put("s",statement);invalid.put("trigger[3]",bad);
        c.assertTrue(!GateConfigCopier.paste(target.logic,invalid).applied() && target.logic.writeConfigData().equals(before),"Malformed last row partially changed target");
        for(int material:new int[]{-1,4,127}){var data=before.copy();data.getCompound("variant").putInt("material",material);c.assertTrue(!GateConfigCopier.paste(target.logic,data).applied(),"Malformed source variant accepted");}
        c.assertTrue(!GateConfigCopier.paste(target.logic,new NbtCompound()).applied() && target.logic.writeConfigData().equals(before),"Empty malformed payload erased target");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingStatementsAndInvalidParameterKindsRecoverWithWarnings(TestContext c){
        var tile=pipe(c,POS);var gate=gate(tile,Direction.UP,FULL);configure(gate);var data=gate.logic.writeConfigData();
        data.getCompound("action[1]").getCompound("s").putString("kind","missing:addon.action");
        data.getCompound("trigger[0]").getCompound("0").putString("kind","missing:addon.parameter");
        var result=GateConfigCopier.paste(gate.logic,data);
        c.assertTrue(result.applied() && result.warnings().contains(GateConfigCopier.Warning.MISSING_DATA),"Missing add-on data not reported");
        c.assertTrue(gate.logic.statements[1].action.get()==null && gate.logic.statements[0].trigger.get()!=null && gate.logic.statements[0].trigger.get(0).getItemStack().isEmpty(),"Invalid parameter retained or valid row discarded");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void copierExcludesRuntimeAndStartsIndependentSingleActionLatch(TestContext c){
        var first=pipe(c,POS);var second=pipe(c,POS.east(3));var source=gate(first,Direction.NORTH,FULL);var target=gate(second,Direction.NORTH,FULL);
        var a=new PluggablePulsar(BCSiliconPlugs.pulsar,first,Direction.UP);first.replacePluggable(Direction.UP,a);
        var b=new PluggablePulsar(BCSiliconPlugs.pulsar,second,Direction.UP);second.replacePluggable(Direction.UP,b);
        source.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE,null));source.logic.statements[0].action.set(ActionWrapper.wrap(BCSiliconStatements.ACTION_PULSAR_SINGLE,Direction.UP));source.logic.resolveActions();source.logic.emitWire(DyeColor.BLUE);
        c.assertTrue(a.getPendingPulses()==1 && source.logic.writeToNbt().getShort("single_action_latches")==1,"Source action did not latch");
        var copied=source.logic.writeConfigData();c.assertTrue(!copied.contains("wireBroadcasts") && !copied.contains("single_action_latches"),"Copier captured live runtime state");
        c.assertTrue(GateConfigCopier.paste(target.logic,copied).applied(),"Single action paste failed");
        for(int i=0;i<5;i++){source.logic.resolveActions();target.logic.resolveActions();}
        c.assertTrue(a.getPendingPulses()==1 && b.getPendingPulses()==1 && !target.logic.isEmitting(DyeColor.BLUE),"Copied latch suppressed/repeated action or leaked wire signal");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sneakClearKeepsCustomNbtAndDoesNotAffectGate(TestContext c){
        var tile=pipe(c,POS);var gate=gate(tile,Direction.UP,FULL);configure(gate);var player=c.createMockCreativeServerPlayerInWorld();aim(player,gate);
        var stack=new ItemStack(BCSiliconItems.gateCopier);stack.setCustomName(net.minecraft.text.Text.literal("My copier"));stack.getOrCreateNbt().putString("owner_note","retain");
        var original=stack.getNbt().copy();ItemGateCopier.setCopiedGateData(stack,gate.logic.writeConfigData());player.setStackInHand(Hand.MAIN_HAND,stack);player.setSneaking(true);
        c.assertTrue(BCSiliconItems.gateCopier.use(c.getWorld(),player,Hand.MAIN_HAND).getResult().isAccepted() && stack.getNbt().equals(original),"Air clear lost custom NBT");
        ItemGateCopier.setCopiedGateData(stack,gate.logic.writeConfigData());var before=gate.logic.writeConfigData();
        var hit=new BlockHitResult(gate.getBoundingBox().getCenter().add(Vec3d.of(tile.getPos())),Direction.UP,tile.getPos(),false);
        c.assertTrue(BCSiliconItems.gateCopier.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted() && stack.getNbt().equals(original) && gate.logic.writeConfigData().equals(before),"Block clear changed target gate");
        var plain=new ItemStack(BCSiliconItems.gateCopier);ItemGateCopier.setCopiedGateData(plain,before);player.setStackInHand(Hand.MAIN_HAND,plain);BCSiliconItems.gateCopier.use(c.getWorld(),player,Hand.MAIN_HAND);
        c.assertTrue(!plain.hasNbt() && BCSiliconItems.gateCopier.use(c.getWorld(),player,Hand.MAIN_HAND).getResult()==ActionResult.PASS,"Clearing empty copier retained tag or consumed use");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeProtectionDistanceSpectatorAndRemovedGateRejectEdits(TestContext c){
        var tile=pipe(c,POS);var gate=gate(tile,Direction.UP,FULL);configure(gate);var player=c.createMockCreativeServerPlayerInWorld();var stack=new ItemStack(BCSiliconItems.gateCopier);player.setStackInHand(Hand.MAIN_HAND,stack);
        DENIED.add(tile.getPos());try{c.assertTrue(click(c,player,gate,Hand.MAIN_HAND)==ActionResult.FAIL && !ItemGateCopier.hasCopiedGateData(stack),"Native use protection bypassed");}finally{DENIED.remove(tile.getPos());}
        player.refreshPositionAndAngles(tile.getPos().add(20,0,0),0,0);c.assertTrue(BCSiliconItems.gateCopier.interact(stack,player,gate)==ActionResult.FAIL,"Distant player copied gate");
        // Vanilla's creative GameTest mock hardcodes isSpectator() to false, even after changeGameMode.
        var spectator=new net.minecraft.entity.player.PlayerEntity(c.getWorld(),tile.getPos(),0,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CopierSpectator")){
            @Override public boolean isSpectator(){return true;}
            @Override public boolean isCreative(){return false;}
        };
        c.assertTrue(BCSiliconItems.gateCopier.interact(stack,spectator,gate)==ActionResult.FAIL,"Spectator edited copier");aim(player,gate);
        var before=gate.logic.writeConfigData();tile.replacePluggable(Direction.UP,null);
        c.assertTrue(BCSiliconItems.gateCopier.interact(stack,player,gate)==ActionResult.FAIL && !GateConfigCopier.paste(gate.logic,before).applied() && !ItemGateCopier.hasCopiedGateData(stack),"Detached gate accepted copier action");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void copiedItemAndPastedGateSurviveIndependentNbtReload(TestContext c){
        var tile=pipe(c,POS);var source=gate(tile,Direction.NORTH,FULL);configure(source);var saved=source.logic.writeConfigData();
        var stack=new ItemStack(BCSiliconItems.gateCopier);ItemGateCopier.setCopiedGateData(stack,saved);saved.putString("foreign","mutated");
        var exposed=ItemGateCopier.getCopiedGateData(stack);exposed.putString("foreign","mutated");
        c.assertTrue(!ItemGateCopier.getCopiedGateData(stack).contains("foreign"),"Copied item aliases caller NBT");
        var item=ItemStack.fromNbt(stack.writeNbt(new NbtCompound()));var target=gate(tile,Direction.SOUTH,FULL);c.assertTrue(GateConfigCopier.paste(target.logic,ItemGateCopier.getCopiedGateData(item)).applied(),"Saved copier failed");
        var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());
        var restored=((PluggableGate)copy.getPluggable(Direction.SOUTH)).logic.writeConfigData();
        c.assertTrue(restored.equals(source.logic.writeConfigData()),"Saved target lost pasted config: restored="+restored+" source="+source.logic.writeConfigData());c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void assemblyConsumesWrenchIronChipsetAndExactlyFiveHundredMj(TestContext c){
        var recipe=(NativeAssemblyRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcraftsilicon","assembly/gate_copier")).orElseThrow();
        c.assertTrue(recipe.cost()==500*MjAPI.MJ && recipe.allocate(List.of(new ItemStack(BCCoreItems.wrench),EnumRedstoneChipset.RED.getStack()))==null,"Wrong copier cost or chipset accepted");
        c.setBlockState(POS,BCSiliconBlocks.assemblyTable);var table=(TileAssemblyTable)c.getBlockEntity(POS);table.onLoad();table.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCSiliconBlocks.assemblyTable));
        c.setBlockState(POS.down(),Blocks.BARREL);var out=(BarrelBlockEntity)c.getBlockEntity(POS.down());
        table.inv.setStackInSlot(0,new ItemStack(BCCoreItems.wrench));table.inv.setStackInSlot(1,EnumRedstoneChipset.IRON.getStack());
        c.assertTrue(table.selectRecipe(recipe.getId(),true) && table.receiveLaserPower(500*MjAPI.MJ+1)==1,"Copier recipe not selectable or overcharged");table.tick();
        c.assertTrue(out.getStack(0).isOf(BCSiliconItems.gateCopier) && out.getStack(0).getCount()==1 && !ItemGateCopier.hasCopiedGateData(out.getStack(0))
            && table.power==0 && table.inv.stacks.stream().allMatch(ItemStack::isEmpty),"Copier assembly duplicated ingredients, data or power");c.complete();
    }
}
