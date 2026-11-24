package uz.sentinel.ac.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ClickConsistencyTest {
    /** Tick intervals of clicks made at {@code meanMs} with a normal timing spread of {@code sdMs}. */
    private static int[] clicks(Random random, int count, double meanMs, double sdMs) {
        int[] intervals = new int[count];
        double time = random.nextDouble() * 50;
        long lastTick = (long) Math.floor(time / 50);
        for (int i = 0; i < count; i++) {
            time += Math.max(10, meanMs + random.nextGaussian() * sdMs);
            long tick = (long) Math.floor(time / 50); // clicks are handled in the next client tick
            intervals[i] = (int) (tick - lastTick);
            lastTick = tick;
        }
        return intervals;
    }

    /** How often a player would be flagged: two robotic windows in a row, as AutoClickerB does. */
    private static double flagRate(long seed, double meanMs, double sdMs, int windows) {
        Random random = new Random(seed);
        ClickConsistency consistency = new ClickConsistency(40);
        int streak = 0, flags = 0, results = 0;
        for (int interval : clicks(random, windows * 40, meanMs, sdMs)) {
            ClickConsistency.Result result = consistency.add(interval);
            if (result == null) continue;
            results++;
            streak = result.robotic(0.95, 3.0) ? streak + 1 : 0;
            if (streak >= 2) flags++;
        }
        return results == 0 ? 0 : flags / (double) results;
    }

    @Test
    void steadyAutoclickerIsRobotic() {
        // Fixed-delay clickers: every interval is the same number of ticks.
        assertTrue(flagRate(1, 100, 0.5, 200) > 0.9);
        assertTrue(flagRate(2, 50, 0.5, 200) > 0.9);
        // With a few ms of timer jitter most windows still look machine-made.
        assertTrue(flagRate(3, 100, 2, 200) > 0.5);
    }

    @Test
    void humansAreNotFlagged() {
        // People (15-30 ms spread) at 7-20 CPS, 2000 windows each (several hours of non-stop clicking).
        for (double mean : new double[]{50, 60, 70, 85, 100, 120, 140}) {
            for (double sd : new double[]{15, 20, 30}) {
                double rate = flagRate((long) (mean * 1000 + sd), mean, sd, 2000);
                assertEquals(0.0, rate, "flagged a person clicking every " + mean + " ms (sd " + sd + ")");
            }
        }
    }

    @Test
    void evenUnusuallySteadyPeopleAreRare() {
        // 12 ms spread is steadier than measured human clicking; a flag stays a rare single alert, far from a kick.
        assertTrue(flagRate(7, 100, 12, 4000) < 0.002);
    }

    @Test
    void slowRhythmIsNeverJudged() {
        // 1.9+ players timing the attack cooldown hit every ~12 ticks, perfectly regular, which is legitimate.
        ClickConsistency consistency = new ClickConsistency(20);
        for (int i = 0; i < 100; i++) assertNull(consistency.add(12));
    }

    @Test
    void summaryIsCorrect() {
        ClickConsistency.Result r = ClickConsistency.summarize(new int[]{2, 2, 2, 1, 3}, 5);
        assertEquals(2.0, r.mean(), 1e-9);
        assertEquals(2, r.mode());
        assertEquals(0.6, r.modeShare(), 1e-9);
    }

    @Test
    void windowsDoNotOverlap() {
        ClickConsistency consistency = new ClickConsistency(10);
        for (int i = 0; i < 9; i++) assertNull(consistency.add(2));
        assertNotNull(consistency.add(2));
        for (int i = 0; i < 9; i++) assertNull(consistency.add(2));
        assertNotNull(consistency.add(2));
    }
}
