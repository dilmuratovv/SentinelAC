package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.CombatLatency;

import java.util.Locale;

/**
 * Backtrack (holding incoming packets while fighting). Every transaction round trip is sorted into "fighting" (within
 * 1 s after the player's own attack) or "calm" (3 s or more after it). Backtrack raises the fighting round trips only;
 * real lag and lag spikes hit both. 400 fighting samples whose median is 8+ ms and 75th percentile 50+ ms above the
 * player's own calm values flag. The comparison is with the player's own ping, so any ping level works.
 */
@CheckData(name = "BacktrackA", stableKey = "sentinel.backtrack.latency",
        description = "Ping rose only while fighting (packets held back)", decay = 0.005, setback = -1)
public class BacktrackA extends Check implements PacketReceiveListener {
    private static final long MILLIS = SentinelCombat.MILLIS;

    private int minMedian; // set by onReload
    private int minUpper;

    private final CombatLatency latency = new CombatLatency(400, 300);
    private long lastAttack;

    public BacktrackA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minMedian = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".min-median-rise-ms", 8), 0, 500);
        minUpper = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".min-upper-rise-ms", 50), 20, 1000);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long now = System.nanoTime();
        if (SentinelCombat.attackTarget(event) >= 0) {
            lastAttack = now;
            return;
        }
        if (!isTransaction(event.getPacketType()) || !player.packetStateData.lastTransactionPacketWasValid
                || SentinelCombat.exempt(player)) return;
        int rtt = player.getTransactionPing();
        if (rtt <= 0 || rtt > 5000) return;
        long since = lastAttack == 0 ? Long.MAX_VALUE : now - lastAttack;
        if (since >= 3_000L * MILLIS) {
            latency.addCalm(rtt);
        } else if (since <= 1_000L * MILLIS) {
            CombatLatency.Verdict verdict = latency.addCombat(rtt, 100, 150);
            if (verdict != null && verdict.holdsPackets(minMedian, minUpper)) {
                flag(String.format(Locale.ROOT, "fight-vs-calm median %+dms p75 %+dms (calm %dms)",
                        verdict.medianDiff(), verdict.upperDiff(), verdict.calmMedian()));
                latency.clearCombat();
            }
        }
    }
}
