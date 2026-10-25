package buildcraft.test.lib.client.model;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import net.minecraft.util.math.Direction;
import buildcraft.lib.client.render.fluid.FluidRenderer;

public class FluidTankGeometryTest {
    private static final FluidRenderer.TankSize SIZE = new FluidRenderer.TankSize(3, 0, 3, 13, 16, 13);
    @Test public void gasFillsFromTopAndLiquidFromBottom() {
        var liquid = FluidRenderer.filledSize(SIZE, 1, 4, false);
        var gas = FluidRenderer.filledSize(SIZE, 1, 4, true);
        assertEquals(0, liquid.min.y, 0); assertEquals(.25, liquid.max.y, 0);
        assertEquals(.75, gas.min.y, 0); assertEquals(1, gas.max.y, 0);
        assertEquals(1, FluidRenderer.filledSize(SIZE, 999, 1, false).max.y, 0);
    }
    @Test public void cuboidFacesHaveOutwardWindingAndBoundedUvs() {
        Map<Direction, List<double[]>> vertices = new EnumMap<>(Direction.class);
        FluidRenderer.emit(SIZE, null, (face, x, y, z, u, v) -> {
            assertTrue(u >= 0 && u <= 1 && v >= 0 && v <= 1);
            vertices.computeIfAbsent(face, f -> new ArrayList<>()).add(new double[] {x,y,z});
        });
        assertEquals(6, vertices.size());
        vertices.forEach((face, list) -> {
            assertEquals(4, list.size());
            double[] a = list.get(0), b = list.get(1), c = list.get(2);
            double x=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
            double y=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
            double z=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
            assertTrue(x*face.getOffsetX()+y*face.getOffsetY()+z*face.getOffsetZ() > 0);
        });
    }
    @Test public void emptyTankHasNoGeometryAndFaceMaskIsRespected() {
        List<Direction> vertices = new ArrayList<>();
        FluidRenderer.emit(FluidRenderer.filledSize(SIZE, 0, 100, false), null,
            (face,x,y,z,u,v) -> vertices.add(face)); assertTrue(vertices.isEmpty());
        boolean[] faces = new boolean[6]; faces[Direction.UP.getId()] = true;
        FluidRenderer.emit(SIZE, faces, (face,x,y,z,u,v) -> vertices.add(face));
        assertEquals(Collections.nCopies(4, Direction.UP), vertices);
    }
}
