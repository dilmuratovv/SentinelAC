package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.HitTiming;

import java.util.Locale;

/**
 * Machine-perfect hit timing at 0.7-5 hits per second: cooldown-paced auras (Meteor and LiquidBounce kill aura),
 * trigger bots and slow autoclickers hit after the same number of client ticks almost every time, people do not.
 * See {@link HitTiming} for the measured numbers and thresholds. Needs reliable tick packets.
 */
@CheckData(name = "KillAuraC", stableKey = "sentinel.killaura.timing",
        description = "Hit with machine-perfect timing", decay = 0.01, setback = -1)
public class KillAuraC extends Check implements PacketReceiveListener {
    private double minShare; // set by onReload

    private final HitTiming timing = new HitTiming(20);
    private long tick;
    private long lastAttackTick = -1;

    public KillAuraC(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minShare = SentinelCombat.clamp(config.getDoubleElse(getConfigName() + ".min-same-interval", 0.8), 0.5, 1.0);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacket(event.getPacketType())) {
            tick++;
            return;
        }
        if (SentinelCombat.attackTarget(event) < 0 || SentinelCombat.exempt(player) || player.canSkipTicks()) return;
        long previous = lastAttackTick;
        lastAttackTick = tick;
        if (previous < 0) return;
        HitTiming.Result result = timing.add(tick - previous, minShare);
        if (result != null && result.regular()) {
            flag(String.format(Locale.ROOT, "same=%.0f%% interval=%dt", result.share() * 100, result.mode()));
        }
    }
}
