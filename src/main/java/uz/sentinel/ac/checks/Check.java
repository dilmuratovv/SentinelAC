package uz.sentinel.ac.checks;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.diagnostics.Finding;
import java.util.UUID;

public abstract class Check implements Listener, AutoCloseable {
    protected final SentinelPlugin plugin;
    protected Check(SentinelPlugin plugin) { this.plugin = plugin; }
    protected boolean exempt(Player player) {
        return player.hasPermission("sentinel.exempt") || player.hasPermission("grim.exempt");
    }
    protected boolean gameplayExempt(Player player) {
        return exempt(player) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }
    protected void flag(Player player, String name, String detail) {
        plugin.bridge().offer(new Finding(System.currentTimeMillis(), player.getUniqueId(), player.getName(), name, detail, false));
    }
    public void reload() {}
    public void onQuit(UUID uuid) {}
    @Override public void close() {}
}
