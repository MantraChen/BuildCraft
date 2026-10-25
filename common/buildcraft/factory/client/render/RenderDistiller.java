/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.client.render;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.lib.client.render.fluid.*;
import buildcraft.lib.fluid.Tank;

/** The original three tank volumes follow the native facing model. */
public class RenderDistiller implements BlockEntityRenderer<TileDistiller_BC8> {
    public RenderDistiller(BlockEntityRendererFactory.Context context) {}
    @Override public void render(TileDistiller_BC8 tile, float delta, MatrixStack matrices,
            VertexConsumerProvider vertices, int light, int overlay) {
        int angle = switch (tile.getCachedState().get(buildcraft.lib.block.BlockBCBase_Neptune.PROP_FACING)) {
            case NORTH -> 90; case EAST -> 180; case SOUTH -> 270; default -> 0;
        };
        matrices.push();
        matrices.translate(.5,0,.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-angle));
        matrices.translate(-.5,0,-.5);
        draw(tile.tankIn,new FluidRenderer.TankSize(.02,.02,4.02,7.98,15.98,11.98),matrices,vertices,light);
        draw(tile.tankGasOut,new FluidRenderer.TankSize(8.02,8.02,.02,15.98,15.98,15.98),matrices,vertices,light);
        draw(tile.tankLiquidOut,new FluidRenderer.TankSize(8.02,.02,.02,15.98,7.98,15.98),matrices,vertices,light);
        matrices.pop();
    }
    private static void draw(Tank tank,FluidRenderer.TankSize size,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        var fluid=tank.getFluidForRender();
        if(fluid==null || fluid.isEmpty())return;
        int emission=fluid.getFluid().getDefaultState().getBlockState().getLuminance();
        int packedLight=Math.max(light&0xffff,emission<<4)|(light&0xffff0000);
        FluidRenderer.renderFluid(FluidSpriteType.STILL,fluid,fluid.amount,tank.getStorage().getCapacity(),size,matrices,vertices,packedLight);
    }
}
