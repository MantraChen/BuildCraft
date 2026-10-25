/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.client.render;

import java.util.*;
import buildcraft.builders.snapshot.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;

/** A bounded native block-model preview; no fake world or client-side placement. */
public final class SnapshotPreview {
    private Snapshot shown;
    private final List<Entry> blocks=new ArrayList<>();
    private record Entry(BlockPos pos,BlockState state){}
    public void draw(DrawContext context,Snapshot snapshot,int x,int y,int width,int height){
        if(snapshot!=shown){shown=snapshot;blocks.clear();
            if(snapshot instanceof Blueprint blueprint){
                var palette=blueprint.palette.stream().map(s->s.isAir()?null:NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),s.serializeNBT().getCompound("blockState"))).toList();
                for(int z=0;z<snapshot.size.getZ() && blocks.size()<4096;z++)for(int h=0;h<snapshot.size.getY() && blocks.size()<4096;h++)for(int w=0;w<snapshot.size.getX() && blocks.size()<4096;w++){
                    var state=palette.get(blueprint.data[blueprint.posToIndex(w,h,z)]);if(state!=null&&!state.isAir())blocks.add(new Entry(new BlockPos(w,h,z),state));
                }
            }
        }
        if(snapshot==null || blocks.isEmpty())return;
        var client=MinecraftClient.getInstance();var matrices=context.getMatrices();
        float scale=Math.min(width/(float)(snapshot.size.getX()+snapshot.size.getZ()+1),height/(float)(snapshot.size.getY()+.6*(snapshot.size.getX()+snapshot.size.getZ())+1));
        context.enableScissor(x,y,x+width,y+height);matrices.push();
        matrices.translate(x+width/2f,y+height/2f,150);matrices.scale(scale,-scale,scale);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30));matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(225));
        matrices.translate(-snapshot.size.getX()/2f,-snapshot.size.getY()/2f,-snapshot.size.getZ()/2f);
        try{
            for(var entry:blocks){matrices.push();matrices.translate(entry.pos.getX(),entry.pos.getY(),entry.pos.getZ());
                client.getBlockRenderManager().renderBlockAsEntity(entry.state,matrices,context.getVertexConsumers(),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);matrices.pop();}
            context.draw();
        }finally{matrices.pop();context.disableScissor();}
    }
}
