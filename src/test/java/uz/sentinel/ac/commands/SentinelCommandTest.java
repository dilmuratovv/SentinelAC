package uz.sentinel.ac.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.config.SentinelSettings;
import uz.sentinel.ac.config.SentinelSettingsTest;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SentinelCommandTest {
    @Test void unauthorizedUsersCannotReloadOrReadOtherPlayerData() {
        var plugin = mock(SentinelPlugin.class);
        when(plugin.settings()).thenReturn(SentinelSettings.read(SentinelSettingsTest.config()));
        var command = new SentinelCommand(plugin);
        var sender = mock(CommandSender.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            for (String sub : new String[]{"reload", "profile", "channels", "history", "perf"}) {
                command.onCommand(sender, mock(Command.class), "sentinel", new String[]{sub, "Player"});
            }
            verify(plugin, never()).reloadSettings();
            verify(plugin, never()).grim();
            verify(plugin, never()).bridge();
            bukkit.verifyNoInteractions();
        }
    }
    @Test void completionsDoNotRevealUnauthorizedCommands() {
        var plugin = mock(SentinelPlugin.class);
        var command = new SentinelCommand(plugin);
        var sender = mock(CommandSender.class);
        assertTrue(command.onTabComplete(sender, mock(Command.class), "sentinel", new String[]{""}).isEmpty());
        when(sender.hasPermission("sentinel.alerts")).thenReturn(true);
        assertEquals(java.util.List.of("alerts"), command.onTabComplete(sender, mock(Command.class), "sentinel", new String[]{""}));
    }
}
