package uz.sentinel.ac.integration;

import ac.grim.grimac.api.*;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.event.ListenerPriority;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.plugin.GrimPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.diagnostics.Finding;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrimEventBridgeTest {
    @Test void observerPreservesCancellationAndDoesNotFailWhenVerboseCannotBeRead() {
        var api = mock(GrimAbstractAPI.class); var bus = mock(EventBus.class);
        var channel = mock(FlagEvent.Channel.class); var owner = mock(GrimPlugin.class);
        var plugin = mock(SentinelPlugin.class);
        when(api.hasStarted()).thenReturn(true); when(api.getConfigManager()).thenReturn(mock(ConfigManager.class));
        when(api.getGrimVersion()).thenReturn("2.3.74"); when(api.getGrimPlugin(plugin)).thenReturn(owner);
        when(api.getEventBus()).thenReturn(bus); when(bus.get(FlagEvent.class)).thenReturn(channel);
        var findings = new ArrayList<Finding>();
        try (var provider = mockStatic(GrimAPIProvider.class)) {
            provider.when(GrimAPIProvider::get).thenReturn(api);
            var integration = new GrimIntegration(plugin, findings::add);
            var capture = ArgumentCaptor.forClass(FlagEvent.SupplierHandler.class);
            verify(channel).onFlagSupplier(eq(owner), capture.capture(), eq(ListenerPriority.MONITOR), eq(true));
            var handler = capture.getValue();
            var user = mock(GrimUser.class); var check = mock(AbstractCheck.class);
            when(user.getUniqueId()).thenReturn(UUID.randomUUID()); when(user.getName()).thenReturn("Player");
            when(check.getCheckName()).thenReturn("Reach");
            assertTrue(handler.onFlag(user, check, () -> "cancelled", true));
            assertTrue(findings.isEmpty());
            assertFalse(handler.onFlag(user, check, () -> { throw new IllegalStateException(); }, false));
            assertEquals("[verbose unavailable]", findings.getFirst().detail());
            integration.close();
            verify(bus).unregisterAllListeners(owner);
        }
    }
    @Test void incompleteEngineStartupDoesNotReportSentinelReady() {
        var api = mock(GrimAbstractAPI.class);
        try (var provider = mockStatic(GrimAPIProvider.class)) {
            provider.when(GrimAPIProvider::get).thenReturn(api);
            assertThrows(IllegalStateException.class, () -> new GrimIntegration(mock(SentinelPlugin.class), f -> {}));
        }
    }
}
