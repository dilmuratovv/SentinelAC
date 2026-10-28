package uz.sentinel.ac.checks.food;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.checks.Check;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FoodCheck extends Check {
    private final Map<UUID, Long> eatStartMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Double> sprintDistance = new ConcurrentHashMap<>();

    private boolean fastEatEnabled = true;
    private int minConsumeTicks = 27;
    private boolean noHungerEnabled = true;
    private boolean enforceExhaustion = true;

    public FoodCheck(Plugin plugin, SentinelBridge bridge) {
        super(plugin, bridge, "FastEat", "Ovqatlanish va ochlik me'yorini noqonuniy tezlashtirish");
        reload();
    }

    @Override
    public void reload() {
        this.fastEatEnabled = plugin.getConfig().getBoolean("food.fast-eat.enabled", true);
        this.minConsumeTicks = plugin.getConfig().getInt("food.fast-eat.min-consume-ticks", 27);
        this.noHungerEnabled = plugin.getConfig().getBoolean("food.no-hunger.enabled", true);
        this.enforceExhaustion = plugin.getConfig().getBoolean("food.no-hunger.enforce-server-exhaustion", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (!fastEatEnabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sentinel.exempt")) return;

        ItemStack item = event.getItem();
        if (item != null && (item.getType().isEdible() || item.getType() == Material.POTION || item.getType() == Material.MILK_BUCKET || item.getType() == Material.HONEY_BOTTLE)) {
            eatStartMillis.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!fastEatEnabled) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sentinel.exempt")) return;

        UUID uuid = player.getUniqueId();
        Long start = eatStartMillis.remove(uuid);

        if (start != null) {
            long duration = System.currentTimeMillis() - start;
            if (duration > 6000L) {
                // Stale interaction (player paused or switched items)
                return;
            }

            long expectedMinMs = (minConsumeTicks * 50L) - 100L;
            if (event.getItem().getType() == Material.DRIED_KELP) {
                expectedMinMs = (14 * 50L) - 100L;
            }

            if (duration < expectedMinMs) {
                event.setCancelled(true);
                flag(player, "G'ayritabiiy tez ovqatlanish: " + duration + "ms (Kutilgan: " + expectedMinMs + "ms+)");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!noHungerEnabled) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sentinel.exempt")) return;

        // Skip exhaustion accumulation during gliding (Elytra), vehicle riding, flying, swimming, or trident riptiding
        if (!player.isSprinting() || player.isInsideVehicle() || player.isFlying() || player.isSwimming() || player.isGliding() || player.isRiptiding()) {
            sprintDistance.remove(player.getUniqueId());
            return;
        }

        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double currentTotal = sprintDistance.merge(player.getUniqueId(), distance, Double::sum);

        if (currentTotal > 70.0) {
            sprintDistance.put(player.getUniqueId(), 0.0);
            if (enforceExhaustion) {
                player.setExhaustion(player.getExhaustion() + 3.0f);
            }
        }
    }

    @Override
    public void onQuit(UUID uuid) {
        super.onQuit(uuid);
        eatStartMillis.remove(uuid);
        sprintDistance.remove(uuid);
    }
}
