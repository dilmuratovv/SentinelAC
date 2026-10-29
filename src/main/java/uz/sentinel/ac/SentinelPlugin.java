package uz.sentinel.ac;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import uz.sentinel.ac.commands.SentinelCommand;
import uz.sentinel.ac.config.SentinelSettings;
import uz.sentinel.ac.integration.GrimIntegration;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class SentinelPlugin extends JavaPlugin {
    private volatile SentinelSettings settings;
    private SentinelBridge bridge;
    private GrimIntegration grim;
    private final AtomicBoolean reloading = new AtomicBoolean();

    @Override public void onEnable() {
        try {
            if (!Bukkit.getMinecraftVersion().matches("26\\.2(?:\\.\\d+)?")) {
                throw new IllegalStateException("This release targets Paper 26.2; found " + Bukkit.getMinecraftVersion());
            }
            saveDefaultConfig();
            settings = readSettings();
            bridge = new SentinelBridge(this, settings);
            grim = new GrimIntegration(this, bridge::offer);
            SentinelCommand commands = new SentinelCommand(this);
            Objects.requireNonNull(getCommand("sentinel")).setExecutor(commands);
            getCommand("sentinel").setTabCompleter(commands);
            bridge.start();
            getLogger().info("Sentinel ready: Paper 26.2, GrimAC " + grim.version() + ". Channel policy is not a full mod scan.");
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
            grim.reload().whenComplete((success, failure) -> {
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
        if (grim != null) { grim.close(); grim = null; }
        if (bridge != null) { bridge.close(); bridge = null; }
    }
    public SentinelSettings settings() { return settings; }
    public SentinelBridge bridge() { return bridge; }
    public GrimIntegration grim() { return grim; }
}
