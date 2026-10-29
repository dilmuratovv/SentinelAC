package uz.sentinel.ac.commands;

import org.bukkit.command.CommandSender;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.engine.platform.api.sender.Sender;
import uz.sentinel.engine.platform.bukkit.SentinelACBukkitLoaderPlugin;
import uz.sentinel.engine.libs.incendo.cloud.CommandManager;
import uz.sentinel.engine.libs.incendo.cloud.parser.standard.StringParser;

/** Adds policy tools to the same command tree as the prediction engine. */
public final class SentinelManagementCommand {
    private SentinelManagementCommand() {}
    public static void register(CommandManager<Sender> manager) {
        for (String name : new String[]{"info", "channels", "recent"}) {
            var builder = manager.commandBuilder("sac", "sentinel", "sentinelac")
                    .literal(name).permission("sentinel." + switch (name) {
                        case "channels" -> "profile"; case "recent" -> "history"; default -> "info";
                    });
            if (!name.equals("info")) builder = builder.required("player", StringParser.stringParser());
            manager.command(builder.handler(context -> {
                if (context.sender().getNativeSender() instanceof CommandSender sender) {
                    var plugin = (SentinelPlugin) SentinelACBukkitLoaderPlugin.LOADER;
                    new SentinelCommand(plugin).onCommand(sender, null, "sac",
                            name.equals("info") ? new String[]{name} : new String[]{name, context.get("player")});
                }
            }));
        }
    }
}
