package uz.sentinel.ac;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import uz.sentinel.engine.platform.bukkit.SentinelACBukkitLoaderPlugin;
import uz.sentinel.ac.config.SentinelSettings;
import uz.sentinel.ac.integration.EngineIntegration;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class SentinelPlugin extends SentinelACBukkitLoaderPlugin {
    private volatile SentinelSettings settings;
    private SentinelBridge bridge;
    private EngineIntegration engine;
    private final AtomicBoolean reloading = new AtomicBoolean();

    @Override public void onEnable() {
        try {
            if (!uz.sentinel.ac.compatibility.ServerCompatibility.supports(Bukkit.getMinecraftVersion())) {
                throw new IllegalStateException("This JAR targets Paper " + uz.sentinel.ac.compatibility.ServerCompatibility.target() + "; found " + Bukkit.getMinecraftVersion());
            }
            saveDefaultConfig();
            settings = readSettings();
            bridge = new SentinelBridge(this, settings);
            super.onEnable();
            engine = new EngineIntegration(this, bridge::offer);
            bridge.start();
            getLogger().info("Sentinel ready: Paper " + Bukkit.getMinecraftVersion() + ", SentinelAC " + engine.version() + ". Channel policy is not a full mod scan.");
        } catch (Exception | LinkageError e) {
            getLogger().log(Level.SEVERE, "Sentinel startup failed; protection is NOT ready", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public SentinelSettings readSettings() throws Exception {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(new File(getDataFolder(), "config.yml"));
        try (var reader = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            candidate.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        return SentinelSettings.read(candidate);
    }

    public CompletableFuture<Boolean> reloadSettings() {
        if (!reloading.compareAndSet(false, true)) return CompletableFuture.failedFuture(new IllegalStateException("Reload already running"));
        try {
            SentinelSettings candidate = readSettings();
            if (candidate.queueCapacity() != settings.queueCapacity() || candidate.recentCapacity() != settings.recentCapacity()
                    || candidate.logEnabled() != settings.logEnabled() || candidate.logFileBytes() != settings.logFileBytes()
                    || candidate.logFiles() != settings.logFiles()) {
                throw new IllegalArgumentException("Diagnostic buffer/log size changes require a restart");
            }
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            engine.reload().whenComplete((success, failure) -> {
                if (!isEnabled()) {
                    reloading.set(false);
                    result.complete(false);
                    return;
                }
                getServer().getScheduler().runTask(this, () -> {
                    try {
                        if (failure != null) result.completeExceptionally(failure);
                        else if (!Boolean.TRUE.equals(success)) result.complete(false);
                        else {
                            settings = candidate;
                            bridge.reloaded();
                            result.complete(true);
                        }
                    } finally { reloading.set(false); }
                });
            });
            return result;
        } catch (Exception e) {
            reloading.set(false);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override public void onDisable() {
        if (engine != null) { engine.close(); engine = null; }
        if (bridge != null) { bridge.close(); bridge = null; }
        super.onDisable();
    }
    public SentinelSettings settings() { return settings; }
    public SentinelBridge bridge() { return bridge; }
    public EngineIntegration engine() { return engine; }
}
