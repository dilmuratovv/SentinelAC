package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/** Shared packet helpers for the Sentinel combat checks. */
final class SentinelCombat {
    static final long MILLIS = 1_000_000L;

    private SentinelCombat() {}

    /** Entity id of an attack (left click on an entity), or -1 when the packet is not an attack. */
    static int attackTarget(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            return new WrapperPlayClientAttack(event).getEntityId();
        }
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) return packet.getEntityId();
        }
        return -1;
    }

    /** Left click on a block (start or abort breaking). */
    static boolean leftClickBlock(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return false;
        DiggingAction action = new WrapperPlayClientPlayerDigging(event).getAction();
        return action == DiggingAction.START_DIGGING || action == DiggingAction.CANCELLED_DIGGING;
    }

    /** Pressed the swap-hands key (F by default). */
    static boolean swapHands(PacketReceiveEvent event) {
        return event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING
                && new WrapperPlayClientPlayerDigging(event).getAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND;
    }

    /** Creative and spectator players cannot gain anything from these checks and are never judged. */
    static boolean exempt(GrimPlayer player) {
        return player.disableGrim || player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR;
    }

    static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }
}
