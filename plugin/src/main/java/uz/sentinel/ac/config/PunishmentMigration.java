package uz.sentinel.ac.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The engine disables every check that no punishment group mentions. A server upgrading from an older SentinelAC
 * keeps its own punishments.yml, so the new Sentinel checks would silently stay off. This appends the bundled group
 * of each Sentinel check that the existing file does not cover yet. Text is appended (comments and formatting are
 * kept), a backup is written first, and the result is parsed again; anything unexpected restores the backup.
 */
public final class PunishmentMigration {
    /** Group in the bundled profile -> Sentinel check it covers. */
    static final Map<String, String> GROUPS = Map.of(
            "KillAura", "KillAuraA", "AimAssist", "KillAuraB", "AuraTiming", "KillAuraC", "AutoClickerRate", "AutoClickerA",
            "AutoClickerPattern", "AutoClickerB", "AutoTotem", "AutoTotemA", "CrystalAura", "CrystalAuraA",
            "Tracking", "TrackingA", "Backtrack", "BacktrackA");
    /** Upstream placeholder entries that match the new names by accident and only ever alerted at 20 violations. */
    private static final List<String> PLACEHOLDERS = List.of("autoclicker", "killaura");

    private PunishmentMigration() {}

    /** Result of {@link #missingGroups}: text to append and the group names it adds. */
    public record Plan(String text, List<String> groups) {
        public boolean empty() { return groups.isEmpty(); }
    }

    /** Which bundled groups the existing punishments.yml lacks, as text to append. */
    public static Plan missingGroups(String existing, String bundled) throws InvalidConfigurationException {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString(existing);
        ConfigurationSection punishments = current.getConfigurationSection("Punishments");
        if (punishments == null) throw new InvalidConfigurationException("punishments.yml has no Punishments section");

        List<String> entries = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String group : punishments.getKeys(false)) {
            names.add(group.toLowerCase(Locale.ROOT));
            for (String entry : punishments.getStringList(group + ".checks")) {
                String e = entry.trim().toLowerCase(Locale.ROOT);
                if (!e.isEmpty() && !e.startsWith("!") && !PLACEHOLDERS.contains(e)) entries.add(e);
            }
        }

        Map<String, String> blocks = groupBlocks(bundled);
        StringBuilder text = new StringBuilder();
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, String> group : blocks.entrySet()) {
            String check = GROUPS.get(group.getKey());
            if (check == null || names.contains(group.getKey().toLowerCase(Locale.ROOT))) continue;
            String lower = check.toLowerCase(Locale.ROOT);
            if (entries.stream().anyMatch(lower::contains)) continue; // already covered by the admin's own group
            text.append(group.getValue());
            added.add(group.getKey());
        }
        return new Plan(text.toString(), added);
    }

    /** Top-level groups (two-space indent under Punishments) of the bundled profile, in file order, as raw text. */
    static Map<String, String> groupBlocks(String yaml) {
        Map<String, String> blocks = new LinkedHashMap<>();
        String current = null;
        StringBuilder block = new StringBuilder();
        for (String line : yaml.split("\n", -1)) {
            boolean groupStart = line.startsWith("  ") && !line.startsWith("   ") && !line.trim().startsWith("#")
                    && line.trim().endsWith(":");
            boolean topLevel = !line.isEmpty() && !line.startsWith(" ");
            boolean groupComment = line.startsWith("  #") && !line.startsWith("   ");
            if (groupStart || topLevel || groupComment) {
                if (current != null) blocks.put(current, block.toString());
                current = groupStart ? line.trim().substring(0, line.trim().length() - 1) : null;
                block.setLength(0);
            }
            if (current != null && !line.isBlank()) block.append(line).append('\n');
        }
        if (current != null) blocks.put(current, block.toString());
        return blocks;
    }

    /**
     * Applies the plan to the file. Returns the added group names (empty when nothing was needed).
     * Throws when the file could not be updated safely; the original is then left in place.
     */
    public static List<String> migrate(Path file, String bundled, String backupSuffix) throws IOException, InvalidConfigurationException {
        if (!Files.isRegularFile(file)) return List.of();
        String existing = Files.readString(file, StandardCharsets.UTF_8);
        Plan plan = missingGroups(existing, bundled);
        if (plan.empty()) return List.of();

        Path backup = file.resolveSibling(file.getFileName() + "." + backupSuffix + ".bak");
        if (Files.notExists(backup)) Files.copy(file, backup);
        String updated = (existing.endsWith("\n") ? existing : existing + "\n")
                + "  # Added by SentinelAC: groups for the Sentinel combat checks (a check in no group is disabled).\n"
                + plan.text();
        YamlConfiguration check = new YamlConfiguration();
        check.loadFromString(updated);
        for (String group : plan.groups()) {
            if (check.getConfigurationSection("Punishments." + group) == null) {
                throw new InvalidConfigurationException("Appended group " + group + " did not end up under Punishments");
            }
        }
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), ".punishments-", ".tmp");
        try {
            Files.writeString(temp, updated, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return plan.groups();
    }
}
