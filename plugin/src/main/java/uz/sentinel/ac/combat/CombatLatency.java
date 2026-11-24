package uz.sentinel.ac.combat;

import java.util.Arrays;

/**
 * Compares a player's round-trip times while fighting with their own round-trip times outside fights.
 *
 * <p>Backtrack holds every incoming packet (the engine's transactions included) for 100-150 ms while its target is in
 * range after an attack, so the player's measured ping rises exactly when they fight. Network lag and lag spikes are
 * not tied to the player's own attacks, and quantiles ignore short spikes. Measured with a real LiquidBounce Backtrack
 * at 120 ms ping with jitter and lag spikes: median +16 ms and 75th percentile +71 ms in fights; a person in the same
 * conditions: -23 ms and -9 ms.</p>
 */
public final class CombatLatency {
    private final int[] combat, calm;
    private int combatSize, combatNext, calmSize, calmNext, sinceVerdict;

    public CombatLatency(int combatWindow, int calmWindow) {
        combat = new int[combatWindow];
        calm = new int[calmWindow];
    }

    public void addCalm(int rttMillis) {
        calm[calmNext] = rttMillis;
        calmNext = (calmNext + 1) % calm.length;
        calmSize = Math.min(calm.length, calmSize + 1);
    }

    /** Adds a fight sample; every {@code every} samples (once both windows are full enough) returns a verdict. */
    public Verdict addCombat(int rttMillis, int every, int minCalm) {
        combat[combatNext] = rttMillis;
        combatNext = (combatNext + 1) % combat.length;
        combatSize = Math.min(combat.length, combatSize + 1);
        if (++sinceVerdict < every || combatSize < combat.length || calmSize < minCalm) return null;
        sinceVerdict = 0;
        int[] a = Arrays.copyOf(combat, combatSize), b = Arrays.copyOf(calm, calmSize);
        Arrays.sort(a);
        Arrays.sort(b);
        return new Verdict(quantile(a, 0.5) - quantile(b, 0.5), quantile(a, 0.75) - quantile(b, 0.75), quantile(b, 0.5));
    }

    public void clearCombat() { combatSize = combatNext = sinceVerdict = 0; }

    /** Differences in ms (fight minus calm) and the player's calm median ping. */
    public record Verdict(int medianDiff, int upperDiff, int calmMedian) {
        public boolean holdsPackets(int minMedian, int minUpper) {
            return medianDiff >= minMedian && upperDiff >= minUpper;
        }
    }

    static int quantile(int[] sorted, double q) {
        return sorted[Math.min(sorted.length - 1, (int) (sorted.length * q))];
    }
}
