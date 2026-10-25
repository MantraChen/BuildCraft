/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.builders.*;
import buildcraft.builders.container.ContainerArchitectTable;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.*;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.server.world.ServerWorld;

public final class ArchitectGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,40,2);
    private static final Map<ServerWorld,Set<BlockPos>> DENIED=new WeakHashMap<>();
    static {PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,tile)->!DENIED.getOrDefault(world,Set.of()).contains(pos));}
    private static TileArchitectTable place(TestContext c){
        c.setBlockState(POS,Blocks.AIR);c.setBlockState(POS,BCBuildersBlocks.architect.getDefaultState().with(BCBuildersBlocks.architect.getFacingProperty(),Direction.NORTH));
        var tile=(TileArchitectTable)c.getBlockEntity(POS);tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.architect));return tile;
    }
    private static TileArchitectTable area(TestContext c,BlockPos size){
        var tile=place(c);var nbt=tile.createNbt();var box=new buildcraft.lib.misc.data.Box();box.setMin(tile.getPos().south());box.setMax(box.min().add(size).add(-1,-1,-1));nbt.put("box",box.writeToNBT());nbt.putBoolean("isValid",true);tile.readNbt(nbt);return tile;
    }
    private static void ticks(TileArchitectTable tile,int n){for(int i=0;i<n;i++)tile.tick();}
    private static ItemStack scan(TileArchitectTable tile,EnumSnapshotType type){tile.invSnapshotIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(type));ticks(tile,10);var stack=tile.invSnapshotOut.getStackInSlot(0).copy();tile.invSnapshotOut.setStackInSlot(0,ItemStack.EMPTY);return stack;}
    private static Snapshot stored(TileArchitectTable tile,ItemStack stack){var header=BCBuildersItems.snapshot.getHeader(stack);return header==null?null:GlobalSavedDataSnapshots.get(tile.getWorld()).getSnapshot(header.key);}
    private static TileArchitectTable reload(TileArchitectTable tile){var w=tile.getWorld();var p=tile.getPos();var s=tile.getCachedState();var n=tile.createNbtWithIdentifyingData();w.removeBlockEntity(p);var fresh=(TileArchitectTable)BlockEntity.createFromNbt(p,s,n);w.addBlockEntity(fresh);fresh.onLoad();return fresh;}
    private static TileMarkerVolume markers(TestContext c){var near=POS.south();for(var p:List.of(near,near.south(2),near.east(2))){c.setBlockState(p.down(),Blocks.STONE);c.setBlockState(p,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));((TileMarkerVolume)c.getBlockEntity(p)).onLoad();}var marker=(TileMarkerVolume)c.getBlockEntity(near);marker.onManualConnectionAttempt(c.createMockCreativeServerPlayerInWorld());return marker;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeMarkersOwnershipSelectionAndReload(TestContext c){
        var marker=markers(c);var min=marker.min();var max=marker.max();var tile=place(c);c.assertTrue(tile.hasArea() && tile.markerBox && tile.box.min().equals(min) && tile.box.max().equals(max) && c.getWorld().isAir(min),"Architect failed native marker adoption");
        var owner=tile.getOwner();tile.name="Native design";tile=reload(tile);c.assertTrue(tile.hasArea() && tile.getOwner().equals(owner) && tile.name.equals("Native design"),"Architect selection/name/owner lost on reload");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protectedDisconnectedAndNullPlacerMarkersAreSafe(TestContext c){
        var marker=markers(c);var p=marker.getPos().south(2);DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(p);
        try{var tile=place(c);c.assertTrue(!tile.hasArea() && marker.getCurrentConnection()!=null && c.getWorld().getBlockEntity(marker.getPos())==marker,"Protected selection consumed markers");}finally{DENIED.get(c.getWorld()).remove(p);}
        c.setBlockState(POS,Blocks.AIR);marker.removeFromWorld();
        var near=POS.south();c.setBlockState(near,BCCoreBlocks.markerVolume.getDefaultState().with(BCCoreBlocks.markerVolume.getFacingProperty(),Direction.UP));((TileMarkerVolume)c.getBlockEntity(near)).onLoad();var tile=place(c);tile.onPlacedBy(null,ItemStack.EMPTY);c.assertTrue(!tile.hasArea(),"Disconnected marker became scan area");c.setBlockState(near,Blocks.AIR);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void repeatBlueprintScansDoNotRetainPaletteAndPersistDurably(TestContext c){
        var tile=area(c,new BlockPos(2,1,1));for(var p:BlockPos.iterate(tile.box.min(),tile.box.max()))c.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());tile.name="Felix design";
        var first=scan(tile,EnumSnapshotType.BLUEPRINT);var header=BCBuildersItems.snapshot.getHeader(first);c.assertTrue(header!=null && header.name.equals("Felix design") && stored(tile,first) instanceof Blueprint b && b.palette.size()==1,"First blueprint missing data/header");
        for(var p:BlockPos.iterate(tile.box.min(),tile.box.max()))c.getWorld().setBlockState(p,Blocks.OAK_LOG.getDefaultState());
        var second=scan(tile,EnumSnapshotType.BLUEPRINT);GlobalSavedDataSnapshots.reInit(TileBC_Neptune.NetSide.SERVER);var saved=(Blueprint)stored(tile,second);
        c.assertTrue(saved!=null && saved.palette.size()==1 && !saved.key.equals(header.key) && saved.data.length==2,"Repeat scan retained stale palette or failed disk reload");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void replacingInputRestartsCaptureWithoutMixingTemplateData(TestContext c){
        var tile=area(c,new BlockPos(8,8,8));for(var p:BlockPos.iterate(tile.box.min(),tile.box.max()))c.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());tile.invSnapshotIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(EnumSnapshotType.BLUEPRINT));tile.tick();
        c.assertTrue(tile.getScanned()==300 && tile.invSnapshotOut.getStackInSlot(0).isEmpty(),"Blueprint scan exceeded per-tick budget");
        for(var p:BlockPos.iterate(tile.box.min(),tile.box.max()))c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());var output=scan(tile,EnumSnapshotType.TEMPLATE);
        c.assertTrue(stored(tile,output) instanceof Template t && t.data.isEmpty() && tile.invSnapshotIn.getStackInSlot(0).isEmpty(),"Swapped input mixed partial old observations");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void restartDiscardsPartialObservationsButPreservesInput(TestContext c){
        var tile=area(c,new BlockPos(8,8,8));tile.invSnapshotIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(EnumSnapshotType.BLUEPRINT));tile.tick();c.assertTrue(tile.getScanned()==300,"Expected partial capture");var old=tile;tile=reload(tile);old.tick();
        c.assertTrue(tile.getScanned()==0 && !tile.invSnapshotIn.getStackInSlot(0).isEmpty() && old.invSnapshotOut.getStackInSlot(0).isEmpty(),"Restart lost input or stale tile finished capture");
        ticks(tile,3);c.assertTrue(stored(tile,tile.invSnapshotOut.getStackInSlot(0)) instanceof Blueprint,"Reloaded scan did not complete");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unloadedAreasPauseWithoutLoadingOrConsumingAndOversizeIsRejected(TestContext c){
        var tile=area(c,new BlockPos(1,1,1));var nbt=tile.createNbt();var box=new buildcraft.lib.misc.data.Box();var far=new BlockPos(1_000_000,64,1_000_000);box.setMin(far);box.setMax(far);nbt.put("box",box.writeToNBT());tile.readNbt(nbt);tile.invSnapshotIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(EnumSnapshotType.TEMPLATE));ticks(tile,3);
        c.assertTrue(tile.getScanStatus()==3 && tile.getScanned()==0 && !c.getWorld().isChunkLoaded(far) && !tile.invSnapshotIn.getStackInSlot(0).isEmpty(),"Architect force-loaded/consumed unloaded area");
        box.setMax(far.add(1_048_576,0,0));nbt.put("box",box.writeToNBT());tile.readNbt(nbt);tile.tick();c.assertTrue(!tile.hasArea(),"Oversized scan was accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeItemStorageFiltersTransactionsAndExtractsOutputOnly(TestContext c){
        var tile=area(c,new BlockPos(1,1,1));var variant=ItemVariant.of(BCBuildersItems.snapshot.getClean(EnumSnapshotType.TEMPLATE));
        for(var side:Direction.values()){var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),side);try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(ItemVariant.of(Items.DIAMOND),1,tx)==0 && storage.insert(variant,3,tx)==1,"Snapshot filter or slot limit wrong");tile.tick();c.assertTrue(tile.invSnapshotOut.getStackInSlot(0).isEmpty(),"Scan ran inside input transaction");}}
        c.assertTrue(tile.invSnapshotIn.getStackInSlot(0).isEmpty(),"Aborted snapshot input leaked");var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),Direction.UP);
        try(var tx=Transaction.openOuter()){storage.insert(variant,1,tx);tx.commit();}try(var tx=Transaction.openOuter()){c.assertTrue(storage.extract(variant,1,tx)==0,"Automation extracted input slot");}tile.tick();
        var output=ItemVariant.of(tile.invSnapshotOut.getStackInSlot(0));try(var tx=Transaction.openOuter()){c.assertTrue(storage.extract(output,1,tx)==1,"Output unavailable");}c.assertTrue(!tile.invSnapshotOut.getStackInSlot(0).isEmpty(),"Aborted output lost blueprint");
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.extract(output,1,tx)==1,"Output unavailable after rollback");tx.commit();}c.assertTrue(tile.invSnapshotOut.getStackInSlot(0).isEmpty(),"Committed output duplicated");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void snapshotWriteFailureRetainsInputAndRetries(TestContext c)throws Exception{
        var tile=area(c,new BlockPos(2,3,4));tile.box.setMin(tile.getPos().add(7,1,9));tile.box.setMax(tile.box.min().add(1,2,3));for(var p:BlockPos.iterate(tile.box.min(),tile.box.max()))c.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());
        var expected=new Template();expected.size=tile.box.size();expected.offset=tile.box.min().subtract(tile.getPos().south());expected.facing=Direction.NORTH;expected.data=new BitSet();expected.computeKey();
        var file=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("snapshots-server").resolve(expected.key+".bcnbt");GlobalSavedDataSnapshots.get(c.getWorld());
        GlobalSavedDataSnapshots.get(c.getWorld()).removeSnapshot(expected.key); java.nio.file.Files.createDirectories(file.getParent()); java.nio.file.Files.createDirectory(file);
        try{tile.invSnapshotIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(EnumSnapshotType.TEMPLATE));tile.tick();c.assertTrue(tile.getScanStatus()==4 && !tile.invSnapshotIn.getStackInSlot(0).isEmpty() && tile.invSnapshotOut.getStackInSlot(0).isEmpty(),"Failed write consumed blank or produced dangling header");}finally{java.nio.file.Files.delete(file);}
        ticks(tile,25);c.assertTrue(stored(tile,tile.invSnapshotOut.getStackInSlot(0)) instanceof Template,"Save did not recover after failure");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void guiEnvelopeNameValidationQuickMoveAndStaleHandler(TestContext c)throws Exception{
        var tile=area(c,new BlockPos(1,1,1));var player=c.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ()-2,0,0);var handler=new ContainerArchitectTable(player,9,tile);player.currentScreenHandler=handler;
        int nameId=-1;for(int i=0;i<100;i++)if(handler.getIdAllocator().getNameFor(i).equals("NAME"))nameId=i;c.assertTrue(nameId>=0,"Name message missing");
        var buffer=PacketBufferBC.write(b->b.writeString("Remake by FelixMantra"));try{handler.readMessage(nameId,buffer,false,null);}finally{buffer.release();}c.assertTrue(tile.name.equals("Remake by FelixMantra"),"Name not applied");
        buffer=PacketBufferBC.write(b->{b.writeString("bad");b.writeByte(1);});boolean rejected=false;try{handler.readMessage(nameId,buffer,false,null);}catch(java.io.IOException invalid){rejected=true;}finally{buffer.release();}c.assertTrue(rejected && tile.name.equals("Remake by FelixMantra"),"Malformed name partially changed tile");
        buffer=PacketBufferBC.write(b->tile.writePayload(TileBC_Neptune.NET_GUI_DATA,b,TileBC_Neptune.NetSide.SERVER));try{tile.readPayload(TileBC_Neptune.NET_GUI_DATA,buffer,TileBC_Neptune.NetSide.CLIENT,null);c.assertTrue(!buffer.isReadable(),"GUI owner/delta tail not consumed");}finally{buffer.release();}
        player.getInventory().setStack(9,BCBuildersItems.snapshot.getClean(EnumSnapshotType.TEMPLATE));handler.quickMove(player,0);c.assertTrue(!tile.invSnapshotIn.getStackInSlot(0).isEmpty() && player.getInventory().getStack(9).isEmpty(),"Native snapshot quick move failed");tile.tick();handler.quickMove(player,37);c.assertTrue(tile.invSnapshotOut.getStackInSlot(0).isEmpty(),"Native output quick move failed");
        var fresh=reload(tile);buffer=PacketBufferBC.write(b->b.writeString("stale"));try{handler.readMessage(nameId,buffer,false,null);}finally{buffer.release();}c.assertTrue(!handler.canUse(player) && fresh.name.equals("Remake by FelixMantra"),"Stale screen renamed replacement");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedStatusAndScanPacketsAreAtomic(TestContext c)throws Exception{
        var tile=area(c,new BlockPos(1,1,1));tile.name="unchanged";var before=tile.box.min();
        var packet=PacketBufferBC.write(b->tile.writePayload(TileBC_Neptune.NET_RENDER_DATA,b,TileBC_Neptune.NetSide.SERVER));packet.writerIndex(packet.writerIndex()-1);boolean rejected=false;
        try{tile.readPayload(TileBC_Neptune.NET_RENDER_DATA,packet,TileBC_Neptune.NetSide.CLIENT,null);}catch(IndexOutOfBoundsException|java.io.IOException invalid){rejected=true;}finally{packet.release();}c.assertTrue(rejected && tile.name.equals("unchanged") && tile.box.min().equals(before),"Truncated status partially changed state");
        packet=PacketBufferBC.write(b->b.writeVarInt(17));rejected=false;try{tile.readPayload(TileArchitectTable.NET_SCAN,packet,TileBC_Neptune.NetSide.CLIENT,null);}catch(java.io.IOException invalid){rejected=true;}finally{packet.release();}c.assertTrue(rejected,"Oversized scan batch accepted");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blockedSnapshotDirectoryIsRecoverableWithoutCrashing(TestContext c)throws Exception{
        // Keep the blocked-directory fixture independent of other tests' real local collections.
        var fixture=java.nio.file.Files.createTempDirectory(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(),"blocked-snapshot-");
        var path=fixture.resolve("snapshots");
        var constructor=GlobalSavedDataSnapshots.class.getDeclaredConstructor(TileBC_Neptune.NetSide.class);constructor.setAccessible(true);
        var store=constructor.newInstance(TileBC_Neptune.NetSide.CLIENT);
        var directory=GlobalSavedDataSnapshots.class.getDeclaredField("snapshotsFile");directory.setAccessible(true);directory.set(store,path.toFile());
        var snapshot=new Template();snapshot.size=new BlockPos(1,1,1);snapshot.offset=BlockPos.ORIGIN;snapshot.facing=Direction.NORTH;snapshot.data=new BitSet();snapshot.computeKey();
        java.nio.file.Files.writeString(path,"blocked snapshot directory");
        try{c.assertTrue(!store.addSnapshot(snapshot),"Blocked directory was accepted");}
        finally{java.nio.file.Files.delete(path);}
        try{c.assertTrue(store.addSnapshot(snapshot) && store.getSnapshot(snapshot.key)!=null,"Snapshot directory did not recover");}
        finally{store.removeSnapshot(snapshot.key);java.nio.file.Files.delete(path);java.nio.file.Files.delete(fixture);}
        c.complete();
    }
    private static ItemStack craft(TestContext c,ItemStack[] input){var player=c.createMockCreativeServerPlayerInWorld();var h=new net.minecraft.screen.CraftingScreenHandler(1,player.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));for(int i=0;i<9;i++)h.getSlot(i+1).setStack(input[i]);h.onContentChanged(h.getSlot(1).inventory);var result=h.getSlot(0).getStack().copy();if(!result.isEmpty()){h.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,player);for(int i=1;i<=9;i++)c.assertTrue(h.getSlot(i).getStack().isEmpty(),"Recipe retained ingredients");}return result;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeSnapshotRecipesPreserveNbtOverNetworkAndArchitectRequiresBlankBlueprint(TestContext c){
        for(var type:EnumSnapshotType.values()){ItemStack[] input=new ItemStack[9];Arrays.fill(input,new ItemStack(Items.PAPER));for(int i=0;i<9;i++)input[i]=input[i].copy();input[4]=new ItemStack(type==EnumSnapshotType.BLUEPRINT?Items.LAPIS_LAZULI:Items.BLACK_DYE);var result=craft(c,input);c.assertTrue(result.getItem()==BCBuildersItems.snapshot && ItemSnapshot.EnumItemSnapshotType.getFromStack(result).snapshotType==type && BCBuildersItems.snapshot.getHeader(result)==null,"Recipe lost snapshot type");
            var recipe=(buildcraft.builders.recipe.SnapshotRecipe)c.getWorld().getRecipeManager().get(new Identifier("buildcraftbuilders:"+type.name().toLowerCase(Locale.ROOT))).orElseThrow();var packet=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());try{BCBuildersRecipes.SNAPSHOT.write(packet,recipe);var decoded=BCBuildersRecipes.SNAPSHOT.read(recipe.getId(),packet);c.assertTrue(ItemStack.areEqual(decoded.getOutput(c.getWorld().getRegistryManager()),result),"Recipe synchronization lost NBT");}finally{packet.release();}}
        Item[] items={Items.BLACK_DYE,BCCoreBlocks.markerVolume.asItem(),Items.BLACK_DYE,Items.YELLOW_DYE,Items.CRAFTING_TABLE,Items.YELLOW_DYE,buildcraft.core.BCCoreItems.gearDiamond,Items.AIR,buildcraft.core.BCCoreItems.gearDiamond};
        for(boolean template:List.of(true,false)){var input=Arrays.stream(items).map(ItemStack::new).toArray(ItemStack[]::new);input[7]=BCBuildersItems.snapshot.getClean(template?EnumSnapshotType.TEMPLATE:EnumSnapshotType.BLUEPRINT);var output=craft(c,input);c.assertTrue(template?output.isEmpty():output.isOf(BCBuildersBlocks.architect.asItem()),"Architect recipe did not distinguish blank blueprint");}
        var input=Arrays.stream(items).map(ItemStack::new).toArray(ItemStack[]::new);input[7]=BCBuildersItems.snapshot.getClean(EnumSnapshotType.BLUEPRINT);input[7].getOrCreateNbt().putString("header","used");c.assertTrue(craft(c,input).isEmpty(),"Architect recipe consumed named/used snapshot");
        input=Arrays.stream(items).map(ItemStack::new).toArray(ItemStack[]::new);input[7]=new ItemStack(Items.CHEST);c.assertTrue(craft(c,input).isOf(BCBuildersBlocks.builder.asItem()),"Builder survival recipe missing");
        for(var id:List.of("blueprint","template","architect_table","builder"))c.assertTrue(c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftbuilders:recipes/"+id))!=null,"Missing recipe unlock "+id);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void scannedBlueprintAndTemplateDriveNativeBuilder(TestContext c){
        var tile=area(c,new BlockPos(2,1,1));var source=tile.box.min();c.getWorld().setBlockState(source,Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X));c.getWorld().setBlockState(source.east(),Blocks.STONE.getDefaultState());
        for(var type:EnumSnapshotType.values()){
            var stack=scan(tile,type);var at=c.getAbsolutePos(new BlockPos(8,40,2));c.getWorld().setBlockState(at,Blocks.AIR.getDefaultState());c.getWorld().setBlockState(at,BCBuildersBlocks.builder.getDefaultState().with(BCBuildersBlocks.builder.getFacingProperty(),Direction.NORTH));var builder=(TileBuilder)c.getWorld().getBlockEntity(at);builder.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCBuildersBlocks.builder));
            c.getWorld().setBlockState(at.south(),Blocks.AIR.getDefaultState());c.getWorld().setBlockState(at.south().east(),Blocks.AIR.getDefaultState());builder.invSnapshot.setStackInSlot(0,stack);builder.invResources.setStackInSlot(0,new ItemStack(type==EnumSnapshotType.TEMPLATE?Items.STONE:Items.OAK_LOG,type==EnumSnapshotType.TEMPLATE?2:1));if(type==EnumSnapshotType.BLUEPRINT)builder.invResources.setStackInSlot(1,new ItemStack(Items.STONE));builder.getBattery().addPower(100*buildcraft.api.mj.MjAPI.MJ,false);for(int i=0;i<20;i++)builder.tick();
            c.assertTrue(builder.isFinished() && c.getWorld().getBlockState(at.south().east()).isOf(Blocks.STONE) && c.getWorld().getBlockState(at.south()).isOf(type==EnumSnapshotType.TEMPLATE?Blocks.STONE:Blocks.OAK_LOG),"Scanned snapshot failed native reconstruction: "+type);
        }c.complete();
    }
}
