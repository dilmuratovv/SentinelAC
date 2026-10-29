package uz.sentinel.engine.command.commands;

import uz.sentinel.engine.SentinelAPI;
import uz.sentinel.engine.command.BuildableCommand;
import uz.sentinel.engine.platform.api.manager.cloud.CloudPlatformCommandArguments;
import uz.sentinel.engine.platform.api.sender.Sender;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;

/** Local build information; release availability is never guessed. */
public class SentinelVersion implements BuildableCommand {
    public static void checkForUpdatesAsync(Sender sender) {
        sender.sendMessage(Component.text("SentinelAC " + SentinelAPI.INSTANCE.getExternalAPI().getSentinelVersion()));
        sender.sendMessage(Component.text("Releases: https://github.com/dilmuratovv/SentinelAC"));
    }
    @Override public void register(CommandManager<Sender> manager, CloudPlatformCommandArguments arguments) {
        manager.command(manager.commandBuilder("sac", "sentinel", "sentinelac")
                .literal("version").permission("sentinel.version")
                .handler(context -> checkForUpdatesAsync(context.sender())));
    }
}
