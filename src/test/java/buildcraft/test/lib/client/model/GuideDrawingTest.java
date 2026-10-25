package buildcraft.test.lib.client.model;

import static org.junit.Assert.*;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.Test;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import buildcraft.lib.client.guide.font.GuideFont;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.guide.node.FormatString;
import buildcraft.lib.client.sprite.DynamicTextureBC;
import buildcraft.lib.client.sprite.SpriteNineSliced;
import buildcraft.lib.client.sprite.SpriteRaw;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.GuiSpriteRenderer;

public class GuideDrawingTest {
    @Test public void nativeFrameScopesRestoreOuterFrameAndRejectOutOfOrderClose() {
        var a = new net.minecraft.client.gui.DrawContext(null, null);
        var b = new net.minecraft.client.gui.DrawContext(null, null);
        assertThrows(IllegalStateException.class, buildcraft.lib.gui.GuiDrawContext::current);
        try (var first = buildcraft.lib.gui.GuiDrawContext.use(a)) {
            assertSame(a, buildcraft.lib.gui.GuiDrawContext.current());
            try (var second = buildcraft.lib.gui.GuiDrawContext.use(b)) {
                assertSame(b, buildcraft.lib.gui.GuiDrawContext.current());
                assertThrows(IllegalStateException.class, first::close);
            }
            assertSame(a, buildcraft.lib.gui.GuiDrawContext.current());
        }
        assertThrows(IllegalStateException.class, buildcraft.lib.gui.GuiDrawContext::current);
    }

    private static final IFontRenderer FONT = new IFontRenderer() {
        public int getStringWidth(String text) {
            String plain = Formatting.strip(text);
            return plain.codePointCount(0, plain.length()) * 5;
        }
        public int getFontHeight(String text) { return 9; }
        public int getMaxFontHeight() { return 9; }
        public int drawString(String t, int x, int y, int c, boolean s, boolean center, float scale) {
            throw new AssertionError("Layout must never draw");
        }
    };

    @Test(timeout = 2000) public void narrowColumnsConsumeWholeUnicodeCodePoints() {
        for (int width : new int[] { -1, 0, 1, 5, 10 }) {
            List<String> lines = FONT.wrapString("\u00a7l中文😀xyz", width, false, 1);
            String joined = String.join("", lines.stream().map(Formatting::strip).toList());
            assertEquals("中文😀xyz", joined);
            for (String line : lines) {
                String plain = Formatting.strip(line);
                assertFalse(plain.isEmpty());
                assertFalse(Character.isLowSurrogate(plain.charAt(0)));
                assertFalse(Character.isHighSurrogate(plain.charAt(plain.length() - 1)));
            }
        }
    }

    @Test public void explicitNewlinesKeepEmptyLinesAndFormattingAcrossWraps() {
        List<String> lines = FONT.wrapString("\u00a7L红色\n\nabc\n", 10, false, 1);
        assertEquals(List.of("红色", "", "ab", "c", ""), lines.stream().map(Formatting::strip).toList());
        assertTrue(FormatString.split(lines.get(2)).segments[0].misc.contains(Formatting.BOLD));
        assertEquals(List.of(""), FONT.wrapString("", 10, false, 1));
    }

    @Test public void wordWrappingSpansStyleBoundariesAndNeverLosesText() {
        String text = "one \u00a7aab\u00a7lcd next";
        List<String> lines = FONT.wrapString(text, 35, false, 1);
        assertEquals("one ", Formatting.strip(lines.get(0)));
        assertEquals("one abcd next", String.join("", lines.stream().map(Formatting::strip).toList()));
        var codes = FormatString.split("\u00a7lBold\u00a7CColor\u00a7rPlain");
        assertTrue(codes.segments[0].misc.contains(Formatting.BOLD));
        assertTrue(codes.segments[1].misc.isEmpty());
        assertEquals(Formatting.RED, codes.segments[1].colour);
        assertNull(codes.segments[2].colour);
    }

    @Test public void scaledWrappingRejectsInvalidScaleAndReservesShadowPixel() {
        assertEquals(2, FONT.wrapString("ab", 20, true, 2).size());
        assertEquals(1, FONT.wrapString("ab", 20, false, 2).size());
        for (float scale : new float[] { 0, -1, Float.NaN, Float.POSITIVE_INFINITY })
            assertThrows(IllegalArgumentException.class, () -> FONT.wrapString("x", 10, false, scale));
    }

