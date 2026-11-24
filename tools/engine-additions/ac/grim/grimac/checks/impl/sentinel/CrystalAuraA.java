package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.PacketSendListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.OutcomeHistory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Crystal aura. The time from the client learning about a new end crystal (the end-of-tick transaction after its
 * spawn packet, independent of ping) to the attack on it is a reaction time. A crystal aura breaks it within the same
 * tick, often before it even answers the transaction. People who spam left click can also be that fast by chance, so
 * a break only counts as "instant" when the player made no other left click (attack or block click) in the 150 ms
 * before the crystal appeared. 6 instant breaks among the last 8 flag.
 */
@CheckData(name = "CrystalAuraA", stableKey = "sentinel.crystalaura.reaction",
        description = "Broke new end crystals faster than a person can react", decay = 0.002, setback = -1)
public class CrystalAuraA extends Check implements PacketSendListener, PacketReceiveListener {
    private static final long IDLE_BEFORE = 150L * SentinelCombat.MILLIS;

    private long maxReactionNanos; // set by onReload
    private int required;

    private final OutcomeHistory history = new OutcomeHistory(8);
    private final Map<Integer, long[]> crystals = new LinkedHashMap<>(16, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, long[]> eldest) { return size() > 64; }
    };
    private long lastLeftClick;

    public CrystalAuraA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxReactionNanos = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".max-reaction-ms", 50), 10, 120)
                * SentinelCombat.MILLIS;
        required = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".required-of-8", 6), 4, 8);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(event);
            if (spawn.getEntityType() != EntityTypes.END_CRYSTAL) return;
            int id = spawn.getEntityId();
            long[] state = {0L}; // [0] = when the client knew about it
            crystals.put(id, state);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> state[0] = System.nanoTime());
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) crystals.remove(id);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        int target = SentinelCombat.attackTarget(event);
        long now = System.nanoTime();
        if (target < 0) {
            if (SentinelCombat.leftClickBlock(event)) lastLeftClick = now;
            return;
        }
        long previousClick = lastLeftClick;
        lastLeftClick = now;
        long[] state = crystals.remove(target);
        if (state == null || SentinelCombat.exempt(player)) return;

        long known = state[0];
        long appeared = known == 0 ? now : known;
        long reaction = known == 0 ? 0 : Math.max(0, now - known);
        boolean idle = previousClick == 0 || appeared - previousClick > IDLE_BEFORE;
        int instant = history.record(idle && reaction <= maxReactionNanos);
        if (history.full() && instant >= required) {
            flag(String.format(Locale.ROOT, "instant=%d/8 reaction=%dms", instant, reaction / SentinelCombat.MILLIS));
            history.clear();
        }
    }
}
