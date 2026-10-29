package uz.sentinel.ac.policy;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowTest {
    @Test void burstCannotDoubleAcrossFixedWindowBoundary() {
        AtomicLong clock = new AtomicLong(999_000_000L);
        var window = new SlidingWindow(2, 1_000_000_000L, clock::get);
        assertTrue(window.tryAcquire()); assertTrue(window.tryAcquire());
        clock.set(1_001_000_000L);
        assertFalse(window.tryAcquire());
        clock.set(1_999_000_000L);
        assertTrue(window.tryAcquire());
    }
    @Test void rejectedSpamDoesNotExtendLockout() {
        AtomicLong clock = new AtomicLong();
        var window = new SlidingWindow(1, 100, clock::get);
        assertTrue(window.tryAcquire());
        for (int i = 0; i < 10000; i++) assertFalse(window.tryAcquire());
        clock.set(100);
        assertTrue(window.tryAcquire());
    }
    @Test void monotonicCounterWrapStillExpiresOldAdmission() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 5);
        var window = new SlidingWindow(1, 10, clock::get);
        assertTrue(window.tryAcquire());
        clock.addAndGet(10);
        assertTrue(window.tryAcquire());
    }
}
