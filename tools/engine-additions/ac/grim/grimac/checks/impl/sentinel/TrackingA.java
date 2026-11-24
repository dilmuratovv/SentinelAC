package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.AimMath;
import uz.sentinel.ac.combat.ReactionCorrelation;

import java.util.Locale;

/**
 * Aimbot / aura tracking without a human reaction delay. While the player fights, every client tick records how
 * much the direction to their target changed (as the client sees it) and how much the player's yaw changed. A mouse
 * can only follow a target move after a visual-motor delay of 2+ ticks; an aimbot follows within 0-1 tick. 200
 * fighting ticks whose 0-1 tick correlation reaches the threshold AND beats the 3-5 tick correlation by 0.1 flag. Ping-independent (see
 * {@link ReactionCorrelation}); needs reliable tick packets.
 */
@CheckData(name = "TrackingA", stableKey = "sentinel.tracking.reaction",
        description = "Followed the target with no human reaction delay", decay = 0.01, setback = -1)
public class TrackingA extends Check implements RotationListener, PacketReceiveListener {
    private double minCorrelation; // set by onReload
    private double minSpread;

    private final ReactionCorrelation correlation = new ReactionCorrelation(200);
    private int sinceCheck;
    private long tick;
    private long lastAttackTick = Long.MIN_VALUE / 2;
    private int target = -1;
    private double yaw = Double.NaN, targetYaw = Double.NaN;       // latest values (set by rotations)
    private double sampledYaw = Double.NaN, sampledTarget = Double.NaN; // values at the previous tick

    public TrackingA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minCorrelation = SentinelCombat.clamp(config.getDoubleElse(getConfigName() + ".min-correlation", 0.45), 0.25, 0.95);
        minSpread = SentinelCombat.clamp(config.getDoubleElse(getConfigName() + ".min-motion-degrees", 0.3), 0.05, 5.0);
    }

    private double directionToTarget() {
        PacketEntity entity = target < 0 ? null : player.compensatedEntities.getEntity(target);
        if (entity == null || entity.isDead) return Double.NaN;
        SimpleCollisionBox b = entity.getPossibleCollisionBoxes();
        return AimMath.yawTo(player.x, player.z, (b.minX + b.maxX) / 2, (b.minZ + b.maxZ) / 2);
    }

    @Override
    public void process(final RotationUpdate rotation) {
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch) {
            sampledYaw = Double.NaN; // a forced rotation is not a reaction
            return;
        }
        yaw = rotation.newYaw();
        targetYaw = directionToTarget();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        int attacked = SentinelCombat.attackTarget(event);
        if (attacked >= 0) {
            if (attacked != target) { target = attacked; sampledYaw = Double.NaN; }
            lastAttackTick = tick;
            return;
        }
        if (!isTickPacket(event.getPacketType())) return;
        tick++;
        if (SentinelCombat.exempt(player) || player.canSkipTicks() || player.inVehicle() || tick - lastAttackTick > 40
                || Double.isNaN(yaw) || Double.isNaN(targetYaw)) {
            sampledYaw = Double.NaN;
            return;
        }
        if (!Double.isNaN(sampledYaw)) {
            correlation.add(targetYaw - sampledTarget, yaw - sampledYaw);
            if (correlation.full() && ++sinceCheck >= 100) {
                sinceCheck = 0;
                if (correlation.instant(minCorrelation, 0.1, minSpread)) {
                    flag(String.format(Locale.ROOT, "instant=%.2f human-lag=%.2f", correlation.instantResponse(minSpread),
                            correlation.delayedResponse(minSpread)));
                }
            }
        }
        sampledYaw = yaw;
        sampledTarget = targetYaw;
    }
}
