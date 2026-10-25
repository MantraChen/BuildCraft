/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.model.plug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.Direction;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Vec3d;

import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.model.MutableVertex;
import buildcraft.lib.misc.VecUtil;

import buildcraft.silicon.client.model.key.KeyPlugFacade;

public enum PlugBakerFacade {
    INSTANCE;

    private void interpolateUv(MutableVertex target,Vec3d pos,List<MutableVertex> source,Direction.Axis axis){
        int a=axis==Direction.Axis.X?1:0,b=axis==Direction.Axis.Z?1:2;
        MutableVertex origin=source.get(0),one=source.get(1),two=source.get(3);
        double ox=coord(origin,a),oy=coord(origin,b),dx=coord(one,a)-ox,dy=coord(one,b)-oy,
            ex=coord(two,a)-ox,ey=coord(two,b)-oy,det=dx*ey-dy*ex;
        if(Math.abs(det)<1e-10)return;
        double x=(a==0?pos.x:a==1?pos.y:pos.z)-ox,y=(b==0?pos.x:b==1?pos.y:pos.z)-oy;
        double u=(x*ey-y*ex)/det,v=(dx*y-dy*x)/det;
        target.texf((float)(origin.tex_u+u*(one.tex_u-origin.tex_u)+v*(two.tex_u-origin.tex_u)),
            (float)(origin.tex_v+u*(one.tex_v-origin.tex_v)+v*(two.tex_v-origin.tex_v)));
    }
    private double coord(MutableVertex vertex,int axis){return axis==0?vertex.position_x:axis==1?vertex.position_y:vertex.position_z;}

