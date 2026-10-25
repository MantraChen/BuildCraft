/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.factory.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.client.model.ModelItemSimple;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.expression.DefaultContexts;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.api.NodeType;
import buildcraft.lib.expression.api.NodeTypes;
import buildcraft.lib.expression.node.value.NodeVariableBoolean;
import buildcraft.lib.expression.node.value.NodeVariableObject;
import buildcraft.lib.misc.ExpressionCompat;
import buildcraft.factory.BCFactoryModels;
import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.block.BlockHeatExchange.EnumExchangePart;

/** Native baked model. Geometry is populated only after the variable model and atlas reload. */
public final class ModelHeatExchange extends ModelItemSimple {
    public static final NodeType<EnumExchangePart> TYPE_EXCHANGE_PART;
    public static final FunctionContext FUNCTION_CONTEXT;
    public static final NodeVariableBoolean VAR_CONNECTED_LEFT, VAR_CONNECTED_RIGHT, VAR_CONNECTED_UP, VAR_CONNECTED_DOWN;
    public static final NodeVariableObject<EnumExchangePart> VAR_PART;
    public static final NodeVariableObject<Direction> VAR_DIRECTION;
    static {
        TYPE_EXCHANGE_PART = new NodeType<>("HeatExchangePart", EnumExchangePart.MIDDLE);
        NodeTypes.addType(TYPE_EXCHANGE_PART);
        for (EnumExchangePart part : EnumExchangePart.values()) {
            TYPE_EXCHANGE_PART.putConstant(part.asString(), part);
            TYPE_EXCHANGE_PART.putConstant(part.name(), part);
        }
        FUNCTION_CONTEXT = new FunctionContext("heat_exchange", DefaultContexts.createWithAll(),
            ExpressionCompat.ENUM_FACING, TYPE_EXCHANGE_PART);
        VAR_CONNECTED_LEFT = FUNCTION_CONTEXT.putVariableBoolean("connected_left");
        VAR_CONNECTED_RIGHT = FUNCTION_CONTEXT.putVariableBoolean("connected_right");
        VAR_CONNECTED_UP = FUNCTION_CONTEXT.putVariableBoolean("connected_up");
        VAR_CONNECTED_DOWN = FUNCTION_CONTEXT.putVariableBoolean("connected_down");
        VAR_PART = FUNCTION_CONTEXT.putVariableObject("part", EnumExchangePart.class);
        VAR_DIRECTION = FUNCTION_CONTEXT.putVariableObject("direction", Direction.class);
    }
    private final BakedModel fallback;
    private final List<List<BakedQuad>> cache = new ArrayList<>();
    private Object cachedModel;
    public ModelHeatExchange(BakedModel fallback) {
        super(List.of(), fallback == null ? TRANSFORM_DEFAULT : fallback.getTransformation(), true);
        this.fallback = fallback;
    }
    private void rebuild() {
        var holder = BCFactoryModels.HEAT_EXCHANGE_STATIC;
        if (!holder.hasBakedQuads()) return;
        Object model = holder.getModel();
        if (cachedModel == model) return;
        cache.clear();
        for (int i = 0; i < 96; i++) {
            EnumExchangePart part = EnumExchangePart.values()[i / 32];
            VAR_CONNECTED_LEFT.value = (i & 1) != 0;
            VAR_CONNECTED_RIGHT.value = (i & 2) != 0;
            VAR_CONNECTED_UP.value = (i & 4) != 0 && part == EnumExchangePart.END;
            VAR_CONNECTED_DOWN.value = (i & 4) != 0 && part == EnumExchangePart.START;
            VAR_PART.value = part;
            VAR_DIRECTION.value = Direction.fromHorizontal((i / 8) & 3);
            List<BakedQuad> quads = new ArrayList<>();
            for (MutableQuad quad : holder.getCutoutQuads()) quads.add(quad.toBakedBlock());
            cache.add(List.copyOf(quads));
        }
        cachedModel = model;
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
        if (side != null) return List.of();
        // Chunk mesh workers share the expression inputs and cache.
        synchronized (FUNCTION_CONTEXT) {
            rebuild();
            if (cache.isEmpty()) return List.of();
            int index = state == null ? 32 + Direction.NORTH.getHorizontal() * 8
                : (state.get(BlockHeatExchange.PROP_CONNECTED_LEFT) ? 1 : 0)
                | (state.get(BlockHeatExchange.PROP_CONNECTED_RIGHT) ? 2 : 0)
                | (state.get(BlockHeatExchange.PROP_CONNECTED_Y) ? 4 : 0)
                | state.get(BlockBCBase_Neptune.PROP_FACING).getHorizontal() * 8
                | state.get(BlockHeatExchange.PROP_PART).ordinal() * 32;
            return cache.get(index);
        }
    }
    @Override public Sprite getParticleSprite() {
        synchronized (FUNCTION_CONTEXT) {
            rebuild();
            if (!cache.isEmpty() && !cache.get(32).isEmpty()) return cache.get(32).get(0).getSprite();
            return fallback == null ? buildcraft.lib.compat.McTextureCompat.getMissingSprite() : fallback.getParticleSprite();
        }
    }
    @Override public boolean isSideLit() { return true; }
    @Override public boolean useAmbientOcclusion() { return true; }
}
