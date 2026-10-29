package uz.sentinel.ac.checks.combat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.config.SentinelSettings;
import uz.sentinel.ac.config.SentinelSettingsTest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CrystalLimiterCheckTest {
    @Test void bothPlacementHandsShareOneBudgetAndQuitClearsState() {
        var plugin = plugin(); var check = new CrystalLimiterCheck(plugin, () -> 0);
        var player = player();
        EntityPlaceEvent first = place(player), second = place(player);
        check.onCrystalPlace(first); check.onCrystalPlace(second);
        verify(first, never()).setCancelled(true); verify(second).setCancelled(true);
        check.onQuit(player.getUniqueId());
        var afterQuit = place(player); check.onCrystalPlace(afterQuit);
        verify(afterQuit, never()).setCancelled(true);
    }
    @Test void duplicateOffhandCallbackCannotUndoAnchorDenial() {
        var plugin = plugin(); var check = new CrystalLimiterCheck(plugin, () -> 0);
        var player = player(); World world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        Block block = mock(Block.class); when(block.getWorld()).thenReturn(world);
        RespawnAnchor anchor = mock(RespawnAnchor.class); when(anchor.getCharges()).thenReturn(4);
        when(block.getBlockData()).thenReturn(anchor);
        AtomicInteger tick = new AtomicInteger(1);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(call -> tick.get());
            check.onAnchorInteract(interaction(player, block));
            tick.incrementAndGet();
            var mainHand = interaction(player, block); var offHand = interaction(player, block);
            check.onAnchorInteract(mainHand); check.onAnchorInteract(offHand);
            verify(mainHand).setUseInteractedBlock(Event.Result.DENY);
            verify(offHand).setUseInteractedBlock(Event.Result.DENY);
        }
    }
    private static SentinelPlugin plugin() {
        var config = SentinelSettingsTest.config();
        config.set("exploits.crystal-limiter.max-crystals-per-second", 1);
        config.set("exploits.crystal-limiter.max-anchors-per-second", 1);
        SentinelPlugin plugin = mock(SentinelPlugin.class);
        when(plugin.settings()).thenReturn(SentinelSettings.read(config));
        when(plugin.bridge()).thenReturn(mock(SentinelBridge.class)); return plugin;
    }
    private static Player player() {
        var p = mock(Player.class); when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getName()).thenReturn("Player"); when(p.getGameMode()).thenReturn(GameMode.SURVIVAL); return p;
    }
    private static EntityPlaceEvent place(Player p) {
        var e = mock(EntityPlaceEvent.class); var crystal = mock(Entity.class);
        when(crystal.getType()).thenReturn(EntityType.END_CRYSTAL);
        when(e.getPlayer()).thenReturn(p); when(e.getEntity()).thenReturn(crystal); return e;
    }
    private static PlayerInteractEvent interaction(Player p, Block block) {
        var e = mock(PlayerInteractEvent.class); when(e.getPlayer()).thenReturn(p);
        when(e.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK); when(e.getClickedBlock()).thenReturn(block);
        when(e.useInteractedBlock()).thenReturn(Event.Result.DEFAULT); return e;
    }
}
