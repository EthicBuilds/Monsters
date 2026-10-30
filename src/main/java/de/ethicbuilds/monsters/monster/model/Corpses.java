package de.ethicbuilds.monsters.monster.model;

import java.util.ArrayDeque;
import java.util.Deque;

/***
 * Corpses and dropped limbs lying around at once (design session): at most 12, the oldest sinks into the ground first
 */
public final class Corpses {
    private static final int MAX = 12;
    private static final Deque<Corpse> lying = new ArrayDeque<>();

    public interface Corpse {
        void sink();

        boolean isGone();
    }

    private Corpses() {}

    public static void add(Corpse corpse) {
        lying.removeIf(Corpse::isGone);
        lying.addLast(corpse);
        while (lying.size() > MAX) lying.pollFirst().sink();
    }
}
