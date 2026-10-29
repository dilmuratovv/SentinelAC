package uz.sentinel.ac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentProfileTest {
    @Test void optionalProfileRetainsAllCoreGroupsAndNeverPermanentlyBans() throws Exception {
        var c = new YamlConfiguration(); c.load(new File("deployment/GrimAC/punishments.yml"));
        var groups = c.getConfigurationSection("Punishments"); assertNotNull(groups);
        assertEquals(Set.of("Simulation", "Knockback", "Post", "BadPackets", "Reach", "Hitboxes", "Misc", "Combat", "Autoclicker"), groups.getKeys(false));
        for (String group : groups.getKeys(false)) {
            assertFalse(c.getStringList("Punishments." + group + ".checks").isEmpty());
            for (String command : c.getStringList("Punishments." + group + ".commands")) {
                assertTrue(command.matches("[1-9][0-9]*:[0-9]+ .+"), command);
                assertFalse(command.matches(".*\\bban(?:-ip)?\\b.*"), command);
            }
        }
    }
}
