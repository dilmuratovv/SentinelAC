package uz.sentinel.ac.diagnostics;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BoundedWebhookDispatcherTest {
    private final HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.invalid/")).build();
    @Test void floodIsBoundedAndFailureDoesNotBlockFollowingAlerts() {
        var client = mock(HttpClient.class);
        when(client.sendAsync(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        var dispatcher = new BoundedWebhookDispatcher(client, 2);
        var first = dispatcher.submit(request); var second = dispatcher.submit(request);
        for (int i = 0; i < 1000; i++) assertFalse(dispatcher.submit(request).join());
        assertEquals(2, dispatcher.queued());
        dispatcher.tick(); assertFalse(first.join()); assertFalse(second.isDone());
        dispatcher.tick(); assertFalse(second.join()); assertEquals(0, dispatcher.queued());
    }
    @Test void repeatedRateLimitsHaveFiniteAttemptsEvenWithoutHeaders() {
        var client = mock(HttpClient.class); var clock = new AtomicLong(1000);
        var response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(429);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        when(client.sendAsync(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        var dispatcher = new BoundedWebhookDispatcher(client, 2, clock::get);
        var first = dispatcher.submit(request);
        for (int i = 0; i < 3; i++) { dispatcher.tick(); clock.addAndGet(1001); }
        assertFalse(first.join()); assertEquals(0, dispatcher.queued());
        verify(client, times(3)).sendAsync(any(), any(HttpResponse.BodyHandler.class));
    }
    @Test void onlyOneDeliveryIsInFlightAndShutdownCompletesEveryFuture() {
        var client = mock(HttpClient.class); var delivery = new CompletableFuture<HttpResponse<Void>>();
        when(client.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(delivery);
        var dispatcher = new BoundedWebhookDispatcher(client, 2);
        var first = dispatcher.submit(request); var second = dispatcher.submit(request);
        for (int i = 0; i < 20; i++) dispatcher.tick();
        verify(client, times(1)).sendAsync(any(), any(HttpResponse.BodyHandler.class));
        dispatcher.close(); assertFalse(first.join()); assertFalse(second.join());
        assertFalse(dispatcher.submit(request).join());
    }
    @Test void staleQueuedRequestsExpireWithoutNetworkWork() {
        var client = mock(HttpClient.class); var clock = new AtomicLong(1000);
        var dispatcher = new BoundedWebhookDispatcher(client, 2, clock::get);
        var result = dispatcher.submit(request); clock.addAndGet(60_001); dispatcher.tick();
        assertFalse(result.join()); verifyNoInteractions(client);
    }
}
