package uz.sentinel.ac.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsTest {
    @Test void untrustedTextCannotInjectLogLinesOrColorCodes() {
        var f = new Finding(1, UUID.randomUUID(), "player", "check", "&cFAKE\n§4Alert\r\u001b\t" + "x".repeat(5000), false);
        assertTrue(f.detail().length() <= 512);
        assertFalse(f.detail().contains("\n")); assertFalse(f.detail().contains("\r"));
        assertFalse(f.detail().contains("§")); assertFalse(f.detail().contains("&c"));
    }
    @Test void disconnectChurnCannotGrowRecentHistoryWithoutBound() {
        var recent = new RecentFindings(32);
        for (int i = 0; i < 10000; i++) recent.add(new Finding(i, UUID.randomUUID(), "p" + i, "Reach", "d", true));
        assertEquals(32, recent.size());
        assertTrue(recent.recent("p0", 10).isEmpty());
        assertEquals(9999, recent.recent("P9999", 10).getFirst().timeMillis());
    }
}
