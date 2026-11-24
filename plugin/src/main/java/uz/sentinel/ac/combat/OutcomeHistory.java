package uz.sentinel.ac.combat;

/** The last N yes/no outcomes of a repeated observation (for example "was this crystal broken instantly?"). */
public final class OutcomeHistory {
    private final boolean[] ring;
    private int index;
    private int size;
    private int hits;

    public OutcomeHistory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        ring = new boolean[capacity];
    }

    /** Records an outcome and returns how many of the remembered outcomes are true. */
    public int record(boolean outcome) {
        if (size == ring.length) {
            if (ring[index]) hits--;
        } else {
            size++;
        }
        ring[index] = outcome;
        if (outcome) hits++;
        index = (index + 1) % ring.length;
        return hits;
    }

    public boolean full() { return size == ring.length; }
    public int hits() { return hits; }
    public int size() { return size; }

    public void clear() {
        java.util.Arrays.fill(ring, false);
        index = size = hits = 0;
    }
}
