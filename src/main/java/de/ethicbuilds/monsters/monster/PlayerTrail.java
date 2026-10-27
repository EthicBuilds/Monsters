package de.ethicbuilds.monsters.monster;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Where each player walked lately. If a monster finds no full path to its player, it follows this trail,
 * the spots are walkable and lead to the player.
 */
public final class PlayerTrail {
    private static final int MAX_SPOTS = 60;
    private static final double SPACING = 2.5;

    private static final Map<UUID, Deque<Location>> trails = new ConcurrentHashMap<>();

    private PlayerTrail() {}

    public static void record(Player player) {
        if (!player.isOnGround() || player.isFlying()) return;
        Deque<Location> trail = trails.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        Location now = player.getLocation();
        Location last = trail.peekFirst();
        if (last != null && last.getWorld().equals(now.getWorld()) && last.distanceSquared(now) < SPACING * SPACING) return;
        trail.addFirst(now);
        if (trail.size() > MAX_SPOTS) trail.removeLast();
    }

    /** Newest spot first */
    public static List<Location> of(Player player) {
        Deque<Location> trail = trails.get(player.getUniqueId());
        return trail == null ? List.of() : List.copyOf(trail);
    }

    public static void clear() {
        trails.clear();
    }
}
