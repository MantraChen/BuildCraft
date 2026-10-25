/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client;

import net.minecraft.util.math.Direction;
import buildcraft.lib.client.model.*;
import buildcraft.lib.expression.*;
import buildcraft.lib.expression.node.value.*;
import buildcraft.lib.misc.ExpressionCompat;
import buildcraft.silicon.gate.GateVariant;

/** The authored static plate and illuminated overlay share the original facing/variant expressions. */
public final class GateModel {
    private final FunctionContext context = new FunctionContext(ExpressionCompat.ENUM_FACING, DefaultContexts.createWithAll());
    private final NodeVariableObject<String> material = context.putVariableObject("material", String.class);
    private final NodeVariableObject<String> modifier = context.putVariableObject("modifier", String.class);
    private final NodeVariableObject<String> logic = context.putVariableObject("logic", String.class);
    private final NodeVariableObject<Direction> side = context.putVariableObject("side", Direction.class);
    private final NodeVariableBoolean on = context.putVariableBoolean("on");
    private final ModelHolderVariable base = new ModelHolderVariable("buildcraftsilicon:bc_models/plugs/gate.json", context);
    private final ModelHolderVariable overlay = new ModelHolderVariable("buildcraftsilicon:bc_models/plugs/gate_dynamic.json", context);
    private final java.util.Map<Key, MutableQuad[]> cache = new java.util.HashMap<>();
    private Object generation;
    private record Key(GateVariant variant, Direction side, boolean on) {}

    public MutableQuad[] getQuads(GateVariant variant, Direction side, boolean on) {
        // JsonVariableModel is replaced on resource reload, invalidating all cached atlas references.
        if (generation != base.getModel()) { generation = base.getModel(); cache.clear(); }
        return cache.computeIfAbsent(new Key(variant, side, on), key -> {
            material.value = variant.material.tag; modifier.value = variant.modifier.tag; logic.value = variant.logic.tag;
            this.side.value = side; this.on.value = on;
            var first = base.getCutoutQuads();
            var second = overlay.getCutoutQuads();
            var result = java.util.Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        });
    }
}
