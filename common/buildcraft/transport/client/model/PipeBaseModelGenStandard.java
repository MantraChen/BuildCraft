/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.transport.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.joml.Vector3f;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import buildcraft.api.transport.pipe.EnumPipeColourType;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFaceTex;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.ColourUtil;
import buildcraft.transport.client.model.key.PipeModelKey;

/** Original hollow centre/arm geometry and texture layout, baked against the current native atlas. */
public final class PipeBaseModelGenStandard implements IPipeBaseModelGen {
    public static final PipeBaseModelGenStandard INSTANCE = new PipeBaseModelGenStandard(
        id -> buildcraft.lib.compat.McTextureCompat.getSprite(id.getTextureId()));
    private static final int[][] ROTATIONS = {{2,0,3,3},{0,2,1,1},{2,0,0,2},{0,2,2,0},{3,3,0,2},{1,1,2,0}};
    private static final UvFaceData[] ARM_UV = {UvFaceData.from16(4,0,12,4), UvFaceData.from16(4,12,12,16),
        UvFaceData.from16(0,4,4,12), UvFaceData.from16(12,4,16,12)};
    private final Function<SpriteIdentifier, Sprite> textures;
    public PipeBaseModelGenStandard(Function<SpriteIdentifier, Sprite> textures) { this.textures = textures; }
    private Sprite sprite(String id) { return textures.apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(id))); }
    public Sprite[] getItemSprites(PipeDefinition definition) {
        return java.util.Arrays.stream(definition.textures).map(this::sprite).toArray(Sprite[]::new);
    }
    public record Geometry(List<BakedQuad> cutout, List<BakedQuad> translucent) {
        public List<BakedQuad> all() {
            var result = new ArrayList<>(cutout); result.addAll(translucent); return List.copyOf(result);
        }
    }
    public Geometry bake(PipeModelKey key) {
        var opaque = new ArrayList<BakedQuad>();
        var translucent = new ArrayList<BakedQuad>();
        if (key.definition == null) return new Geometry(List.of(), List.of());
        var sprites = getItemSprites(key.definition);
        var colourType = key.getColourType();
        int colour = 0xFF000000 | ColourUtil.swapArgbToAbgr(ColourUtil.getLightHex(key.colour));
        for (var side : Direction.values()) {
            float length = key.connected[side.ordinal()];
            List<MutableQuad> surfaces = surfaces(side, length);
            PipeFaceTex tex = length > 0 ? key.sides[side.ordinal()] : key.center;
            for (int layer = 0; layer < tex.getCount(); layer++) {
                int index = tex.getTexture(layer);
                Sprite texture = index >= 0 && index < sprites.length ? sprites[index] : sprite("minecraft:missingno");
                for (var face : surfaces) addDouble(opaque, face, texture, 0xFF000000 | ColourUtil.swapArgbToAbgr(tex.getColour(layer)), false);
            }
            if (key.colour != null) {
                boolean stained = colourType == EnumPipeColourType.TRANSLUCENT;
                Sprite overlay = sprite("buildcrafttransport:pipes/" + (stained ? "overlay_stained"
                    : colourType == EnumPipeColourType.BORDER_INNER ? "colour_border_inner" : "colour_border_outer"));
                for (var face : surfaces) addDouble(stained ? translucent : opaque, face, overlay, colour, stained);
            }
        }
        return new Geometry(List.copyOf(opaque), List.copyOf(translucent));
    }
    private static List<MutableQuad> surfaces(Direction side, float length) {
        if (length <= 0) return List.of(ModelUtil.createFace(side, new Vector3f(0.5f), new Vector3f(0.25f), UvFaceData.from16(4,4,12,12)));
        var center = new Vector3f(0.5f).add(side.getOffsetX() * (0.25f + length / 2),
            side.getOffsetY() * (0.25f + length / 2), side.getOffsetZ() * (0.25f + length / 2));
        var radius = new Vector3f(0.25f);
        radius.setComponent(side.getAxis().ordinal(), length / 2);
        var result = new ArrayList<MutableQuad>();
        int index = 0;
        for (var face : Direction.values()) if (face.getAxis() != side.getAxis()) {
            result.add(ModelUtil.createFace(face, center, radius, ARM_UV[index]).rotateTextureUp(ROTATIONS[side.ordinal()][index]));
            index++;
        }
        return result;
    }
    private static void addDouble(List<BakedQuad> output, MutableQuad source, Sprite sprite, int abgr, boolean inset) {
        var quad = new MutableQuad(source);
        if (inset) {
            var normal = quad.normalvf();
            quad.translatevd(new net.minecraft.util.math.Vec3d(-normal.x * 0.01, -normal.y * 0.01, -normal.z * 0.01));
        }
        quad.setSprite(sprite);
        quad.texFromSprite(sprite).multColouri(abgr & 255, (abgr >>> 8) & 255, (abgr >>> 16) & 255, (abgr >>> 24) & 255);
        quad.setShade(true);
        output.add(quad.toBakedBlock());
        var inside = quad.copyAndInvertNormal();
        if (!inset) inside.multColourd(0.7);
        output.add(inside.toBakedBlock());
    }
}
