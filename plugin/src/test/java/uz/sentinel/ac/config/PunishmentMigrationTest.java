package uz.sentinel.ac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PunishmentMigrationTest {
    private static String bundled() throws Exception {
        try (var in = PunishmentMigrationTest.class.getResourceAsStream("/sentinel-punishments.yml")) {
            assertNotNull(in, "bundled profile missing");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static final String OLD = """
            # my comment stays
            Punishments:
              Reach:
                remove-violations-after: 300
                checks:
                  - "Reach"
                commands:
                  - "1:1 [alert]"
              Autoclicker:
                remove-violations-after: 300
                checks:
                  - "Autoclicker"
                commands:
                  - "20:40 [alert]"
            """;

    @Test
    void bundledProfileCoversEverySentinelCheck() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(bundled());
        for (Map.Entry<String, String> group : PunishmentMigration.GROUPS.entrySet()) {
            assertEquals(List.of(group.getValue()), yaml.getStringList("Punishments." + group.getKey() + ".checks"));
        }
        assertEquals(PunishmentMigration.GROUPS.keySet().size(),
                PunishmentMigration.groupBlocks(bundled()).keySet().stream().filter(PunishmentMigration.GROUPS::containsKey).count());
    }

    @Test
    void upgradeAddsOnlyMissingGroupsAndKeepsComments(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("punishments.yml");
        Files.writeString(file, OLD);
        List<String> added = PunishmentMigration.migrate(file, bundled(), "before-2.8.0");
        assertEquals(9, added.size(), "the upstream Autoclicker placeholder must not count as coverage");
        String text = Files.readString(file);
        assertTrue(text.startsWith("# my comment stays"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        assertEquals(List.of("Reach"), yaml.getStringList("Punishments.Reach.checks"));
        assertEquals(List.of("KillAuraA"), yaml.getStringList("Punishments.KillAura.checks"));
        assertTrue(yaml.getStringList("Punishments.CrystalAura.commands").stream().anyMatch(c -> c.contains("kick")));
        assertTrue(Files.exists(dir.resolve("punishments.yml.before-2.8.0.bak")));

        // Second start: nothing more to add.
        assertTrue(PunishmentMigration.migrate(file, bundled(), "before-2.8.0").isEmpty());
    }

    @Test
    void adminsOwnGroupIsRespected() throws Exception {
        String own = OLD + """
                  MyAura:
                    checks:
                      - "KillAuraA"
                    commands:
                      - "1:1 [alert]"
                """;
        PunishmentMigration.Plan plan = PunishmentMigration.missingGroups(own, bundled());
        assertFalse(plan.groups().contains("KillAura"));
        assertTrue(plan.groups().contains("CrystalAura"));
    }

    @Test
    void missingFileIsLeftToTheEngine(@TempDir Path dir) throws Exception {
        assertTrue(PunishmentMigration.migrate(dir.resolve("punishments.yml"), bundled(), "x").isEmpty());
    }

    @Test
    void brokenFileIsNotTouched(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("punishments.yml");
        Files.writeString(file, "Something: else\n");
        assertThrows(Exception.class, () -> PunishmentMigration.migrate(file, bundled(), "x"));
        assertEquals("Something: else\n", Files.readString(file));
    }
}
