package uz.sentinel.ac.combat;

/**
 * Counts events inside the last {@code size} client ticks. Client ticks (not wall time) make the count immune to
 * network jitter: a lag spike that delivers a second of clicks at once still spreads them over the ticks they were
 * made in. Not thread-safe; one instance per player, used from that player's packet thread.
 */
public final class TickWindow {
    private final int[] ring;
    private int index;
    private int total;

    public TickWindow(int size) {
        if (size < 1) throw new IllegalArgumentException("size must be positive");
        ring = new int[size];
    }

    /** Records one event in the current tick and returns the number of events in the window. */
    public int add() {
        ring[index]++;
        return ++total;
    }

    /** Advances to the next client tick, forgetting the oldest one. */
    public void tick() {
        index = (index + 1) % ring.length;
        total -= ring[index];
        ring[index] = 0;
    }

    public int total() { return total; }
}
