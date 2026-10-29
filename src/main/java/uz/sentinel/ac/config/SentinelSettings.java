package uz.sentinel.ac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import uz.sentinel.ac.policy.ChannelPolicy;
import java.util.ArrayList;
import java.util.List;

/** Validated immutable snapshot. A failed reload never publishes half a configuration. */
public record SentinelSettings(ChannelPolicy channels, boolean modEnabled, boolean kickOnChannel,
        int joinDelayTicks, boolean limiterEnabled, int placements, int breaks, int anchors,
        int queueCapacity, int eventsPerTick, int recentCapacity, long alertCooldownMillis,
        boolean logEnabled, int logFileBytes, int logFiles, String prefix, String alert, String kick, String noPermission) {

    public static SentinelSettings read(YamlConfiguration c) {
        int schema = integer(c, "schema-version", 1, 1);
        List<String> exact = strings(c, "mod-sentinel.blacklisted-channels");
        List<String> namespaces = strings(c, "mod-sentinel.blacklisted-namespaces");
        // The old binary used mod names and channel names in the same list.
        // Preserve explicit local legacy entries as exact namespaces/channels, never substrings.
        if (c.contains("mod-sentinel.blacklisted-mods", true)) {
            exact = new ArrayList<>(exact);
            namespaces = new ArrayList<>(namespaces);
            for (String entry : strings(c, "mod-sentinel.blacklisted-mods")) {
                if (entry.contains(":")) exact.add(entry); else namespaces.add(entry);
            }
        }
        return new SentinelSettings(new ChannelPolicy(exact, namespaces),
                bool(c, "mod-sentinel.enabled"), bool(c, "mod-sentinel.kick-on-detect"),
                integer(c, "mod-sentinel.join-check-delay-ticks", 1, 200),
                bool(c, "exploits.crystal-limiter.enabled"),
                integer(c, "exploits.crystal-limiter.max-crystals-per-second", 1, 1000),
                integer(c, "exploits.crystal-limiter.max-breaks-per-second", 1, 1000),
                integer(c, "exploits.crystal-limiter.max-anchors-per-second", 1, 1000),
                integer(c, "diagnostics.queue-capacity", 64, 65536),
                integer(c, "diagnostics.events-per-tick", 1, 1000),
                integer(c, "diagnostics.recent-capacity", 32, 10000),
                integer(c, "diagnostics.alert-cooldown-ms", 100, 60000),
                bool(c, "diagnostics.log-enabled"),
                integer(c, "diagnostics.log-file-size-bytes", 65536, 52428800),
                integer(c, "diagnostics.log-file-count", 1, 10),
                text(c, "prefix"), text(c, "messages.alert"), text(c, "messages.kick"), text(c, "messages.no-permission"));
    }

    private static boolean bool(YamlConfiguration c, String key) {
        Object value = c.get(key);
        if (!(value instanceof Boolean b)) throw new IllegalArgumentException(key + " must be true or false");
        return b;
    }

    private static int integer(YamlConfiguration c, String key, int min, int max) {
        Object value = c.get(key);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())
                || n.doubleValue() != n.longValue() || n.longValue() < min || n.longValue() > max) {
            throw new IllegalArgumentException(key + " must be an integer in [" + min + ", " + max + "]");
        }
        return n.intValue();
    }

    private static String text(YamlConfiguration c, String key) {
        Object value = c.get(key);
        if (!(value instanceof String s) || s.length() > 2048) throw new IllegalArgumentException(key + " must be text <= 2048 characters");
        return s;
    }

    private static List<String> strings(YamlConfiguration c, String key) {
        Object value = c.get(key);
        if (!(value instanceof List<?> list) || list.size() > 256 || list.stream().anyMatch(v -> !(v instanceof String))) {
            throw new IllegalArgumentException(key + " must be a list of at most 256 strings");
        }
        return list.stream().map(String.class::cast).toList();
    }
}
