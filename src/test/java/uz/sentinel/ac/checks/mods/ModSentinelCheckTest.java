package uz.sentinel.ac.checks.mods;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.config.SentinelSettings;
import uz.sentinel.ac.config.SentinelSettingsTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModSentinelCheckTest {
    @Test void existingJoinChannelsAreCheckedAndDuplicateSignalsScheduleOnlyOneKick() {
        var plugin = mock(SentinelPlugin.class);
        var bridge = mock(SentinelBridge.class);
        when(plugin.settings()).thenReturn(SentinelSettings.read(SentinelSettingsTest.config()));
        when(plugin.bridge()).thenReturn(bridge);
        Player player = player();
        when(player.getListeningPluginChannels()).thenReturn(Set.of("baritone:channel", "baritone:main"));
        List<Runnable> tasks = new ArrayList<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var check = new ModSentinelCheck(plugin);
            check.inspectExisting(player);
            verify(player, never()).kickPlayer(anyString());
            org.junit.jupiter.api.Assertions.assertEquals(1, tasks.size());
            tasks.getFirst().run();
            verify(player).kickPlayer(contains("baritone:"));
            verify(bridge, times(1)).offer(any());
        }
    }
    @Test void pendingKickRechecksNewPolicyAndExemptPermission() {
        var plugin = mock(SentinelPlugin.class);
        when(plugin.settings()).thenReturn(SentinelSettings.read(SentinelSettingsTest.config()));
        when(plugin.bridge()).thenReturn(mock(SentinelBridge.class));
        Player player = player();
        List<Runnable> tasks = new ArrayList<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var check = new ModSentinelCheck(plugin);
            check.inspect(player, "freecam:sync");
            when(player.hasPermission("sentinel.exempt")).thenReturn(true);
            tasks.getFirst().run();
            verify(player, never()).kickPlayer(anyString());
        }
    }
    @Test void unrelatedNamespaceAndExemptPlayersAreNeverFlagged() {
        var plugin = mock(SentinelPlugin.class);
        when(plugin.settings()).thenReturn(SentinelSettings.read(SentinelSettingsTest.config()));
        var bridge = mock(SentinelBridge.class); when(plugin.bridge()).thenReturn(bridge);
        var check = new ModSentinelCheck(plugin); Player player = player();
        check.inspect(player, "notbaritone:main");
        when(player.hasPermission("grim.exempt")).thenReturn(true);
        check.inspect(player, "baritone:main");
        verifyNoInteractions(bridge);
    }
    private static Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID()); when(p.getName()).thenReturn("Player");
        when(p.isOnline()).thenReturn(true); return p;
    }
}
