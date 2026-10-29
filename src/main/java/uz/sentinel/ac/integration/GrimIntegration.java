package uz.sentinel.ac.integration;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.event.ListenerPriority;
import ac.grim.grimac.api.plugin.GrimPlugin;
import org.bukkit.entity.Player;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.diagnostics.Finding;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Only Grim's public API. No patched loader, internal fields or second packet injector. */
public final class GrimIntegration implements AutoCloseable {
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");
    private final GrimAbstractAPI api;
    private final GrimPlugin owner;

    public GrimIntegration(SentinelPlugin plugin, Consumer<Finding> sink) {
        api = GrimAPIProvider.get();
        if (api == null || !api.hasStarted() || api.getConfigManager() == null) {
            throw new IllegalStateException("GrimAC is not ready; inspect its startup log/configuration");
        }
        if (!supportedVersion(api.getGrimVersion())) throw new IllegalStateException("GrimAC >= 2.3.74 required; found " + api.getGrimVersion());
        owner = api.getGrimPlugin(plugin);
        api.getEventBus().get(FlagEvent.class).onFlagSupplier(owner, (user, check, verbose, cancelled) -> {
            if (!cancelled) sink.accept(new Finding(System.currentTimeMillis(), user.getUniqueId(),
                    user.getName(), check.getCheckName(), safeVerbose(verbose), true));
            return cancelled; // Observational: never un-cancel, suppress or manufacture Grim violations.
        }, ListenerPriority.MONITOR, true);
    }

    public static boolean supportedVersion(String version) {
        if (version == null) return false;
        var matcher = VERSION.matcher(version);
        if (!matcher.matches()) return false;
        try {
            int major = Integer.parseInt(matcher.group(1)), minor = Integer.parseInt(matcher.group(2)), patch = Integer.parseInt(matcher.group(3));
            return major == 2 && (minor > 3 || minor == 3 && patch >= 74);
        } catch (NumberFormatException e) { return false; }
    }

    private static String safeVerbose(java.util.function.Supplier<String> supplier) {
        try { return supplier == null ? "" : supplier.get(); }
        catch (RuntimeException ignored) { return "[verbose unavailable]"; }
    }

    public GrimUser user(UUID uuid) { return api.getGrimUser(uuid); }
    public boolean alertsEnabled(Player player) {
        GrimUser user = user(player.getUniqueId());
        return user != null && api.getAlertManager().hasAlertsEnabled(user);
    }
    public boolean toggleAlerts(Player player) {
        GrimUser user = user(player.getUniqueId());
        if (user == null) throw new IllegalStateException("Grim hali o'yinchini kuzatishga olmagan. Biroz kuting.");
        return api.getAlertManager().toggleAlerts(user, true);
    }
    public String version() { return api.getGrimVersion(); }
    public java.util.concurrent.CompletableFuture<Boolean> reload() { return api.reloadAsync(); }
    @Override public void close() { api.getEventBus().unregisterAllListeners(owner); }
}
