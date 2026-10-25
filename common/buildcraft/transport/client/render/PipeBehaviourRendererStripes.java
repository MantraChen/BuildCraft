/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.client.render;
import net.fabricmc.api.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import buildcraft.api.transport.pipe.IPipeBehaviourRenderer;
import buildcraft.transport.client.model.StripesModel;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;

@Environment(EnvType.CLIENT)
public enum PipeBehaviourRendererStripes implements IPipeBehaviourRenderer<PipeBehaviourStripes> {
    INSTANCE;
    private final StripesModel model=new StripesModel();
    @Override public void render(PipeBehaviourStripes stripes,MatrixStack matrices,VertexConsumer vc,int light,float partialTicks){
        if(stripes.direction!=null)for(var quad:model.getQuads(stripes.direction))quad.render(vc,matrices.peek(),light,OverlayTexture.DEFAULT_UV);
    }
}
