/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.snapshot;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.nbt.*;

/** Bounded, ordered transport for complete blueprints, independent of the small preview protocol. */
public final class LibraryTransfer {
    // Leave room for the screen envelope, transfer UUID and sequence below Minecraft's 32767-byte C2S limit.
    public static final int CHUNK_BYTES=30*1024, MAX_COMPRESSED=16*1024*1024, MAX_EXPANDED=64*1024*1024;
    private LibraryTransfer(){}
    public static Snapshot.Key key(byte[] hash)throws IOException{
        if(hash.length!=32)throw new IOException("Invalid library content hash");
        return new Snapshot.Key(new Snapshot.Key(),hash.clone());
    }
    public static Snapshot.Header header(Snapshot.Header original,Snapshot.Key key,UUID fallback){
        return new Snapshot.Header(key,original==null?fallback:original.owner,
            original==null?new Date():original.created,original==null?key.toString().substring(0,12):original.name);
    }
    public static byte[] pack(Snapshot snapshot,Snapshot.Header header)throws IOException{
        var copy=snapshot.copy();copy.key=new Snapshot.Key(copy.key,header(header,key(snapshot.key.hash),new UUID(0,0)));
        var bytes=new ByteArrayOutputStream();
        try(var gzip=new GZIPOutputStream(new FilterOutputStream(bytes){
            private int count;
            @Override public void write(int b)throws IOException{if(++count>MAX_COMPRESSED)throw new IOException("Library snapshot too large");out.write(b);}
            @Override public void write(byte[] b,int off,int len)throws IOException{if(len>MAX_COMPRESSED-count)throw new IOException("Library snapshot too large");count+=len;out.write(b,off,len);}
        });var out=new DataOutputStream(gzip)){NbtIo.write(Snapshot.writeToNBT(copy),out);}
        return bytes.toByteArray();
    }
    public static Snapshot unpack(byte[] bytes,Snapshot.Key expected)throws IOException{
        if(bytes.length==0||bytes.length>MAX_COMPRESSED)throw new IOException("Invalid library size");
        NbtCompound nbt;
        try(var gzip=new GZIPInputStream(new ByteArrayInputStream(bytes));var limited=new FilterInputStream(gzip){
            private int count;
            @Override public int read()throws IOException{int b=in.read();if(b>=0&&++count>MAX_EXPANDED)throw new IOException("Expanded library snapshot too large");return b;}
            @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,Math.min(len,MAX_EXPANDED-count+1));if(n>0&&(count+=n)>MAX_EXPANDED)throw new IOException("Expanded library snapshot too large");return n;}
        };var input=new DataInputStream(limited)){
            nbt=NbtIo.read(input,new NbtTagSizeTracker(128L*1024*1024));if(input.read()!=-1)throw new IOException("Trailing library snapshot data");
        }catch(RuntimeException invalid){throw new IOException("Invalid library NBT",invalid);}
        try{
            var savedKey=nbt.getCompound("key");var hash=key(savedKey.getByteArray("hash"));
            if(!Arrays.equals(hash.hash,expected.hash))throw new IOException("Library hash changed");
            Snapshot.Header metadata=null;
            if(savedKey.contains("header",NbtElement.COMPOUND_TYPE)){
                var tag=savedKey.getCompound("header");var inner=tag.getCompound("key");
                if(inner.contains("header")||!Arrays.equals(inner.getByteArray("hash"),expected.hash)||!tag.containsUuid("owner")||tag.getString("name").length()>256)
                    throw new IOException("Invalid library header");
                metadata=new Snapshot.Header(hash,tag.getUuid("owner"),new Date(tag.getLong("created")),tag.getString("name"));
            }
            savedKey.remove("header");var snapshot=Snapshot.readFromNBT(nbt);snapshot.computeKey();
            if(!Arrays.equals(snapshot.key.hash,expected.hash))throw new IOException("Library content does not match hash");
            snapshot.key=new Snapshot.Key(snapshot.key,metadata);return snapshot;
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid library snapshot",invalid);}
    }
    public static final class Receiver {
        private final byte[] data;private int next,position;
        public Receiver(int total)throws IOException{if(total<=0||total>MAX_COMPRESSED)throw new IOException("Invalid library transfer length");data=new byte[total];}
        public boolean accept(int index,byte[] chunk)throws IOException{
            int required=Math.min(CHUNK_BYTES,data.length-position);
            if(index!=next||required==0||chunk.length!=required)throw new IOException("Out-of-order or invalid library chunk");
            System.arraycopy(chunk,0,data,position,chunk.length);position+=chunk.length;next++;return position==data.length;
        }
        public byte[] complete()throws IOException{if(position!=data.length)throw new IOException("Incomplete library transfer");return data;}
        public int received(){return position;}
        public int total(){return data.length;}
    }
}
