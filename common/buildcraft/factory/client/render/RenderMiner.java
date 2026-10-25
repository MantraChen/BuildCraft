/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.client.render;

import buildcraft.factory.tile.TileMiner;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.client.render.laser.LaserData_BC8.*;
import buildcraft.lib.client.render.tile.RenderPartCube;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/** Pipe animation and the original charge/work indicator geometry, using native entity buffers. */
abstract class RenderMiner<T extends TileMiner> implements BlockEntityRenderer<T> {
    private final RenderTube tube;
    private final boolean allSides;
    private final RenderPartCube led = new RenderPartCube();
    protected RenderMiner(String texture, boolean allSides) {
        var sprite = SpriteHolderRegistry.getHolder(texture);
        var cap = new LaserRow(sprite, 0, 8, 8, 16);
        var row = new LaserRow(sprite, 0, 0, 16, 8);
        tube = new RenderTube(new LaserType(cap, row, new LaserRow[]{row}, null, cap));
        this.allSides = allSides;
    }
    @Override public void render(T tile, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        float charge = MathHelper.clamp(tile.getPercentFilledForRender(), 0, 1);
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (!allSides && tile.getCachedState().contains(BlockBCBase_Neptune.PROP_FACING)
                && direction != tile.getCachedState().get(BlockBCBase_Neptune.PROP_FACING)) continue;
            double edge = allSides ? .475 : .4875;
            double x = .5 + direction.getOffsetX()*edge, z = .5 + direction.getOffsetZ()*edge;
            int dx = -direction.getOffsetZ(), dz = direction.getOffsetX();
            double powerOffset = (allSides ? 1.5 : 2.5)/16, statusOffset = (allSides ? 3.5 : 4.5)/16;
            double y = (allSides ? 13.5 : 5.5)/16;
            int red = (allSides ? 31 : 79) + (int)(charge*(allSides ? 210 : 165));
            int other = (int)(charge*60);
            led.center.positiond(x+dx*powerOffset, y, z+dz*powerOffset).colouri(red, other, other, 255);
            led.center.lighti(charge > .01 ? 15 : 0, 0);
            led.render(matrices, consumers, light, overlay);
            boolean complete = tile.isComplete();
            led.center.positiond(x+dx*statusOffset, y, z+dz*statusOffset)
                .colouri(complete ? 27 : 119, complete ? 16 : 221, complete ? 31 : 119, 255);
            led.center.lighti(complete ? 0 : 15, 0);
            led.render(matrices, consumers, light, overlay);
        }
        tube.render(tile, delta, matrices, consumers, overlay);
    }
    @Override public boolean rendersOutsideBoundingBox(T tile) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
