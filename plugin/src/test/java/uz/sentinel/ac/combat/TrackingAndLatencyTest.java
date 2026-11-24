package uz.sentinel.ac.combat;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TrackingAndLatencyTest {
    /**
     * Simulates fights: the target direction moves (jerky A/D strafing or smooth circling), the player turns after
     * {@code lag} ticks, smoothing {@code gain} of the remaining error per tick, plus hand noise. Returns how many
     * 200-tick windows (evaluated every 100 ticks) look instant.
     */
    private static int instantWindows(long seed, int lag, double gain, double noise, boolean smooth, int ticks) {
        Random r = new Random(seed);
        ReactionCorrelation c = new ReactionCorrelation(200);
        ArrayDeque<Double> seen = new ArrayDeque<>();
        double target = 0, yaw = 0, velocity = 0;
        int flagged = 0, since = 0;
        for (int t = 0; t < ticks; t++) {
            double before = target;
            if (smooth) target += 3 * Math.sin(t / 15.0);                       // circling: smooth direction change
            else { if (r.nextDouble() < 0.3) velocity = r.nextGaussian() * 3; target += velocity; } // A/D strafe taps
            seen.addLast(target);
            double perceived = seen.size() > lag ? seen.toArray(new Double[0])[seen.size() - 1 - lag] : 0;
            while (seen.size() > 20) seen.removeFirst();
            double oldYaw = yaw;
            yaw += gain * (perceived - yaw) + r.nextGaussian() * noise;
            yaw = Math.round(yaw / 0.15) * 0.15; // whole mouse steps
            c.add(target - before, yaw - oldYaw);
            if (c.full() && ++since >= 100) {
                since = 0;
                if (c.instant(0.45, 0.1, 0.3)) flagged++;
            }
        }
        return flagged;
    }

    @Test
    void aimbotsFollowInstantly() {
        assertTrue(instantWindows(1, 1, 0.6, 0.05, false, 4000) >= 30, "aimbot, 1 tick, jerky target");
        assertTrue(instantWindows(2, 0, 0.4, 0.05, false, 4000) >= 30, "aimbot, same tick, smoothed");
    }

    @Test
    void peopleNeverLookInstant() {
        for (int lag = 3; lag <= 6; lag++) {
            for (double gain : new double[]{0.3, 0.5, 0.8}) {
                assertEquals(0, instantWindows(10 + lag, lag, gain, 0.4, false, 20000), "person lag " + lag + " gain " + gain + " jerky");
                assertEquals(0, instantWindows(20 + lag, lag, gain, 0.4, true, 20000), "person lag " + lag + " gain " + gain + " smooth");
            }
        }
    }

    @Test
    void standingTargetsAreNotJudged() {
        ReactionCorrelation c = new ReactionCorrelation(50);
        for (int i = 0; i < 50; i++) c.add(0, i % 2 == 0 ? 1 : -1);
        assertTrue(Double.isNaN(c.instantResponse(0.3)));
    }

    @Test
    void wrapping() {
        assertEquals(-10, ReactionCorrelation.wrap(350), 1e-9);
        assertEquals(10, ReactionCorrelation.wrap(-350), 1e-9);
    }

    /** Fight vs calm round trips; spikes are not tied to fighting. */
    private static boolean flags(long seed, double fightExtraMs, double fightExtraShare, double baseMs, double jitterMs, int samples) {
        Random r = new Random(seed);
        CombatLatency latency = new CombatLatency(400, 300);
        boolean flagged = false;
        for (int i = 0; i < samples; i++) {
            boolean fighting = (i / 200) % 3 != 0;                 // fights with calm breaks
            double spike = (i % 500) < 20 ? 250 : 0;               // 1 s lag spike every 25 s, anywhere
            double rtt = baseMs + r.nextGaussian() * jitterMs + spike;
            if (fighting && r.nextDouble() < fightExtraShare) rtt += fightExtraMs * (0.7 + 0.3 * r.nextDouble());
            int ms = (int) Math.max(1, rtt);
            if (fighting) {
                CombatLatency.Verdict v = latency.addCombat(ms, 100, 150);
                if (v != null && v.holdsPackets(8, 50)) flagged = true;
            } else latency.addCalm(ms);
        }
        return flagged;
    }

    @Test
    void backtrackRaisesFightPing() {
        assertTrue(flags(1, 130, 0.5, 140, 15, 6000), "backtrack 100-150 ms, half of the time");
        assertTrue(flags(2, 130, 0.5, 40, 5, 6000), "same on a low-ping connection");
    }

    @Test
    void lagAloneDoesNotFlag() {
        for (double base : new double[]{20, 80, 150, 300}) {
            for (double jitter : new double[]{5, 15, 40}) {
                assertFalse(flags((long) (base * 10 + jitter), 0, 0, base, jitter, 20000), "ping " + base + " jitter " + jitter);
            }
        }
    }
}
