package uz.sentinel.ac.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatMathTest {
    @Test
    void yawMatchesMinecraftConvention() {
        assertEquals(0, AimMath.angleBetween(AimMath.yawTo(0, 0, 0, 5), 0), 1e-9);    // +Z
        assertEquals(0, AimMath.angleBetween(AimMath.yawTo(0, 0, -5, 0), 90), 1e-9);  // -X
        assertEquals(0, AimMath.angleBetween(AimMath.yawTo(0, 0, 5, 0), -90), 1e-9);  // +X
        assertEquals(0, AimMath.angleBetween(AimMath.yawTo(0, 0, 0, -5), 180), 1e-9); // -Z
    }

    @Test
    void anglesWrap() {
        assertEquals(20, AimMath.angleBetween(350, 10), 1e-9);
        assertEquals(180, AimMath.angleBetween(-90, 90), 1e-9);
        assertEquals(1, AimMath.angleBetween(719, 0), 1e-9);
    }

    @Test
    void stepError() {
        double step = 0.15 * Math.pow(0.5 * 0.6 + 0.2, 3) * 8; // 50 % sensitivity
        assertEquals(0, AimMath.stepError(step * 37, step), 1e-6);
        assertEquals(0.5, AimMath.stepError(step * 37.5, step), 1e-6);
        assertEquals(0, AimMath.stepError(5, 0), 0);
    }

    @Test
    void tickWindowForgetsOldTicks() {
        TickWindow window = new TickWindow(20);
        for (int i = 0; i < 5; i++) window.add();
        assertEquals(5, window.total());
        for (int i = 0; i < 19; i++) window.tick();
        assertEquals(5, window.total());
        window.tick();
        assertEquals(0, window.total());
        assertEquals(1, window.add());
    }

    @Test
    void timeWindowSlides() {
        TimeWindow window = new TimeWindow(1000, 10);
        assertEquals(1, window.add(0));
        assertEquals(2, window.add(500));
        assertEquals(2, window.add(1000)); // the event at 0 left the window
        assertEquals(1, window.count(1999));
        for (int i = 0; i < 20; i++) window.add(1999);
        assertEquals(10, window.count(1999)); // bounded
    }

    @Test
    void outcomeHistoryKeepsTheLastN() {
        OutcomeHistory history = new OutcomeHistory(3);
        assertEquals(1, history.record(true));
        assertEquals(2, history.record(true));
        assertFalse(history.full());
        assertEquals(2, history.record(false));
        assertTrue(history.full());
        assertEquals(1, history.record(false)); // the first true dropped out
        history.clear();
        assertEquals(0, history.hits());
        assertEquals(0, history.size());
    }
}
