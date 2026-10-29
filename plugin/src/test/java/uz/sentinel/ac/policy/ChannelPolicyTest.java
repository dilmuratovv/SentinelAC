package uz.sentinel.ac.policy;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class ChannelPolicyTest {
    @Test void namespaceBoundariesPreventUnrelatedClientKicks() {
        var policy = new ChannelPolicy(List.of("freecam:sync"), List.of("baritone"));
        assertTrue(policy.match("minecraft:baritone").isEmpty());
        assertTrue(policy.match("notbaritone:channel").isEmpty());
        assertTrue(policy.match("example:freecam:sync").isEmpty());
        assertEquals("baritone:*", policy.match("baritone:channel").orElseThrow());
        assertEquals("freecam:sync", policy.match("freecam:sync").orElseThrow());
        assertTrue(policy.match("freecam:other").isEmpty());
    }
    @Test void rejectInvalidRulesBeforePublishingConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelPolicy(List.of("freecam"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ChannelPolicy(List.of(), List.of("*")));
        assertThrows(IllegalArgumentException.class, () -> new ChannelPolicy(List.of("a:b\n".replace("\n", "\nx")), List.of()));
    }
    @Test void normalizationDoesNotDependOnServerLocaleAndDefensivelyCopiesRules() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var rules = new java.util.ArrayList<>(List.of(" BARITONE "));
            var policy = new ChannelPolicy(List.of(), rules);
            rules.clear();
            assertTrue(policy.match("BARITONE:channel").isPresent());
            assertTrue(policy.match(null).isEmpty());
            assertTrue(policy.match("a:" + "x".repeat(5000)).isEmpty());
        } finally { Locale.setDefault(previous); }
    }
}
