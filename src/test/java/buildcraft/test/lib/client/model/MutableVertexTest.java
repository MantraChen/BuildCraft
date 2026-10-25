package buildcraft.test.lib.client.model;

import static org.junit.Assert.*;
import org.junit.Test;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import buildcraft.lib.client.model.MutableVertex;

public class MutableVertexTest {
    @Test public void bakedNormalsRoundTripSignedComponentsWithoutContaminatingOtherAxes() {
        MutableVertex source = new MutableVertex().normalf(-1, 0.5f, -0.25f);
        MutableVertex decoded = new MutableVertex().normali(source.normalToPackedInt());
        assertEquals(-1, decoded.normal_x, 0.00001);
        assertEquals(0.5, decoded.normal_y, 1.0 / 127);
        assertEquals(-0.25, decoded.normal_z, 1.0 / 127);
    }

    @Test public void entityOutputTransformsPositionNormalAndMergesLightingWithoutMutatingModel() {
        MutableVertex vertex = new MutableVertex().positionf(1, 0, 0).normalf(1, 0, 0)
            .colouri(200, 100, 50, 255).texf(0.25f, 0.75f).lighti(10, 2);
        MatrixStack matrices = new MatrixStack();
        matrices.translate(2, 3, 4);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        Capture consumer = new Capture();
        vertex.render(consumer, matrices.peek(), (4 << 4) | (15 << 20), 0x000A0003, false);
        assertEquals(1, consumer.count);
        assertEquals(2, consumer.x, 0.00001);
        assertEquals(3, consumer.y, 0.00001);
        assertEquals(3, consumer.z, 0.00001);
        assertEquals(0, consumer.nx, 0.00001);
        assertEquals(-1, consumer.nz, 0.00001);
        assertEquals(200, consumer.r);
        assertEquals(100, consumer.g);
        assertEquals(50, consumer.b);
        assertEquals(255, consumer.a);
        assertEquals(10 << 4, consumer.blockLight);
        assertEquals(15 << 4, consumer.skyLight);
        assertEquals(3, consumer.overlayU);
        assertEquals(10, consumer.overlayV);
        assertEquals(0.25, consumer.u, 0.00001);
        assertEquals(0.75, consumer.v, 0.00001);
        assertEquals(1, vertex.position_x, 0);
        assertEquals(1, vertex.normal_x, 0);
        assertEquals(2, vertex.light_sky);
    }

    @Test public void arbitraryRotationRotatesNormalsWithPositions() {
        MutableVertex vertex = new MutableVertex().positionf(1, 0, 0).normalf(1, 0, 0);
        vertex.rotateY((float) (Math.PI / 4));
        vertex.rotateX((float) (Math.PI / 6));
        vertex.rotateZ((float) (Math.PI / 3));
        assertEquals(vertex.position_x, vertex.normal_x, 0.00001);
        assertEquals(vertex.position_y, vertex.normal_y, 0.00001);
        assertEquals(vertex.position_z, vertex.normal_z, 0.00001);
        assertEquals(1, vertex.normal().length(), 0.0001);
    }

    private static final class Capture implements VertexConsumer {
        double x, y, z;
        float nx, ny, nz, u, v;
        int r, g, b, a, blockLight, skyLight, overlayU, overlayV, count;
        @Override public VertexConsumer vertex(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z; return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            this.r = r; this.g = g; this.b = b; this.a = a; return this;
        }
        @Override public VertexConsumer texture(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer overlay(int u, int v) { overlayU = u; overlayV = v; return this; }
        @Override public VertexConsumer light(int u, int v) { blockLight = u; skyLight = v; return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { nx = x; ny = y; nz = z; return this; }
        @Override public void next() { count++; }
        @Override public void fixedColor(int r, int g, int b, int a) { throw new AssertionError(); }
        @Override public void unfixColor() { throw new AssertionError(); }
    }
}