    private int getVertexIndex(List<Vec3d> positions,
                               Direction.Axis axis,
                               boolean minOrMax1, boolean minOrMax2) {
        Direction.Axis axis1, axis2;
        switch (axis) {
            case X:
                axis1 = Direction.Axis.Y;
                axis2 = Direction.Axis.Z;
                break;
            case Y:
                axis1 = Direction.Axis.X;
                axis2 = Direction.Axis.Z;
                break;
            case Z:
                axis1 = Direction.Axis.X;
                axis2 = Direction.Axis.Y;
                break;
            default:
                throw new IllegalArgumentException();
        }
        double min1 = positions.stream().mapToDouble(pos -> VecUtil.getValue(pos, axis1)).min().orElse(0);
        double min2 = positions.stream().mapToDouble(pos -> VecUtil.getValue(pos, axis2)).min().orElse(0);
        double max1 = positions.stream().mapToDouble(pos -> VecUtil.getValue(pos, axis1)).max().orElse(0);
        double max2 = positions.stream().mapToDouble(pos -> VecUtil.getValue(pos, axis2)).max().orElse(0);
        double center1 = (min1 + max1) / 2;
        double center2 = (min2 + max2) / 2;
        return positions.indexOf(
            positions.stream()
                .filter(pos ->
                    (minOrMax1 ? VecUtil.getValue(pos, axis1) < center1 : VecUtil.getValue(pos, axis1) > center1) &&
                        (minOrMax2 ? VecUtil.getValue(pos, axis2) < center2 : VecUtil.getValue(pos, axis2) > center2)
                )
                .findFirst()
                .orElse(positions.get(0))
        );
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private List<MutableQuad> getTransformedQuads(BlockState state,
                                                  BakedModel model,
                                                  Direction side,
                                                  Vec3d pos0, Vec3d pos1, Vec3d pos2, Vec3d pos3) {
        return Stream.concat(model.getQuads(state, side, net.minecraft.util.math.random.Random.create(0)).stream(),
            model.getQuads(state, null, net.minecraft.util.math.random.Random.create(0)).stream().filter(q->q.getFace()==side))
            .map(quad -> {
                MutableQuad mutableQuad = new MutableQuad().fromBakedItem(quad);
                boolean positive = side.getDirection() == Direction.AxisDirection.POSITIVE;
                Function<Vec3d, Vec3d> transformPosition = pos -> {
                    switch (side.getAxis()) {
                        case X:
                            return new Vec3d(
                                positive ? 1 - pos.z : pos.z,
                                pos.y,
                                pos.x
                            );
                        case Y:
                            return new Vec3d(
                                pos.x,
                                positive ? 1 - pos.z : pos.z,
                                pos.y
                            );
                        case Z:
                            return new Vec3d(
                                pos.y,
                                pos.x,
                                positive ? 1 - pos.z : pos.z
                            );
                        default:
                            throw new IllegalArgumentException();
                    }
                };
                List<Vec3d> poses = Arrays.asList(
                    transformPosition.apply(pos0),
                    transformPosition.apply(pos1),
                    transformPosition.apply(pos2),
                    transformPosition.apply(pos3)
                );
                List<MutableVertex> vertexes = Arrays.asList(
                    mutableQuad.vertex_0,
                    mutableQuad.vertex_1,
                    mutableQuad.vertex_2,
                    mutableQuad.vertex_3
                );
                List<Vec3d> vertexesPoses = vertexes.stream()
                    .map(vertex -> new Vec3d(vertex.position_x, vertex.position_y, vertex.position_z))
                    .collect(Collectors.toList());
                var source=vertexes.stream().map(v->new MutableVertex().copyFrom(v)).collect(Collectors.toList());
                Stream.of(
                    Pair.of(false, false),
                    Pair.of(false, true),
                    Pair.of(true, true),
                    Pair.of(true, false)
                ).forEach(minOrMaxPair -> {
                    Vec3d newPos = poses.get(
                        getVertexIndex(poses, side.getAxis(), minOrMaxPair.getLeft(), minOrMaxPair.getRight())
                    );
                    MutableVertex vertex = vertexes.get(
                        getVertexIndex(vertexesPoses, side.getAxis(), minOrMaxPair.getLeft(), minOrMaxPair.getRight())
                    );
                    vertex.positiond(newPos.x, newPos.y, newPos.z);
                    interpolateUv(vertex,newPos,source,side.getAxis());
                });
                return mutableQuad;
            })
            .collect(Collectors.toList());
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Vec3d rotate(Vec3d vec, BlockRotation rotation) {
        switch (rotation) {
            case NONE:
                return new Vec3d(vec.x, vec.y, vec.z);
            case CLOCKWISE_90:
                return new Vec3d(1 - vec.y, 1 - vec.x, vec.z);
            case CLOCKWISE_180:
                return new Vec3d(1 - vec.x, 1 - vec.y, vec.z);
            case COUNTERCLOCKWISE_90:
                return new Vec3d(vec.y, vec.x, vec.z);
        }
        throw new IllegalArgumentException();
    }

    private void addRotatedQuads(List<MutableQuad> quads,
                                 BlockState state,
                                 BakedModel model,
                                 Direction side,
                                 BlockRotation rotation,
                                 Vec3d pos0, Vec3d pos1, Vec3d pos2, Vec3d pos3) {
        quads.addAll(getTransformedQuads(
            state, model, side,
            rotate(pos0, rotation),
            rotate(pos1, rotation),
            rotate(pos2, rotation),
            rotate(pos3, rotation)
        ));
    }

    public List<MutableQuad> bakeForKey(KeyPlugFacade key) {
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(key.state);
        List<MutableQuad> quads = new ArrayList<>();
        int pS = 2;
        int nS = 16 - pS;
        if (!key.isHollow) {
            quads.addAll(getTransformedQuads(
                key.state, model, key.side,
                new Vec3d(0 / 16D, 16 / 16D, 0 / 16D),
                new Vec3d(16 / 16D, 16 / 16D, 0 / 16D),
                new Vec3d(16 / 16D, 0 / 16D, 0 / 16D),
                new Vec3d(0 / 16D, 0 / 16D, 0 / 16D)
            ));
            quads.addAll(getTransformedQuads(
                key.state, model, key.side.getOpposite(),
                new Vec3d(pS / 16D, nS / 16D, nS / 16D),
                new Vec3d(nS / 16D, nS / 16D, nS / 16D),
                new Vec3d(nS / 16D, pS / 16D, nS / 16D),
                new Vec3d(pS / 16D, pS / 16D, nS / 16D)
            ));
        }
        for (BlockRotation rotation : BlockRotation.values()) {
            if (key.isHollow) {
                addRotatedQuads(
                        quads, key.state, model, key.side, rotation,
                        new Vec3d(0 / 16D, rotation.ordinal() % 2 == 0 ? 4 / 16D : 0 / 16D, 0 / 16D),
                        new Vec3d(4 / 16D, rotation.ordinal() % 2 == 0 ? 4 / 16D : 0 / 16D, 0 / 16D),
                        new Vec3d(4 / 16D, rotation.ordinal() % 2 == 0 ? 16 / 16D : 12 / 16D, 0 / 16D),
                        new Vec3d(0 / 16D, rotation.ordinal() % 2 == 0 ? 16 / 16D : 12 / 16D, 0 / 16D)
                );
            }
            addRotatedQuads(
                quads, key.state, model, key.side.getOpposite(), rotation,
                new Vec3d(0 / 16D, 16 / 16D, 16 / 16D),
                new Vec3d(pS / 16D, nS / 16D, nS / 16D),
                new Vec3d(pS / 16D, pS / 16D, nS / 16D),
                new Vec3d(0 / 16D, 0 / 16D, 16 / 16D)
            );
            if (key.isHollow) {
                addRotatedQuads(
                    quads, key.state, model, key.side.getOpposite(), rotation,
                    new Vec3d(pS / 16D, rotation.ordinal() % 2 == 0 ? nS / 16D : 12 / 16D, nS / 16D),
                    new Vec3d(4 / 16D, rotation.ordinal() % 2 == 0 ? nS / 16D : 12 / 16D, nS / 16D),
                    new Vec3d(4 / 16D, rotation.ordinal() % 2 == 0 ? 4 / 16D : pS / 16D, nS / 16D),
                    new Vec3d(pS / 16D, rotation.ordinal() % 2 == 0 ? 4 / 16D : pS / 16D, nS / 16D)
                );
            }
        }
        if (key.isHollow) {
            for (Direction facing : Direction.values()) {
                if (facing.getAxis() != key.side.getAxis()) {
                    boolean positive = key.side.getDirection() == Direction.AxisDirection.POSITIVE;
                    if (key.side.getAxis() == Direction.Axis.Z && facing.getAxis() == Direction.Axis.X ||
                        key.side.getAxis() == Direction.Axis.X && facing.getAxis() == Direction.Axis.Y ||
                        key.side.getAxis() == Direction.Axis.Y && facing.getAxis() == Direction.Axis.Z) {
                        quads.addAll(getTransformedQuads(
                            key.state, model, facing,
                            new Vec3d(positive ? 16 / 16D : pS / 16D, 4 / 16D, 12.003 / 16D),
                            new Vec3d(positive ? 16 / 16D : pS / 16D, 12 / 16D, 12.003 / 16D),
                            new Vec3d(positive ? nS / 16D : 0 / 16D, 12 / 16D, 12.003 / 16D),
                            new Vec3d(positive ? nS / 16D : 0 / 16D, 4 / 16D, 12.003 / 16D)
                        ));
                    } else {
                        quads.addAll(getTransformedQuads(
                            key.state, model, facing,
                            new Vec3d(4 / 16D, positive ? 16 / 16D : pS / 16D, 12.003 / 16D),
                            new Vec3d(4 / 16D, positive ? nS / 16D : 0 / 16D, 12.003 / 16D),
                            new Vec3d(12 / 16D, positive ? nS / 16D : 0 / 16D, 12.003 / 16D),
                            new Vec3d(12 / 16D, positive ? 16 / 16D : pS / 16D, 12.003 / 16D)
                        ));
                    }
                }
            }
        }
        for (MutableQuad quad : quads) {
            var normal=quad.getCalculatedNormal();
            if(normal.lengthSquared()>0)quad.normalvf(normal.normalize());
        }
        return quads;
    }
}