    @Test public void iconsRetainSizeOffsetsAndActualSpriteUvs() {
        Identifier texture = new Identifier("buildcraftlib", "textures/gui/guide/icons.png");
        GuiIcon icon = new GuiIcon(texture, 80, 164, 16, 16);
        assertNotNull(icon.sprite);
        assertEquals(80.0 / 256, icon.sprite.getInterpU(0), 1e-8);
        GuiIcon spriteIcon = new GuiIcon(icon.sprite, 256);
        assertEquals(16, spriteIcon.width);
        assertEquals(16, spriteIcon.height);
        for (GuiIcon shifted : new GuiIcon[] { icon.offset(8, 12), spriteIcon.offset(8, 12) }) {
            assertEquals(88.0 / 256, shifted.sprite.getInterpU(0), 1e-8);
            assertEquals(192.0 / 256, shifted.sprite.getInterpV(1), 1e-8);
            assertEquals(16, shifted.width);
        }
    }

    @Test public void spriteQuadsApplyFrameTranslationScaleAndNormalizedUvs() {
        SpriteRaw sprite = new SpriteRaw(new Identifier("test", "texture"), 32, 64, 16, 8, 256);
        Collector output = new Collector();
        GuiSpriteRenderer.quad(output, new Matrix4f().translate(10, 20, 3).scale(2, 3, 1),
            sprite, 1, 2, 5, 7, 0, 0, 1, 1);
        assertEquals(4, output.vertices.size());
        assertArrayEquals(new double[] { 12, 26, 3, 0.125, 0.25 }, output.vertices.get(0), 1e-6);
        assertArrayEquals(new double[] { 20, 41, 3, 0.1875, 0.28125 }, output.vertices.get(2), 1e-6);
    }

    @Test public void nineSliceBordersKeepUvOrderForNormalAndTinyPanels() {
        var sprite = new SpriteRaw(new Identifier("test", "texture"), 0, 0, 16, 16, 256);
        var nine = new SpriteNineSliced(sprite, 4, 4, 12, 12, 16);
        for (int width : new int[] { 3, 8, 50 }) {
            Collector output = new Collector();
            nine.emit(output, new Matrix4f(), 7, 11, width, 30);
            assertTrue(output.vertices.size() > 0);
            for (int i = 0; i < output.vertices.size(); i += 4) {
                var top = output.vertices.get(i);
                var bottom = output.vertices.get(i + 2);
                assertTrue(top[0] <= bottom[0]);
                assertTrue(top[1] <= bottom[1]);
                assertTrue(top[0] >= 7 && bottom[0] <= 7 + width);
                assertTrue(top[1] >= 11 && bottom[1] <= 41);
                assertTrue(top[3] <= bottom[3] && top[4] <= bottom[4]);
            }
        }
    }

    @Test public void dynamicTexturePreservesArgbClampsChannelsAndClosesIdempotently() {
        var texture = new DynamicTextureBC(2, 2);
        texture.setColor(0, 0, 0x80123456);
        assertEquals(0x80123456, texture.getColor(0, 0));
        texture.setColori(1, 0, 280, -2, 33, 128);
        assertEquals(0x80FF0021, texture.getColor(1, 0));
        texture.setColord(0, 1, 1, 0.5, 0, 0.25);
        assertEquals(0x40FF8000, texture.getColor(0, 1));
        texture.close();
        texture.close();
        assertThrows(IllegalStateException.class, () -> texture.getColor(0, 0));
        assertThrows(IllegalStateException.class, () -> texture.setColor(0, 0, 0));
    }

    @Test public void smoothFontRendersActualTransparentPixelsAndHonorsStyleAtTwoDensities() {
        try (GuideFont font = new GuideFont(new Font("SansSerif", Font.PLAIN, 12))) {
            assertEquals(font.getStringWidth("abc"), font.getStringWidth("\u00a7cabc"));
            assertTrue(font.getStringWidth("\u00a7labc") >= font.getStringWidth("abc"));
            for (float density : new float[] { 1, 2 }) {
                try (var texture = font.rasterize("\u00a7cHello", 0xFFFFFFFF, false, density)) {
                    int transparent = 0, colored = 0;
                    for (int y = 0; y < texture.height; y++) for (int x = 0; x < texture.width; x++) {
                        int pixel = texture.getColor(x, y);
                        if (pixel >>> 24 == 0) transparent++;
                        else {
                            assertTrue((pixel >>> 16 & 255) > (pixel & 255));
                            colored++;
                        }
                    }
                    assertTrue(transparent > 0 && colored > 0);
                    assertEquals((int) Math.ceil(font.getStringWidth("Hello") * density), texture.width);
                }
            }
        }
    }

    private static final class Collector implements VertexConsumer {
        final List<double[]> vertices = new ArrayList<>();
        double x, y, z, u, v;
        public VertexConsumer vertex(double x, double y, double z) { this.x=x; this.y=y; this.z=z; return this; }
        public VertexConsumer texture(float u, float v) { this.u=u; this.v=v; return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer overlay(int u, int v) { return this; }
        public VertexConsumer light(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void next() { vertices.add(new double[] {x, y, z, u, v}); }
        public void fixedColor(int r, int g, int b, int a) {}
        public void unfixColor() {}
    }
}
