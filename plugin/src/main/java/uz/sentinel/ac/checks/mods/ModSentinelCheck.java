package uz.sentinel.ac.checks.mods;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.scheduler.BukkitTask;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.checks.Check;
import uz.sentinel.ac.diagnostics.Finding;
import uz.sentinel.ac.utils.ColorUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Inspect declared channels on join and on later registration. No client file scanning. */
public final class ModSentinelCheck extends Check {
    private final Map<UUID, BukkitTask> joins = new HashMap<>();
    private final Map<UUID, BukkitTask> kicks = new HashMap<>();
    public ModSentinelCheck(SentinelPlugin plugin) { super(plugin); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        inspectExisting(player);
        BukkitTask previous = joins.remove(player.getUniqueId());
        if (previous != null) previous.cancel();
        joins.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            joins.remove(player.getUniqueId());
            if (player.isOnline()) inspectExisting(player);
        }, plugin.settings().joinDelayTicks()));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        if (Bukkit.isPrimaryThread()) inspect(event.getPlayer(), event.getChannel());
        else {
            UUID id = event.getPlayer().getUniqueId();
            String channel = event.getChannel();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(id);
                if (player != null) inspect(player, channel);
            });
        }
    }
    public void inspectExisting(Player player) {
        if (!plugin.settings().modEnabled() || exempt(player)) return;
        for (String channel : player.getListeningPluginChannels()) inspect(player, channel);
    }
    public void inspect(Player player, String channel) {
        if (!plugin.settings().modEnabled() || exempt(player) || kicks.containsKey(player.getUniqueId())) return;
        plugin.settings().channels().match(channel).ifPresent(rule -> {
            flag(player, "ChannelPolicy", "channel=" + channel + " rule=" + rule);
            if (!plugin.settings().kickOnChannel()) return;
            // Defer until the registration callback has unwound. One pending kick per player.
            kicks.put(player.getUniqueId(), Bukkit.getScheduler().runTask(plugin, () -> {
                kicks.remove(player.getUniqueId());
                if (!player.isOnline() || !plugin.settings().modEnabled() || !plugin.settings().kickOnChannel() || exempt(player)) return;
                plugin.settings().channels().match(channel).ifPresent(currentRule -> {
                    String message = plugin.settings().kick().replace("%prefix%", plugin.settings().prefix())
                            .replace("%channel%", Finding.clean(channel, 193)).replace("%rule%", currentRule);
                    player.kickPlayer(ColorUtil.color(message));
                });
            }));
        });
    }
    @Override public void onQuit(UUID uuid) {
        BukkitTask join = joins.remove(uuid), kick = kicks.remove(uuid);
        if (join != null) join.cancel();
        if (kick != null) kick.cancel();
    }
    @Override public void reload() {
        // Re-evaluate delayed callbacks against the new immutable snapshot.
        Bukkit.getOnlinePlayers().forEach(this::inspectExisting);
    }
    @Override public void close() {
        joins.values().forEach(BukkitTask::cancel);
        kicks.values().forEach(BukkitTask::cancel);
        joins.clear(); kicks.clear();
    }
}
