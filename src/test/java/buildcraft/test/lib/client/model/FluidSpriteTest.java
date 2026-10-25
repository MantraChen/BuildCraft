package buildcraft.test.lib.client.model;

import static org.junit.Assert.*;
import java.nio.file.*;
import com.google.gson.JsonParser;
import org.junit.Test;
import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
import buildcraft.energy.client.sprite.AtlasSpriteFluid;

public class FluidSpriteTest {
    @Test public void gasMeshOccupiesTopOfBlockAndSideUvTracksVisibleHeight() {
        var side = buildcraft.energy.client.render.GasFluidRenderHandler.createFace(
            net.minecraft.util.math.Direction.EAST, 0.75f, 1);
        for (var vertex : new buildcraft.lib.client.model.MutableVertex[] {
                side.vertex_0, side.vertex_1, side.vertex_2, side.vertex_3 }) {
            assertTrue(vertex.position_y >= 0.75f && vertex.position_y <= 1);
            assertTrue(vertex.tex_v >= 0 && vertex.tex_v <= 0.25f);
            assertEquals(1, vertex.normal_x, 0);
        }
        var bottom = buildcraft.energy.client.render.GasFluidRenderHandler.createFace(
            net.minecraft.util.math.Direction.DOWN, 0.75f, 1);
        assertEquals(0.75, bottom.vertex_0.position_y, 0);
        assertEquals(-1, bottom.vertex_0.normal_y, 0);
    }

    @Test public void rgbEndpointsProduceNativeAbgrPixelsAndPreserveAlpha() {
        assertEquals(0x80563412, AtlasSpriteFluid.recolorPixel(0x80000000, 0xABCDEF, 0x123456));
        assertEquals(0x80EFCDAB, AtlasSpriteFluid.recolorPixel(0x80FFFFFF, 0xABCDEF, 0x123456));
        assertEquals(0, AtlasSpriteFluid.recolorPixel(0x00FFFFFF, 0, 0));
    }

    @Test public void eachActualHeatTemplateRetainsAnimationAndClosesItsNativeImage() throws Exception {
        for (int heat = 0; heat < 3; heat++) for (String kind : new String[] { "still", "flow" }) {
            Path path = Path.of("../../buildcraft_resources/assets/buildcraftenergy/textures/blocks/fluids",
                "heat_" + heat + "_" + kind + ".png");
            AnimationResourceMetadata metadata;
            try (var reader = Files.newBufferedReader(Path.of(path + ".mcmeta"))) {
                metadata = AnimationResourceMetadata.READER.fromJson(
                    JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animation"));
            }
            try (var input = Files.newInputStream(path);
                    SpriteContents sprite = AtlasSpriteFluid.loadRecolored(new Identifier("test", "heat_" + heat + kind),
                        input, metadata, 0xFFFF30, 0xE4CF00)) {
                assertTrue(sprite.getWidth() > 0 && sprite.getHeight() > 0);
                assertTrue("Animated fluid must retain all frames", sprite.getDistinctFrameCount().count() > 1);
            }
        }
    }
}
