package uz.sentinel.ac.integration;

import uz.sentinel.engine.api.SentinelAPIProvider;
import uz.sentinel.engine.api.SentinelAbstractAPI;
import uz.sentinel.engine.api.SentinelUser;
import uz.sentinel.engine.api.event.events.FlagEvent;
import uz.sentinel.engine.api.event.ListenerPriority;
import uz.sentinel.engine.api.plugin.EnginePlugin;
import org.bukkit.entity.Player;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.diagnostics.Finding;
import java.util.UUID;
import java.util.function.Consumer;

/** Connects policy diagnostics to the embedded engine public API. */
public final class EngineIntegration implements AutoCloseable {
    private final SentinelAbstractAPI api;
    private final EnginePlugin owner;

    public EngineIntegration(SentinelPlugin plugin, Consumer<Finding> sink) {
        api = SentinelAPIProvider.get();
        if (api == null || !api.hasStarted() || api.getConfigManager() == null) {
            throw new IllegalStateException("SentinelAC is not ready; inspect its startup log/configuration");
        }
        owner = api.getSentinelPlugin(plugin);
        api.getEventBus().get(FlagEvent.class).onFlagSupplier(owner, (user, check, verbose, cancelled) -> {
            if (!cancelled) sink.accept(new Finding(System.currentTimeMillis(), user.getUniqueId(),
                    user.getName(), check.getCheckName(), safeVerbose(verbose), true));
            return cancelled; // Observational: never un-cancel, suppress or manufacture Sentinel violations.
        }, ListenerPriority.MONITOR, true);
    }

    private static String safeVerbose(java.util.function.Supplier<String> supplier) {
        try { return supplier == null ? "" : supplier.get(); }
        catch (RuntimeException ignored) { return "[verbose unavailable]"; }
    }

    public SentinelUser user(UUID uuid) { return api.getSentinelUser(uuid); }
    public boolean alertsEnabled(Player player) {
        SentinelUser user = user(player.getUniqueId());
        return user != null && api.getAlertManager().hasAlertsEnabled(user);
    }
    public boolean toggleAlerts(Player player) {
        SentinelUser user = user(player.getUniqueId());
        if (user == null) throw new IllegalStateException("Sentinel hali o'yinchini kuzatishga olmagan. Biroz kuting.");
        return api.getAlertManager().toggleAlerts(user, true);
    }
    public String version() { return api.getSentinelVersion(); }
    public java.util.concurrent.CompletableFuture<Boolean> reload() { return api.reloadAsync(); }
    @Override public void close() { api.getEventBus().unregisterAllListeners(owner); }
}
