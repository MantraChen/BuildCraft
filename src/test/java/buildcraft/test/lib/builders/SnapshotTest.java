package buildcraft.test.lib.builders;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.SchematicBlockFactoryRegistry;
import buildcraft.builders.snapshot.*;
import java.util.BitSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.Test;
import static org.junit.Assert.*;

public class SnapshotTest {
    static { SchematicBlockFactoryRegistry.registerFactory("buildcraft_port_tests:air", 0, c -> true, SchematicBlockAir::new); }
    private static Template template() {
        Template template = new Template();
        template.size = new BlockPos(4, 3, 2);
        template.offset = new BlockPos(-10, 4, 8);
        template.facing = Direction.NORTH;
        template.data = new BitSet(24);
        template.data.set(0);
        template.data.set(23);
        return template;
    }

    @Test public void templateRoundTripAndCopyHaveStableHash() throws Exception {
        Template original = template();
        original.computeKey();
        Template restored = (Template) Snapshot.readFromNBT(Snapshot.writeToNBT(original));
        assertEquals(original.data, restored.data);
        assertEquals(original.size, restored.size);
        assertEquals(original.offset, restored.offset);
        assertEquals(original.facing, restored.facing);
        byte[] hash = restored.key.hash.clone();
        restored.computeKey();
        assertArrayEquals(hash, restored.key.hash);
        Template copy = original.copy();
        assertArrayEquals(original.key.hash, copy.key.hash);
        copy.data.clear();
        assertEquals(2, original.data.cardinality());
    }

    @Test public void areaAndPlaneClearingHonorsFalse() {
        Template template = template();
        var filled = template.getFilledTemplate();
        filled.setAll(true);
        filled.setAreaXZ(0, 3, 1, 0, 1, false);
        assertEquals(16, template.data.cardinality());
        filled.setAll(true);
        filled.setAreaXY(0, 3, 0, 2, 1, false);
        assertEquals(12, template.data.cardinality());
        filled.setAll(true);
        filled.setPlaneXZ(1, false);
        assertEquals(16, template.data.cardinality());
        filled.setAll(true);
        filled.setPlaneXY(1, false);
        assertEquals(12, template.data.cardinality());
        filled.setAll(true);
        filled.setPlaneYZ(1, false);
        assertEquals(18, template.data.cardinality());
        template.invert();
        assertEquals(6, template.data.cardinality());
    }

    @Test public void rotatedPlacementCoordinatesRoundTrip() {
        Template template = template();
        for (BlockRotation rotation : BlockRotation.values()) {
            var info = template.new BuildingInfo(new BlockPos(21, 30, -17), rotation);
            for (int i = 0; i < template.getDataSize(); i++) {
                BlockPos local = template.indexToPos(i);
                BlockPos world = info.toWorld(local);
                assertEquals(local, info.fromWorld(world));
                assertTrue(info.box.contains(world));
                assertEquals(i, template.posToIndex(local));
            }
        }
    }

    @Test public void malformedDimensionsFacingAndBitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Snapshot.getDataSize(0, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> Snapshot.getDataSize(-1, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> Snapshot.getDataSize(Integer.MAX_VALUE, Integer.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class, () -> Snapshot.getDataSize(1024, 1024, 2));
        NbtCompound tag = Snapshot.writeToNBT(template());
        tag.put("size", NbtHelper.fromBlockPos(new BlockPos(0, 2, 2)));
        assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(tag));
        NbtCompound badFacing = Snapshot.writeToNBT(template());
        badFacing.remove("facing");
        assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(badFacing));
        NbtCompound badData = Snapshot.writeToNBT(template());
        BitSet bits = new BitSet(); bits.set(24);
        badData.putByteArray("data", bits.toByteArray());
        assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(badData));
    }

    @Test public void blueprintAcceptsLegacyDataAndRejectsBadPaletteIndices() throws Exception {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.offset = BlockPos.ORIGIN;
        blueprint.facing = Direction.NORTH;
        blueprint.palette.add(new SchematicBlockAir());
        blueprint.data = new int[] {0};
        blueprint.computeKey();
        NbtCompound tag = Snapshot.writeToNBT(blueprint);
        Blueprint restored = (Blueprint) Snapshot.readFromNBT(tag);
        assertArrayEquals(new int[] {0}, restored.data);
        assertTrue(restored.palette.get(0).isAir());
        assertArrayEquals(blueprint.key.hash, restored.copy().key.hash);
        NbtList legacy = new NbtList(); legacy.add(NbtInt.of(0));
        tag.put("data", legacy);
        assertArrayEquals(new int[] {0}, ((Blueprint) Snapshot.readFromNBT(tag)).data);
        tag.putIntArray("data", new int[] {1});
        assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(tag));
        tag.putIntArray("data", new int[] {-1});
        assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(tag));
        tag.putIntArray("data", new int[] {0, 0});
        assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(tag));
    }
}
