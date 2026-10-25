package buildcraft.test.lib.inventory;

import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import org.junit.Test;
import buildcraft.lib.fluid.FluidTankStorage;
import buildcraft.lib.fluid.FluidTransfers;
import buildcraft.lib.compat.FluidStackBC;
import net.minecraftforge.fluids.FluidTank;

public class FluidTankStorageTest {
    private static final long BUCKET = FluidConstants.BUCKET;
    private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER), LAVA = FluidVariant.of(Fluids.LAVA);
    private static FluidTankStorage tank(long capacity) { return new FluidTankStorage(capacity, () -> {}); }
    private static void fill(FluidTankStorage tank, FluidVariant variant, long amount) {
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(amount, tank.insert(variant, amount, tx)); tx.commit();
        }
    }
    private static ContainerItemContext context(SimpleInventory inventory) {
        return ContainerItemContext.ofSingleSlot(InventoryStorage.of(inventory, null).getSlot(0));
    }

    @Test public void nestedCommitThenOuterAbortRestoresContentsAndLock() {
        AtomicInteger changes = new AtomicInteger();
        FluidTankStorage tank = new FluidTankStorage(BUCKET, changes::incrementAndGet);
        tank.setLockFirst(true);
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = outer.openNested()) {
                assertEquals(7, tank.insert(WATER, 7, nested)); nested.commit();
            }
            assertEquals(WATER, tank.getAcceptedVariant());
        }
        assertEquals(0, tank.getAmount()); assertTrue(tank.isResourceBlank());
        assertTrue(tank.getAcceptedVariant().isBlank()); assertEquals(0, changes.get());
        fill(tank, LAVA, 9); assertEquals(1, changes.get());
    }

    @Test public void lockSurvivesDrainAndRejectedInsertDoesNotLock() {
        FluidTankStorage tank = tank(BUCKET); tank.setLockFirst(true);
        tank.setFilter(v -> v.equals(WATER));
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(0, tank.insert(LAVA, BUCKET, tx)); tx.commit();
        }
        assertTrue(tank.getAcceptedVariant().isBlank());
        fill(tank, WATER, 19);
        try (Transaction tx = Transaction.openOuter()) { assertEquals(19, tank.extract(WATER, 19, tx)); tx.commit(); }
        tank.setFilter(v -> true);
        try (Transaction tx = Transaction.openOuter()) { assertEquals(0, tank.insert(LAVA, 1, tx)); tx.commit(); }
        assertEquals(WATER, tank.getAcceptedVariant()); assertTrue(tank.isResourceBlank());
    }

    @Test public void variantsAreIsolatedIncludingNbt() {
        NbtCompound nbt = new NbtCompound(); nbt.putString("sample", "A");
        FluidVariant tagged = FluidVariant.of(Fluids.WATER, nbt);
        FluidTankStorage tank = tank(100); fill(tank, tagged, 95);
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(0, tank.insert(WATER, 50, tx)); assertEquals(0, tank.extract(WATER, 50, tx));
            assertEquals(5, tank.insert(tagged, Long.MAX_VALUE, tx)); tx.commit();
        }
        assertEquals(100, tank.getAmount());
    }

    @Test public void exactDropletsAndLockRoundTripAndCapacityCannotBeChangedBySave() {
        FluidTankStorage tank = tank(BUCKET); tank.setLockFirst(true); fill(tank, WATER, 12345);
        NbtCompound tag = tank.writeNbt(new NbtCompound()); tag.putInt("Capacity", Integer.MAX_VALUE);
        FluidTankStorage restored = tank(10000); restored.setLockFirst(true); restored.readNbt(tag);
        assertEquals(10000, restored.getAmount()); assertEquals(10000, restored.getCapacity());
        assertEquals(WATER, restored.getAcceptedVariant());
        tag.putLong("amount", -1); restored.readNbt(tag);
        assertEquals(0, restored.getAmount()); assertTrue(restored.isResourceBlank());
    }

    @Test public void legacyForgeAndEarlierFabricSavesUseTheirOriginalUnits() {
        NbtCompound legacy = new NbtCompound(); legacy.putString("FluidName", "water"); legacy.putInt("Amount", 1000);
        FluidTankStorage tank = tank(2 * BUCKET); tank.readNbt(legacy); assertEquals(BUCKET, tank.getAmount());
        NbtCompound fabric = new NbtCompound(); fabric.put("Fluid", FluidStackBC.of(WATER, 12345).toNbt());
        tank.readNbt(fabric); assertEquals(12345, tank.getAmount());
        legacy.putString("FluidName", "invalid fluid name"); tank.readNbt(legacy); assertEquals(0, tank.getAmount());
    }

    @Test public void legacySignaturesConvertOnceAndExposeDefensiveSnapshots() {
        FluidTank tank = new FluidTank(1500);
        assertEquals(1500, tank.fill(new FluidStackBC(Fluids.WATER, 2000), false)); assertNull(tank.getFluid());
        assertEquals(1500, tank.fill(new FluidStackBC(Fluids.WATER, 2000), true));
        assertEquals(1500 * 81L, tank.getStorage().getAmount());
        tank.getFluid().amount = 0; assertEquals(1500, tank.getFluidAmount());
        assertEquals(0, tank.fill(new FluidStackBC(Fluids.LAVA, 10), true));
        assertEquals(BUCKET, tank.drain(1000, true).getAmount()); assertEquals(500, tank.getFluidAmount());
    }

    @Test public void nativeSubMillibucketsAreNeverLostByLegacyCalls() {
        FluidTank tank = new FluidTank(2); fill(tank.getStorage(), WATER, 82);
        assertEquals(81, tank.drain(10, true).getAmount()); assertEquals(1, tank.getStorage().getAmount());
        assertNull(tank.drain(1, true)); assertEquals(1, tank.getStorage().getAmount());
    }

    @Test public void bucketExchangeCommitsItemsAndFluidTogether() {
        FluidTankStorage tank = tank(2 * BUCKET);
        SimpleInventory inventory = new SimpleInventory(new ItemStack(Items.WATER_BUCKET));
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(BUCKET, FluidTransfers.exchange(tank, context(inventory), tx).amount()); tx.commit();
        }
        assertTrue(inventory.getStack(0).isOf(Items.BUCKET)); assertEquals(BUCKET, tank.getAmount());
        try (Transaction tx = Transaction.openOuter()) {
            assertTrue(FluidTransfers.exchange(tank, context(inventory), tx).filledItem()); tx.commit();
        }
        assertTrue(inventory.getStack(0).isOf(Items.WATER_BUCKET)); assertEquals(0, tank.getAmount());
    }

    @Test public void partialBucketCapacityRejectsWithoutConsumingOrLocking() {
        FluidTankStorage tank = tank(BUCKET - 1); tank.setLockFirst(true);
        SimpleInventory inventory = new SimpleInventory(new ItemStack(Items.WATER_BUCKET));
        try (Transaction tx = Transaction.openOuter()) { assertNull(FluidTransfers.exchange(tank, context(inventory), tx)); tx.commit(); }
        assertEquals(0, tank.getAmount()); assertTrue(tank.getAcceptedVariant().isBlank());
        assertTrue(inventory.getStack(0).isOf(Items.WATER_BUCKET));
    }

    @Test public void fullInventoryAndStackedEmptyBucketsRollBack() {
        FluidTankStorage tank = tank(BUCKET); fill(tank, WATER, BUCKET);
        SimpleInventory inventory = new SimpleInventory(new ItemStack(Items.BUCKET, 2));
        try (Transaction tx = Transaction.openOuter()) { assertNull(FluidTransfers.exchange(tank, context(inventory), tx)); tx.commit(); }
        assertEquals(2, inventory.getStack(0).getCount()); assertEquals(BUCKET, tank.getAmount());
    }

    @Test public void successfulExchangeCanBeAbortedByCaller() {
        FluidTankStorage tank = tank(BUCKET);
        SimpleInventory inventory = new SimpleInventory(new ItemStack(Items.WATER_BUCKET));
        try (Transaction tx = Transaction.openOuter()) { assertNotNull(FluidTransfers.exchange(tank, context(inventory), tx)); }
        assertEquals(0, tank.getAmount()); assertTrue(inventory.getStack(0).isOf(Items.WATER_BUCKET));
    }

    @Test public void everyBuildCraftBucketExchangesItsOwnFluidAndHeatVariant() {
        assertEquals(30, buildcraft.energy.BCEnergyFluids.allFluids.size());
        for (var fluid : buildcraft.energy.BCEnergyFluids.allFluids) {
            var tank = tank(BUCKET);
            SimpleInventory inventory = new SimpleInventory(new ItemStack(fluid.getBucketItem()));
            try (Transaction tx = Transaction.openOuter()) {
                assertNotNull(FluidTransfers.exchange(tank, context(inventory), tx)); tx.commit();
            }
            assertEquals(FluidVariant.of(fluid), tank.getResource()); assertEquals(BUCKET, tank.getAmount());
            assertTrue(inventory.getStack(0).isOf(Items.BUCKET));
            try (Transaction tx = Transaction.openOuter()) {
                assertNotNull(FluidTransfers.exchange(tank, context(inventory), tx)); tx.commit();
            }
            assertTrue(inventory.getStack(0).isOf(fluid.getBucketItem())); assertEquals(0, tank.getAmount());
        }
    }

    @Test public void columnFillsBottomFirstAndConservesEveryDroplet() {
        var lower = tank(BUCKET); var middle = tank(BUCKET); var upper = tank(BUCKET);
        fill(middle, WATER, BUCKET / 2); fill(upper, WATER, BUCKET);
        assertEquals(BUCKET + BUCKET / 2, buildcraft.lib.fluid.FluidTankColumn.settle(java.util.List.of(lower, middle, upper)));
        assertEquals(BUCKET, lower.getAmount()); assertEquals(BUCKET / 2, middle.getAmount()); assertEquals(0, upper.getAmount());
        assertEquals(0, buildcraft.lib.fluid.FluidTankColumn.settle(java.util.List.of(lower, middle, upper)));
    }

    @Test public void columnDoesNotMoveWaterThroughLavaOrChangeItsVariant() {
        var lower = tank(BUCKET); var middle = tank(BUCKET); var upper = tank(BUCKET);
        fill(lower, WATER, BUCKET / 2); fill(middle, LAVA, BUCKET / 2); fill(upper, WATER, BUCKET / 2);
        assertEquals(0, buildcraft.lib.fluid.FluidTankColumn.settle(java.util.List.of(lower, middle, upper)));
        assertEquals(WATER, lower.getResource()); assertEquals(LAVA, middle.getResource()); assertEquals(WATER, upper.getResource());
        assertEquals(BUCKET / 2, lower.getAmount()); assertEquals(BUCKET / 2, upper.getAmount());
    }
}
