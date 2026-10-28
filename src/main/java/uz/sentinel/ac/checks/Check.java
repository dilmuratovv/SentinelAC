package uz.sentinel.ac.checks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.utils.ColorUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Check implements Listener {
    protected final Plugin plugin;
    protected final SentinelBridge bridge;
    protected final String name;
    protected final String description;
    protected final Map<UUID, Integer> violations = new ConcurrentHashMap<>();

    public Check(Plugin plugin, SentinelBridge bridge, String name, String description) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void flag(Player player, String verbose) {
        int vl = violations.merge(player.getUniqueId(), 1, Integer::sum);
        String prefix = plugin.getConfig().getString("prefix", "&#FF416Cꜱᴇɴᴛɪɴᴇʟ &#94A3B8»");
        String format = plugin.getConfig().getString("messages.alerts-format",
                "%prefix% &#E2E8F0%player% &#94A3B8shubha: &#FFD200%check% &#94A3B8(x&#EF4444%vl%&#94A3B8) &#718096%verbose%");

        String msg = format
                .replace("%prefix%", prefix)
                .replace("%player%", player.getName())
                .replace("%check%", name)
                .replace("%description%", description)
                .replace("%vl%", String.valueOf(vl))
                .replace("%verbose%", verbose != null ? verbose : "");

        String colored = ColorUtil.color(msg);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if ((staff.hasPermission("sentinel.alerts")) && bridge.hasAlertsEnabled(staff.getUniqueId())) {
                staff.sendMessage(colored);
            }
        }
    }

    public void clear(Player player) {
        violations.remove(player.getUniqueId());
    }

    public void onQuit(UUID uuid) {
        violations.remove(uuid);
    }

    public abstract void reload();
}
