/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client.render;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import buildcraft.silicon.tile.TileLaser;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.lib.client.render.laser.*;
public final class RenderLaser implements BlockEntityRenderer<TileLaser> {
    public RenderLaser(BlockEntityRendererFactory.Context context) {}
    @Override public void render(TileLaser tile,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay) {
        if(tile.laserPos==null || tile.getLastTransfer()<=0)return;
        var face=tile.getCachedState().get(BuildCraftProperties.BLOCK_FACING_6);
        Vec3d origin=Vec3d.of(tile.getPos()),start=Vec3d.ofCenter(tile.getPos()).add(face.getOffsetX()*.4,face.getOffsetY()*.4,face.getOffsetZ()*.4);
        int colour=(int)Math.min(3,tile.getAverageClient()/MjAPI.MJ);
        var data=new LaserData_BC8(BuildCraftLaserManager.POWERS[colour],start,tile.laserPos,.075,false,true,15);
        LaserRenderer_BC8.render(data,matrices,vertices,origin,overlay);
    }
    @Override public boolean rendersOutsideBoundingBox(TileLaser tile){return true;}
    @Override public int getRenderDistance(){return 128;}
}
