package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.AimMath;
import uz.sentinel.ac.combat.TimeWindow;

import java.util.Locale;

/**
 * Multi-target aura. Hitting two different living entities in very different directions (90 degrees or more apart,
 * as the client sees them) within two client ticks needs a flick of 900+ degrees per second that also lands on both
 * hitboxes. A person can do it once by luck; an aura does it every time it switches targets. Flags on the third such
 * switch within 20 seconds and then cancels those hits.
 */
@CheckData(name = "KillAuraA", stableKey = "sentinel.killaura.switch",
        description = "Hit targets in very different directions within a few ticks", decay = 0.005, setback = -1)
public class KillAuraA extends Check implements PacketReceiveListener {
    private double minAngle; // set by onReload
    private int maxTicks;
    private int required;

    private final TimeWindow switches = new TimeWindow(20_000L * SentinelCombat.MILLIS, 64);
    private long tick;
    private int lastTarget = -1;
    private long lastTick;
    private long lastNanos;
    private double lastYaw;

    public KillAuraA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minAngle = SentinelCombat.clamp(config.getDoubleElse(getConfigName() + ".min-angle", 90.0), 45.0, 179.0);
        maxTicks = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".max-ticks", 2), 1, 5);
        required = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".switches", 3), 2, 20);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacket(event.getPacketType())) {
            tick++;
            return;
        }
        int target = SentinelCombat.attackTarget(event);
        if (target < 0 || SentinelCombat.exempt(player) || player.inVehicle()) return;
        PacketEntity entity = player.compensatedEntities.getEntity(target);
        if (entity == null || entity.isDead || !entity.isLivingEntity || entity.riding != null) return;

        SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
        double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
        double dx = cx - player.x, dz = cz - player.z;
        if (dx * dx + dz * dz < 1.5 * 1.5) { // too close: the direction to the target is not meaningful
            lastTarget = -1;
            return;
        }
        double yaw = AimMath.yawTo(player.x, player.z, cx, cz);
        long now = System.nanoTime();

        boolean quick = player.canSkipTicks()
                ? now - lastNanos <= (maxTicks * 50L + 10) * SentinelCombat.MILLIS
                : tick - lastTick <= maxTicks;
        if (lastTarget >= 0 && target != lastTarget && quick) {
            double angle = AimMath.angleBetween(yaw, lastYaw);
            if (angle >= minAngle) {
                int count = switches.add(now);
                if (count >= required && flag(String.format(Locale.ROOT, "angle=%.0f ticks=%d switches=%d",
                        angle, tick - lastTick, count)) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
        lastTarget = target;
        lastTick = tick;
        lastNanos = now;
        lastYaw = yaw;
    }
}
