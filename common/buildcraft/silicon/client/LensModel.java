/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client;

import buildcraft.lib.client.model.*;
import buildcraft.lib.expression.*;
import buildcraft.lib.expression.node.value.*;
import buildcraft.lib.misc.ExpressionCompat;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;

/** Original frame and glass geometry, with separate opaque and translucent buffers. */
public final class LensModel {
    public record Quads(MutableQuad[] frame,MutableQuad[] glass){}
    private record Key(Direction side,DyeColor colour,boolean filter){}
    private final FunctionContext context=new FunctionContext(ExpressionCompat.ENUM_FACING,ExpressionCompat.ENUM_DYE_COLOUR,DefaultContexts.createWithAll());
    private final NodeVariableObject<Direction> side=context.putVariableObject("side",Direction.class);
    private final NodeVariableObject<DyeColor> colour=context.putVariableObject("colour",DyeColor.class);
    private final NodeVariableBoolean hasColour=context.putVariableBoolean("has_colour");
    private final ModelHolderVariable lens=new ModelHolderVariable("buildcraftsilicon:bc_models/plugs/lens.json",context);
    private final ModelHolderVariable filter=new ModelHolderVariable("buildcraftsilicon:bc_models/plugs/filter.json",context);
    private final java.util.Map<Key,Quads> cache=new java.util.HashMap<>();
    private Object lensGeneration,filterGeneration;
    public Quads getQuads(Direction face,DyeColor dye,boolean filtering){
        if(lensGeneration!=lens.getModel() || filterGeneration!=filter.getModel()){
            lensGeneration=lens.getModel();filterGeneration=filter.getModel();cache.clear();
        }
        return cache.computeIfAbsent(new Key(face,dye,filtering),key->{
            side.value=face;colour.value=dye==null?DyeColor.WHITE:dye;hasColour.value=dye!=null;
            var model=filtering?filter:lens;
            var variables=new buildcraft.lib.misc.data.ModelVariableData();variables.setNodes(model.createTickableNodes());variables.refresh();
            return new Quads(model.getCutoutQuads(),model.getTranslucentQuads());
        });
    }
    public void render(Direction face,DyeColor dye,boolean filtering,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        var quads=getQuads(face,dye,filtering);
        var frame=consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        for(var quad:quads.frame())quad.render(frame,matrices.peek(),light,overlay);
        if(quads.glass().length>0){
            var glass=consumers.getBuffer(RenderLayer.getEntityTranslucent(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
            for(var quad:quads.glass())quad.render(glass,matrices.peek(),light,overlay);
        }
    }
}
