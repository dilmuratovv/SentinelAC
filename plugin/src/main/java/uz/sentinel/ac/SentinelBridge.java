package uz.sentinel.ac;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import uz.sentinel.ac.checks.Check;
import uz.sentinel.ac.checks.combat.CrystalLimiterCheck;
import uz.sentinel.ac.checks.mods.ModSentinelCheck;
import uz.sentinel.ac.config.SentinelSettings;
import uz.sentinel.ac.diagnostics.*;
import uz.sentinel.ac.utils.ColorUtil;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Paper main-thread enforcement, bounded cross-thread diagnostics. */
public final class SentinelBridge implements Listener, AutoCloseable {
    private final SentinelPlugin plugin;
    private final List<Check> checks;
    private final ArrayBlockingQueue<Finding> pending;
    private final RecentFindings recent;
    private final AuditLog log;
    private final AtomicLong dropped = new AtomicLong();
    private final Map<Key, Counter> counters = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Counter> entry) { return size() > 4096; }
    };
    private BukkitTask drain;
    private long lastWarning;
    private long processed;

    public SentinelBridge(SentinelPlugin plugin, SentinelSettings settings) throws IOException {
        this.plugin = plugin;
        pending = new ArrayBlockingQueue<>(settings.queueCapacity());
        recent = new RecentFindings(settings.recentCapacity());
        log = settings.logEnabled() ? new AuditLog(plugin.getDataFolder().toPath().resolve("logs"), settings) : null;
        checks = List.of(new CrystalLimiterCheck(plugin), new ModSentinelCheck(plugin));
    }
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Check check : checks) Bukkit.getPluginManager().registerEvents(check, plugin);
        drain = Bukkit.getScheduler().runTaskTimer(plugin, this::drain, 1, 1);
        // Plugin reloads/startup with already connected players must also check existing channels.
        checks.stream().filter(ModSentinelCheck.class::isInstance).map(ModSentinelCheck.class::cast)
                .forEach(check -> Bukkit.getOnlinePlayers().forEach(check::inspectExisting));
    }
    public void offer(Finding finding) {
        if (!pending.offer(finding)) dropped.incrementAndGet();
    }
    private void drain() {
        long now = System.nanoTime();
        for (int i = 0; i < plugin.settings().eventsPerTick(); i++) {
            Finding f = pending.poll();
            if (f == null) break;
            processed++;
            Key key = new Key(f.playerId(), f.check(), f.core());
            Counter c = counters.computeIfAbsent(key, ignored -> new Counter());
            c.violations = now - c.lastSeen > 300_000_000_000L ? 1 : c.violations + 1;
            c.lastSeen = now;
            if (c.lastEmitted != 0 && now - c.lastEmitted < plugin.settings().alertCooldownMillis() * 1_000_000L) continue;
            c.lastEmitted = now;
            recent.add(f);
            if (log != null) log.record(f);
            if (!f.core()) {
                String text = plugin.settings().alert().replace("%prefix%", plugin.settings().prefix())
                        .replace("%player%", f.playerName()).replace("%check%", f.check())
                        .replace("%vl%", Long.toString(c.violations)).replace("%verbose%", f.detail());
                plugin.getLogger().info(f.playerName() + " | " + f.check() + " | " + f.detail());
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("sentinel.alerts") && plugin.engine().alertsEnabled(staff)) staff.sendMessage(ColorUtil.color(text));
                }
            }
        }
        if (dropped.get() > 0 && now - lastWarning > 60_000_000_000L) {
            lastWarning = now;
            plugin.getLogger().warning("Diagnostics queue overflow: " + dropped.get() + " dropped. Sentinel checks remain independent.");
        }
    }
    public List<Finding> history(String player) { return recent.recent(player, 10); }
    public long processed() { return processed; }
    public int queued() { return pending.size(); }
    public long dropped() { return dropped.get() + (log == null ? 0 : log.dropped()); }
    public void reloaded() { checks.forEach(Check::reload); }
    @EventHandler(priority = EventPriority.MONITOR) public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        checks.forEach(check -> check.onQuit(id));
        counters.keySet().removeIf(key -> key.player.equals(id));
    }
    @Override public void close() {
        if (drain != null) drain.cancel();
        HandlerList.unregisterAll(this);
        for (Check check : checks) { HandlerList.unregisterAll(check); check.close(); }
        pending.clear();
        counters.clear();
        if (log != null) log.close();
    }
    private record Key(UUID player, String check, boolean core) {}
    private static final class Counter { long violations; long lastSeen; long lastEmitted; }
}
