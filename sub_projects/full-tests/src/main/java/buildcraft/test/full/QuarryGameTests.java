/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.mj.*;
import buildcraft.builders.*;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.*;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.chunkload.ChunkLoaderManager;
import buildcraft.lib.net.*;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public final class QuarryGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(1,2,1);
    private static final Map<ServerWorld,Set<BlockPos>> PROTECTED=new WeakHashMap<>();
    static {
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,tile)->
            !PROTECTED.getOrDefault(world,Set.of()).contains(pos));
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void defaultPlacementUsesEachFacingAndModernWorldBottom(TestContext c) {
        for(var facing:Direction.Type.HORIZONTAL) {
            c.setBlockState(POS,Blocks.AIR);c.setBlockState(POS,BCBuildersBlocks.quarry.getDefaultState().with(BCBuildersBlocks.quarry.getFacingProperty(),facing));
            var tile=(TileQuarry)c.getBlockEntity(POS);tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.quarry));
            c.assertTrue(tile.frameBox.isOnEdge(tile.getPos().offset(facing.getOpposite())) && tile.frameBox.size().equals(new BlockPos(11,5,11)),"Default quarry frame lost facing/size");
            c.assertTrue(tile.getMiningBox().min().getY()==c.getWorld().getBottomY() && tile.getMiningBox().size().getX()==9,"Quarry still stops at legacy Y=0");
            c.assertTrue(ChunkLoaderManager.getActiveChunks(c.getWorld(),tile.getPos()).contains(new ChunkPos(tile.getPos())),"Quarry ticket omitted its own chunk");
        }
        c.setBlockState(POS,Blocks.AIR);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=350)
    public void markerSelectedQuarryBuildsFramesMinesAndOutputsNativeLoot(TestContext c) {
        var tile=small(c);var target=POS.add(1,-1,2);
        c.setBlockState(POS.west(),Blocks.BARREL);c.setBlockState(target,Blocks.DIAMOND_BLOCK);
        var bank=(BarrelBlockEntity)c.getBlockEntity(POS.west());
        power(tile,20_000*MjAPI.MJ);
        c.waitAndRun(240,()->{
            c.assertTrue(c.getBlockState(target).isAir(),"Quarry did not mine below its frame");
            c.assertTrue(count(bank,Items.DIAMOND_BLOCK)==1,"Quarry lost/duplicated native mined loot");
            c.assertTrue(tile.framePoses.stream().allMatch(p->c.getWorld().getBlockState(p).isOf(BCBuildersBlocks.frame)),"Quarry failed to build or retain frames");
            c.assertTrue(tile.isMiningFinished(),"Quarry did not finish scanning to the world bottom");
            c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=380)
    public void partialTaskAndMinedOutputSurviveNativeReload(TestContext c) {
        var tile=small(c);var target=POS.add(1,-1,2);c.setBlockState(target,Blocks.GOLD_BLOCK);c.setBlockState(POS.west(),Blocks.BARREL);
        power(tile,10*MjAPI.MJ);tile.tick();tile.tick();
        var saved=tile.createNbt();
        c.assertTrue(saved.contains("currentTaskId") && saved.getCompound("currentTaskData").getLong("power")>0,"Fixture did not save a partially powered task");
        long progress=saved.getCompound("currentTaskData").getLong("power");long balance=tile.getStoredPower();
        var old=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP);
        var copy=reload(c,tile);
        c.assertTrue(copy.createNbt().getCompound("currentTaskData").getLong("power")==progress && copy.getStoredPower()==balance,"Reload lost task type or paid work");
        c.assertTrue(old.receivePower(1,false)==1,"Detached quarry kept a writable MJ handle");
        power(copy,20_000*MjAPI.MJ);
        c.waitAndRun(250,()->{
            c.assertTrue(c.getBlockState(target).isAir() && count((BarrelBlockEntity)c.getBlockEntity(POS.west()),Items.GOLD_BLOCK)==1,"Reloaded quarry lost or repeated mined loot");
            var finished=reload(c,copy);c.assertTrue(finished.isMiningFinished(),"Completed quarry forgot its saved completion state");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=400)
    public void nativeWorldUpdatesRepairFramesAndRevisitPlacedBlocks(TestContext c) {
        var tile=small(c);c.setBlockState(POS.west(),Blocks.BARREL);power(tile,22_000*MjAPI.MJ);
        c.waitAndRun(220,()->{
            c.assertTrue(tile.isMiningFinished(),"Quarry fixture did not finish");
            var frame=tile.framePoses.get(0);c.getWorld().removeBlock(frame,false);
            var target=POS.add(1,-1,2);c.setBlockState(target,Blocks.EMERALD_BLOCK);
            c.waitAndRun(100,()->{
                c.assertTrue(c.getWorld().getBlockState(frame).isOf(BCBuildersBlocks.frame),"Native block change did not rebuild removed frame");
                c.assertTrue(c.getBlockState(target).isAir() && count((BarrelBlockEntity)c.getBlockEntity(POS.west()),Items.EMERALD_BLOCK)==1,"Finished quarry ignored a newly placed block");
                c.complete();
            });
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=400)
    public void protectionPreventsMiningAndResumesAfterRemoval(TestContext c) {
        var tile=small(c);var target=POS.add(1,-1,2);c.setBlockState(target,Blocks.DIAMOND_BLOCK);c.setBlockState(POS.west(),Blocks.BARREL);
        PROTECTED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(c.getAbsolutePos(target));power(tile,22_000*MjAPI.MJ);
        c.waitAndRun(180,()->{
            c.assertTrue(c.getBlockState(target).isOf(Blocks.DIAMOND_BLOCK) && count((BarrelBlockEntity)c.getBlockEntity(POS.west()),Items.DIAMOND_BLOCK)==0,"Quarry bypassed native block protection");
            PROTECTED.get(c.getWorld()).remove(c.getAbsolutePos(target));
            c.waitAndRun(120,()->{
                c.assertTrue(c.getBlockState(target).isAir() && count((BarrelBlockEntity)c.getBlockEntity(POS.west()),Items.DIAMOND_BLOCK)==1,"Quarry failed to resume after protection was removed");c.complete();
            });
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=100)
    public void noPowerDoesNotBuildAndRemovalReleasesOnlyQuarryTickets(TestContext c) {
        var tile=small(c);var chunk=new ChunkPos(tile.getPos());
        boolean previouslyForced=c.getWorld().getForcedChunks().contains(chunk.toLong());
        c.getWorld().setChunkForced(chunk.x,chunk.z,true);
        c.waitAndRun(30,()->{
            c.assertTrue(tile.framePoses.stream().noneMatch(p->c.getWorld().getBlockState(p).isOf(BCBuildersBlocks.frame)),"Unpowered quarry placed free frames");
            var input=(IMjReceiver)MjAPI.CONNECTOR.find(c.getWorld(),tile.getPos(),Direction.UP);
            var frames=List.copyOf(tile.framePoses);
            c.getWorld().setBlockState(frames.get(0),BCBuildersBlocks.frame.getDefaultState());
            c.getWorld().removeBlock(tile.getPos(),false);
            c.assertTrue(ChunkLoaderManager.getActiveChunks(c.getWorld(),tile.getPos()).isEmpty() && input.receivePower(1,false)==1,"Removed quarry retained chunk tickets or MJ input");
            c.assertTrue(c.getWorld().getForcedChunks().contains(chunk.toLong()),"Quarry removal unforced an administrator-owned chunk");
            c.assertTrue(!c.getWorld().getBlockState(frames.get(0)).isOf(BCBuildersBlocks.frame),"Quarry removal left its frame behind");
            if(!previouslyForced)c.getWorld().setChunkForced(chunk.x,chunk.z,false);
            c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=350)
    public void nativePipeOutputAndFullInventorySpillKeepExactItems(TestContext c) {
        var tile=small(c);var target=POS.add(1,-1,2);c.setBlockState(target,Blocks.IRON_BLOCK);
        c.setBlockState(POS.west(),BCTransportBlocks.pipeHolder);
        var pipe=(TilePipeHolder)c.getBlockEntity(POS.west());pipe.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));
        c.setBlockState(POS.west().up(),Blocks.BARREL);c.setBlockState(POS.up(),Blocks.BARREL);
        power(tile,20_000*MjAPI.MJ);
        c.waitAndRun(240,()->{
            var pipeBank=(BarrelBlockEntity)c.getBlockEntity(POS.west().up());var direct=(BarrelBlockEntity)c.getBlockEntity(POS.up());
            c.assertTrue(count(pipeBank,Items.IRON_BLOCK)==1 && count(direct,Items.IRON_BLOCK)==0,"Quarry did not prefer connected native item pipes");
            for(int i=0;i<direct.size();i++)direct.setStack(i,new ItemStack(Items.COBBLESTONE,64));
            c.setBlockState(POS.west(),Blocks.AIR);
            buildcraft.lib.misc.InventoryUtil.addToBestAcceptor(c.getWorld(),tile.getPos(),null,new ItemStack(Items.DIAMOND,3));
            int dropped=c.getWorld().getEntitiesByClass(ItemEntity.class,new net.minecraft.util.math.Box(tile.getPos()).expand(1),e->e.getStack().isOf(Items.DIAMOND)).stream().mapToInt(e->e.getStack().getCount()).sum();
            c.assertTrue(dropped==3,"Full native output destroyed or duplicated quarry overflow");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void miningPowerUsesHardnessAndMalformedTasksCannotEscapeArea(TestContext c) {
        var tile=small(c);var target=c.getAbsolutePos(POS.add(1,-1,2));
        c.getWorld().setBlockState(target,Blocks.STONE.getDefaultState());
        c.assertTrue(buildcraft.lib.misc.BlockUtil.computeBlockBreakPower(c.getWorld(),target)==80*MjAPI.MJ,"Mining still uses placeholder power cost");
        var data=tile.createNbt();data.putByte("currentTaskId",(byte)127);tile.readNbt(data);
        c.assertTrue(tile.currentTask==null,"Invalid task ID accepted");
        data=tile.createNbt();data.putByte("currentTaskId",(byte)0);
        var task=new NbtCompound();task.putLong("power",1);task.put("breakPos",buildcraft.lib.misc.NBTUtilBC.writeBlockPos(target.add(100,0,0)));data.put("currentTaskData",task);tile.readNbt(data);
        c.assertTrue(tile.currentTask==null,"Out-of-area mining task accepted");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=350)
    public void waterColumnsRemainPassableAndBedrockIsPreserved(TestContext c) {
        var tile=small(c);
        c.setBlockState(POS.add(1,-1,2),Blocks.WATER);c.setBlockState(POS.add(1,-2,2),Blocks.DIAMOND_BLOCK);
        c.setBlockState(POS.add(2,-1,2),Blocks.BEDROCK);c.setBlockState(POS.add(2,-2,2),Blocks.GOLD_BLOCK);
        c.setBlockState(POS.west(),Blocks.BARREL);power(tile,22_000*MjAPI.MJ);
        c.waitAndRun(250,()->{
            c.assertTrue(!c.getBlockState(POS.add(1,-2,2)).isOf(Blocks.DIAMOND_BLOCK) && count((BarrelBlockEntity)c.getBlockEntity(POS.west()),Items.DIAMOND_BLOCK)==1,"Quarry could not descend through water");
            c.assertTrue(c.getBlockState(POS.add(2,-1,2)).isOf(Blocks.BEDROCK) && c.getBlockState(POS.add(2,-2,2)).isOf(Blocks.GOLD_BLOCK),"Quarry mined through an impassable ceiling");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sharedChunkTicketsRespectConfigAndReplacementIdentity(TestContext c) {
        var a=small(c);var other=POS.west(2);
        c.setBlockState(other,BCBuildersBlocks.quarry.getDefaultState().with(BCBuildersBlocks.quarry.getFacingProperty(),Direction.NORTH));
        var b=(TileQuarry)c.getBlockEntity(other);b.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.quarry));
        var common=new HashSet<>(ChunkLoaderManager.getActiveChunks(c.getWorld(),a.getPos()));
        common.retainAll(ChunkLoaderManager.getActiveChunks(c.getWorld(),b.getPos()));
        c.assertTrue(!common.isEmpty(),"Fixture did not share quarry ticket chunks");
        c.setBlockState(POS,Blocks.AIR);
        c.assertTrue(ChunkLoaderManager.getActiveChunks(c.getWorld(),b.getPos()).containsAll(common),"Removing one quarry released another quarry's chunks");
        var replacement=reload(c,b);replacement.tick();ChunkLoaderManager.forget(b);
        c.assertTrue(!ChunkLoaderManager.getActiveChunks(c.getWorld(),replacement.getPos()).isEmpty(),"Stale quarry detached replacement tickets");
        var original=buildcraft.lib.BCLibConfig.chunkLoadingLevel;
        try {
            buildcraft.lib.BCLibConfig.chunkLoadingLevel=buildcraft.lib.BCLibConfig.ChunkLoaderLevel.NONE;
            ChunkLoaderManager.loadChunksForTile(replacement);
            c.assertTrue(ChunkLoaderManager.getActiveChunks(c.getWorld(),replacement.getPos()).isEmpty(),"NONE chunk-loading configuration was ignored");
            buildcraft.lib.BCLibConfig.chunkLoadingLevel=buildcraft.lib.BCLibConfig.ChunkLoaderLevel.STRICT_TILES;
            ChunkLoaderManager.loadChunksForTile(replacement);
            c.assertTrue(!ChunkLoaderManager.getActiveChunks(c.getWorld(),replacement.getPos()).isEmpty(),"STRICT_TILES rejected HARD quarry tickets");
        } finally {buildcraft.lib.BCLibConfig.chunkLoadingLevel=original;c.setBlockState(other,Blocks.AIR);}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=350)
    public void movingRailsUseNativeCollisionAndDisappearOnRemoval(TestContext c) {
        var tile=small(c);power(tile,20_000*MjAPI.MJ);
        c.waitAndRun(240,()->{
            c.assertTrue(tile.drillPos!=null && tile.getCollisionBoxes().size()==3,"Quarry has no moving arm collision");
            var query=tile.getCollisionBoxes().get(0).contract(.01);
            c.assertTrue(c.getWorld().getBlockCollisions(null,query).iterator().hasNext(),"Native CollisionView omitted quarry rails");
            c.setBlockState(POS,Blocks.AIR);
            c.assertTrue(!c.getWorld().getBlockCollisions(null,query).iterator().hasNext(),"Removed quarry retained ghost collisions");c.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void quarryRecipeConsumesOriginalIngredientsAndRegistersAdvancements(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var handler=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        Item iron=net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier("buildcraftcore:gear_iron"));
        Item gold=net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier("buildcraftcore:gear_gold"));
        Item diamond=net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier("buildcraftcore:gear_diamond"));
        Item[] ingredients={iron,Items.REDSTONE,iron,gold,iron,gold,diamond,Items.DIAMOND_PICKAXE,diamond};
        for(int i=0;i<9;i++)handler.getSlot(i+1).setStack(new ItemStack(ingredients[i]));
        handler.onContentChanged(handler.getSlot(1).inventory);
        c.assertTrue(handler.getSlot(0).getStack().isOf(BCBuildersBlocks.quarry.asItem()),"Quarry survival crafting recipe missing");
        handler.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);
        for(int i=1;i<=9;i++)c.assertTrue(handler.getSlot(i).getStack().isEmpty(),"Quarry recipe failed to consume ingredients");
        for(String path:List.of("shaping_the_world","diggy_diggy_hole","recipes/quarry"))
            c.assertTrue(c.getWorld().getServer().getAdvancementLoader().get(new net.minecraft.util.Identifier("buildcraftbuilders",path))!=null,"Missing quarry advancement "+path);
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void renderPacketsRejectTruncationAndNonFiniteDrillsAtomically(TestContext c) throws Exception {
        var source=small(c);power(source,10*MjAPI.MJ);source.tick();source.tick();
        var target=buildcraft.lib.registry.BlockEntityRegistry.create(TileQuarry.class,source.getPos(),source.getCachedState());
        target.readNbt(source.createNbt());
        var before=target.createNbt();
        var packet=PacketBufferBC.write(b->source.writePayload(TileBC_Neptune.NET_RENDER_DATA,b,TileBC_Neptune.NetSide.SERVER));
        try {
            packet.writerIndex(packet.writerIndex()-1);boolean rejected=false;
            try {target.readPayload(TileBC_Neptune.NET_RENDER_DATA,packet,TileBC_Neptune.NetSide.CLIENT,null);}
            catch(java.io.IOException | IndexOutOfBoundsException expected){rejected=true;}
            c.assertTrue(rejected && target.createNbt().equals(before),"Truncated quarry render packet mutated live state");
        } finally {packet.release();}
        source.drillPos=new Vec3d(Double.NaN,0,0);
        packet=PacketBufferBC.write(b->source.writePayload(TileBC_Neptune.NET_RENDER_DATA,b,TileBC_Neptune.NetSide.SERVER));
        source.drillPos=null;
        try {
            boolean rejected=false;
            try {target.readPayload(TileBC_Neptune.NET_RENDER_DATA,packet,TileBC_Neptune.NetSide.CLIENT,null);}
            catch(java.io.IOException expected){rejected=true;}
            c.assertTrue(rejected && target.createNbt().equals(before),"Non-finite quarry drill corrupted live state");
        } finally {packet.release();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacyZeroDepthExtendsToModernBottomWithoutLoadingInvalidHeights(TestContext c) {
        var tile=small(c);
        // A realistic old quarry was placed above zero; move its frame and owner together.
        var high=tile.getPos().withY(10);var state=tile.getCachedState();
        c.getWorld().setBlockState(high,state);var highTile=(TileQuarry)c.getWorld().getBlockEntity(high);
        highTile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.quarry));
        var data=highTile.createNbt();data.remove("quarry_format");
        var legacy=new buildcraft.lib.misc.data.Box(highTile.getMiningBox().min().withY(0),highTile.getMiningBox().max());
        data.put("box",legacy.writeToNBT());data.remove("boxIterator");highTile.readNbt(data);highTile.tick();
        c.assertTrue(highTile.getMiningBox().min().getY()==c.getWorld().getBottomY(),"Old Y=0 quarry was not upgraded to modern depth");
        var invalid=highTile.createNbt();var frame=new buildcraft.lib.misc.data.Box(highTile.frameBox.min(),highTile.frameBox.max().withY(c.getWorld().getTopY()+10));
        invalid.put("frame",frame.writeToNBT());highTile.readNbt(invalid);
        c.assertTrue(!highTile.frameBox.isInitialized(),"Quarry accepted bounds outside world build height");
        c.getWorld().setBlockState(high,Blocks.AIR.getDefaultState());c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=650)
    public void nativeEnginePipesPowerQuarryWithoutInjectedBattery(TestContext c) {
        var tile=small(c);var target=POS.add(1,-1,2);
        c.setBlockState(target,Blocks.DIAMOND_BLOCK);c.setBlockState(POS.west(),Blocks.BARREL);
        for(int i=1;i<=2;i++) {
            c.setBlockState(POS.north(i),BCTransportBlocks.pipeHolder);
            ((TilePipeHolder)c.getBlockEntity(POS.north(i))).onPlacedBy(null,new ItemStack(i==2?BCTransportItems.pipePowerWood:BCTransportItems.pipePowerStone));
        }
        c.setBlockState(POS.north(3).down(),Blocks.REDSTONE_BLOCK);
        c.setBlockState(POS.north(3),BCCoreBlocks.engine.getDefaultState().with(buildcraft.api.properties.BuildCraftProperties.ENGINE_TYPE,buildcraft.api.enums.EnumEngineType.CREATIVE));
        ((buildcraft.core.tile.TileEngineCreative)c.getBlockEntity(POS.north(3))).currentOutputIndex=6;
        c.waitAndRun(550,()->{
            c.assertTrue(c.getBlockState(target).isAir() && count((BarrelBlockEntity)c.getBlockEntity(POS.west()),Items.DIAMOND_BLOCK)==1,
                "Native engine and MJ pipe chain did not power quarry excavation");
            c.assertTrue(tile.getStoredPower()>0 && tile.isMiningFinished(),"Quarry did not finish on live pipe power");c.complete();
        });
    }
    private static TileQuarry small(TestContext c) {
        var marker=POS.south();var far=marker.south(3);var other=marker.east(3);
        for(var p:List.of(marker,far,other)) {
            c.setBlockState(p.down(),Blocks.STONE);
            c.setBlockState(p,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));
            ((TileMarkerVolume)c.getBlockEntity(p)).onLoad();
        }
        var volume=(TileMarkerVolume)c.getBlockEntity(marker);volume.onManualConnectionAttempt(c.createMockCreativeServerPlayerInWorld());
        c.assertTrue(volume.max().getX()-volume.min().getX()==3 && volume.max().getZ()-volume.min().getZ()==3,"Native marker fixture failed to connect");
        c.setBlockState(POS,BCBuildersBlocks.quarry.getDefaultState().with(BCBuildersBlocks.quarry.getFacingProperty(),Direction.NORTH));
        var tile=(TileQuarry)c.getBlockEntity(POS);tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.quarry));
        c.assertTrue(tile.frameBox.size().equals(new BlockPos(4,5,4)) && c.getBlockState(marker).isAir(),"Quarry did not adopt and consume native marker volume");
        return tile;
    }
    private static void power(TileQuarry tile,long amount) {
        var input=(IMjReceiver)MjAPI.CONNECTOR.find(tile.getWorld(),tile.getPos(),Direction.UP);
        if(input==null || input.receivePower(amount,false)!=0)throw new AssertionError("Quarry fixture rejected MJ");
    }
    private static TileQuarry reload(TestContext c,TileQuarry tile) {
        var copy=buildcraft.lib.registry.BlockEntityRegistry.create(TileQuarry.class,tile.getPos(),tile.getCachedState());
        copy.readNbt(tile.createNbt());c.getWorld().removeBlockEntity(tile.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();return copy;
    }
    private static int count(net.minecraft.inventory.Inventory inventory,Item item) {
        int count=0;for(int i=0;i<inventory.size();i++)if(inventory.getStack(i).isOf(item))count+=inventory.getStack(i).getCount();return count;
    }
}
