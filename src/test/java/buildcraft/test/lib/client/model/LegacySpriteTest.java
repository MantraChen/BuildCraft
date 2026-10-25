/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.lib.client.model;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import com.google.gson.JsonParser;
import net.minecraft.client.resource.metadata.*;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import buildcraft.lib.client.sprite.AtlasSpriteLegacy;

public class LegacySpriteTest {
    private static byte[] pixels(int width,int height)throws Exception{
        try(var image=new NativeImage(width,height,false)){
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setColor(x,y,0x80000000|y<<8|x);
            return image.getBytes();
        }
    }
    private static List<String> sequence(AnimationResourceMetadata animation){var result=new ArrayList<String>();animation.forEachFrame((i,t)->result.add(i+":"+t));return result;}
    @Test public void staticSpritesPreserveEveryRgbaPixelAtNativeMipmapResolution()throws Exception{
        for(int size:new int[]{1,8,16,32}){
            byte[] source=pixels(size,size);try(var prepared=AtlasSpriteLegacy.prepare(new ByteArrayInputStream(source),AnimationResourceMetadata.EMPTY)){
                int expected=Math.max(16,size),scale=expected/size;assertEquals(expected,prepared.size().width());assertEquals(expected,prepared.size().height());
                for(int y=0;y<expected;y++)for(int x=0;x<expected;x++)assertEquals(0x80000000|y/scale<<8|x/scale,prepared.image().getColor(x,y));
            }
        }
    }
    @Test public void sparseMultirowFramesKeepOrderDurationInterpolationAndPixelIdentity()throws Exception{
        var metadata=new AnimationResourceMetadata(List.of(new AnimationFrameResourceMetadata(5,3),new AnimationFrameResourceMetadata(1,7),new AnimationFrameResourceMetadata(5,2)),2,2,4,true);
        try(var prepared=AtlasSpriteLegacy.prepare(new ByteArrayInputStream(pixels(8,4)),metadata)){
            assertEquals(List.of("0:3","1:7","0:2"),sequence(prepared.animation()));assertTrue(prepared.animation().shouldInterpolate());assertEquals(4,prepared.animation().getDefaultFrameTime());
            assertEquals(16,prepared.image().getWidth());assertEquals(32,prepared.image().getHeight());
            assertEquals(0x80000202,prepared.image().getColor(0,0));assertEquals(0x80000303,prepared.image().getColor(15,15));assertEquals(0x80000002,prepared.image().getColor(0,16));
        }
    }
    @Test public void implicitFramesRemainAnimatedInsteadOfBecomingOneTallSprite()throws Exception{
        var metadata=new AnimationResourceMetadata(List.of(),2,2,6,false);
        try(var prepared=AtlasSpriteLegacy.prepare(new ByteArrayInputStream(pixels(4,4)),metadata)){
            assertEquals(List.of("0:6","1:6","2:6","3:6"),sequence(prepared.animation()));assertEquals(64,prepared.image().getHeight());
        }
    }
    @Test public void actualFluidSpritesKeepExactPlaybackAndSupportFourMipmapLevels()throws Exception{
        for(String name:List.of("oil_heat_0_still","fuel_still","redplasma_still")){
            Path path=Path.of("../../buildcraft_resources/assets/buildcraftenergy/textures/blocks/fluids/"+name+".png");
            var metadata=AnimationResourceMetadata.READER.fromJson(JsonParser.parseString(Files.readString(Path.of(path+".mcmeta"))).getAsJsonObject().getAsJsonObject("animation"));
            try(var originalInput=Files.newInputStream(path);var adaptedInput=Files.newInputStream(path);
                var source=NativeImage.read(originalInput);var prepared=AtlasSpriteLegacy.prepare(adaptedInput,metadata)){
                assertEquals(sequence(metadata),sequence(prepared.animation()));assertEquals(320,prepared.image().getHeight());
                for(int y=0;y<320;y++)for(int x=0;x<16;x++)assertEquals(source.getColor(x,y),prepared.image().getColor(x,y));
            }
            try(var input=Files.newInputStream(path)){
                var prepared=AtlasSpriteLegacy.prepare(input,metadata);
                try(var sprite=new SpriteContents(new Identifier("test",name),prepared.size(),prepared.image(),prepared.animation())){
                    assertEquals(20,sprite.getDistinctFrameCount().count());sprite.generateMipmaps(4);
                }
            }
        }
    }
    @Test public void invalidFrameReferencesFailBeforeReturningAnAtlasSprite()throws Exception{
        byte[] source=pixels(4,4);var metadata=new AnimationResourceMetadata(List.of(new AnimationFrameResourceMetadata(4)),2,2,1,false);
        assertThrows(IOException.class,()->AtlasSpriteLegacy.prepare(new ByteArrayInputStream(source),metadata));
    }
}
