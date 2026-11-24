package uz.sentinel.ac.compatibility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionOrderTest {
    @Test
    void numericOrder() {
        assertTrue(ServerCompatibility.compare("1.21.10", "1.21.9") > 0);
        assertTrue(ServerCompatibility.compare("26.4", "26.3") > 0);
        assertTrue(ServerCompatibility.compare("27", "26.3") > 0);
        assertEquals(0, ServerCompatibility.compare("26.3", "26.3.0"));
    }

    @Test
    void onlyNewerReleasesMayBeForced() {
        String newest = ServerCompatibility.supportedVersions().get(ServerCompatibility.supportedVersions().size() - 1);
        assertFalse(ServerCompatibility.newerThanTested(newest));
        assertFalse(ServerCompatibility.newerThanTested("1.20.6"));
        assertTrue(ServerCompatibility.newerThanTested("99.1"));
        assertFalse(ServerCompatibility.newerThanTested(null));
    }
}
