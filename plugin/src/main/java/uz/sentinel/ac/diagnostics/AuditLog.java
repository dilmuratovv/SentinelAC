package uz.sentinel.ac.diagnostics;

import uz.sentinel.ac.config.SentinelSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Level;

/** Bounded asynchronous disk writer, with size/count-based rotation. No network upload. */
public final class AuditLog implements AutoCloseable {
    private final ThreadPoolExecutor writer;
    private final FileHandler handler;
    private final AtomicLong dropped = new AtomicLong();

    public AuditLog(Path folder, SentinelSettings settings) throws IOException {
        Files.createDirectories(folder);
        handler = new FileHandler(folder.resolve("violations.%g.log").toString(), settings.logFileBytes(), settings.logFiles(), true);
        handler.setFormatter(new java.util.logging.Formatter() {
            @Override public String format(LogRecord r) { return r.getMessage() + System.lineSeparator(); }
        });
        writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1024), r -> {
            Thread t = new Thread(r, "Sentinel-Audit"); t.setDaemon(true); return t;
        }, (r, e) -> dropped.incrementAndGet());
    }

    public void record(Finding f) {
        writer.execute(() -> handler.publish(new LogRecord(Level.INFO, f.timeMillis() + "\t" + f.playerId()
                + "\t" + f.playerName() + "\t" + f.check() + "\t" + f.detail())));
    }

    public long dropped() { return dropped.get(); }

    @Override public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException e) { writer.shutdownNow(); Thread.currentThread().interrupt(); }
        handler.close();
    }
}
