/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.client.model;
import buildcraft.lib.client.model.*;
import buildcraft.lib.expression.*;
import buildcraft.lib.expression.node.value.NodeVariableObject;
import buildcraft.lib.misc.ExpressionCompat;
import net.minecraft.util.math.Direction;

/** Original gold direction rod, with atlas-reload-aware geometry. */
public final class StripesModel {
    private final FunctionContext context=new FunctionContext(ExpressionCompat.ENUM_FACING,DefaultContexts.createWithAll());
    private final NodeVariableObject<Direction> side=context.putVariableObject("side",Direction.class);
    private final ModelHolderVariable model=new ModelHolderVariable("buildcrafttransport:bc_models/pipes/stripes.json",context);
    private final java.util.Map<Direction,MutableQuad[]> cache=new java.util.EnumMap<>(Direction.class);
    private Object generation;
    public MutableQuad[] getQuads(Direction face){
        if(generation!=model.getModel()){generation=model.getModel();cache.clear();}
        side.value=face;return cache.computeIfAbsent(face,key->model.getCutoutQuads());
    }
}
