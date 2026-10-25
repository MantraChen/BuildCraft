package buildcraft.test.lib.client.model;

import java.util.ArrayList;
import java.util.List;
import buildcraft.api.core.render.ISprite;
import buildcraft.lib.client.render.laser.*;
import buildcraft.lib.client.render.laser.LaserData_BC8.*;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import static org.junit.Assert.*;

public class LaserGeometryTest {
    private static final ISprite SPRITE = new ISprite() {
        public double getInterpU(double u) { return u; }
        public double getInterpV(double v) { return v; }
        public void bindTexture() {}
    };
    record Vertex(Vec3d position, double u, double v, int light, Vec3d normal, float shade) {}
    private static LaserType type(boolean ends) {
        var cap = new LaserRow(SPRITE, 0, 0, 2, 2);
        var middle = new LaserRow(SPRITE, 0, 2, 16, 4);
        return new LaserType(cap, ends ? middle : null, new LaserRow[]{middle}, ends ? middle : null, cap);
    }
    private static List<Vertex> bake(LaserType type, Vec3d start, Vec3d end, double scale, boolean doubled) {
        List<Vertex> vertices = new ArrayList<>();
        var data = new LaserData_BC8(type, start, end, scale, true, doubled, 0);
        var context = new LaserContext((x,y,z,u,v,light,nx,ny,nz,shade) ->
            vertices.add(new Vertex(new Vec3d(x,y,z),u,v,light,new Vec3d(nx,ny,nz),shade)), data, true, doubled, point -> 0xF000F0);
        new CompiledLaserType(type).bakeFor(context);
        return vertices;
    }
    @Test public void endpointsNormalsAndWindingWorkOnEveryAxisAndDiagonal() {
        for (Vec3d end : List.of(new Vec3d(5,0,0), new Vec3d(-5,0,0), new Vec3d(0,5,0), new Vec3d(0,-5,0),
                new Vec3d(0,0,5), new Vec3d(0,0,-5), new Vec3d(3,4,5))) {
            var vertices = bake(type(true), Vec3d.ZERO, end, 1/16.0, false);
            assertFalse(vertices.isEmpty());
            Vec3d axis = end.normalize();
            double length = end.length();
            double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
            for (Vertex v : vertices) {
                double along = v.position.dotProduct(axis);
                minimum = Math.min(minimum, along); maximum = Math.max(maximum, along);
                assertTrue(along >= -1e-7 && along <= length+1e-7);
                assertEquals(1, v.normal.length(), 1e-6);
                assertTrue(v.u >= 0 && v.u <= 1 && v.v >= 0 && v.v <= 1);
                assertEquals(0xF000F0, v.light);
                assertTrue(v.shade > 0 && v.shade <= 1);
            }
            assertEquals(0, minimum, 1e-6); assertEquals(length, maximum, 1e-6);
            for (int i=0; i<vertices.size(); i+=4) {
                Vec3d a=vertices.get(i).position, b=vertices.get(i+1).position, c=vertices.get(i+2).position;
                assertTrue("Quad winding disagrees with its normal", b.subtract(a).crossProduct(c.subtract(a)).dotProduct(vertices.get(i).normal) > 0);
            }
        }
    }
    @Test public void shortAndFractionalBeamsNeverExtendPastTheEnd() {
        for (boolean ends : List.of(false,true)) for (double length : new double[]{.01,.25,1,1.1,2.3,3.7}) {
            var vertices=bake(type(ends),Vec3d.ZERO,new Vec3d(length,0,0),1/16.0,false);
            assertFalse(vertices.isEmpty());
            for (Vertex v:vertices) assertTrue(v.position.x >= -1e-8 && v.position.x <= length+1e-8);
            assertEquals(length,vertices.stream().mapToDouble(v->v.position.x).max().orElseThrow(),1e-8);
        }
    }
    @Test public void twoSidedFacesReverseWindingAndNormals() {
        var single=bake(type(false),Vec3d.ZERO,new Vec3d(1,0,0),1/16.0,false);
        var doubled=bake(type(false),Vec3d.ZERO,new Vec3d(1,0,0),1/16.0,true);
        assertEquals(single.size()*2,doubled.size());
        for (int i=0;i<doubled.size();i+=8) for(int j=0;j<4;j++) {
            Vertex a=doubled.get(i+j), b=doubled.get(i+7-j);
            assertEquals(a.position,b.position);
            assertEquals(-1,a.normal.dotProduct(b.normal),1e-7);
        }
    }
    @Test public void invalidAndZeroLengthInputsEmitNothing() {
        assertTrue(bake(type(false),Vec3d.ZERO,Vec3d.ZERO,1/16.0,false).isEmpty());
        assertTrue(bake(type(false),Vec3d.ZERO,new Vec3d(1,0,0),0,false).isEmpty());
        assertTrue(bake(type(false),Vec3d.ZERO,new Vec3d(Double.NaN,0,0),1/16.0,false).isEmpty());
    }
    @Test public void farCoordinatesKeepSubBlockDetailAndLongMeshesAreBounded() {
        Vec3d origin=new Vec3d(29_999_000,180,-29_999_000);
        var vertices=bake(type(false),origin,origin.add(.2,0,0),1/16.0,false);
        assertEquals(.2,vertices.stream().mapToDouble(v->v.position.x-origin.x).max().orElseThrow(),1e-7);
        assertTrue(vertices.stream().anyMatch(v->Math.abs(v.position.y-origin.y-.0625)<1e-7));
        var longBeam=bake(type(false),Vec3d.ZERO,new Vec3d(1_000_000,0,0),1/16.0,false);
        assertTrue(longBeam.size()<=8+4*4*4096);
        assertEquals(1_000_000,longBeam.stream().mapToDouble(v->v.position.x).max().orElseThrow(),1e-7);
    }
}
