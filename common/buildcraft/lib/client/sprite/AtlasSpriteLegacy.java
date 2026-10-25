/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.client.sprite;

import java.io.*;
import java.util.*;
import com.mojang.serialization.Codec;
import net.minecraft.client.resource.metadata.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.texture.atlas.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import buildcraft.lib.mixin.client.AtlasSourceManagerAccessor;

/** Adapts legacy atlas sprites without modifying source art or animation timing. */
public final class AtlasSpriteLegacy implements AtlasSource {
    public static final Codec<AtlasSpriteLegacy> CODEC=Codec.unit(AtlasSpriteLegacy::new);
    private static AtlasSourceType type;
    private static final List<Identifier> SPRITES=List.of(
        new Identifier("buildcraftlib:blocks/white"),new Identifier("buildcraftfactory:blocks/plain_pipe/end"),
        new Identifier("buildcraftfactory:blocks/tube/end"),
        new Identifier("buildcraftenergy:blocks/fluids/oil_heat_0_still"),new Identifier("buildcraftenergy:blocks/fluids/fuel_still"),
        new Identifier("buildcraftenergy:blocks/fluids/redplasma_still"));
    public static void register(){if(type==null)type=AtlasSourceManagerAccessor.buildcraft_register("buildcraftlib:legacy_sprites",CODEC);}
    @Override public AtlasSourceType getType(){return Objects.requireNonNull(type);}
    @Override public void load(ResourceManager resources,SpriteRegions regions){
        for(var id:SPRITES)resources.getResource(RESOURCE_FINDER.toResourcePath(id)).ifPresent(resource->regions.add(id,()->{
            try(var stream=resource.getInputStream()){
                var metadata=resource.getMetadata().decode(AnimationResourceMetadata.READER).orElse(AnimationResourceMetadata.EMPTY);
                var prepared=prepare(stream,metadata);
                try{return new SpriteContents(id,prepared.size,prepared.image,prepared.animation);}
                catch(RuntimeException|Error failure){prepared.close();throw failure;}
            }catch(IOException invalid){buildcraft.api.core.BCLog.logger.warn("Could not adapt legacy sprite "+id,invalid);return null;}
        }));
    }
    /** The caller owns the returned image; SpriteContents takes ownership after construction. */
    public record Prepared(NativeImage image,SpriteDimensions size,AnimationResourceMetadata animation) implements AutoCloseable {
        @Override public void close(){image.close();}
    }
    private record Frame(int index,int time){}
    public static Prepared prepare(InputStream stream,AnimationResourceMetadata metadata)throws IOException{
        try(var source=NativeImage.read(stream)){
            var size=metadata.getSize(source.getWidth(),source.getHeight());int w=size.width(),h=size.height();
            if(w<=0||h<=0||source.getWidth()%w!=0||source.getHeight()%h!=0)throw new IOException("Invalid animation frame dimensions");
            int columns=source.getWidth()/w,total=Math.multiplyExact(columns,source.getHeight()/h);
            var sequence=new ArrayList<Frame>();metadata.forEachFrame((index,time)->sequence.add(new Frame(index,time)));
            if(sequence.isEmpty())for(int i=0;i<total;i++)sequence.add(new Frame(i,metadata.getDefaultFrameTime()));
            var remap=new LinkedHashMap<Integer,Integer>();var frames=new ArrayList<AnimationFrameResourceMetadata>();
            for(var frame:sequence){
                if(frame.index<0||frame.index>=total||frame.time<=0)throw new IOException("Invalid animation frame "+frame);
                int index=remap.computeIfAbsent(frame.index,ignored->remap.size());frames.add(new AnimationFrameResourceMetadata(index,frame.time));
            }
            // Integer nearest-neighbour replication preserves every original RGBA pixel and UV.
            int divisor=Math.min(Integer.lowestOneBit(w),Integer.lowestOneBit(h)),scale=divisor<16?16/divisor:1;
            int width=Math.multiplyExact(w,scale),height=Math.multiplyExact(h,scale);
            var image=new NativeImage(width,Math.multiplyExact(height,remap.size()),false);
            try{
                for(var entry:remap.entrySet())for(int y=0;y<height;y++)for(int x=0;x<width;x++)
                    image.setColor(x,entry.getValue()*height+y,source.getColor(entry.getKey()%columns*w+x/scale,entry.getKey()/columns*h+y/scale));
                return new Prepared(image,new SpriteDimensions(width,height),new AnimationResourceMetadata(frames,width,height,metadata.getDefaultFrameTime(),metadata.shouldInterpolate()));
            }catch(RuntimeException|Error failure){image.close();throw failure;}
        }
    }
}
