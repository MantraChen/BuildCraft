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
import buildcraft.test.lib.config.ConfigurationTest;

/** Runs regression tests in Fabric's real transformed class loader. */
public final class PortTestRunner implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        buildcraft.test.lib.world.NetworkGameTests.initialize();
        buildcraft.test.lib.world.BlockEntityRegistryGameTests.initialize();
        Result result = JUnitCore.runClasses(StorageItemTransactorTest.class, CompactingBitSetTest.class,
            ItemHandlerManagerTest.class, IngredientNBTBCTest.class, ScriptResourceRootsTest.class, ListHandlerTest.class,
            MjConnectionTest.class, ConfigurationTest.class, buildcraft.test.lib.net.PacketCodecTest.class,
            buildcraft.test.lib.net.TickTaskQueueTest.class, buildcraft.test.lib.net.NetworkedCacheTest.class,
            buildcraft.test.lib.net.MarkerPacketTest.class, buildcraft.test.lib.registry.RegistryMetadataTest.class);
        for (Failure failure : result.getFailures()) {
            System.err.println(failure.getTestHeader());
            System.err.println(failure.getTrace());
        }
        System.out.printf("BUILDCRAFT PORT TESTS: %d run, %d failed, %d ignored%n",
            result.getRunCount(), result.getFailureCount(), result.getIgnoreCount());
        if (System.getProperty("fabric-api.gametest") != null) {
            if (!result.wasSuccessful()) throw new IllegalStateException("Port regression tests failed");
            // GameTest starts a real test world after the common regression tests pass.
            return;
        }
        // Only this dedicated test JVM exits; the ordinary regression run creates no world.
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}
