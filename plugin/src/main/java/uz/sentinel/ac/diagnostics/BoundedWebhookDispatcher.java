package uz.sentinel.ac.diagnostics;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** One in-flight delivery, finite queue, finite lifetime and at most three attempts. */
public final class BoundedWebhookDispatcher implements AutoCloseable {
    private record Pending(HttpRequest request, CompletableFuture<Boolean> result, long expires) {
        private Pending(HttpRequest request, long expires) { this(request, new CompletableFuture<>(), expires); }
    }
    private final ArrayBlockingQueue<Pending> queue;
    private final HttpClient client;
    private final LongSupplier clock;
    private final AtomicBoolean sending = new AtomicBoolean();
    private volatile boolean closed;
    private volatile long retryAt;
    private int attempts;

    public BoundedWebhookDispatcher(HttpClient client, int capacity) { this(client, capacity, System::currentTimeMillis); }
    public BoundedWebhookDispatcher(HttpClient client, int capacity, LongSupplier clock) {
        this.client = client; this.clock = clock; queue = new ArrayBlockingQueue<>(capacity);
    }
    public synchronized CompletableFuture<Boolean> submit(HttpRequest request) {
        Pending item = new Pending(request, clock.getAsLong() + 60_000);
        if (closed || !queue.offer(item)) item.result.complete(false);
        return item.result;
    }
    public int queued() { return queue.size(); }
    public void tick() {
        if (closed || !sending.compareAndSet(false, true)) return;
        Pending item = queue.peek();
        if (item == null) { sending.set(false); return; }
        long now = clock.getAsLong();
        if (item.expires <= now) { finish(item, false); sending.set(false); return; }
        if (now < retryAt) { sending.set(false); return; }
        attempts++;
        try {
            client.sendAsync(item.request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, failure) -> {
                try {
                    if (failure != null || response == null) finish(item, false);
                    else if (response.statusCode() == 429 && attempts < 3 && !closed) {
                        retryAt = clock.getAsLong() + retryMillis(response);
                    } else finish(item, response.statusCode() >= 200 && response.statusCode() < 300);
                } finally { sending.set(false); }
            });
        } catch (RuntimeException failure) { finish(item, false); sending.set(false); }
    }
    private void finish(Pending item, boolean success) {
        queue.remove(item); attempts = 0; retryAt = 0; item.result.complete(success);
    }
    private static long retryMillis(HttpResponse<?> response) {
        try {
            double seconds = Double.parseDouble(response.headers().firstValue("Retry-After").orElse("1"));
            if (!Double.isFinite(seconds)) return 1000;
            return (long) Math.max(1000, Math.min(60_000, seconds * 1000));
        } catch (NumberFormatException ignored) { return 1000; }
    }
    @Override public synchronized void close() {
        closed = true;
        Pending item;
        while ((item = queue.poll()) != null) item.result.complete(false);
    }
}
