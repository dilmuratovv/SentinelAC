package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.checks.GrimProcessor;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import uz.sentinel.ac.combat.AimMath;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Developer tool for calibrating the combat checks: when the JVM runs with {@code -Dsentinel.trace=<directory>},
 * every rotation and attack of every player is written to {@code <directory>/<name>.tsv}. Off (and free) otherwise.
 * Never enable it on a production server.
 */
public final class SentinelTrace extends GrimProcessor implements PacketReceiveListener, RotationListener,
        ac.grim.grimac.checks.type.PacketSendListener {
    private static final String DIRECTORY = System.getProperty("sentinel.trace");

    private BufferedWriter out;
    private long tick;
    private long lastAttackTick = -1000;
    private int lastTarget = -1;

    public SentinelTrace(GrimPlayer player) {
        super(player);
    }

    private void write(String line) {
        if (DIRECTORY == null) return;
        try {
            if (out == null) {
                Path dir = Path.of(DIRECTORY);
                Files.createDirectories(dir);
                out = Files.newBufferedWriter(dir.resolve(player.getName() + ".tsv"), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            out.write(System.nanoTime() + "\t" + tick + "\t" + line + "\n");
            out.flush();
        } catch (IOException | RuntimeException ignored) {
            // diagnostics only
        }
    }

    @Override
    public void onPacketSend(com.github.retrooper.packetevents.event.PacketSendEvent event) {
        if (DIRECTORY == null || event.getPacketType() != com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.ENTITY_STATUS) return;
        var status = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus(event);
        if (status.getEntityId() != player.entityID || status.getStatus() != 35) return;
        write("pop");
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> write("popknown"));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (DIRECTORY == null) return;
        if (isTickPacket(event.getPacketType())) { tick++; return; }
        var type = event.getPacketType();
        if (isTransaction(type)) {
            var server = lastTarget < 0 ? null : player.compensatedEntities.serverPositionsMap.get(lastTarget);
            double d = server == null ? -1 : Math.hypot(server.getX() - player.x, server.getZ() - player.z);
            write(String.format(Locale.ROOT, "png\t%d\t%.3f", player.getTransactionPing(), d));
            return;
        }
        if (type == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.CLICK_WINDOW) {
            var click = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow(event);
            write("click\t" + click.getWindowId() + "\t" + click.getSlot() + "\t" + click.getWindowClickType());
            return;
        }
        if (type == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.HELD_ITEM_CHANGE) {
            write("slot\t" + new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange(event).getSlot());
            return;
        }
        if (SentinelCombat.swapHands(event)) { write("swap"); return; }
        int id = SentinelCombat.attackTarget(event);
        if (id < 0) return;
        lastTarget = id;
        PacketEntity entity = player.compensatedEntities.getEntity(id);
        String aim = "";
        if (entity != null) {
            SimpleCollisionBox b = entity.getPossibleCollisionBoxes();
            double cx = (b.minX + b.maxX) / 2, cy = (b.minY + b.maxY) / 2, cz = (b.minZ + b.maxZ) / 2;
            double ex = player.x, ey = player.y + player.getEyeHeight(), ez = player.z;
            double yawTo = AimMath.yawTo(ex, ez, cx, cz);
            double pitchTo = -Math.toDegrees(Math.atan2(cy - ey, Math.hypot(cx - ex, cz - ez)));
            var server = player.compensatedEntities.serverPositionsMap.get(id);
            double stale = server == null ? -1 : Math.hypot(server.getX() - cx, server.getZ() - cz);
            double serverDist = server == null ? -1 : Math.hypot(server.getX() - ex, server.getZ() - ez);
            aim = String.format(Locale.ROOT, "\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%d", Math.hypot(cx - ex, cz - ez),
                    yawTo, pitchTo, b.maxX - b.minX, b.maxY - b.minY, stale, serverDist, player.getTransactionPing());
        }
        write("atk\t" + id + "\t" + (tick - lastAttackTick) + aim);
        lastAttackTick = tick;
    }

    @Override
    public void process(RotationUpdate r) {
        if (DIRECTORY == null) return;
        AimProcessor aim = player.checkManager.get(AimProcessor.class);
        double yawTo = Double.NaN;
        PacketEntity target = lastTarget < 0 ? null : player.compensatedEntities.getEntity(lastTarget);
        if (target != null) { // where the client currently sees the last target
            SimpleCollisionBox b = target.getPossibleCollisionBoxes();
            yawTo = AimMath.yawTo(player.x, player.z, (b.minX + b.maxX) / 2, (b.minZ + b.maxZ) / 2);
        }
        write(String.format(Locale.ROOT, "rot\t%.5f\t%.5f\t%.5f\t%.5f\t%.6f\t%.6f\t%d\t%.4f", r.newYaw(), r.newPitch(),
                r.deltaYaw(), r.deltaPitch(), aim == null ? 0 : aim.modeYaw, aim == null ? 0 : aim.modePitch,
                tick - lastAttackTick, yawTo));
    }
}
