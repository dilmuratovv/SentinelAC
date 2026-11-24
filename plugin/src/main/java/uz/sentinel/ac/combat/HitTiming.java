package uz.sentinel.ac.combat;

/**
 * Regularity of the client-tick intervals between hits in the 4-30 tick range (about 0.7 to 5 hits per second):
 * cooldown-paced auras, trigger bots and slow autoclickers hit on the same tick count almost every time. Measured with
 * real clients: Meteor kill aura 90 % of intervals at exactly 12 ticks, LiquidBounce kill aura 88 % at 13 ticks,
 * LiquidBounce auto clicker 96 % at 5 ticks; people fighting the same target 19 % for their most common interval.
 *
 * <p>Every interval up to 30 ticks enters the window (dropping the short ones would make the rest look artificially
 * uniform); a window is only judged when its most common interval is 4-30 ticks. Short modes (4-7 ticks) need 95 %
 * identical, because a steady person clicking at 3-5 CPS reaches about 60 % there; 8+ ticks need the configured
 * share (default 80 %).</p>
 */
public final class HitTiming {
    public static final int MIN_INTERVAL = 4, MAX_INTERVAL = 30, SHORT_LIMIT = 8;
    public static final double SHORT_MIN_SHARE = 0.95;

    /** Summary of a full window. */
    public record Result(int mode, double share, boolean regular) {}

    private final int[] window;
    private int size;

    public HitTiming(int window) {
        if (window < 10) throw new IllegalArgumentException("window must be at least 10");
        this.window = new int[window];
    }

    /** Adds an interval in client ticks; returns the verdict when the window is full (then starts a new one). */
    public Result add(long intervalTicks, double minShare) {
        if (intervalTicks < 0 || intervalTicks > MAX_INTERVAL) return null; // a pause, not a rhythm
        window[size++] = (int) intervalTicks;
        if (size < window.length) return null;
        size = 0;
        int mode = 0, best = 0;
        for (int candidate : window) {
            int count = 0;
            for (int value : window) if (value == candidate) count++;
            if (count > best) { best = count; mode = candidate; }
        }
        double share = best / (double) window.length;
        double needed = mode < SHORT_LIMIT ? Math.max(minShare, SHORT_MIN_SHARE) : minShare;
        return new Result(mode, share, mode >= MIN_INTERVAL && share >= needed);
    }
}
