/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.transport.client.model;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFaceTex;
import buildcraft.lib.client.model.ModelItemSimple;
import buildcraft.transport.client.model.key.PipeModelKey;
import buildcraft.transport.item.ItemPipeHolder;

/** Native renderer uses immutable render attachments; each resource reload gets a fresh atlas and cache. */
public final class ModelPipe extends ModelItemSimple implements FabricBakedModel {
    private final PipeDefinition itemDefinition;
    private final PipeBaseModelGenStandard geometry;
    private final PipeModelKey fallbackKey;
    private final Map<PipeModelKey, PipeBaseModelGenStandard.Geometry> cache = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<PipeModelKey, PipeBaseModelGenStandard.Geometry> entry) { return size() > 512; }
    };
    public ModelPipe(BakedModel fallback, PipeDefinition itemDefinition, Function<SpriteIdentifier, Sprite> textures) {
        super(List.of(), fallback.getTransformation(), true);
        this.itemDefinition = itemDefinition;
        geometry = new PipeBaseModelGenStandard(textures);
        fallbackKey = itemKey(itemDefinition == null ? buildcraft.transport.BCTransportPipes.structure : itemDefinition, null);
    }
    public static PipeModelKey itemKey(PipeDefinition def, net.minecraft.util.DyeColor colour) {
        var sides = new PipeFaceTex[] {def.itemModelBottom, def.itemModelTop, def.itemModelCenter, def.itemModelCenter, def.itemModelCenter, def.itemModelCenter};
        return new PipeModelKey(def, def.itemModelCenter, sides, new float[] {0.25f, 0.25f, 0, 0, 0, 0}, def.canBeColoured ? colour : null);
    }
    public synchronized PipeBaseModelGenStandard.Geometry getGeometry(PipeModelKey key) {
        return cache.computeIfAbsent(key, geometry::bake);
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {
        return face == null ? getGeometry(fallbackKey).all() : List.of();
    }
    @Override public Sprite getParticleSprite() { return geometry.getItemSprites(fallbackKey.definition)[fallbackKey.definition.itemTextureCenter]; }
    @Override public boolean isSideLit() { return true; }
    @Override public boolean isVanillaAdapter() { return false; }
    @Override public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> random, RenderContext context) {
        Object data = world instanceof RenderAttachedBlockView attached ? attached.getBlockEntityRenderAttachment(pos) : null;
        if (data instanceof PipeModelKey key) emit(getGeometry(key), context);
    }
    @Override public void emitItemQuads(ItemStack stack, Supplier<Random> random, RenderContext context) {
        if (itemDefinition != null) emit(getGeometry(itemKey(itemDefinition, ItemPipeHolder.getColour(stack))), context);
    }
    private static void emit(PipeBaseModelGenStandard.Geometry geometry, RenderContext context) {
        var renderer = RendererAccess.INSTANCE.getRenderer();
        var cutout = renderer.materialFinder().blendMode(BlendMode.CUTOUT).find();
        var translucent = renderer.materialFinder().blendMode(BlendMode.TRANSLUCENT).find();
        var emitter = context.getEmitter();
        for (var quad : geometry.cutout()) emitter.fromVanilla(quad, cutout, null).emit();
        for (var quad : geometry.translucent()) emitter.fromVanilla(quad, translucent, null).emit();
    }
}
