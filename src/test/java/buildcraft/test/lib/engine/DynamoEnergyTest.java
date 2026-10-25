package buildcraft.test.lib.engine;

import buildcraft.api.mj.MjBattery;
import buildcraft.energy.DynamoEnergyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class DynamoEnergyTest {
    @Test public void exactConversionPreservesFractionAndSimulation() {
        MjBattery battery = new MjBattery(10_000);
        battery.addPower(1_055, false);
        int[] commits = {0};
        DynamoEnergyStorage output = new DynamoEnergyStorage(battery, () -> 100, () -> 10_000, () -> 0, () -> commits[0]++);
        try (Transaction simulated = Transaction.openOuter()) { assertEquals(10, output.extract(99, simulated)); }
        assertEquals(1_055, battery.getStored()); assertEquals(0, commits[0]);
        try (Transaction committed = Transaction.openOuter()) { assertEquals(10, output.extract(99, committed)); committed.commit(); }
        assertEquals(55, battery.getStored()); assertEquals(1, commits[0]);
        assertEquals(0, output.getAmount()); assertEquals(100, output.getCapacity());
    }
    @Test public void quotaIsSharedByMultipleConsumersAndRestoredByNestedRollback() {
        MjBattery battery = new MjBattery(10_000); battery.addPower(10_000, false);
        long[] tick = {5};
        DynamoEnergyStorage output = new DynamoEnergyStorage(battery, () -> 100, () -> 450, () -> tick[0], () -> {});
        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(2, output.extract(2, outer));
            try (Transaction nested = outer.openNested()) { assertEquals(2, output.extract(99, nested)); }
            assertEquals(200, output.usedThisTick());
            assertEquals(2, output.extract(99, outer)); outer.commit();
        }
        try (Transaction outer = Transaction.openOuter()) { assertEquals(0, output.extract(99, outer)); }
        assertEquals(9_600, battery.getStored());
        tick[0]++;
        try (Transaction outer = Transaction.openOuter()) { assertEquals(4, output.extract(99, outer)); outer.commit(); }
        assertEquals(9_200, battery.getStored());
    }
    @Test public void committedNestedTransferStillRollsBackWithOuterTransaction() {
        MjBattery battery = new MjBattery(5_000); battery.addPower(5_000, false);
        DynamoEnergyStorage output = new DynamoEnergyStorage(battery, () -> 100, () -> 5_000, () -> 0, () -> fail("Outer did not commit"));
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction child = outer.openNested()) { assertEquals(50, output.extract(Long.MAX_VALUE, child)); child.commit(); }
        }
        assertEquals(5_000, battery.getStored()); assertEquals(0, output.usedThisTick());
    }
    @Test public void extremeAmountsCannotOverflowOrCreateEnergy() {
        MjBattery battery = new MjBattery(Long.MAX_VALUE); battery.addPower(Long.MAX_VALUE, false);
        DynamoEnergyStorage output = new DynamoEnergyStorage(battery, () -> 200_000, () -> Long.MAX_VALUE, () -> 0, () -> {});
        try (Transaction transaction = Transaction.openOuter()) {
            assertThrows(IllegalArgumentException.class, () -> output.extract(-1, transaction));
            assertEquals(0, output.insert(Long.MAX_VALUE, transaction));
            assertEquals(Long.MAX_VALUE / 200_000, output.extract(Long.MAX_VALUE, transaction)); transaction.commit();
        }
        assertEquals(Long.MAX_VALUE % 200_000, battery.getStored());
    }
}
