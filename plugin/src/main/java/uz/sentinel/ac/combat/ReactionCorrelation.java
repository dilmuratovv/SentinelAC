package uz.sentinel.ac.combat;

/**
 * How closely a player's per-tick yaw changes follow the per-tick changes of the direction to their target, at a
 * lag of 0..{@code maxLag} client ticks.
 *
 * <p>A person sees the target move and needs at least ~100-150 ms (2-3 ticks) of visual-motor reaction before the
 * mouse follows; an aimbot or aura reads the target position every tick and turns within 0-1 tick. Measured with a
 * real LiquidBounce Aimbot (GCD-corrected, smoothed): correlation 0.58 at a lag of 1 tick, against 0.07 for a person
 * tracking the same target, both at 120 ms ping with jitter. Both inputs are read on the server in the client's own
 * time frame (the target direction from positions the client has acknowledged, the yaw from its rotation packets),
 * so the player's ping cancels out.</p>
 */
public final class ReactionCorrelation {
    private final double[] target, yaw;
    private int size;

    public ReactionCorrelation(int window) {
        if (window < 40) throw new IllegalArgumentException("window must be at least 40 ticks");
        target = new double[window];
        yaw = new double[window];
    }

    /** Adds one client tick: change of the direction to the target and change of the player's yaw (degrees). */
    public void add(double targetDelta, double yawDelta) {
        if (size == target.length) {
            System.arraycopy(target, 1, target, 0, size - 1);
            System.arraycopy(yaw, 1, yaw, 0, size - 1);
            size--;
        }
        target[size] = wrap(targetDelta);
        yaw[size] = wrap(yawDelta);
        size++;
    }

    public boolean full() { return size == target.length; }
    public void clear() { size = 0; }

    /** Pearson correlation of yaw changes {@code lag} ticks after the target changes; NaN without enough motion. */
    public double correlation(int lag, double minSpread) {
        int n = size - lag;
        if (n < 20) return Double.NaN;
        double ma = 0, mb = 0;
        for (int i = 0; i < n; i++) { ma += target[i]; mb += yaw[i + lag]; }
        ma /= n; mb /= n;
        double cov = 0, va = 0, vb = 0;
        for (int i = 0; i < n; i++) {
            double a = target[i] - ma, b = yaw[i + lag] - mb;
            cov += a * b; va += a * a; vb += b * b;
        }
        // Without real target and yaw motion the correlation means nothing (standing target, idle player).
        if (Math.sqrt(va / n) < minSpread || Math.sqrt(vb / n) < minSpread) return Double.NaN;
        return cov / Math.sqrt(va * vb);
    }

    /** Strongest correlation at a lag the human visual-motor system cannot produce (0 or 1 tick). */
    public double instantResponse(double minSpread) {
        double best = Double.NaN;
        for (int lag = 0; lag <= 1; lag++) {
            double c = correlation(lag, minSpread);
            if (!Double.isNaN(c) && (Double.isNaN(best) || c > best)) best = c;
        }
        return best;
    }

    /** Strongest correlation at a human reaction lag (3-5 ticks, 150-250 ms). */
    public double delayedResponse(double minSpread) {
        double best = Double.NaN;
        for (int lag = 3; lag <= 5; lag++) {
            double c = correlation(lag, minSpread);
            if (!Double.isNaN(c) && (Double.isNaN(best) || c > best)) best = c;
        }
        return best;
    }

    /**
     * True when the yaw follows the target instantly: strong correlation at 0-1 tick AND clearly stronger than at a
     * human reaction lag. The second condition keeps smooth, predictable target motion (where even a late follower
     * correlates at every lag) from looking instant.
     */
    public boolean instant(double minCorrelation, double minLead, double minSpread) {
        double now = instantResponse(minSpread), later = delayedResponse(minSpread);
        return !Double.isNaN(now) && now >= minCorrelation && (Double.isNaN(later) || now - later >= minLead);
    }

    static double wrap(double degrees) {
        double d = (degrees + 180) % 360;
        if (d < 0) d += 360;
        return d - 180;
    }
}
