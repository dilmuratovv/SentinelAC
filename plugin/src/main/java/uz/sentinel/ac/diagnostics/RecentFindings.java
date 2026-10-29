package uz.sentinel.ac.diagnostics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Global bound, including disconnected players, rather than an unbounded UUID map. */
public final class RecentFindings {
    private final int capacity;
    private final Deque<Finding> findings = new ArrayDeque<>();
    public RecentFindings(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public void add(Finding finding) {
        if (findings.size() == capacity) findings.removeFirst();
        findings.addLast(finding);
    }
    public List<Finding> recent(String player, int count) {
        return findings.reversed().stream().filter(f -> f.playerName().equalsIgnoreCase(player)).limit(count).toList();
    }
    public int size() { return findings.size(); }
}
