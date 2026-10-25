/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.client.render;

import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.lib.client.render.laser.*;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;

public class RenderQuarry implements BlockEntityRenderer<TileQuarry> {
    public static final LaserData_BC8.LaserType FRAME;
    public static final LaserData_BC8.LaserType FRAME_BOTTOM;
    public static final LaserData_BC8.LaserType DRILL;
    public static final LaserData_BC8.LaserType LASER;

    static {
        {
            SpriteHolderRegistry.SpriteHolder sprite = SpriteHolderRegistry.getHolder("buildcraftbuilders:blocks/frame/default");
            LaserData_BC8.LaserRow capStart = new LaserData_BC8.LaserRow(sprite, 0, 0, 0, 0);
            LaserData_BC8.LaserRow start = null;
            LaserData_BC8.LaserRow[] middle = { new LaserData_BC8.LaserRow(sprite, 0, 4, 16, 12) };
            LaserData_BC8.LaserRow end = new LaserData_BC8.LaserRow(sprite, 0, 4, 16, 12);
            LaserData_BC8.LaserRow capEnd = new LaserData_BC8.LaserRow(sprite, 0, 0, 0, 0);
            FRAME = new LaserData_BC8.LaserType(capStart, start, middle, end, capEnd);
        }
        {
            SpriteHolderRegistry.SpriteHolder sprite = SpriteHolderRegistry.getHolder("buildcraftbuilders:blocks/frame/default");
            LaserData_BC8.LaserRow capStart = new LaserData_BC8.LaserRow(sprite, 0, 0, 0, 0);
            LaserData_BC8.LaserRow start = null;
            LaserData_BC8.LaserRow[] middle = { new LaserData_BC8.LaserRow(sprite, 0, 4, 16, 12) };
            LaserData_BC8.LaserRow end = new LaserData_BC8.LaserRow(sprite, 0, 4, 16, 12);
            LaserData_BC8.LaserRow capEnd = new LaserData_BC8.LaserRow(sprite, 4, 4, 12, 12);
            FRAME_BOTTOM = new LaserData_BC8.LaserType(capStart, start, middle, end, capEnd);
        }
        {
            SpriteHolderRegistry.SpriteHolder sprite = SpriteHolderRegistry.getHolder("buildcraftbuilders:blocks/quarry/drill");
            LaserData_BC8.LaserRow capStart = new LaserData_BC8.LaserRow(sprite, 6, 0, 10, 4);
            LaserData_BC8.LaserRow start = null;
            LaserData_BC8.LaserRow[] middle = { new LaserData_BC8.LaserRow(sprite, 0, 0, 16, 4) };
            LaserData_BC8.LaserRow end = null;
            LaserData_BC8.LaserRow capEnd = new LaserData_BC8.LaserRow(sprite, 6, 0, 10, 4);
            DRILL = new LaserData_BC8.LaserType(capStart, start, middle, end, capEnd);
        }
        {
            LASER = BuildCraftLaserManager.POWER_LOW;
        }
    }

    public RenderQuarry(BlockEntityRendererFactory.Context context) {}
    public static void init() { /* Loading this class registers its sprite holders. */ }
    @Override public void render(TileQuarry tile, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        if (tile.getWorld() == null || !tile.frameBox.isInitialized()) return;
        Vec3d origin = Vec3d.of(tile.getPos());
        BlockPos min = tile.frameBox.min(), max = tile.frameBox.max();
        double drillOffset = 1.25;
        if (tile.currentTask instanceof TileQuarry.TaskBreakBlock task) {
            if (tile.drillPos == null && task.clientPower > 0) {
                beam(LASER, Vec3d.ofCenter(tile.getPos()), Vec3d.ofCenter(task.breakPos), matrices, consumers, origin, overlay);
            } else if (tile.drillPos != null) {
                double power = MathHelper.lerp(delta, task.prevClientPower, task.clientPower);
                double progress = MathHelper.clamp(power / Math.max(1, task.getTarget()), 0, 1);
                double travel = progress < .9 ? 1-progress/.9 : (progress-.9)/.1;
                var shape = tile.getWorld().getBlockState(task.breakPos).getOutlineShape(tile.getWorld(), task.breakPos);
                Box bounds = shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox();
                drillOffset = MathHelper.lerp(travel, (bounds.minY+bounds.maxY)/2, 1.25);
            }
        }
        if (tile.clientDrillPos == null || tile.prevClientDrillPos == null) {
            LaserBoxRenderer.render(tile.frameBox, BuildCraftLaserManager.STRIPES_WRITE, true, matrices, consumers, origin, overlay);
            return;
        }
        Vec3d drill = tile.prevClientDrillPos.lerp(tile.clientDrillPos, MathHelper.clamp(delta, 0, 1));
        beam(FRAME, new Vec3d(drill.x+.5, max.getY()+.5, drill.z), new Vec3d(drill.x+.5, max.getY()+.5, max.getZ()+.75), matrices, consumers, origin, overlay);
        beam(FRAME, new Vec3d(drill.x+.5, max.getY()+.5, drill.z), new Vec3d(drill.x+.5, max.getY()+.5, min.getZ()+.25), matrices, consumers, origin, overlay);
        beam(FRAME, new Vec3d(drill.x, max.getY()+.5, drill.z+.5), new Vec3d(max.getX()+.75, max.getY()+.5, drill.z+.5), matrices, consumers, origin, overlay);
        beam(FRAME, new Vec3d(drill.x, max.getY()+.5, drill.z+.5), new Vec3d(min.getX()+.25, max.getY()+.5, drill.z+.5), matrices, consumers, origin, overlay);
        beam(FRAME_BOTTOM, drill.add(.5, 1.25, .5), new Vec3d(drill.x+.5, max.getY()+.5, drill.z+.5), matrices, consumers, origin, overlay);
        beam(DRILL, drill.add(.5, 1+drillOffset, .5), drill.add(.5, drillOffset, .5), matrices, consumers, origin, overlay);
    }
    private static void beam(LaserData_BC8.LaserType type, Vec3d start, Vec3d end, MatrixStack matrices,
            VertexConsumerProvider consumers, Vec3d origin, int overlay) {
        LaserRenderer_BC8.render(new LaserData_BC8(type, start, end, 1/16.0, true, true, 0), matrices, consumers, origin, overlay);
    }
    @Override public boolean rendersOutsideBoundingBox(TileQuarry tile) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
