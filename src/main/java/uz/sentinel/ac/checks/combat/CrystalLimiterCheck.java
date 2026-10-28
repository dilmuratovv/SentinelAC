package uz.sentinel.ac.checks.combat;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.checks.Check;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CrystalLimiterCheck extends Check {
    private final Map<UUID, WindowTracker> crystalPlaceTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, WindowTracker> crystalBreakTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, WindowTracker> anchorTrackers = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private int maxCrystalsPerSecond = 12;
    private int maxBreaksPerSecond = 14;
    private int maxAnchorsPerSecond = 8;

    public CrystalLimiterCheck(Plugin plugin, SentinelBridge bridge) {
        super(plugin, bridge, "FastCrystal/Anchor", "Sekundiga ruxsat etilgan kristal/anchor sonidan oshirib yuborish");
        reload();
    }

    @Override
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("exploits.crystal-limiter.enabled", true);
        this.maxCrystalsPerSecond = plugin.getConfig().getInt("exploits.crystal-limiter.max-crystals-per-second", 12);
        this.maxBreaksPerSecond = plugin.getConfig().getInt("exploits.crystal-limiter.max-breaks-per-second", 14);
        this.maxAnchorsPerSecond = plugin.getConfig().getInt("exploits.crystal-limiter.max-anchors-per-second", 8);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalPlace(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sentinel.exempt")) return;

        // Prevent dual-hand duplicate event processing when main hand is holding an item
        if (event.getHand() == EquipmentSlot.OFF_HAND && player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }

        if (event.getItem() != null && event.getItem().getType() == Material.END_CRYSTAL) {
            WindowTracker tracker = crystalPlaceTrackers.computeIfAbsent(player.getUniqueId(), k -> new WindowTracker());
            int count = tracker.increment();

            if (count > maxCrystalsPerSecond) {
                event.setCancelled(true);
                flag(player, "Kristal qo'yish tezligi: " + count + "/s (Limit: " + maxCrystalsPerSecond + "/s)");
            }
        } else if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.RESPAWN_ANCHOR) {
            WindowTracker tracker = anchorTrackers.computeIfAbsent(player.getUniqueId(), k -> new WindowTracker());
            int count = tracker.increment();

            if (count > maxAnchorsPerSecond) {
                event.setCancelled(true);
                flag(player, "Anchor harakat tezligi: " + count + "/s (Limit: " + maxAnchorsPerSecond + "/s)");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalBreak(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        if (event.getEntityType() != EntityType.END_CRYSTAL) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sentinel.exempt")) return;

        WindowTracker tracker = crystalBreakTrackers.computeIfAbsent(player.getUniqueId(), k -> new WindowTracker());
        int count = tracker.increment();

        if (count > maxBreaksPerSecond) {
            event.setCancelled(true);
            flag(player, "Kristal portlatish tezligi: " + count + "/s (Limit: " + maxBreaksPerSecond + "/s)");
        }
    }

    @Override
    public void onQuit(UUID uuid) {
        super.onQuit(uuid);
        crystalPlaceTrackers.remove(uuid);
        crystalBreakTrackers.remove(uuid);
        anchorTrackers.remove(uuid);
    }

    private static class WindowTracker {
        private long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        public synchronized int increment() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 1000L) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet();
        }
    }
}
