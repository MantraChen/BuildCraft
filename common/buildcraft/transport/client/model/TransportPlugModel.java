/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport.client.model;

import buildcraft.lib.client.model.*;
import buildcraft.lib.expression.*;
import buildcraft.lib.expression.node.value.NodeVariableObject;
import buildcraft.lib.misc.ExpressionCompat;
import net.minecraft.util.math.Direction;

/** Original blocker/adaptor geometry, rotated to the mounted face and cached per atlas load. */
public final class TransportPlugModel {
    private final FunctionContext context=new FunctionContext(ExpressionCompat.ENUM_FACING,DefaultContexts.createWithAll());
    private final NodeVariableObject<Direction> side=context.putVariableObject("side",Direction.class);
    private final ModelHolderVariable blocker=new ModelHolderVariable("buildcrafttransport:bc_models/plugs/blocker.json",context);
    private final ModelHolderVariable adaptor=new ModelHolderVariable("buildcrafttransport:bc_models/plugs/power_adapter.json",context);
    private final java.util.Map<String,MutableQuad[]> cache=new java.util.HashMap<>();
    private Object generation;
    public MutableQuad[] getQuads(boolean powerAdaptor,Direction face){
        if(generation!=blocker.getModel()){generation=blocker.getModel();cache.clear();}
        side.value=face;
        return cache.computeIfAbsent(powerAdaptor+face.name(),key->(powerAdaptor?adaptor:blocker).getCutoutQuads());
    }
}
