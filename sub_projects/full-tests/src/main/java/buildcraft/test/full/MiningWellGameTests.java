/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.core.*;
import buildcraft.factory.*;
import buildcraft.factory.tile.*;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class MiningWellGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,6,3);
    private static final Map<ServerWorld,Set<BlockPos>> PROTECTED=new WeakHashMap<>(),NO_TUBES=new WeakHashMap<>();
    static {
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((w,p,pos,s,t)->!PROTECTED.getOrDefault(w,Set.of()).contains(pos));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((p,w,h,hit)->
            NO_TUBES.getOrDefault(w,Set.of()).contains(hit.getBlockPos())?ActionResult.FAIL:ActionResult.PASS);
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void miningConsumesExactHardnessEnergyAndOutputsNativeLoot(TestContext c) {
        var tile=well(c);c.setBlockState(POS.down(),Blocks.DIAMOND_BLOCK);c.setBlockState(POS.down(2),Blocks.BEDROCK);
        c.setBlockState(POS.west(),Blocks.BARREL);
        power(tile,200*MjAPI.MJ);tile.tick();
        c.assertTrue(c.getBlockState(POS.down()).isAir() && count(c,POS.west(),Items.DIAMOND_BLOCK)==1,"Mining well lost native diamond-block loot");
        c.assertTrue(tile.getStoredPower()==8*MjAPI.MJ && tile.isComplete() && !tile.hasWork(),"Mining well hardness charge/completion is incorrect");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void shaftBuildsRepairsAndRetractsWithoutDestroyingNewObstructions(TestContext c) {
        var tile=well(c);c.setBlockState(POS.down(4),Blocks.DIAMOND_BLOCK);c.setBlockState(POS.down(5),Blocks.BEDROCK);tile.tick();
        c.assertTrue(tile.getWantedLength()==4 && tile.hasWork(),"Unpowered shaft did not find its target");
        for(int i=1;i<4;i++)c.assertTrue(c.getBlockState(POS.down(i)).isOf(BCFactoryBlocks.tube),"Missing shaft tube");
        var player=c.createMockCreativeServerPlayerInWorld();
        c.assertTrue(!player.interactionManager.tryBreakBlock(c.getAbsolutePos(POS.down())),"Creative breaking removed a connected tube");
        c.setBlockState(POS.down(2),Blocks.STONE);tile.tick();
        c.assertTrue(tile.getCurrentPos().equals(c.getAbsolutePos(POS.down(2))) && c.getBlockState(POS.down(2)).isOf(Blocks.STONE)
            && c.getBlockState(POS.down(3)).isAir(),"Shaft erased an inserted obstruction or left its old lower tube");
        c.setBlockState(POS,Blocks.AIR);
        c.assertTrue(c.getBlockState(POS.down()).isAir() && c.getBlockState(POS.down(2)).isOf(Blocks.STONE),"Removing miner did not clean only its tubes");
        c.setBlockState(POS.down(),BCFactoryBlocks.tube);
        c.assertTrue(player.interactionManager.tryBreakBlock(c.getAbsolutePos(POS.down())),"Disconnected tube remained protected");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void longPaidWorkSurvivesReloadBeyondIntegerRange(TestContext c) {
        var tile=well(c);c.setBlockState(POS.down(),Blocks.OBSIDIAN);c.setBlockState(POS.down(2),Blocks.BEDROCK);c.setBlockState(POS.west(),Blocks.BARREL);
        double old=BCCoreConfig.miningMultiplier;
        try {
            BCCoreConfig.miningMultiplier=2;
            for(int i=0;i<3;i++){power(tile,1000*MjAPI.MJ);tile.tick();}
            c.assertTrue(tile.getPaidWork()==3000*MjAPI.MJ && tile.getPaidWork()>Integer.MAX_VALUE,"Paid work overflowed a 32-bit integer");
            var stale=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP);
            var restored=reload(c,tile);
            c.assertTrue(restored.getPaidWork()==3000*MjAPI.MJ && stale.receivePower(MjAPI.MJ,false)==MjAPI.MJ,"Reload lost paid work or retained stale MJ input");
            power(restored,264*MjAPI.MJ);restored.tick();
            c.assertTrue(c.getBlockState(POS.down()).isAir() && count(c,POS.west(),Items.OBSIDIAN)==1
                && restored.getStoredPower()==0,"Restored obsidian mining lost energy/loot");
        } finally {BCCoreConfig.miningMultiplier=old;}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void redstonePausesMiningAndRetainsEnergy(TestContext c) {
        var tile=well(c);c.setBlockState(POS.down(),Blocks.STONE);c.setBlockState(POS.down(2),Blocks.BEDROCK);
        c.setBlockState(POS.east(),Blocks.REDSTONE_BLOCK);power(tile,80*MjAPI.MJ);tile.tick();
        c.assertTrue(c.getBlockState(POS.down()).isOf(Blocks.STONE) && tile.getStoredPower()==80*MjAPI.MJ,"Redstone pause consumed energy or mined");
        c.setBlockState(POS.east(),Blocks.AIR);tile.tick();
        c.assertTrue(c.getBlockState(POS.down()).isAir() && tile.getStoredPower()==0,"Miner did not resume after redstone removal");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeProtectionPreservesPaidWorkUntilAllowed(TestContext c) {
        var tile=well(c);var target=c.getAbsolutePos(POS.down());c.setBlockState(POS.down(),Blocks.STONE);
        c.setBlockState(POS.down(2),Blocks.BEDROCK);c.setBlockState(POS.west(),Blocks.BARREL);
        PROTECTED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(target);
        try {
            power(tile,80*MjAPI.MJ);tile.tick();
            for(int i=0;i<30;i++)tile.tick();
            c.assertTrue(c.getBlockState(POS.down()).isOf(Blocks.STONE) && tile.getPaidWork()==80*MjAPI.MJ
                && count(c,POS.west(),Items.COBBLESTONE)==0,"Mining bypassed protection or discarded paid work");
        } finally {PROTECTED.get(c.getWorld()).remove(target);}
        for(int i=0;i<22;i++)tile.tick();
        c.assertTrue(c.getBlockState(POS.down()).isAir() && count(c,POS.west(),Items.COBBLESTONE)==1,"Protection removal did not resume paid mining");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeInteractionProtectionPreventsShaftPlacement(TestContext c) {
        var tile=well(c);var target=c.getAbsolutePos(POS.down());c.setBlockState(POS.down(2),Blocks.DIAMOND_BLOCK);
        NO_TUBES.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(target);
        try {power(tile,200*MjAPI.MJ);tile.tick();
            c.assertTrue(c.getBlockState(POS.down()).isAir() && c.getBlockState(POS.down(2)).isOf(Blocks.DIAMOND_BLOCK)
                && tile.getStoredPower()==200*MjAPI.MJ,"Shaft placement bypassed native interaction protection");
        } finally {NO_TUBES.get(c.getWorld()).remove(target);}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void waterIsPassableLavaAndBedrockStopTheShaft(TestContext c) {
        var tile=well(c);c.setBlockState(POS.down(),Blocks.LAVA);c.setBlockState(POS.down(2),Blocks.DIAMOND_BLOCK);
        c.setBlockState(POS.down(3),Blocks.BEDROCK);power(tile,200*MjAPI.MJ);tile.tick();
        c.assertTrue(tile.isComplete() && c.getBlockState(POS.down(2)).isOf(Blocks.DIAMOND_BLOCK),"Miner drilled through lava");
        c.setBlockState(POS.down(),Blocks.WATER);tile.tick();
        c.assertTrue(c.getBlockState(POS.down(2)).isAir() && c.getBlockState(POS.down(3)).isOf(Blocks.BEDROCK)
            && tile.isComplete(),"Miner could not pass water or mined bedrock");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void minedItemsPreferPipesAndFullInventoriesSpillExactRemainder(TestContext c) {
        var tile=well(c);c.setBlockState(POS.down(),Blocks.IRON_BLOCK);c.setBlockState(POS.down(2),Blocks.BEDROCK);
        c.setBlockState(POS.west(),BCTransportBlocks.pipeHolder);((TilePipeHolder)c.getBlockEntity(POS.west())).onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));
        c.setBlockState(POS.west().down(),Blocks.BARREL);c.setBlockState(POS.north(),Blocks.BARREL);
        // Establish native pipe connections before manually advancing the producer.
        ((TilePipeHolder)c.getBlockEntity(POS.west())).tick();
        power(tile,200*MjAPI.MJ);tile.tick();
        c.waitAndRun(30,()->{
            c.assertTrue(count(c,POS.west().down(),Items.IRON_BLOCK)==1 && count(c,POS.north(),Items.IRON_BLOCK)==0,
                "Mining well ignored native pipe-first output: pipe bank="+count(c,POS.west().down(),Items.IRON_BLOCK)+", direct="+count(c,POS.north(),Items.IRON_BLOCK));
            c.setBlockState(POS.west(),Blocks.AIR);
            var bank=(BarrelBlockEntity)c.getBlockEntity(POS.north());for(int i=0;i<bank.size();i++)bank.setStack(i,new ItemStack(Items.COBBLESTONE,64));
            c.setBlockState(POS.down(),Blocks.DIAMOND_BLOCK);power(tile,192*MjAPI.MJ);tile.tick();
            int count=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new net.minecraft.util.math.Box(tile.getPos()).expand(1),e->e.getStack().isOf(Items.DIAMOND_BLOCK))
                .stream().mapToInt(e->e.getStack().getCount()).sum();
            c.assertTrue(count==1,"Mining overflow lost or duplicated its drop");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void configDepthAndColumnUpdatesRetargetWithoutFreeMining(TestContext c) {
        var tile=well(c);c.setBlockState(POS.down(2),Blocks.DIAMOND_BLOCK);c.setBlockState(POS.down(3),Blocks.BEDROCK);
        int old=BCCoreConfig.miningMaxDepth;
        try {
            BCCoreConfig.miningMaxDepth=1;tile.tick();c.assertTrue(tile.isComplete(),"Miner ignored configured maximum depth");
            BCCoreConfig.miningMaxDepth=3;power(tile,40*MjAPI.MJ);tile.tick();
            c.assertTrue(tile.getPaidWork()==40*MjAPI.MJ,"Miner did not resume after depth change");
            c.setBlockState(POS.down(),Blocks.STONE);tile.tick();
            c.assertTrue(tile.getPaidWork()==0 && tile.getCurrentPos().equals(c.getAbsolutePos(POS.down()))
                && c.getBlockState(POS.down()).isOf(Blocks.STONE),"Old target's paid work moved to an unrelated block");
        } finally {BCCoreConfig.miningMaxDepth=old;}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedSavedTargetsAndRenderPacketsCannotEscapeColumn(TestContext c) throws Exception {
        var tile=well(c);c.setBlockState(POS.down(3),Blocks.DIAMOND_BLOCK);tile.tick();
        var data=tile.createNbt();data.put("currentPos",NbtHelper.fromBlockPos(tile.getPos().east()));data.putLong("progress",Long.MAX_VALUE);data.putInt("wantedLength",-1);
        var copy=BlockEntityRegistry.create(TileMiningWell.class,tile.getPos(),tile.getCachedState());copy.readNbt(data);
        c.assertTrue(copy.getCurrentPos()==null && copy.getPaidWork()==0 && copy.getWantedLength()==0,"Malformed miner data retained foreign work");
        var before=tile.createNbt();var buffer=PacketBufferBC.write(b->tile.writePayload(TileMiner.NET_RENDER_DATA,b,TileBC_Neptune.NetSide.SERVER));
        try {
            buffer.writerIndex(buffer.writerIndex()-1);
            try {tile.readPayload(TileMiner.NET_RENDER_DATA,buffer,TileBC_Neptune.NetSide.CLIENT,null);throw new AssertionError("Truncated full render state accepted");}
            catch(IndexOutOfBoundsException expected){}
            c.assertTrue(tile.createNbt().equals(before),"Truncated miner render state changed live state");
            // Incremental length is independently bounded, and a truncated LED state must not partially apply.
            boolean rejected=false;var length=PacketBufferBC.write(b->b.writeInt(Integer.MAX_VALUE));
            try {tile.readPayload(TileMiner.NET_WANTED_Y,length,TileBC_Neptune.NetSide.CLIENT,null);}catch(java.io.IOException expected){rejected=true;}finally{length.release();}
            c.assertTrue(rejected && tile.createNbt().equals(before),"Unbounded tube packet changed live miner state");
            var shortLed=PacketBufferBC.write(b->b.writeBoolean(true));
            try {tile.readPayload(TileMiner.NET_LED_STATUS,shortLed,TileBC_Neptune.NetSide.CLIENT,null);throw new AssertionError("Truncated LED accepted");}
            catch(IndexOutOfBoundsException expected){}finally{shortLed.release();}
            c.assertTrue(tile.createNbt().equals(before),"Truncated miner LED changed live state");
        } finally {buffer.release();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=250)
    public void nativeEngineAndMjPipesDriveMining(TestContext c) {
        var tile=well(c);c.setBlockState(POS.down(),Blocks.DIAMOND_BLOCK);c.setBlockState(POS.down(2),Blocks.BEDROCK);c.setBlockState(POS.west(),Blocks.BARREL);
        for(int i=1;i<=2;i++) {c.setBlockState(POS.east(i),BCTransportBlocks.pipeHolder);
            ((TilePipeHolder)c.getBlockEntity(POS.east(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));}
        c.setBlockState(POS.east(3).down(),Blocks.REDSTONE_BLOCK);
        c.setBlockState(POS.east(3),BCCoreBlocks.engine.getDefaultState().with(buildcraft.api.properties.BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));
        ((buildcraft.core.tile.TileEngineCreative)c.getBlockEntity(POS.east(3))).currentOutputIndex=5;
        c.waitAndRun(150,()->{c.assertTrue(tile.isComplete() && count(c,POS.west(),Items.DIAMOND_BLOCK)==1,"Real engine/MJ pipes did not power mining well");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void survivalMiningWellRecipeConsumesOriginalIngredients(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var handler=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        var gear=net.minecraft.registry.Registries.ITEM.get(new Identifier("buildcraftcore:gear_iron"));
        Item[] ingredients={Items.IRON_INGOT,Items.REDSTONE,Items.IRON_INGOT,Items.IRON_INGOT,gear,Items.IRON_INGOT,Items.IRON_INGOT,Items.IRON_PICKAXE,Items.IRON_INGOT};
        for(int i=0;i<9;i++)handler.getSlot(i+1).setStack(new ItemStack(ingredients[i]));
        handler.onContentChanged(handler.getSlot(1).inventory);
        c.assertTrue(handler.getSlot(0).getStack().isOf(BCFactoryBlocks.miningWell.asItem()),"Missing native mining-well recipe");
        handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        for(int i=1;i<=9;i++)c.assertTrue(handler.getSlot(i).getStack().isEmpty(),"Mining-well recipe did not consume ingredients");c.complete();
    }
    private static TileMiningWell well(TestContext c) {
        for(int i=1;i<=6;i++)if(c.getWorld().isInBuildLimit(c.getAbsolutePos(POS.down(i))))c.setBlockState(POS.down(i),Blocks.AIR);
        c.setBlockState(POS,BCFactoryBlocks.miningWell);
        var tile=(TileMiningWell)c.getBlockEntity(POS);tile.onLoad();tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCFactoryBlocks.miningWell));return tile;
    }
    private static void power(TileMiningWell tile,long amount) {
        var input=(IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);
        if(input==null || input.receivePower(amount,false)!=0)throw new AssertionError("Mining well rejected fixture MJ");
    }
    private static int count(TestContext c,BlockPos pos,Item item) {
        var bank=(BarrelBlockEntity)c.getBlockEntity(pos);int total=0;for(int i=0;i<bank.size();i++)if(bank.getStack(i).isOf(item))total+=bank.getStack(i).getCount();return total;
    }
    private static TileMiningWell reload(TestContext c,TileMiningWell tile) {
        var copy=BlockEntityRegistry.create(TileMiningWell.class,tile.getPos(),tile.getCachedState());copy.readNbt(tile.createNbt());
        c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();return copy;
    }
}
