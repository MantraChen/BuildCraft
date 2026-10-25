/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client;
import java.util.*;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.silicon.client.model.key.KeyPlugFacade;
import buildcraft.silicon.client.model.plug.PlugBakerFacade;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockRenderView;

/** Original bevels and hollow ring, with native per-face textures and biome tints. */
public final class FacadeModel {
    private record Key(BlockState state,Direction side,boolean hollow){}
    private final Map<Key,List<MutableQuad>> cache=new HashMap<>();
    private Object generation;
    public List<MutableQuad> getQuads(BlockState state,Direction side,boolean hollow){
        var modelGeneration=MinecraftClient.getInstance().getBakedModelManager().getMissingModel();
        if(generation!=modelGeneration){generation=modelGeneration;cache.clear();}
        return cache.computeIfAbsent(new Key(state,side,hollow),k->List.copyOf(PlugBakerFacade.INSTANCE.bakeForKey(new KeyPlugFacade(state,side,hollow))));
    }
    public void render(BlockState state,Direction face,boolean hollow,BlockRenderView world,BlockPos pos,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        // Original solid facades include a pipe cap behind the panel, visible through glass.
        // Draw its opaque surface before the translucent facade. Item previews show only the plate.
        if(world!=null && !hollow){
            var backing=consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
            for(var quad:buildcraft.transport.BCTransportModels.PLUG_MODEL.getQuads(false,face))quad.render(backing,matrices.peek(),light,overlay);
        }
        var layer=RenderLayers.getBlockLayer(state)==RenderLayer.getTranslucent()
            ?RenderLayer.getEntityTranslucent(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE):RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        var consumer=consumers.getBuffer(layer);
        for(var source:getQuads(state,face,hollow)){
            var quad=source;
            if(source.getTint()>=0){
                int colour=MinecraftClient.getInstance().getBlockColors().getColor(state,world,pos,source.getTint());
                quad=new MutableQuad(source).multColouri(colour>>16&255,colour>>8&255,colour&255,255);
            }
            quad.render(consumer,matrices.peek(),light,overlay);
        }
    }
}
