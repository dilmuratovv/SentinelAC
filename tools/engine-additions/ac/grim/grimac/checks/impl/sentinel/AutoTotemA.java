package ac.grim.grimac.checks.impl.sentinel;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.PacketSendListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import org.jetbrains.annotations.NotNull;
import uz.sentinel.ac.combat.OutcomeHistory;

import java.util.Locale;
import java.util.Map;

/**
 * Auto totem. When a totem pops, the client learns it from entity status 35. The engine sends a transaction at the
 * end of every server tick, so the moment the client answers the one after the status is (within a tick) the moment
 * the client knew about the pop, independent of ping. From there, a person has to see the animation, open the
 * inventory and move the cursor (or press the swap key with a totem in hand): 150 ms or more. An auto-totem clicks
 * within the same or the next client tick, often even before it answers the transaction.
 *
 * <p>The first inventory click or swap-hands key after the pop is timed. It is only judged when that action put a
 * totem into the offhand: a click whose changed slots put a totem into the offhand slot, the swap key while holding
 * a totem (the engine's packet inventory knows the held item), or the server sending the offhand slot with a totem.
 * The server does not always send the offhand slot after a swap (a pop and an instant refill can fall into the same
 * inventory sync), so the client's own action is the main signal. Everything is read from packets on this thread;
 * the server inventory is never touched off the main thread. Two instant refills out of the last three flag.</p>
 */
@CheckData(name = "AutoTotemA", stableKey = "sentinel.autototem.reaction",
        description = "Put a totem back into the offhand faster than a person can react", decay = 0.002, setback = -1)
public class AutoTotemA extends Check implements PacketSendListener, PacketReceiveListener {
    private static final int MENU_OFFHAND_SLOT = 45;      // slot index in the player's inventory menu (window 0)
    private static final int INVENTORY_OFFHAND_SLOT = 40; // index in the player inventory (set player inventory packet)

    private long maxReactionNanos; // set by onReload

    private final OutcomeHistory history = new OutcomeHistory(3);
    private boolean popSent;      // status 35 sent; watching for the refill
    private long knownAt;         // when the client confirmed the pop (0 = not yet)
    private long firstAction;     // nanos of the first click/swap after the pop (0 = none yet)
    private boolean refilled;     // a totem is back in the offhand
    private int ticksWatched;

    public AutoTotemA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxReactionNanos = SentinelCombat.clamp(config.getIntElse(getConfigName() + ".max-reaction-ms", 120), 20, 140)
                * SentinelCombat.MILLIS;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus status = new WrapperPlayServerEntityStatus(event);
            if (status.getEntityId() != player.entityID || status.getStatus() != 35) return;
            popSent = true;
            knownAt = 0;
            firstAction = 0;
            refilled = false;
            ticksWatched = 0;
            // The end-of-tick transaction that follows this status confirms the client has processed it. It may be up
            // to one server tick later, which only makes a reaction look slower (never faster) than it was.
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> {
                if (popSent && knownAt == 0) knownAt = System.nanoTime();
            });
        } else if (popSent && event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot slot = new WrapperPlayServerSetSlot(event);
            if (slot.getWindowId() == 0 && slot.getSlot() == MENU_OFFHAND_SLOT && isTotem(slot.getItem())) refilled = true;
        } else if (popSent && event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            WrapperPlayServerSetPlayerInventory slot = new WrapperPlayServerSetPlayerInventory(event);
            if (slot.getSlot() == INVENTORY_OFFHAND_SLOT && isTotem(slot.getStack())) refilled = true;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!popSent) return;
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            if (firstAction == 0) firstAction = System.nanoTime();
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            if (click.getWindowId() == 0) {
                Map<Integer, ItemStack> changed = click.getSlots().orElse(Map.of());
                if (isTotem(changed.get(MENU_OFFHAND_SLOT))) refilled = true;
            }
            return;
        }
        if (SentinelCombat.swapHands(event)) {
            if (firstAction == 0) firstAction = System.nanoTime();
            if (isTotem(player.inventory.getHeldItem())) refilled = true; // the held totem goes to the offhand
            return;
        }
        if (!isTickPacket(event.getPacketType()) || knownAt == 0) return;
        if (++ticksWatched > 60 || SentinelCombat.exempt(player)) { // 3 s: a person was slow, or no refill happened
            popSent = false;
            return;
        }
        if (firstAction == 0 || !refilled) return;

        popSent = false;
        long reaction = Math.max(0, firstAction - knownAt);
        // Instant only when the totem was also back within 8 client ticks: an unrelated click that happened to come
        // right after the pop, followed by a slow manual refill, is not an auto-totem.
        int instant = history.record(reaction <= maxReactionNanos && ticksWatched <= 8);
        if (instant >= 2) {
            flag(String.format(Locale.ROOT, "reaction=%dms instant=%d/3", reaction / SentinelCombat.MILLIS, instant));
        }
    }

    private static boolean isTotem(ItemStack item) {
        return item != null && item.getType() == ItemTypes.TOTEM_OF_UNDYING;
    }
}
