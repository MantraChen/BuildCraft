package buildcraft.lib.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/** Tick delays with stable ordering; tasks scheduled by a callback run on a later tick. */
public final class TickTaskQueue {
    private record Task(long due, long sequence, Runnable action) {}
    private final PriorityQueue<Task> tasks = new PriorityQueue<>(
        Comparator.comparingLong(Task::due).thenComparingLong(Task::sequence));
    private long tick, sequence;

    public synchronized void schedule(int delay, Runnable action) {
        if (delay < 1) throw new IllegalArgumentException("Delay must be at least one tick");
        tasks.add(new Task(tick + delay, sequence++, Objects.requireNonNull(action)));
    }

    public void tick() {
        List<Runnable> ready = new ArrayList<>();
        synchronized (this) {
            tick++;
            while (!tasks.isEmpty() && tasks.peek().due <= tick) ready.add(tasks.remove().action);
        }
        for (Runnable action : ready) action.run();
    }
}
