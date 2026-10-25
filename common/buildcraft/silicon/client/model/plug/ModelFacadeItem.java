/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client.model.plug;
import buildcraft.silicon.client.BCSiliconClient;
import buildcraft.silicon.item.ItemPluggableFacade;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
/** Native built-in item renderer, sharing block geometry and resource-reload caches. */
public final class ModelFacadeItem implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    public static final ModelFacadeItem INSTANCE=new ModelFacadeItem();
    private ModelFacadeItem(){}
    @Override public void render(ItemStack stack,ModelTransformationMode mode,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        var data=ItemPluggableFacade.getStates(stack);
        matrices.push();matrices.translate(7/16.0,0,0);
        BCSiliconClient.FACADE_MODEL.render(data.getCurrentStateForStack().stateInfo.state,Direction.WEST,data.isHollow,null,null,matrices,consumers,light,overlay);matrices.pop();
    }
}