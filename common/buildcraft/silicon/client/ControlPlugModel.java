/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client;

import buildcraft.lib.client.model.*;
import buildcraft.lib.expression.*;
import buildcraft.lib.expression.node.value.*;
import buildcraft.lib.misc.ExpressionCompat;
import buildcraft.silicon.item.ItemPluggableControl.Kind;
import net.minecraft.util.math.Direction;

/** Original plate textures and pulsar stroke/LED model, backed by native atlas reloads. */
public final class ControlPlugModel {
    private final FunctionContext context=new FunctionContext(ExpressionCompat.ENUM_FACING,DefaultContexts.createWithAll());
    private final NodeVariableObject<Direction> side=context.putVariableObject("side",Direction.class);
    private final NodeVariableObject<String> kind=context.putVariableObject("kind",String.class);
    private final NodeVariableDouble stage=context.putVariableDouble("stage");
    private final NodeVariableBoolean on=context.putVariableBoolean("on"),automatic=context.putVariableBoolean("auto"),manual=context.putVariableBoolean("manual");
    private final ModelHolderVariable plate=new ModelHolderVariable("buildcraftsilicon:bc_models/plugs/control_plate.json",context);
    private final ModelHolderVariable piston=new ModelHolderVariable("buildcraftsilicon:bc_models/plugs/pulsar_dynamic.json",context);
    private final buildcraft.lib.misc.data.ModelVariableData animation=new buildcraft.lib.misc.data.ModelVariableData();
    private final java.util.Map<String,MutableQuad[]> cache=new java.util.HashMap<>();
    private Object generation;
    public MutableQuad[] getQuads(Kind type,Direction face,boolean pulsing,boolean auto,boolean enabled,double phase) {
        if(generation!=plate.getModel()){generation=plate.getModel();cache.clear();}
        side.value=face;kind.value=type.name().toLowerCase(java.util.Locale.ROOT);
        var base=cache.computeIfAbsent(type.name()+face,key->plate.getCutoutQuads());
        if(type!=Kind.PULSAR)return base;
        on.value=pulsing;automatic.value=auto;manual.value=enabled;stage.value=pulsing?phase%1:0;
        if(animation.hasNoNodes())animation.setNodes(piston.createTickableNodes());
        animation.refresh();
        var dynamic=piston.getCutoutQuads();var result=java.util.Arrays.copyOf(base,base.length+dynamic.length);
        System.arraycopy(dynamic,0,result,base.length,dynamic.length);return result;
    }
}
