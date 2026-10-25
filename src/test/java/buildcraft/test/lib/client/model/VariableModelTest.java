package buildcraft.test.lib.client.model;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.lib.client.model.*;
import buildcraft.lib.client.model.json.*;
import buildcraft.lib.expression.DefaultContexts;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.node.value.NodeVariableDouble;
import buildcraft.lib.expression.node.value.NodeVariableObject;
import buildcraft.lib.misc.ExpressionCompat;
import buildcraft.lib.misc.data.ModelVariableData;

public class VariableModelTest {
    private final FunctionContext context = new FunctionContext(
        ExpressionCompat.ENUM_FACING, ExpressionCompat.ENUM_POWER_STAGE, DefaultContexts.createWithAll());
    private final NodeVariableDouble progress = context.putVariableDouble("progress");
    private final NodeVariableObject<EnumPowerStage> stage = context.putVariableObject("stage", EnumPowerStage.class);
    private final NodeVariableObject<Direction> facing = context.putVariableObject("direction", Direction.class);
    private final Map<String, Sprite> sprites = new HashMap<>();

    private static final class TestSprite extends Sprite {
        TestSprite(Identifier id) {
            super(new Identifier("minecraft", "textures/atlas/blocks.png"), new SpriteContents(id,
                new SpriteDimensions(16, 16), new NativeImage(16, 16, false), AnimationResourceMetadata.EMPTY),
                32, 32, 0, 0);
        }
    }

    @After public void closeSprites() {
        sprites.values().forEach(sprite -> sprite.getContents().close());
    }

    private JsonVariableModel load(String location) throws Exception {
        return JsonVariableModel.deserialize(new Identifier(location), context, new ResourceLoaderContext(id ->
            Files.newInputStream(Path.of("../../buildcraft_resources/assets", id.getNamespace(), id.getPath()))));
    }

    private JsonVariableModel parse(String json) {
        return new JsonVariableModel(JsonParser.parseString(json).getAsJsonObject(), context, new ResourceLoaderContext());
    }

    private MutableQuad[] bake(JsonVariableModel model) {
        ModelVariableData data = new ModelVariableData();
        data.setNodes(model.createTickableNodes());
        data.refresh();
        return model.bakePart(model.cutoutElements, lookup -> {
            JsonTexture texture = model.resolveTexture(lookup);
            ModelUtil.TexturedFace face = new ModelUtil.TexturedFace();
            face.sprite = sprites.computeIfAbsent(texture.location, key -> new TestSprite(new Identifier(key)));
            face.faceData = texture.faceData;
            return face;
        });
    }

    @Test public void realEngineAnimatesAndSelectsStageTextureAndLight() throws Exception {
        JsonVariableModel model = load("buildcraftcore:models/block/engine_redstone.json");
        stage.value = EnumPowerStage.RED;
        facing.value = Direction.UP;
        progress.value = 0;
        MutableQuad[] idle = bake(model);
        progress.value = 0.5;
        MutableQuad[] extended = bake(model);
        assertEquals(22, idle.length);
        assertEquals(idle.length, extended.length);
        // The second cuboid is the moving piston; its height changes by just under half a block.
        assertEquals(7.995 / 16, extended[6].vertex_0.position_y - idle[6].vertex_0.position_y, 0.00001);
        assertTrue(sprites.containsKey("buildcraftcore:blocks/engine/wood/back"));
        assertTrue(sprites.containsKey("buildcraftlib:blocks/engine/trunk_red"));
        for (int i = 12; i < 18; i++) assertEquals(10, extended[i].vertex_0.light_block);
        progress.value = 1;
        MutableQuad[] returned = bake(model);
        assertEquals(idle[6].vertex_0.position_y, returned[6].vertex_0.position_y, 0.00001);
    }

