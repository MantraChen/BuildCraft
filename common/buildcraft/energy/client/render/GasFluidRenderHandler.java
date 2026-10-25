/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.energy.client.render;

import org.joml.Vector3f;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;

/** Gas fills from the ceiling; vanilla's liquid renderer always fills from the floor. */
public final class GasFluidRenderHandler extends SimpleFluidRenderHandler {
    public GasFluidRenderHandler(Identifier still, Identifier flowing) { super(still, flowing); }

    @Override public void renderFluid(BlockPos pos, BlockRenderView world, VertexConsumer buffer,
            BlockState block, FluidState fluid) {
        float height = fluid.getHeight(world, pos);
        float bottom = Math.max(0, 1 - height);
        int light = WorldRenderer.getLightmapCoordinates(world, pos);
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (neighbor.isSideSolidFullSquare(world, neighborPos, direction.getOpposite())) continue;
            FluidState neighborFluid = world.getFluidState(neighborPos);
            float top = 1;
            if (fluid.getFluid().matchesType(neighborFluid.getFluid())) {
                if (direction.getAxis() == Direction.Axis.Y) continue;
                top = 1 - neighborFluid.getHeight(world, neighborPos);
                if (top <= bottom) continue;
            }
            MutableQuad quad = createFace(direction, bottom, top);
            quad.texFromSprite(sprites[direction.getAxis() == Direction.Axis.Y ? 0 : 1]);
            quad.lighti(light);
            quad.multColourd(world.getBrightness(direction, true));
            quad.translatef((pos.getX() & 15) - direction.getOffsetX() * 0.001f,
                (pos.getY() & 15) - direction.getOffsetY() * 0.001f,
                (pos.getZ() & 15) - direction.getOffsetZ() * 0.001f);
            quad.render(buffer);
        }
    }

    public static MutableQuad createFace(Direction direction, float bottom, float top) {
        var uv = direction.getAxis() == Direction.Axis.Y ? new ModelUtil.UvFaceData(0, 0, 1, 1)
            : new ModelUtil.UvFaceData(0, 1 - top, 1, 1 - bottom);
        return ModelUtil.createFace(direction, new Vector3f(0.5f, (bottom + top) / 2, 0.5f),
            new Vector3f(0.5f, (top - bottom) / 2, 0.5f), uv);
    }
}
