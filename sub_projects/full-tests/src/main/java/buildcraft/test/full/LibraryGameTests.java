/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.schematics.*;
import buildcraft.builders.*;
import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.lib.net.*;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public final class LibraryGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,175,2);
    private static Template plan(int seed,boolean large){
        var t=new Template();t.size=large?new BlockPos(128,128,64):new BlockPos(8,1,1);t.offset=new BlockPos(seed,0,0);t.facing=Direction.NORTH;
        byte[] bits=new byte[large?131072:1];new Random(seed).nextBytes(bits);t.data=BitSet.valueOf(bits);t.computeKey();return t;
    }
    private static Snapshot.Header header(Snapshot s){return new Snapshot.Header(s.key,UUID.fromString("2177f8f7-9641-4f8b-bb2e-29df78b6b24a"),new Date(4754),"Felix collection");}
    private static TileElectronicLibrary machine(TestContext c){
        var at=c.getAbsolutePos(POS);c.getWorld().setBlockState(at,Blocks.AIR.getDefaultState());c.getWorld().setBlockState(at,BCBuildersBlocks.library.getDefaultState());return (TileElectronicLibrary)c.getWorld().getBlockEntity(at);
    }
    private static ServerPlayerEntity player(TestContext c,TileElectronicLibrary t){
        var p=c.createMockCreativeServerPlayerInWorld();p.refreshPositionAndAngles(t.getPos().getX()+.5,t.getPos().getY()+2,t.getPos().getZ()+.5,0,0);p.getInventory().clear();return p;
    }
    private static ContainerElectronicLibrary open(TestContext c,TileElectronicLibrary t){
        var p=player(c,t);BCBuildersScreens.openLibrary(p,t.getPos());c.assertTrue(p.currentScreenHandler instanceof ContainerElectronicLibrary,"Native library screen missing");return (ContainerElectronicLibrary)p.currentScreenHandler;
    }
    private static UUID token(ContainerElectronicLibrary h)throws Exception{var f=ContainerElectronicLibrary.class.getDeclaredField("token");f.setAccessible(true);return (UUID)f.get(h);}
    private static void message(ContainerElectronicLibrary h,String name,IPayloadWriter writer)throws Exception{
        int id=-1;for(int i=0;i<32;i++)if(h.getIdAllocator().getNameFor(i).equals(name))id=i;
        if(id<0)throw new IllegalStateException(name);
        var packet=MessageContainer.of(h.syncId,id,writer);NetworkRouting.validateServerbound(packet);var b=packet.getPayload();
        try{h.readMessage(id,b,false,null);if(b.isReadable())throw new IllegalStateException("Unread library packet");}finally{b.release();}
    }
    private static void tick(TileElectronicLibrary t,int count){for(int i=0;i<count;i++)t.tick();}
    private static UUID request(ContainerElectronicLibrary h,Snapshot s)throws Exception{
        message(h,"select",b->b.writeByteArray(s.key.hash));tick(h.tile,51);return token(h);
    }
    private static void upload(ContainerElectronicLibrary h,UUID id,Snapshot s)throws Exception{
        byte[] bytes=LibraryTransfer.pack(s,header(s));message(h,"up_begin",b->{b.writeUuid(id);b.writeInt(bytes.length);});
        for(int offset=0,index=0;offset<bytes.length;offset+=LibraryTransfer.CHUNK_BYTES,index++){
            byte[] chunk=Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+LibraryTransfer.CHUNK_BYTES));int n=index;
            message(h,"up_part",b->{b.writeUuid(id);b.writeInt(n);b.writeByteArray(chunk);});
        }
    }
    private static void bad(TestContext c,Throwing work){try{work.run();throw new AssertionError("Malformed library data accepted");}catch(IOException expected){}catch(Exception other){throw new RuntimeException(other);}}
    private interface Throwing{void run()throws Exception;}
    private static byte[] compressed(NbtCompound n)throws IOException{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(new GZIPOutputStream(bytes))){NbtIo.write(n,out);}return bytes.toByteArray();}

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void completeMaximumVolumeTemplateCrossesMultipleChunksWithMetadata(TestContext c)throws Exception{
        var t=plan(801,true);byte[] bytes=LibraryTransfer.pack(t,header(t));c.assertTrue(bytes.length>3*LibraryTransfer.CHUNK_BYTES,"Fixture did not exercise multipart transport");
        var receiver=new LibraryTransfer.Receiver(bytes.length);for(int pos=0,n=0;pos<bytes.length;pos+=LibraryTransfer.CHUNK_BYTES,n++)
            receiver.accept(n,Arrays.copyOfRange(bytes,pos,Math.min(bytes.length,pos+LibraryTransfer.CHUNK_BYTES)));
        var restored=(Template)LibraryTransfer.unpack(receiver.complete(),t.key);
        c.assertTrue(restored.data.equals(t.data)&&restored.size.equals(t.size)&&header(t).equals(restored.key.header)&&t.key.header==null,"Multipart transfer changed data/metadata/source");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blueprintCodecPreservesChestDataPaletteAndNativeEntities(TestContext c)throws Exception{
        var at=c.getAbsolutePos(POS);var w=c.getWorld();w.setBlockState(at,Blocks.CHEST.getDefaultState());var gems=new ItemStack(Items.DIAMOND,3);gems.getOrCreateNbt().putString("Felix","Original chest");((ChestBlockEntity)w.getBlockEntity(at)).setStack(0,gems);
        var b=new Blueprint();b.size=new BlockPos(1,1,1);b.offset=BlockPos.ORIGIN;b.facing=Direction.NORTH;b.data=new int[]{0};b.palette.add(SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(w,at,at,w.getBlockState(at),Blocks.CHEST)));
        b.entities.add(SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(w,at,new net.minecraft.entity.decoration.ArmorStandEntity(w,at.getX()+.5,at.getY(),at.getZ()+.5))));b.computeKey();
        var restored=(Blueprint)LibraryTransfer.unpack(LibraryTransfer.pack(b,header(b)),b.key);restored.key=b.key;
        c.assertTrue(restored.serializeNBT().equals(b.serializeNBT())&&restored.entities.size()==1,"Blueprint lost chest/entity data");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedLengthsOrderContentAndRecursiveHeadersAreRejected(TestContext c)throws Exception{
        bad(c,()->new LibraryTransfer.Receiver(0));bad(c,()->new LibraryTransfer.Receiver(LibraryTransfer.MAX_COMPRESSED+1));
        var receiver=new LibraryTransfer.Receiver(2);bad(c,()->receiver.accept(1,new byte[2]));bad(c,()->receiver.accept(0,new byte[1]));bad(c,receiver::complete);receiver.accept(0,new byte[2]);bad(c,()->receiver.accept(1,new byte[0]));
        var t=plan(802,false);bad(c,()->LibraryTransfer.unpack(new byte[]{1,2,3},t.key));bad(c,()->LibraryTransfer.unpack(LibraryTransfer.pack(t,header(t)),plan(803,false).key));
        var n=Snapshot.writeToNBT(t);n.getCompound("key").put("header",header(t).createNbt());n.getCompound("key").getCompound("header").getCompound("key").put("header",new NbtCompound());bad(c,()->LibraryTransfer.unpack(compressed(n),t.key));
        n.getCompound("key").remove("header");n.putByteArray("data",new byte[]{0});bad(c,()->LibraryTransfer.unpack(compressed(n),t.key));c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void localMetadataUpdatesUseContentIdentityAndDeletionDoesNotEraseServer(TestContext c)throws Exception{
        var t=plan(804,false);var local=GlobalSavedDataSnapshots.get(NetSide.CLIENT);var server=GlobalSavedDataSnapshots.get(NetSide.SERVER);local.removeSnapshot(t.key);
        c.assertTrue(server.addSnapshot(t),"Server save failed");t.key=new Snapshot.Key(t.key,header(t));c.assertTrue(local.addSnapshot(t),"Local metadata save failed");
        GlobalSavedDataSnapshots.reInit(NetSide.CLIENT);local=GlobalSavedDataSnapshots.get(NetSide.CLIENT);var bare=LibraryTransfer.key(t.key.hash);
        c.assertTrue(local.getSnapshot(bare)!=null&&local.getSnapshot(t.key)!=null&&local.getSnapshot(bare).key.header.name.equals("Felix collection"),"Metadata cache identity mismatch");
        t.key=new Snapshot.Key(bare,new Snapshot.Header(bare,header(t).owner,new Date(123),"Renamed collection"));c.assertTrue(local.addSnapshot(t),"Metadata update failed");
        GlobalSavedDataSnapshots.reInit(NetSide.CLIENT);local=GlobalSavedDataSnapshots.get(NetSide.CLIENT);
        c.assertTrue(local.getSnapshot(bare).key.header.name.equals("Renamed collection")&&local.getList().stream().filter(k->Arrays.equals(k.hash,bare.hash)).count()==1,"Metadata update was not durable/deduplicated");
        c.assertTrue(local.removeSnapshot(bare)&&local.getSnapshot(bare)==null&&server.getSnapshot(bare)!=null,"Local delete erased server or left cache");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeUploadConsumesAnySnapshotMediaOnlyAfterDurableFullSave(TestContext c)throws Exception{
        var t=machine(c);var h=open(c,t);var plan=plan(805,true);GlobalSavedDataSnapshots.get(NetSide.SERVER).removeSnapshot(plan.key);
        t.invUpIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(EnumSnapshotType.BLUEPRINT));UUID id=request(h,plan);c.assertTrue(id!=null&&h.status()==4,"Upload was not requested");upload(h,id,plan);
        var result=t.invUpOut.getStackInSlot(0);var savedHeader=BCBuildersItems.snapshot.getHeader(result);GlobalSavedDataSnapshots.reInit(NetSide.SERVER);
        c.assertTrue(h.status()==5&&t.invUpIn.getStackInSlot(0).isEmpty()&&savedHeader.equals(header(plan))&&buildcraft.builders.item.ItemSnapshot.EnumItemSnapshotType.getFromStack(result).snapshotType==EnumSnapshotType.TEMPLATE,"Upload media/result/header wrong");
        c.assertTrue(((Template)GlobalSavedDataSnapshots.get(NetSide.SERVER).getSnapshot(plan.key)).data.equals(plan.data),"Uploaded file did not survive cache restart");h.onClosed(h.player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void failedWorldSaveRetainsInputAndRecoversOnRetry(TestContext c)throws Exception{
        var plan=plan(806,false);var store=GlobalSavedDataSnapshots.get(NetSide.SERVER);store.removeSnapshot(plan.key);
        var path=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("snapshots-server").resolve(plan.key+".bcnbt");java.nio.file.Files.createDirectories(path);
        var t=machine(c);var h=open(c,t);var media=BCBuildersItems.snapshot.getClean(EnumSnapshotType.TEMPLATE);t.invUpIn.setStackInSlot(0,media.copy());
        try{upload(h,request(h,plan),plan);c.assertTrue(h.status()==7&&ItemStack.areEqual(media,t.invUpIn.getStackInSlot(0))&&t.invUpOut.getStackInSlot(0).isEmpty(),"Failed save consumed input");}finally{java.nio.file.Files.delete(path);}
        tick(t,151);upload(h,token(h),plan);c.assertTrue(h.status()==5&&!t.invUpOut.getStackInSlot(0).isEmpty(),"Retry did not recover");h.onClosed(h.player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exportWaitsForExactAcknowledgementAndKeepsOriginalMedia(TestContext c)throws Exception{
        var plan=plan(807,true);GlobalSavedDataSnapshots.get(NetSide.SERVER).addSnapshot(plan);var t=machine(c);var h=open(c,t);var media=BCBuildersItems.snapshot.getUsed(plan.getType(),header(plan));t.invDownIn.setStackInSlot(0,media.copy());tick(t,51);var id=token(h);
        message(h,"down_ack",b->{b.writeUuid(id);b.writeByte(1);});c.assertTrue(!t.invDownIn.getStackInSlot(0).isEmpty(),"Early ack consumed input");
        tick(t,10);message(h,"down_ack",b->{b.writeUuid(UUID.randomUUID());b.writeByte(1);});c.assertTrue(!t.invDownIn.getStackInSlot(0).isEmpty(),"Foreign ack consumed input");
        try(var tx=Transaction.openOuter()){message(h,"down_ack",b->{b.writeUuid(id);b.writeByte(1);});c.assertTrue(!t.invDownIn.getStackInSlot(0).isEmpty(),"Ack mutated inside foreign transaction");}
        message(h,"down_ack",b->{b.writeUuid(id);b.writeByte(1);});c.assertTrue(t.invDownIn.getStackInSlot(0).isEmpty()&&ItemStack.areEqual(media,t.invDownOut.getStackInSlot(0)),"Ack lost original media");h.onClosed(h.player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void changedAndRestoredInputOrClosedScreenInvalidatesUpload(TestContext c)throws Exception{
        var plan=plan(808,false);var t=machine(c);var h=open(c,t);var blank=BCBuildersItems.snapshot.getClean(plan.getType());t.invUpIn.setStackInSlot(0,blank.copy());var id=request(h,plan);
        t.invUpIn.setStackInSlot(0,ItemStack.EMPTY);t.invUpIn.setStackInSlot(0,blank.copy());upload(h,id,plan);c.assertTrue(t.invUpOut.getStackInSlot(0).isEmpty()&&!t.invUpIn.getStackInSlot(0).isEmpty(),"Stale transfer consumed replaced input");
        tick(t,52);var second=token(h);h.onClosed(h.player);upload(h,second,plan);c.assertTrue(t.invUpOut.getStackInSlot(0).isEmpty()&&!t.invUpIn.getStackInSlot(0).isEmpty(),"Closed screen consumed input");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void concurrentViewersHaveSeparateSelectionsAndReleaseMachineOnClose(TestContext c)throws Exception{
        var t=machine(c);var first=open(c,t);var second=open(c,t);var a=plan(809,false);var b=plan(810,false);
        message(first,"select",buf->buf.writeByteArray(a.key.hash));message(second,"select",buf->buf.writeByteArray(b.key.hash));t.invUpIn.setStackInSlot(0,BCBuildersItems.snapshot.getClean(a.getType()));tick(t,51);
        c.assertTrue(token(first)!=null&&token(second)==null&&second.status()==8,"Multiple viewers acquired the same input");first.onClosed(first.player);tick(t,51);upload(second,token(second),b);
        c.assertTrue(BCBuildersItems.snapshot.getHeader(t.invUpOut.getStackInSlot(0)).key.equals(b.key),"Closing viewer leaked its selection into another collection");second.onClosed(second.player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingFilesAndTimeoutKeepMediaAndRequireAnOpenViewer(TestContext c)throws Exception{
        var plan=plan(811,false);var t=machine(c);var media=BCBuildersItems.snapshot.getUsed(plan.getType(),header(plan));GlobalSavedDataSnapshots.get(NetSide.SERVER).removeSnapshot(plan.key);t.invDownIn.setStackInSlot(0,media.copy());tick(t,300);
        c.assertTrue(ItemStack.areEqual(media,t.invDownIn.getStackInSlot(0)),"Library exported without a local destination");var h=open(c,t);tick(t,51);c.assertTrue(h.status()==6&&!t.invDownIn.getStackInSlot(0).isEmpty(),"Missing file consumed media");
        GlobalSavedDataSnapshots.get(NetSide.SERVER).addSnapshot(plan);tick(t,152);tick(t,202);c.assertTrue(h.status()==9&&!t.invDownIn.getStackInSlot(0).isEmpty(),"Unacknowledged transfer did not time out safely");h.onClosed(h.player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeSlotsAutomationPersistenceAndDistanceAreEnforced(TestContext c)throws Exception{
        var t=machine(c);var h=open(c,t);var p=(ServerPlayerEntity)h.player;var s=plan(812,false);var blank=BCBuildersItems.snapshot.getClean(s.getType());var used=BCBuildersItems.snapshot.getUsed(s.getType(),header(s));
        p.getInventory().setStack(9,new ItemStack(Items.DIAMOND));c.assertTrue(h.quickMove(p,4).isEmpty(),"Invalid item entered library");p.getInventory().setStack(9,blank.copy());h.quickMove(p,4);c.assertTrue(!t.invUpIn.getStackInSlot(0).isEmpty()&&t.invDownIn.getStackInSlot(0).isEmpty(),"Blank entered download slot");t.invDownOut.setStackInSlot(0,used.copy());
        var storage=ItemStorage.SIDED.find(c.getWorld(),t.getPos(),Direction.UP);try(var tx=Transaction.openOuter()){
            c.assertTrue(storage.extract(ItemVariant.of(blank),1,tx)==0&&storage.extract(ItemVariant.of(used),1,tx)==1,"Automation access directions incorrect");
        }
        c.assertTrue(!t.invDownOut.getStackInSlot(0).isEmpty(),"Aborted extraction lost media");var n=t.createNbtWithIdentifyingData();var pos=t.getPos();var state=t.getCachedState();h.onClosed(p);c.getWorld().removeBlockEntity(pos);var restored=(TileElectronicLibrary)BlockEntity.createFromNbt(pos,state,n);c.getWorld().addBlockEntity(restored);restored.onLoad();
        c.assertTrue(ItemStack.areEqual(restored.invUpIn.getStackInSlot(0),blank)&&ItemStack.areEqual(restored.invDownOut.getStackInSlot(0),used)&&!h.canUse(p),"Reload lost media or stale screen retained access");var second=open(c,restored);second.player.refreshPositionAndAngles(pos.getX()+100,pos.getY(),pos.getZ(),0,0);c.assertTrue(!second.canUse(second.player)&&second.quickMove(second.player,1).isEmpty(),"Distant library access accepted");second.onClosed(second.player);c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void librarySurvivalRecipeProducesNativeMachine(TestContext c){
        var t=machine(c);var p=player(c,t);var h=new net.minecraft.screen.CraftingScreenHandler(1,p.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),t.getPos()));
        var items=new Item[]{Items.IRON_INGOT,Items.REDSTONE,Items.IRON_INGOT,buildcraft.core.BCCoreItems.gearGold,Items.BOOK,buildcraft.core.BCCoreItems.gearGold,Items.IRON_INGOT,Items.CHEST,Items.IRON_INGOT};
        for(int i=0;i<9;i++)h.getSlot(i+1).setStack(new ItemStack(items[i]));h.onContentChanged(h.getSlot(1).inventory);c.assertTrue(h.getSlot(0).getStack().isOf(BCBuildersBlocks.library.asItem()),"Native library recipe missing");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void trailingPacketsCannotChangeSelectionOrConsumeMedia(TestContext c)throws Exception{
        var plan=plan(813,false);GlobalSavedDataSnapshots.get(NetSide.SERVER).addSnapshot(plan);var t=machine(c);var h=open(c,t);
        bad(c,()->message(h,"select",b->{b.writeByteArray(plan.key.hash);b.writeByte(1);}));c.assertTrue(h.selection()==null,"Trailing selection packet changed state");
        var media=BCBuildersItems.snapshot.getUsed(plan.getType(),header(plan));t.invDownIn.setStackInSlot(0,media.copy());tick(t,53);var id=token(h);
        bad(c,()->message(h,"down_ack",b->{b.writeUuid(id);b.writeByte(1);b.writeByte(7);}));
        c.assertTrue(ItemStack.areEqual(media,t.invDownIn.getStackInSlot(0))&&t.invDownOut.getStackInSlot(0).isEmpty(),"Trailing acknowledgement consumed media");h.onClosed(h.player);c.complete();
    }

}
