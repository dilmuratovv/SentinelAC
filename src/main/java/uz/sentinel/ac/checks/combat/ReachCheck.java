package uz.sentinel.ac.checks.combat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.checks.Check;

public class ReachCheck extends Check {
    private boolean enabled = true;
    private double maxReachDistance = 3.35;
    private boolean cancelHits = true;

    public ReachCheck(Plugin plugin, SentinelBridge bridge) {
        super(plugin, bridge, "Reach", "Maksimal ruxsat berilgan masofadan uzoqroqdan zarba berish");
        reload();
    }

    @Override
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("combat.enabled", true);
        this.maxReachDistance = plugin.getConfig().getDouble("combat.max-reach-distance", 3.35);
        this.cancelHits = plugin.getConfig().getBoolean("combat.cancel-impossible-hits", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sentinel.exempt")) return;

        Entity target = event.getEntity();
        // PvP-only: PvE entities (Ender Dragon, Ghast, Ravager, Sniffer, etc.) have non-standard hitboxes handled by Grim raytracing
        if (!(target instanceof Player targetPlayer)) return;
        if (player.getWorld() != targetPlayer.getWorld()) return;

        Location eyeLoc = player.getEyeLocation();
        BoundingBox box = targetPlayer.getBoundingBox();

        // Exact mathematical distance from attacker's eyes to nearest point of target bounding box (Zero-Allocation)
        double nearestX = Math.max(box.getMinX(), Math.min(eyeLoc.getX(), box.getMaxX()));
        double nearestY = Math.max(box.getMinY(), Math.min(eyeLoc.getY(), box.getMaxY()));
        double nearestZ = Math.max(box.getMinZ(), Math.min(eyeLoc.getZ(), box.getMaxZ()));

        double dx = eyeLoc.getX() - nearestX;
        double dy = eyeLoc.getY() - nearestY;
        double dz = eyeLoc.getZ() - nearestZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        int ping = player.getPing();
        double pingBuffer = Math.min(ping, 300) * 0.003;
        double threshold = maxReachDistance + pingBuffer;

        if (distance > threshold) {
            if (cancelHits) {
                event.setCancelled(true);
            }
            String formattedDist = String.format("%.2f", distance);
            String formattedMax = String.format("%.2f", threshold);
            flag(player, "Masofa: " + formattedDist + "m (Limit: " + formattedMax + "m, Ping: " + ping + "ms)");
        }
    }
}
