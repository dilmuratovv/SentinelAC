package uz.sentinel.ac.integration;

import uz.sentinel.engine.api.*;
import uz.sentinel.engine.api.config.ConfigManager;
import uz.sentinel.engine.api.event.EventBus;
import uz.sentinel.engine.api.event.ListenerPriority;
import uz.sentinel.engine.api.event.events.FlagEvent;
import uz.sentinel.engine.api.plugin.EnginePlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.diagnostics.Finding;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineEventBridgeTest {
    @Test void observerPreservesCancellationAndDoesNotFailWhenVerboseCannotBeRead() {
        var api = mock(SentinelAbstractAPI.class); var bus = mock(EventBus.class);
        var channel = mock(FlagEvent.Channel.class); var owner = mock(EnginePlugin.class);
        var plugin = mock(SentinelPlugin.class);
        when(api.hasStarted()).thenReturn(true); when(api.getConfigManager()).thenReturn(mock(ConfigManager.class));
        when(api.getSentinelVersion()).thenReturn("2.3.74"); when(api.getSentinelPlugin(plugin)).thenReturn(owner);
        when(api.getEventBus()).thenReturn(bus); when(bus.get(FlagEvent.class)).thenReturn(channel);
        var findings = new ArrayList<Finding>();
        try (var provider = mockStatic(SentinelAPIProvider.class)) {
            provider.when(SentinelAPIProvider::get).thenReturn(api);
            var integration = new EngineIntegration(plugin, findings::add);
            var capture = ArgumentCaptor.forClass(FlagEvent.SupplierHandler.class);
            verify(channel).onFlagSupplier(eq(owner), capture.capture(), eq(ListenerPriority.MONITOR), eq(true));
            var handler = capture.getValue();
            var user = mock(SentinelUser.class); var check = mock(AbstractCheck.class);
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
        var api = mock(SentinelAbstractAPI.class);
        try (var provider = mockStatic(SentinelAPIProvider.class)) {
            provider.when(SentinelAPIProvider::get).thenReturn(api);
            assertThrows(IllegalStateException.class, () -> new EngineIntegration(mock(SentinelPlugin.class), f -> {}));
        }
    }
}
