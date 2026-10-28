package uz.sentinel.ac.checks.mods;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.checks.Check;
import uz.sentinel.ac.utils.ColorUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModSentinelCheck extends Check {
    private boolean enabled = true;
    private boolean kickOnDetect = true;
    private String kickMessage = "";
    private final Set<String> blacklistedChannels = new HashSet<>();

    public ModSentinelCheck(Plugin plugin, SentinelBridge bridge) {
        super(plugin, bridge, "ModSentinel", "Mijoz (Client) tomondagi taqiqlangan modlarni aniqlash");
        reload();
    }

    @Override
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("mod-sentinel.enabled", true);
        this.kickOnDetect = plugin.getConfig().getBoolean("mod-sentinel.kick-on-detect", true);
        this.kickMessage = plugin.getConfig().getString("mod-sentinel.kick-message",
                "&#FF416C[SENTINEL AC HIMOYASI]\n&#E2E8F0Taqiqlangan mod aniqlandi: &#FFD200%mod%");

        blacklistedChannels.clear();
        List<String> list = plugin.getConfig().getStringList("mod-sentinel.blacklisted-mods");
        for (String item : list) {
            blacklistedChannels.add(item.toLowerCase());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        if (player.hasPermission("sentinel.exempt")) return;

        String channel = event.getChannel().toLowerCase();

        for (String blacklisted : blacklistedChannels) {
            if (channel.contains(blacklisted) || channel.equalsIgnoreCase(blacklisted)) {
                String detectedMod = blacklisted.toUpperCase();
                flag(player, "Taqiqlangan mod kanali aniqlandi: " + channel);

                if (kickOnDetect) {
                    Runnable kickTask = () -> {
                        String msg = kickMessage.replace("%mod%", detectedMod);
                        player.kickPlayer(ColorUtil.color(msg));
                    };
                    try {
                        // Folia regional entity scheduler
                        player.getScheduler().run(plugin, scheduledTask -> kickTask.run(), null);
                    } catch (Throwable t) {
                        // Paper / Spigot standard scheduler fallback
                        Bukkit.getScheduler().runTask(plugin, kickTask);
                    }
                }
                break;
            }
        }
    }
}
