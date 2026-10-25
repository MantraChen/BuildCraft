package buildcraft.test.lib.builders;

import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.item.ItemSnapshot.EnumItemSnapshotType;
import buildcraft.builders.snapshot.Snapshot;
import java.util.Date;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import org.junit.Test;
import static org.junit.Assert.*;

public class SnapshotItemTest {
    private static final ItemSnapshot ITEM = Registry.register(Registries.ITEM,
        new Identifier("buildcraft_port_tests", "snapshot"), new ItemSnapshot("snapshot"));
    private static Snapshot.Header header() {
        return new Snapshot.Header(new Snapshot.Key(), UUID.fromString("00000000-0000-0000-0000-000000000075"), new Date(1000), "Fixture");
    }
    @Test public void nativeKindsAndHeadersSurviveStackSerialization() {
        for (EnumSnapshotType type : EnumSnapshotType.values()) {
            var blank = ITEM.getClean(type);
            assertEquals(EnumItemSnapshotType.get(type, false), EnumItemSnapshotType.getFromStack(blank));
            assertNull(ITEM.getHeader(blank));
            var header = header();
            var stack = ITEM.getUsed(type, header);
            var restored = ItemStack.fromNbt(stack.writeNbt(new NbtCompound()));
            assertEquals(EnumItemSnapshotType.get(type, true), EnumItemSnapshotType.getFromStack(restored));
            assertEquals(header, ITEM.getHeader(restored));
            assertEquals(1, restored.getMaxCount());
            assertEquals(0, restored.getDamage());
        }
    }
    @Test public void legacyMetadataAndHeaderOnlyItemsRemainReadable() {
        for (int damage = 0; damage < 4; damage++) {
            ItemStack stack = new ItemStack(ITEM);
            stack.setDamage(damage);
            assertEquals(EnumItemSnapshotType.values()[damage], EnumItemSnapshotType.getFromStack(stack));
            stack.getOrCreateNbt().put("header", header().createNbt());
            assertNotNull(ITEM.getHeader(stack));
        }
    }
    @Test public void malformedHeadersAndKindsDoNotCrashInventoryInsertion() {
        ItemStack stack = ITEM.getClean(EnumSnapshotType.BLUEPRINT);
        stack.getOrCreateNbt().put("header", new NbtCompound());
        assertNull(ITEM.getHeader(stack));
        stack.getOrCreateNbt().putString("snapshotType", "not-a-kind");
        assertNull(ITEM.getHeader(stack));
        assertFalse(EnumItemSnapshotType.getFromStack(stack).used);
    }
}
