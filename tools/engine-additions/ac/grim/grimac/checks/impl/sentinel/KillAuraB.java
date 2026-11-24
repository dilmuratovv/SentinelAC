package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.AimMath;
import uz.sentinel.ac.combat.OutcomeHistory;

import java.util.Locale;

/**
 * Computed aim during combat. A mouse turns the camera in whole steps of the sensitivity. The step is learned from
 * the player's own rotations OUTSIDE combat, and only trusted when at least 90 % of those rotations really are whole
 * steps (players on trackpads, zoom mods or cinematic camera never reach that, so they are never judged). In combat,
 * aura and aim-assist rotations are calculated angles that fall between those steps; with a real Meteor Client kill
 * aura 71 % of combat yaw changes were off-step, against 0 % for a person fighting with the mouse. 12 off-step yaw
 * changes among the last 20 combat rotations flag.
 */
@CheckData(name = "KillAuraB", stableKey = "sentinel.killaura.sensitivity",
        description = "Rotated in steps that do not match the player's own mouse sensitivity during combat", decay = 0.02, setback = -1)
public class KillAuraB extends Check implements RotationListener, PacketReceiveListener {
    private int required; // set by onReload
    private double maxStepError;

    private final OutcomeHistory calm = new OutcomeHistory(40);   // out-of-combat rotations: on-step?
    private final OutcomeHistory combat = new OutcomeHistory(20); // combat rotations: off-step?
    private double learnedStep;   // trusted sensitivity step (degrees), 0 = not trusted yet
    private long tick;
    private long lastAttackTick = Long.MIN_VALUE / 2;
    private long lastAttackNanos;

    public KillAuraB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        required = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".required-of-20", 12), 8, 20);
        maxStepError = SentinelCombat.clamp(config.getDoubleElse(getConfigName() + ".max-step-error", 0.15), 0.05, 0.45);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (isTickPacket(event.getPacketType())) {
            tick++;
            return;
        }
        if (SentinelCombat.attackTarget(event) >= 0) {
            lastAttackTick = tick;
            lastAttackNanos = System.nanoTime();
        }
    }

    private long sinceAttackTicks() {
        return player.canSkipTicks() ? (System.nanoTime() - lastAttackNanos) / (50 * SentinelCombat.MILLIS) : tick - lastAttackTick;
    }

    @Override
    public void process(final RotationUpdate rotation) {
        if (SentinelCombat.exempt(player) || player.packetStateData.lastPacketWasTeleport
                || player.vehicleData.wasVehicleSwitch || player.packetStateData.horseInteractCausedForcedRotation
                || player.inVehicle()) return;
        double yaw = rotation.deltaYawABS();
        if (yaw < 0.5 || yaw > 40.0 || Math.abs(player.pitch) >= 89.5) return;
        // Huge yaw values lose float precision; then steps cannot be measured reliably.
        if (Math.ulp(Math.abs(player.yaw)) * 4 > 0.005) return;

        long since = sinceAttackTicks();
        if (since > 40) {
            AimProcessor aim = player.checkManager.get(AimProcessor.class);
            if (aim == null || aim.modeYaw <= GrimMath.MINIMUM_DIVISOR) return;
            if (learnedStep > 0 && Math.abs(aim.modeYaw - learnedStep) > 1e-3) { // sensitivity changed: relearn
                learnedStep = 0;
                calm.clear();
                combat.clear();
            }
            calm.record(AimMath.stepError(yaw, aim.modeYaw) <= 0.1);
            if (calm.full() && calm.hits() >= 36) learnedStep = aim.modeYaw;
            return;
        }
        if (since > 20 || learnedStep <= 0) return;

        double error = AimMath.stepError(yaw, learnedStep);
        int off = combat.record(error > maxStepError);
        if (combat.full() && off >= required) {
            flag(String.format(Locale.ROOT, "offstep=%d/20 step=%.4f err=%.2f", off, learnedStep, error));
            combat.clear();
        }
    }
}
