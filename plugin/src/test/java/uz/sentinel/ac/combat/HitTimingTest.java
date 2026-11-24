package uz.sentinel.ac.combat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HitTimingTest {
    /** Share of full windows judged regular for hits every meanMs with a normal timing spread. */
    private static double regularRate(long seed, double meanMs, double sdMs, int windows) {
        Random random = new Random(seed);
        HitTiming timing = new HitTiming(20);
        double time = random.nextDouble() * 50;
        long lastTick = (long) Math.floor(time / 50);
        int results = 0, regular = 0;
        while (results < windows) {
            time += Math.max(30, meanMs + random.nextGaussian() * sdMs);
            long tick = (long) Math.floor(time / 50);
            HitTiming.Result r = timing.add(tick - lastTick, 0.8);
            lastTick = tick;
            if (r == null) continue;
            results++;
            if (r.regular()) regular++;
        }
        return regular / (double) results;
    }

    @Test
    void machinesAreRegular() {
        assertTrue(regularRate(1, 250, 1, 200) > 0.8, "LiquidBounce-like 5 tick clicker");
        assertTrue(regularRate(2, 650, 1, 200) > 0.8, "cooldown aura at 13 ticks");
    }

    @Test
    void steadyPeopleAreNot() {
        // People clicking at 3-5 CPS (25-40 ms spread) or timing the sword cooldown (50-80 ms spread; the real-client
        // legit sword fight measured about 90 ms), 2000 windows each.
        double[][] cases = {{200, 25}, {200, 40}, {250, 25}, {250, 40}, {300, 30}, {350, 35},
                            {450, 50}, {600, 50}, {650, 60}, {700, 80}};
        for (double[] c : cases) {
            double mean = c[0];
            for (double sd : new double[]{c[1]}) {
                double rate = regularRate((long) (mean * 100 + sd), mean, sd, 2000);
                assertTrue(rate < 0.001, "person every " + mean + " ms (sd " + sd + ") judged regular " + rate);
            }
        }
    }

    @Test
    void outsideTheRangeIsIgnored() {
        HitTiming timing = new HitTiming(10);
        for (int i = 0; i < 50; i++) assertNull(timing.add(40, 0.75)); // pauses never count
        HitTiming.Result r = null;
        for (int i = 0; i < 10; i++) r = timing.add(2, 0.75);       // fast clicking belongs to AutoClickerB
        assertNotNull(r);
        assertFalse(r.regular());
    }

    @Test
    void shortIntervalsNeedNinetyFivePercent() {
        HitTiming timing = new HitTiming(10);
        HitTiming.Result r = null;
        for (int i = 0; i < 10; i++) r = timing.add(i < 9 ? 5 : 6, 0.75); // 90 % identical at 5 ticks
        assertNotNull(r);
        assertFalse(r.regular());
        for (int i = 0; i < 10; i++) r = timing.add(i < 8 ? 12 : 13, 0.75); // 80 % identical at 12 ticks
        assertTrue(r.regular());
    }
}