    @Test public void realEngineRotatesAroundBlockCenterForEveryFacing() throws Exception {
        JsonVariableModel model = load("buildcraftcore:models/block/engine_redstone.json");
        stage.value = EnumPowerStage.BLUE;
        progress.value = 0.25;
        facing.value = Direction.UP;
        MutableQuad[] upright = bake(model);
        for (Direction direction : Direction.values()) {
            facing.value = direction;
            MutableQuad[] rotated = bake(model);
            assertEquals(22, rotated.length);
            for (int i = 0; i < rotated.length; i++) {
                MutableVertex vertex = rotated[i].vertex_0;
                assertTrue(Float.isFinite(vertex.position_x) && Float.isFinite(vertex.tex_u));
                assertEquals(upright[i].vertex_0.positionvf().sub(0.5f, 0.5f, 0.5f).length(),
                    vertex.positionvf().sub(0.5f, 0.5f, 0.5f).length(), 0.00001);
                assertEquals(1, vertex.normal().length(), 0.00001);
            }
        }
    }

    @Test public void everyEngineVariantAndStageBakesUsingExistingTextures() throws Exception {
        for (String location : new String[] { "buildcraftcore:models/block/engine_redstone.json",
                "buildcraftcore:models/block/engine_creative.json", "buildcraftenergy:models/block/engine_stone.json",
                "buildcraftenergy:models/block/engine_iron.json", "buildcraftenergy:models/block/engine_rf.json",
                "buildcraftenergy:models/block/mj_dynamo.json" }) {
            JsonVariableModel model = load(location);
            progress.value = 0.35;
            for (EnumPowerStage power : EnumPowerStage.values()) {
                stage.value = power;
                for (Direction direction : Direction.values()) {
                    facing.value = direction;
                    assertTrue(location, bake(model).length >= 22);
                }
            }
        }
        for (String texture : sprites.keySet()) {
            Identifier id = new Identifier(texture);
            assertTrue("Missing texture " + texture, Files.exists(Path.of("../../buildcraft_resources/assets",
                id.getNamespace(), "textures", id.getPath() + ".png")));
        }
    }

    @Test public void ledPreservesColorEmissionAndAtlasTexel() {
        JsonVariableModel model = parse("""
            {"elements":[{"type":"led","from":[1,2,3],"to":[4,5,6],
              "colour":4278255360,"light":15,"shade":false}]}
            """);
        MutableQuad[] quads = bake(model);
        assertEquals(6, quads.length);
        for (MutableQuad quad : quads) {
            assertEquals(0, quad.vertex_0.colour_r);
            assertEquals(255, quad.vertex_0.colour_g);
            assertEquals(0, quad.vertex_0.colour_b);
            assertEquals(255, quad.vertex_0.colour_a);
            assertEquals(15, quad.vertex_0.light_block);
            assertEquals(0.25, quad.vertex_0.tex_u, 0.0001);
        }
    }

    @Test public void textureAliasesComposeSubregionsAndRejectCycles() {
        JsonVariableModel model = parse("""
            {"textures":{"#base":{"location":"test:blocks/a","uv":[4,4,12,12]},
                "#part":{"location":"#base","uv":[0,0,8,8]},"#cycle":"#cycle"},"elements":[]}
            """);
        JsonTexture resolved = model.resolveTexture("#part");
        assertEquals("test:blocks/a", resolved.location);
        assertEquals(0.25, resolved.faceData.minU, 0.00001);
        assertEquals(0.5, resolved.faceData.maxU, 0.00001);
        assertThrows(JsonParseException.class, () -> model.resolveTexture("#cycle"));
        assertThrows(JsonParseException.class, () -> model.resolveTexture("#missing"));
    }

    @Test public void parentStackAllowsReuseButRejectsRecursion() throws Exception {
        ResourceLoaderContext resources = new ResourceLoaderContext(id -> new ByteArrayInputStream(
            (id.getPath().equals("loop.json") ? "{\"parent\":\"test:loop\"}" : "{\"elements\":[]}")
                .getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 2; i++) {
            assertNotNull(JsonVariableModel.deserialize(new Identifier("test:shared.json"), context, resources));
        }
        assertThrows(JsonParseException.class, () ->
            JsonVariableModel.deserialize(new Identifier("test:loop.json"), context, resources));
        assertNotNull(JsonVariableModel.deserialize(new Identifier("test:shared.json"), context, resources));
    }

    @Test public void reloadInvalidatesNodesBeforeRendering() {
        ModelVariableData data = new ModelVariableData();
        data.setNodes(new buildcraft.lib.expression.node.value.ITickableNode[0]);
        assertFalse(data.hasNoNodes());
        ModelVariableData.onModelBake();
        assertTrue(data.hasNoNodes());
    }
}
