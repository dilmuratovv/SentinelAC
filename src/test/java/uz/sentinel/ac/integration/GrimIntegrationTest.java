package uz.sentinel.ac.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrimIntegrationTest {
    @Test void requireTheEngineReleaseThatSupportsTargetProtocol() {
        assertFalse(GrimIntegration.supportedVersion("2.3.73"));
        assertTrue(GrimIntegration.supportedVersion("2.3.74"));
        assertTrue(GrimIntegration.supportedVersion("2.3.75-abcd123"));
        assertFalse(GrimIntegration.supportedVersion("3.0.0"));
        assertFalse(GrimIntegration.supportedVersion(null));
        assertFalse(GrimIntegration.supportedVersion("unknown"));
    }
}
