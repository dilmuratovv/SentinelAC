package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.TickWindow;
import uz.sentinel.ac.combat.TimeWindow;

/**
 * Attack rate limit. Counts attacks per second of client ticks (wall time only for clients that may skip tick
 * packets). Attacks above the limit are always cancelled, because no player gains anything legitimate from them;
 * the check flags once the limit was exceeded in two different seconds within ten seconds, so one lucky burst of a
 * fast butterfly clicker is never reported.
 */
@CheckData(name = "AutoClickerA", stableKey = "sentinel.autoclicker.rate",
        description = "Attacked more often per second than the configured limit", decay = 0.02, setback = -1)
public class AutoClickerA extends Check implements PacketReceiveListener {
    private int maxCps; // set by onReload, which runs before field initialisers: no initialiser here

    private final TickWindow ticks = new TickWindow(20);
    private final TimeWindow wallClock = new TimeWindow(1_000_000_000L, 1000);
    private final TimeWindow exceededSeconds = new TimeWindow(10_000L * SentinelCombat.MILLIS, 16);
    private long lastExceeded = Long.MIN_VALUE;

    public AutoClickerA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxCps = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".max-cps", 20), 8, 100);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacket(event.getPacketType())) {
            ticks.tick();
            return;
        }
        if (SentinelCombat.attackTarget(event) < 0 || SentinelCombat.exempt(player)) return;

        long now = System.nanoTime();
        int rate = player.canSkipTicks() ? wallClock.add(now) : ticks.add();
        if (rate <= maxCps) return;

        int seconds;
        if (lastExceeded == Long.MIN_VALUE || now - lastExceeded >= 1_000L * SentinelCombat.MILLIS) {
            lastExceeded = now;
            seconds = exceededSeconds.add(now);
        } else {
            seconds = exceededSeconds.count(now);
        }
        if (seconds >= 2) flag("cps=" + rate + " limit=" + maxCps);
        if (shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
