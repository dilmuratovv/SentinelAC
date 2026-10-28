package uz.sentinel.ac.checks.inventory;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.checks.Check;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoTotemCheck extends Check {
    private final Map<UUID, Long> lastDamageMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPopMillis = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private long minSwapDelayMs = 90;
    private boolean preventSwapWhileMoving = true;
    private boolean cancelIllegalSwaps = true;

    public AutoTotemCheck(Plugin plugin, SentinelBridge bridge) {
        super(plugin, bridge, "AutoTotem", "0-tick va yopiq inventardan noqonuniy totem almashish");
        reload();
    }

    @Override
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("inventory.autototem.enabled", true);
        this.minSwapDelayMs = plugin.getConfig().getLong("inventory.autototem.min-swap-delay-ms", 90);
        this.preventSwapWhileMoving = plugin.getConfig().getBoolean("inventory.autototem.prevent-swap-while-moving", true);
        this.cancelIllegalSwaps = plugin.getConfig().getBoolean("inventory.autototem.cancel-illegal-swaps", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastDamageMillis.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastPopMillis.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sentinel.exempt")) return;

        ItemStack offHandItem = event.getOffHandItem();
        ItemStack mainHandItem = event.getMainHandItem();

        boolean totemInvolved = (offHandItem != null && offHandItem.getType() == Material.TOTEM_OF_UNDYING) ||
                                (mainHandItem != null && mainHandItem.getType() == Material.TOTEM_OF_UNDYING);

        if (!totemInvolved) {
            return;
        }

        long now = System.currentTimeMillis();
        long damageDelta = now - lastDamageMillis.getOrDefault(player.getUniqueId(), 0L);
        long popDelta = now - lastPopMillis.getOrDefault(player.getUniqueId(), 0L);

        // Check 1: 0-tick swap immediately after totem pop (< minSwapDelayMs)
        if (popDelta >= 0 && popDelta < minSwapDelayMs) {
            if (cancelIllegalSwaps) {
                event.setCancelled(true);
            }
            flag(player, "0-tick totem pop almashish (Reaksiya: " + popDelta + "ms)");
            return;
        }

        // Check 2: 0-tick swap immediately after critical damage (< minSwapDelayMs, health <= 10.0)
        if (damageDelta >= 0 && damageDelta < minSwapDelayMs && player.getHealth() <= 10.0) {
            if (cancelIllegalSwaps) {
                event.setCancelled(true);
            }
            flag(player, "0-tick kritik zarbadan so'ng totem almashish (Reaksiya: " + damageDelta + "ms)");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sentinel.exempt")) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        boolean isTotem = (cursor != null && cursor.getType() == Material.TOTEM_OF_UNDYING) ||
                          (current != null && current.getType() == Material.TOTEM_OF_UNDYING);

        if (!isTotem) return;

        if (event.getSlot() == 40 || event.getRawSlot() == 45 || event.getClick() == ClickType.SWAP_OFFHAND) {
            long now = System.currentTimeMillis();
            long damageDelta = now - lastDamageMillis.getOrDefault(player.getUniqueId(), 0L);
            long popDelta = now - lastPopMillis.getOrDefault(player.getUniqueId(), 0L);

            if ((damageDelta >= 0 && damageDelta < minSwapDelayMs) || (popDelta >= 0 && popDelta < minSwapDelayMs)) {
                if (cancelIllegalSwaps) {
                    event.setCancelled(true);
                }
                flag(player, "Inventar orqali g'ayritabiiy tezkor totem joylash (" + Math.min(damageDelta, popDelta) + "ms)");
                return;
            }

            if (preventSwapWhileMoving && (player.isSprinting() || player.getVelocity().lengthSquared() > 0.18)) {
                if (event.getView().getType() == InventoryType.CRAFTING || event.getView().getType() == InventoryType.CHEST) {
                    if (cancelIllegalSwaps) {
                        event.setCancelled(true);
                    }
                    flag(player, "Yugurib ketayotganda inventar GUI'siz totem o'tkazish");
                }
            }
        }
    }

    @Override
    public void onQuit(UUID uuid) {
        super.onQuit(uuid);
        lastDamageMillis.remove(uuid);
        lastPopMillis.remove(uuid);
    }
}
