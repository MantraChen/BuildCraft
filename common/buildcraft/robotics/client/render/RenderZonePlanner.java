/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.robotics.client.render;
import java.util.*;
import buildcraft.robotics.tile.TileZonePlanner;
import buildcraft.robotics.zone.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
/** The original 10 by 8 front map, drawn with native colored quads from loaded client chunks. */
public final class RenderZonePlanner implements BlockEntityRenderer<TileZonePlanner> {
    private record Sample(long tick,int[] colours){}
    private final Map<TileZonePlanner,Sample> samples=new WeakHashMap<>();
    @Override public void render(TileZonePlanner tile,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        var world=tile.getWorld();if(world==null)return;var cached=samples.get(tile);
        if(cached==null||world.getTime()-cached.tick>=40){
            int[] colours=new int[80];var chunks=new HashMap<ChunkPos,ZonePlannerMapChunk>();int level=Math.floorDiv(world.getTopY()-1,32)+1;
            for(int x=0;x<10;x++)for(int z=0;z<8;z++){int wx=tile.getPos().getX()+(x-5)*8,wz=tile.getPos().getZ()+(z-4)*8;var chunkPos=new ChunkPos(wx>>4,wz>>4);if(!world.isChunkLoaded(chunkPos.x,chunkPos.z)){colours[x+z*10]=0x252d35;continue;}
                var chunk=chunks.computeIfAbsent(chunkPos,p->new ZonePlannerMapChunk(world,new ZonePlannerMapChunkKey(p,world.getRegistryKey().getValue(),level)));colours[x+z*10]=chunk.getColour(wx,wz);
            }cached=new Sample(world.getTime(),colours);samples.put(tile,cached);
        }
        matrices.push();matrices.translate(.5,.5,.5);var facing=tile.getCachedState().get(buildcraft.lib.block.BlockBCBase_Neptune.PROP_FACING);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()+180));matrices.translate(-.5,-.5,-.5);
        var m=matrices.peek().getPositionMatrix();var n=matrices.peek().getNormalMatrix();var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(new net.minecraft.util.Identifier("buildcraftlib:textures/blocks/white.png")));
        for(int x=0;x<10;x++)for(int y=0;y<8;y++){float left=(3+x)/16f,right=left+1/16f,top=(13-y)/16f,bottom=top-1/16f;int c=cached.colours[x+y*10];
            vertex(buffer,m,n,left,bottom,c);vertex(buffer,m,n,right,bottom,c);vertex(buffer,m,n,right,top,c);vertex(buffer,m,n,left,top,c);
        }matrices.pop();
    }
    private static void vertex(VertexConsumer v,org.joml.Matrix4f m,org.joml.Matrix3f n,float x,float y,int c){v.vertex(m,x,y,-.001f).color(c>>16&255,c>>8&255,c&255,255).texture(.5f,.5f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(n,0,0,-1).next();}
}
