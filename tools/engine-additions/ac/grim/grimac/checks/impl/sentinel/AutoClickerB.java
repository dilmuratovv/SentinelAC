package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.ClickConsistency;

import java.util.Locale;

/**
 * Machine-regular clicking. Looks at the client-tick intervals between attacks in windows of 40 and flags when two
 * windows in a row are both fast (mean of at most 3 ticks, about 7+ CPS) and almost entirely one interval. Needs
 * reliable tick packets, so clients that may skip ticks are not judged.
 */
@CheckData(name = "AutoClickerB", stableKey = "sentinel.autoclicker.consistency",
        description = "Clicked with machine-like regular timing", decay = 0.01, setback = -1)
public class AutoClickerB extends Check implements PacketReceiveListener {
    private double minModeShare; // set by onReload
    private int window;

    private ClickConsistency consistency;
    private long tick;
    private long lastAttackTick = -1;
    private int roboticWindows;

    public AutoClickerB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minModeShare = SentinelCombat.clamp(config.getDoubleElse(getConfigName() + ".min-same-interval", 0.95), 0.85, 1.0);
        int newWindow = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".window", 40), 20, 200);
        if (newWindow != window) consistency = null;
        window = newWindow;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacket(event.getPacketType())) {
            tick++;
            return;
        }
        if (SentinelCombat.attackTarget(event) < 0 || SentinelCombat.exempt(player) || player.canSkipTicks()) return;

        if (consistency == null) consistency = new ClickConsistency(window);
        long previous = lastAttackTick;
        lastAttackTick = tick;
        if (previous < 0) return;

        ClickConsistency.Result result = consistency.add((int) Math.min(Integer.MAX_VALUE, tick - previous));
        if (result == null) return;
        if (!result.robotic(minModeShare, 3.0)) {
            roboticWindows = 0;
            return;
        }
        if (++roboticWindows >= 2) {
            flag(String.format(Locale.ROOT, "same=%.0f%% interval=%dt mean=%.2ft sd=%.2f",
                    result.modeShare() * 100, result.mode(), result.mean(), result.deviation()));
        }
    }
}
