package uz.sentinel.ac;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import uz.sentinel.ac.checks.Check;
import uz.sentinel.ac.checks.combat.CrystalLimiterCheck;
import uz.sentinel.ac.checks.combat.ReachCheck;
import uz.sentinel.ac.checks.food.FoodCheck;
import uz.sentinel.ac.checks.inventory.AutoTotemCheck;
import uz.sentinel.ac.checks.mods.ModSentinelCheck;
import uz.sentinel.ac.commands.SentinelCommand;
import uz.sentinel.ac.utils.ColorUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class SentinelBridge implements Listener {
    private static SentinelBridge instance;
    private final Plugin plugin;
    private final List<Check> checks = new ArrayList<>();
    private final Set<UUID> alertsDisabled = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private BukkitTask bukkitCleanupTask;
    private Object foliaScheduledTask;

    private SentinelBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    public static SentinelBridge getInstance() {
        return instance;
    }

    public static void init(Plugin plugin) {
        if (instance != null) return;
        instance = new SentinelBridge(plugin);
        instance.start();
    }

    public static void stop() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    private void start() {
        // Register Sentinel bridge listener for PlayerQuitEvent
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Register Sentinel custom checks
        checks.add(new AutoTotemCheck(plugin, this));
        checks.add(new FoodCheck(plugin, this));
        checks.add(new ReachCheck(plugin, this));
        checks.add(new CrystalLimiterCheck(plugin, this));
        checks.add(new ModSentinelCheck(plugin, this));

        for (Check check : checks) {
            Bukkit.getPluginManager().registerEvents(check, plugin);
        }

        // Register Sentinel command
        PluginCommand cmd = Bukkit.getPluginCommand("sentinel");
        if (cmd != null) {
            SentinelCommand executor = new SentinelCommand(plugin, this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // Periodic violation cleanup
        long cleanupTicks = plugin.getConfig().getLong("performance.cleanup-interval-ticks", 1200L);
        Runnable cleanupTask = () -> {
            for (Check check : checks) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    check.clear(player);
                }
            }
        };

        try {
            // Check for Folia / modern Paper async scheduler
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            foliaScheduledTask = asyncSchedulerClass.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class, TimeUnit.class)
                    .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) o -> cleanupTask.run(), cleanupTicks * 50L, cleanupTicks * 50L, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            // Fallback to standard Bukkit async task
            bukkitCleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, cleanupTask, cleanupTicks, cleanupTicks);
        }

        // Banner in console
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#FF416C======================================================"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#FF416C  ____             _   _            _      _    ____ "));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#FF416C / ___|  ___ _ __ | |_(_)_ __   ___| |    / \\  / ___|"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#FF416C \\___ \\ / _ \\ '_ \\| __| | '_ \\ / _ \\ |   / _ \\| |    "));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#FF416C  ___) |  __/ | | | |_| | | | |  __/ |  / ___ \\ |___ "));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#FF416C |____/ \\___|_| |_|\\__|_|_| |_|\\___|_| /_/   \\_\\____|"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#FF416C  Next-Gen Universal Anticheat Engine (v2.3.74-SENTINEL)"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#10B981  1:1 Fizik Simulyatsiya + Max-Efficiency Himoya Faollashdi!"));
        Bukkit.getConsoleSender().sendMessage(ColorUtil.color("&#FF416C======================================================"));
    }

    private void shutdown() {
        if (bukkitCleanupTask != null) {
            bukkitCleanupTask.cancel();
            bukkitCleanupTask = null;
        }
        if (foliaScheduledTask != null) {
            try {
                foliaScheduledTask.getClass().getMethod("cancel").invoke(foliaScheduledTask);
            } catch (Throwable ignored) {}
            foliaScheduledTask = null;
        }
        HandlerList.unregisterAll(this);
        for (Check check : checks) {
            HandlerList.unregisterAll(check);
        }
        checks.clear();
        alertsDisabled.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        alertsDisabled.remove(uuid);
        for (Check check : checks) {
            check.onQuit(uuid);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        try {
            if (ac.grim.grimac.GrimAPI.INSTANCE != null && ac.grim.grimac.GrimAPI.INSTANCE.getExternalAPI() != null) {
                ac.grim.grimac.GrimAPI.INSTANCE.getExternalAPI().reloadAsync();
            }
        } catch (Throwable ignored) {}
        for (Check check : checks) {
            check.reload();
        }
    }

    public boolean hasAlertsEnabled(UUID uuid) {
        return !alertsDisabled.contains(uuid);
    }

    public boolean toggleAlerts(UUID uuid) {
        if (alertsDisabled.contains(uuid)) {
            alertsDisabled.remove(uuid);
            return true;
        } else {
            alertsDisabled.add(uuid);
            return false;
        }
    }

    public List<Check> getChecks() {
        return Collections.unmodifiableList(checks);
    }
}
