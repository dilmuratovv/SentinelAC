package uz.sentinel.ac.combat;

/**
 * Collects the client-tick intervals between attacks in non-overlapping windows and describes how regular they are.
 *
 * <p>Modern clients handle clicks inside the client tick, so intervals are whole ticks. A person clicking at about
 * 10 CPS has a timing spread of 10-25 ms, which turns into a mix of 1, 2 and 3 tick intervals (the most common
 * interval is typically 65-85% of them). A plain autoclicker produces the same interval almost every time.</p>
 */
public final class ClickConsistency {
    /** Summary of one full window. */
    public record Result(int samples, double mean, int mode, double modeShare, double deviation) {
        /** Machine-like: fast enough to matter and almost every interval identical. */
        public boolean robotic(double minModeShare, double maxMeanTicks) {
            return mean <= maxMeanTicks && modeShare >= minModeShare;
        }
    }

    public static final int MAX_INTERVAL = 5;

    private final int[] samples;
    private int size;

    public ClickConsistency(int window) {
        if (window < 10) throw new IllegalArgumentException("window must be at least 10");
        samples = new int[window];
    }

    /**
     * Adds one interval (ticks between two attacks). Intervals above {@link #MAX_INTERVAL} are pauses, not clicking
     * rhythm, and are ignored. Returns the summary when the window is full (and starts a new one), otherwise null.
     */
    public Result add(int intervalTicks) {
        if (intervalTicks < 0 || intervalTicks > MAX_INTERVAL) return null;
        samples[size++] = intervalTicks;
        if (size < samples.length) return null;
        Result result = summarize(samples, size);
        size = 0;
        return result;
    }

    public void reset() { size = 0; }

    static Result summarize(int[] values, int count) {
        int[] histogram = new int[MAX_INTERVAL + 1];
        double sum = 0;
        for (int i = 0; i < count; i++) { histogram[values[i]]++; sum += values[i]; }
        double mean = sum / count;
        int mode = 0;
        for (int i = 1; i < histogram.length; i++) if (histogram[i] > histogram[mode]) mode = i;
        double variance = 0;
        for (int i = 0; i < count; i++) variance += (values[i] - mean) * (values[i] - mean);
        return new Result(count, mean, mode, histogram[mode] / (double) count, Math.sqrt(variance / count));
    }
}
