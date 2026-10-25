/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.client.model;

import net.minecraft.util.math.Direction;
import buildcraft.api.enums.EnumPowerStage;
import buildcraft.lib.expression.DefaultContexts;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.node.value.NodeVariableDouble;
import buildcraft.lib.expression.node.value.NodeVariableObject;
import buildcraft.lib.misc.ExpressionCompat;
import buildcraft.lib.misc.data.ModelVariableData;

/** Each model owns its expression inputs; each engine owns its stateful animation nodes. */
public final class EngineModel {
    private final NodeVariableDouble progress;
    private final NodeVariableObject<EnumPowerStage> stage;
    private final NodeVariableObject<Direction> facing;
    private final ModelHolderVariable holder;
    private final ModelVariableData itemData = new ModelVariableData();

    public EngineModel(String location) {
        FunctionContext context = new FunctionContext(ExpressionCompat.ENUM_FACING,
            ExpressionCompat.ENUM_POWER_STAGE, DefaultContexts.createWithAll());
        progress = context.putVariableDouble("progress");
        stage = context.putVariableObject("stage", EnumPowerStage.class);
        facing = context.putVariableObject("direction", Direction.class);
        holder = new ModelHolderVariable(location, context);
    }

    public MutableQuad[] getQuads(double progress, EnumPowerStage stage, Direction facing, ModelVariableData data) {
        this.progress.value = progress;
        this.stage.value = stage;
        this.facing.value = facing;
        if (data.hasNoNodes()) data.setNodes(holder.createTickableNodes());
        data.refresh();
        return holder.getCutoutQuads();
    }

    public MutableQuad[] getItemQuads(EnumPowerStage stage) {
        return getQuads(0.2, stage, Direction.UP, itemData);
    }
}
