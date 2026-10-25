package buildcraft.test;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import buildcraft.test.lib.inventory.StorageItemTransactorTest;
import buildcraft.test.lib.inventory.ItemHandlerManagerTest;
import buildcraft.test.lib.inventory.IngredientNBTBCTest;
import buildcraft.test.lib.inventory.ScriptResourceRootsTest;
import buildcraft.test.lib.inventory.ListHandlerTest;
import buildcraft.test.lib.misc.data.CompactingBitSetTest;
import buildcraft.test.lib.mj.MjConnectionTest;

/** Runs regression tests in Fabric's real transformed class loader. */
public final class PortTestRunner implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        Result result = JUnitCore.runClasses(StorageItemTransactorTest.class, CompactingBitSetTest.class,
            ItemHandlerManagerTest.class, IngredientNBTBCTest.class, ScriptResourceRootsTest.class, ListHandlerTest.class,
            MjConnectionTest.class);
        for (Failure failure : result.getFailures()) {
            System.err.println(failure.getTestHeader());
            System.err.println(failure.getTrace());
        }
        System.out.printf("BUILDCRAFT PORT TESTS: %d run, %d failed, %d ignored%n",
            result.getRunCount(), result.getFailureCount(), result.getIgnoreCount());
        // Only this dedicated test JVM exits; no server/world is started by the fixture.
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}
