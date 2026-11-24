package uz.sentinel.ac.combat;

import java.util.ArrayDeque;

/** Counts events inside a sliding wall-clock window. Used for clients whose tick packets are not reliable. */
public final class TimeWindow {
    private final long windowNanos;
    private final int cap;
    private final ArrayDeque<Long> times = new ArrayDeque<>();

    public TimeWindow(long windowNanos, int cap) {
        if (windowNanos < 1 || cap < 1) throw new IllegalArgumentException("positive window and cap required");
        this.windowNanos = windowNanos;
        this.cap = cap;
    }

    /** Records an event at {@code now} and returns the number of events in the window (bounded by the cap). */
    public int add(long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= windowNanos) times.removeFirst();
        if (times.size() >= cap) times.removeFirst();
        times.addLast(now);
        return times.size();
    }

    /** Number of events in the window at {@code now}, without recording one. */
    public int count(long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= windowNanos) times.removeFirst();
        return times.size();
    }

    public void clear() { times.clear(); }
}
