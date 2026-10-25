/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.container;

import java.io.IOException;
import java.util.*;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.slot.*;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;

/** Ordered, screen-scoped transfers. No inventory mutation occurs before durable storage succeeds. */
public class ContainerElectronicLibrary extends ContainerBCTile<TileElectronicLibrary> {
    private static final IdAllocator IDS=ContainerBC_Neptune.IDS.makeChild("electronic_library");
    private static final int SELECT=IDS.allocId("select"),DOWN_BEGIN=IDS.allocId("down_begin"),DOWN_PART=IDS.allocId("down_part"),
        DOWN_ACK=IDS.allocId("down_ack"),UP_REQUEST=IDS.allocId("up_request"),UP_BEGIN=IDS.allocId("up_begin"),
        UP_PART=IDS.allocId("up_part"),CANCEL=IDS.allocId("cancel");
    private Snapshot.Key selected,jobKey;
    private UUID token;
    private ItemStack input=ItemStack.EMPTY;
    private long epoch;
    private boolean upload,closed;
    private int warmup,age,idle,cooldown,part,status,progress;
    private byte[] outgoing;
    private LibraryTransfer.Receiver incoming;
    private final PropertyDelegate properties=new PropertyDelegate(){
        public int size(){return 2;}
        public int get(int index){return index==0?status:progress;}
        public void set(int index,int value){if(index==0)status=value;else progress=value;}
    };
    public ContainerElectronicLibrary(PlayerEntity player,TileElectronicLibrary tile){this(player,0,tile);}
    public ContainerElectronicLibrary(PlayerEntity player,int syncId,TileElectronicLibrary tile){
        super(buildcraft.builders.BCBuildersScreens.LIBRARY,player,syncId,tile);
        addSlot(new SlotBase(tile.invDownIn,0,216,39));addSlot(new SlotOutput(tile.invDownOut,0,172,39));
        addSlot(new SlotBase(tile.invUpIn,0,172,83));addSlot(new SlotOutput(tile.invUpOut,0,216,83));
        addFullPlayerInventory(42,139);addProperties(properties);
        if(!player.getWorld().isClient)tile.opened(this);
    }
    @Override public IdAllocator getIdAllocator(){return IDS;}
    public int status(){return status;}
    public int progress(){return progress;}
    public Snapshot.Key selection(){return selected;}
    public boolean isOpen(){return !closed && player.currentScreenHandler==this && canUse(player);}
    private boolean valid(){
        return isOpen() && tile.isStorageAvailable() && tile.owns(this) && epoch==tile.inventoryEpoch()
            && Transaction.getCurrentUnsafe()==null && ItemStack.areEqual(input,(upload?tile.invUpIn:tile.invDownIn).getStackInSlot(0))
            && (upload?tile.invUpOut:tile.invDownOut).getStackInSlot(0).isEmpty();
    }
    public void select(Snapshot.Key key){
        if(!player.getWorld().isClient)return;
        selected=key;sendMessage(SELECT,b->b.writeByteArray(key==null?new byte[0]:key.hash));
    }
    public void cancel(){finish(0);}
    private void finish(int result){
        UUID previous=token;
        token=null;input=ItemStack.EMPTY;outgoing=null;incoming=null;jobKey=null;part=0;warmup=0;progress=0;age=0;idle=0;
        status=result;cooldown=result>=6?100:0;tile.release(this);
        if(previous!=null && !player.getWorld().isClient && player.currentScreenHandler==this)
            sendMessage(CANCEL,b->b.writeUuid(previous));
    }
    @Override public void onClosed(PlayerEntity player){closed=true;cancel();tile.closed(this);super.onClosed(player);}
    public void serverTick(){
        if(player.getWorld().isClient || Transaction.getCurrentUnsafe()!=null)return;
        if(token!=null){
            if(!valid()){finish(0);return;}
            if(++age>1200 || ++idle>200){finish(9);return;}
            if(warmup>0){progress=(50-warmup)*20;if(--warmup==0)begin();return;}
            if(!upload && outgoing!=null){
                int start=part*LibraryTransfer.CHUNK_BYTES;
                if(start<outgoing.length){
                    byte[] chunk=Arrays.copyOfRange(outgoing,start,Math.min(outgoing.length,start+LibraryTransfer.CHUNK_BYTES));
                    int index=part++;UUID id=token;sendMessage(DOWN_PART,b->{b.writeUuid(id);b.writeInt(index);b.writeByteArray(chunk);});
                    progress=(int)((long)(start+chunk.length)*1000/outgoing.length);idle=0;
                }
            }
            return;
        }
        if(cooldown>0){cooldown--;return;}
        if(!isOpen() || !tile.isStorageAvailable())return;
        var down=tile.invDownIn.getStackInSlot(0);var up=tile.invUpIn.getStackInSlot(0);
        if(!down.isEmpty() && tile.invDownOut.getStackInSlot(0).isEmpty())upload=false;
        else if(selected!=null && !up.isEmpty() && tile.invUpOut.getStackInSlot(0).isEmpty())upload=true;
        else{if(status!=5)status=0;return;}
        if(!tile.claim(this)){status=8;return;}
        input=(upload?up:down).copy();epoch=tile.inventoryEpoch();
        if(input.getCount()!=1 || !(input.getItem() instanceof ItemSnapshot)){finish(6);return;}
        token=UUID.randomUUID();warmup=50;age=0;idle=0;status=upload?2:1;progress=0;
        jobKey=upload?selected:null;
    }
    private void begin(){
        try{
            UUID id=token;idle=0;progress=0;
            if(upload){status=4;sendMessage(UP_REQUEST,b->{b.writeUuid(id);b.writeByteArray(jobKey.hash);});}
            else{
                var header=BCBuildersItems.snapshot.getHeader(input);
                if(header==null)throw new IOException("Missing library input header");
                var snapshot=GlobalSavedDataSnapshots.get(NetSide.SERVER).getSnapshot(header.key);
                if(snapshot==null || snapshot.getType()!=ItemSnapshot.EnumItemSnapshotType.getFromStack(input).snapshotType)
                    throw new IOException("Missing or mismatched library input");
                jobKey=LibraryTransfer.key(header.key.hash);outgoing=LibraryTransfer.pack(snapshot,header);status=3;
                sendMessage(DOWN_BEGIN,b->{b.writeUuid(id);b.writeByteArray(jobKey.hash);b.writeInt(outgoing.length);});
            }
        }catch(IOException|RuntimeException failure){finish(6);}
    }
    public void clientTick(){
        if(!player.getWorld().isClient || token==null)return;
        if(!isOpen()){finish(0);return;}
        if(++age>1200 || ++idle>200){UUID id=token;sendMessage(CANCEL,b->b.writeUuid(id));finish(9);return;}
        if(upload && outgoing!=null){
            int start=part*LibraryTransfer.CHUNK_BYTES;
            if(start<outgoing.length){
                byte[] chunk=Arrays.copyOfRange(outgoing,start,Math.min(outgoing.length,start+LibraryTransfer.CHUNK_BYTES));
                int index=part++;UUID id=token;sendMessage(UP_PART,b->{b.writeUuid(id);b.writeInt(index);b.writeByteArray(chunk);});idle=0;
            }
        }
    }
    private static void exact(PacketBufferBC buffer)throws IOException{if(buffer.isReadable())throw new IOException("Trailing library packet data");}
    @Override public void readMessage(int id,PacketBufferBC buffer,boolean isClient,Object context)throws IOException{
        super.readMessage(id,buffer,isClient,context);
        if(id==SELECT){
            byte[] hash=buffer.readByteArray(32);exact(buffer);
            if(isClient)throw new IOException("Invalid library direction");
            Snapshot.Key choice=hash.length==0?null:LibraryTransfer.key(hash);
            if(!isOpen())return;
            if(!Objects.equals(selected,choice)){if(upload)cancel();selected=choice;cooldown=0;}
        }else if(id==DOWN_BEGIN || id==UP_REQUEST){
            UUID request=buffer.readUuid();Snapshot.Key key=LibraryTransfer.key(buffer.readByteArray(32));int size=id==DOWN_BEGIN?buffer.readInt():0;exact(buffer);
            if(!isClient)throw new IOException("Invalid library direction");
            if(!isOpen())return;
            finish(0);token=request;jobKey=key;upload=id==UP_REQUEST;
            try{
                if(upload){
                    if(selected==null || !Arrays.equals(selected.hash,key.hash))throw new IOException("Library selection changed");
                    var snapshot=GlobalSavedDataSnapshots.get(NetSide.CLIENT).getSnapshot(key);
                    if(snapshot==null)throw new IOException("Missing local library entry");
                    outgoing=LibraryTransfer.pack(snapshot,LibraryTransfer.header(snapshot.key.header,key,player.getUuid()));
                    sendMessage(UP_BEGIN,b->{b.writeUuid(request);b.writeInt(outgoing.length);});
                }else incoming=new LibraryTransfer.Receiver(size);
            }catch(IOException|RuntimeException failure){sendMessage(CANCEL,b->b.writeUuid(request));finish(6);}
        }else if(id==UP_BEGIN){
            UUID request=buffer.readUuid();int size=buffer.readInt();exact(buffer);
            if(isClient)throw new IOException("Invalid library direction");
            if(!request.equals(token) || !upload || !valid() || warmup!=0)return;
            if(incoming!=null)throw new IOException("Duplicate library begin");
            incoming=new LibraryTransfer.Receiver(size);idle=0;
        }else if(id==DOWN_PART || id==UP_PART){
            UUID request=buffer.readUuid();int index=buffer.readInt();byte[] chunk=buffer.readByteArray(LibraryTransfer.CHUNK_BYTES);exact(buffer);
            if(isClient!=(id==DOWN_PART))throw new IOException("Invalid library direction");
            if(!request.equals(token) || !isOpen() || (!isClient && !valid()))return;
            if(incoming==null || upload==isClient)throw new IOException("Unrequested library chunk");
            boolean complete=incoming.accept(index,chunk);idle=0;progress=(int)(1000L*incoming.received()/incoming.total());
            if(complete){
                try{
                    var snapshot=LibraryTransfer.unpack(incoming.complete(),jobKey);
                    if(isClient){
                        boolean saved=GlobalSavedDataSnapshots.get(NetSide.CLIENT).addSnapshot(snapshot);
                        sendMessage(DOWN_ACK,b->{b.writeUuid(request);b.writeByte(saved?1:0);});finish(saved?5:7);
                    }else{
                        var metadata=LibraryTransfer.header(snapshot.key.header,jobKey,player.getUuid());
                        snapshot.key=jobKey;
                        if(!GlobalSavedDataSnapshots.get(NetSide.SERVER).addSnapshot(snapshot)){finish(7);return;}
                        if(!valid()){finish(0);return;}
                        tile.invUpIn.setStackInSlot(0,ItemStack.EMPTY);
                        tile.invUpOut.setStackInSlot(0,BCBuildersItems.snapshot.getUsed(snapshot.getType(),metadata));finish(5);
                    }
                }catch(IOException|RuntimeException failure){
                    if(isClient)sendMessage(DOWN_ACK,b->{b.writeUuid(request);b.writeByte(0);});finish(6);
                }
            }
        }else if(id==DOWN_ACK){
            UUID request=buffer.readUuid();int saved=buffer.readUnsignedByte();exact(buffer);
            if(isClient || saved>1)throw new IOException("Invalid library acknowledgement");
            if(!request.equals(token) || upload || outgoing==null || part*LibraryTransfer.CHUNK_BYTES<outgoing.length || !valid())return;
            if(saved==1){tile.invDownIn.setStackInSlot(0,ItemStack.EMPTY);tile.invDownOut.setStackInSlot(0,input.copy());finish(5);}
            else finish(7);
        }else if(id==CANCEL){
            UUID request=buffer.readUuid();exact(buffer);if(request.equals(token))finish(isClient?status:6);
        }
    }
}
