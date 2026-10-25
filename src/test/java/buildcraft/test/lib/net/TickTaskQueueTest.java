package buildcraft.test.lib.net;

import buildcraft.lib.net.TickTaskQueue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TickTaskQueueTest {
    @Test public void delaysAndSubmissionOrderAreRespected() {
        TickTaskQueue queue = new TickTaskQueue();
        List<Integer> calls = new ArrayList<>();
        queue.schedule(3, () -> calls.add(3));
        queue.schedule(1, () -> calls.add(1));
        queue.schedule(3, () -> calls.add(4));
        assertTrue(calls.isEmpty());
        queue.tick(); assertEquals(List.of(1), calls);
        queue.tick(); assertEquals(List.of(1), calls);
        queue.tick(); assertEquals(List.of(1, 3, 4), calls);
        queue.tick(); assertEquals(List.of(1, 3, 4), calls);
    }

    @Test public void callbacksCanScheduleWorkForTheFollowingTick() {
        TickTaskQueue queue = new TickTaskQueue();
        List<Integer> calls = new ArrayList<>();
        queue.schedule(1, () -> {
            calls.add(1);
            queue.schedule(1, () -> calls.add(2));
        });
        queue.tick(); assertEquals(List.of(1), calls);
        queue.tick(); assertEquals(List.of(1, 2), calls);
    }

    @Test public void queuesAreIsolatedAndRejectInvalidDelays() {
        TickTaskQueue server = new TickTaskQueue(), client = new TickTaskQueue();
        List<Integer> calls = new ArrayList<>();
        server.schedule(1, () -> calls.add(1));
        client.tick(); assertTrue(calls.isEmpty());
        server.tick(); assertEquals(List.of(1), calls);
        assertThrows(IllegalArgumentException.class, () -> server.schedule(0, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> server.schedule(-1, () -> {}));
    }
}
