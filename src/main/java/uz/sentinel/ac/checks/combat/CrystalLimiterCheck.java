package uz.sentinel.ac.checks.combat;

import org.bukkit.Material;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.checks.Check;
import uz.sentinel.ac.policy.SlidingWindow;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Explicit server rate policy. A limit hit cancels an action; it never auto-bans. */
public final class CrystalLimiterCheck extends Check {
    private enum Kind { PLACE, BREAK, ANCHOR }
    private final Map<UUID, EnumMap<Kind, SlidingWindow>> windows = new HashMap<>();
    private final LongSupplier clock;
    public CrystalLimiterCheck(SentinelPlugin plugin) { this(plugin, System::nanoTime); }
    CrystalLimiterCheck(SentinelPlugin plugin, LongSupplier clock) { super(plugin); this.clock = clock; }

    private boolean allowed(Player player, Kind kind, int limit) {
        SlidingWindow window = windows.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(Kind.class))
                .computeIfAbsent(kind, k -> new SlidingWindow(limit, 1_000_000_000L, clock));
        if (window.tryAcquire()) return true;
        flag(player, "RatePolicy/" + kind, "Server limit: " + limit + "/s; action cancelled");
        return false;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.settings().limiterEnabled() || player == null || gameplayExempt(player)
                || event.getEntity().getType() != EntityType.END_CRYSTAL) return;
        // EntityPlaceEvent represents the placement, regardless of the originating hand.
        if (!allowed(player, Kind.PLACE, plugin.settings().placements())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalBreak(EntityDamageByEntityEvent event) {
        if (!plugin.settings().limiterEnabled() || event.getEntityType() != EntityType.END_CRYSTAL) return;
        Player player = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
        if (player == null || gameplayExempt(player)) return;
        if (!allowed(player, Kind.BREAK, plugin.settings().breaks())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnchorInteract(PlayerInteractEvent event) {
        if (!plugin.settings().limiterEnabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == Event.Result.DENY || event.getClickedBlock() == null
                || !(event.getClickedBlock().getBlockData() instanceof RespawnAnchor anchor)) return;
        Player player = event.getPlayer();
        if (gameplayExempt(player) || player.getWorld().isRespawnAnchorWorks() || anchor.getCharges() == 0) return;
        if (event.getItem() != null && event.getItem().getType() == Material.GLOWSTONE && anchor.getCharges() < anchor.getMaximumCharges()) return;
        // A successful use consumes the charged anchor. Do not grant an uncounted
        // second action merely because a client changes hands or reuses the same block/tick.
        if (!allowed(player, Kind.ANCHOR, plugin.settings().anchors())) event.setUseInteractedBlock(Event.Result.DENY);
    }
    @Override public void onQuit(UUID uuid) { windows.remove(uuid); }
    @Override public void reload() { windows.clear(); }
    @Override public void close() { reload(); }
}
