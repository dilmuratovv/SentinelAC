package uz.sentinel.engine.command.commands;

import uz.sentinel.engine.SentinelAPI;
import uz.sentinel.engine.command.BuildableCommand;
import uz.sentinel.engine.manager.init.start.SuperDebug;
import uz.sentinel.engine.platform.api.manager.cloud.CloudPlatformCommandArguments;
import uz.sentinel.engine.platform.api.sender.Sender;
import uz.sentinel.engine.utils.anticheat.LogUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.parser.standard.IntegerParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

/** Diagnostics stay on the server and have a fixed retention and byte limit. */
public class SentinelLog implements BuildableCommand {
    public static void sendLogAsync(Sender sender, String log, Consumer<String> consumer, String type) {
        sender.sendMessage("SentinelAC: saving diagnostic...");
        SentinelAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(SentinelAPI.INSTANCE.getSentinelPlugin(), () -> {
            try {
                Path path = save(log);
                String message = "SentinelAC: diagnostic saved to " + path;
                consumer.accept(message);
                sender.sendMessage(message);
            } catch (Exception exception) {
                sender.sendMessage("SentinelAC: diagnostic could not be saved; see console.");
                LogUtil.error("Could not save local diagnostic", exception);
            }
        });
    }
    private static synchronized Path save(String log) throws Exception {
        byte[] bytes = log.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("Diagnostic exceeds 2 MiB");
        Path folder = uz.sentinel.engine.platform.bukkit.SentinelACBukkitLoaderPlugin.LOADER
                .getDataFolder().toPath().resolve("diagnostics");
        Files.createDirectories(folder);
        try (var files = Files.list(folder)) {
            var existing = files.filter(path -> path.getFileName().toString().startsWith("diagnostic-")
                    && path.getFileName().toString().endsWith(".txt")).sorted(Comparator.comparing(Path::toString)).toList();
            for (int i = 0; i < existing.size() - 19; i++) Files.deleteIfExists(existing.get(i));
        }
        Path path = Files.createTempFile(folder, "diagnostic-" + System.currentTimeMillis() + "-", ".txt");
        Files.write(path, bytes);
        return path;
    }
    @Override public void register(CommandManager<Sender> manager, CloudPlatformCommandArguments arguments) {
        manager.command(manager.commandBuilder("sac", "sentinel", "sentinelac")
                .literal("log", "logs").permission("sentinel.log")
                .required("flagId", IntegerParser.integerParser()).handler(context -> {
                    StringBuilder log = SuperDebug.getFlag(context.get("flagId"));
                    if (log == null) context.sender().sendMessage("SentinelAC: diagnostic not found.");
                    else sendLogAsync(context.sender(), log.toString(), ignored -> {}, "text/plain");
                }));
    }
}
