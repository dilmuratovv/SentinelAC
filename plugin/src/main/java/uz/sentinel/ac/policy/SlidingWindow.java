package uz.sentinel.ac.policy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/** Main-thread admission window. Rejected attempts never grow the queue. */
public final class SlidingWindow {
    private final int limit;
    private final long intervalNanos;
    private final LongSupplier clock;
    private final Deque<Long> admitted = new ArrayDeque<>();

    public SlidingWindow(int limit, long intervalNanos, LongSupplier clock) {
        if (limit < 1 || intervalNanos < 1) throw new IllegalArgumentException("Positive window and limit required");
        this.limit = limit;
        this.intervalNanos = intervalNanos;
        this.clock = clock;
    }

    public boolean tryAcquire() {
        long now = clock.getAsLong();
        while (!admitted.isEmpty() && now - admitted.peekFirst() >= intervalNanos) admitted.removeFirst();
        if (admitted.size() >= limit) return false;
        admitted.addLast(now);
        return true;
    }
}
