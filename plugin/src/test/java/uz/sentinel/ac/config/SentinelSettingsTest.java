package uz.sentinel.ac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SentinelSettingsTest {
    public static YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                SentinelSettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }
    @Test void bundledDefaultsAreCompleteAndUseExactPolicy() {
        var s = SentinelSettings.read(config());
        assertTrue(s.modEnabled()); assertTrue(s.kickOnChannel());
        assertTrue(s.channels().match("baritone:channel").isPresent());
        assertTrue(s.channels().match("innocent:baritone").isEmpty());
    }
    @Test void badReloadDoesNotMutatePreviousSnapshot() {
        var c = config(); var old = SentinelSettings.read(c);
        c.set("mod-sentinel.blacklisted-namespaces", List.of("*"));
        assertThrows(IllegalArgumentException.class, () -> SentinelSettings.read(c));
        assertTrue(old.channels().match("baritone:channel").isPresent());
    }
    @Test void rejectNegativeFractionalAndMistypedLimitsInsteadOfSilentlyDisablingEnforcement() {
        for (Object value : List.of(-1, 0, 1.5, "12", Double.NaN, 1001)) {
            var c = config(); c.set("exploits.crystal-limiter.max-crystals-per-second", value);
            assertThrows(IllegalArgumentException.class, () -> SentinelSettings.read(c));
        }
        var c = config(); c.set("mod-sentinel.enabled", "false");
        assertThrows(IllegalArgumentException.class, () -> SentinelSettings.read(c));
    }
    @Test void legacyListIsMigratedWithoutSubstringRules() {
        var c = config(); c.set("mod-sentinel.blacklisted-mods", List.of("legacy", "other:exact"));
        var s = SentinelSettings.read(c);
        assertTrue(s.channels().match("legacy:main").isPresent());
        assertTrue(s.channels().match("other:exact").isPresent());
        assertTrue(s.channels().match("notlegacy:main").isEmpty());
    }
}
