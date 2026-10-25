/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.client.render;

import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionStart;
import buildcraft.lib.client.render.fluid.*;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.fluid.Tank;

/** Local endpoint tanks and counter-flowing native fluid ribbons across the warmed structure. */
public final class RenderHeatExchange implements BlockEntityRenderer<TileHeatExchange> {
    public RenderHeatExchange(BlockEntityRendererFactory.Context context) {}
    @Override public void render(TileHeatExchange tile,float delta,MatrixStack matrices,
            VertexConsumerProvider vertices,int light,int overlay) {
        var section=tile.getSection();
        if(!tile.isFormed() || section==null)return;
        Direction facing=tile.getFacing();
        if(tile.isStart()) {
            tank(section.tankInput,new FluidRenderer.TankSize(2.02,.02,2.02,13.98,1.98,13.98),matrices,vertices,light);
            tank(section.tankOutput,sideTank(facing.rotateYClockwise()),matrices,vertices,light);
        } else if(tile.isEnd()) {
            tank(section.tankInput,sideTank(facing.rotateYCounterclockwise()),matrices,vertices,light);
            tank(section.tankOutput,new FluidRenderer.TankSize(2.02,14.02,2.02,13.98,15.98,13.98),matrices,vertices,light);
        }
        if(!(section instanceof ExchangeSectionStart start) || !tile.isStart())return;
        var end=tile.getClientEnd();
        if(end==null || end.getSection()==null)return;
        double progress=start.getProgress(delta),length=tile.getStructureSize()-.25;
        if(progress<=0)return;
        Direction along=facing.rotateYCounterclockwise();
        stream(section.tankInput.getFluidForRender(),along,.125,.125+length*progress,2.1,matrices,vertices,light);
        stream(end.getSection().tankInput.getFluidForRender(),along,
            tile.getStructureSize()-.125-length*progress,tile.getStructureSize()-.125,4.1,matrices,vertices,light);
    }
    private static FluidRenderer.TankSize sideTank(Direction side) {
        return switch(side) {
            case WEST -> new FluidRenderer.TankSize(.02,4.02,4.02,1.98,11.98,11.98);
            case EAST -> new FluidRenderer.TankSize(14.02,4.02,4.02,15.98,11.98,11.98);
            case NORTH -> new FluidRenderer.TankSize(4.02,4.02,.02,11.98,11.98,1.98);
            default -> new FluidRenderer.TankSize(4.02,4.02,14.02,11.98,11.98,15.98);
        };
    }
    private static void tank(Tank tank,FluidRenderer.TankSize size,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        var fluid=tank.getFluidForRender();
        if(fluid!=null)draw(fluid,fluid.amount,tank.getStorage().getCapacity(),size,matrices,vertices,light,FluidSpriteType.STILL);
    }
    private static void stream(FluidStackBC fluid,Direction direction,double from,double to,double inset,
            MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        if(fluid==null || fluid.isEmpty())return;
        for(int i=(int)Math.floor(from);i<Math.ceil(to);i++) {
            double a=Math.max(from-i,0)*16,b=Math.min(to-i,1)*16;
            if(b<=a)continue;
            if(direction.getDirection()==Direction.AxisDirection.NEGATIVE) {double flipped=16-b;b=16-a;a=flipped;}
            var size=direction.getAxis()==Direction.Axis.X
                ?new FluidRenderer.TankSize(a,inset,inset,b,16-inset,16-inset)
                :new FluidRenderer.TankSize(inset,inset,a,16-inset,16-inset,b);
            matrices.push();matrices.translate(direction.getOffsetX()*i,0,direction.getOffsetZ()*i);
            draw(fluid,1,1,size,matrices,vertices,light,FluidSpriteType.FLOWING);matrices.pop();
        }
    }
    private static void draw(FluidStackBC fluid,long amount,long capacity,FluidRenderer.TankSize size,
            MatrixStack matrices,VertexConsumerProvider vertices,int light,FluidSpriteType type) {
        int emission=fluid.getFluid().getDefaultState().getBlockState().getLuminance();
        int packedLight=Math.max(light&0xffff,emission<<4)|(light&0xffff0000);
        FluidRenderer.renderFluid(type,fluid,amount,capacity,size,matrices,vertices,packedLight);
    }
    @Override public boolean rendersOutsideBoundingBox(TileHeatExchange tile) {return tile.isStart();}
    @Override public int getRenderDistance() {return 128;}
}
