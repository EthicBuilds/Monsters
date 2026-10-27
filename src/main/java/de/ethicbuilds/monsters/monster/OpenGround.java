package de.ethicbuilds.monsters.monster;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Free spots in the play area for big monsters: the spots players walked on, with enough room around and above
 */
public final class OpenGround {
    private static final double MIN_PLAYER_DISTANCE = 10;

    private OpenGround() {}

    /** Free spot closest to the middle of the area the players walked, null if there is none */
    public static Location middle(Collection<Player> players, int radius, int height) {
        List<Location> spots = freeSpots(players, radius, height);
        if (spots.isEmpty()) return null;
        double x = 0, z = 0;
        for (Location spot : spots) {
            x += spot.getX();
            z += spot.getZ();
        }
        double midX = x / spots.size(), midZ = z / spots.size();
        return spots.stream().min(Comparator.comparingDouble(s -> square(s.getX() - midX) + square(s.getZ() - midZ))).orElse(null);
    }

    /** Any free spot, null if there is none */
    public static Location random(Collection<Player> players, int radius, int height) {
        List<Location> spots = freeSpots(players, radius, height);
        return spots.isEmpty() ? null : spots.get(ThreadLocalRandom.current().nextInt(spots.size()));
    }

    // Spots away from the players first
    private static List<Location> freeSpots(Collection<Player> players, int radius, int height) {
        List<Location> free = new ArrayList<>(), near = new ArrayList<>();
        for (Player player : players) {
            for (Location spot : PlayerTrail.of(player)) {
                Location block = spot.toBlockLocation().add(0.5, 0, 0.5);
                if (!isFree(block, radius, height)) continue;
                if (awayFrom(players, block)) free.add(block);
                else near.add(block);
            }
        }
        return free.isEmpty() ? near : free;
    }

    public static boolean isFree(Location at, int radius, int height) {
        if (!at.getBlock().getRelative(0, -1, 0).isSolid()) return false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y < height; y++) {
                    Block block = at.getBlock().getRelative(x, y, z);
                    if (!block.isPassable()) return false;
                }
            }
        }
        return true;
    }

    private static boolean awayFrom(Collection<Player> players, Location spot) {
        for (Player player : players) {
            if (player.getWorld().equals(spot.getWorld()) && player.getLocation().distance(spot) < MIN_PLAYER_DISTANCE) return false;
        }
        return true;
    }

    private static double square(double value) {
        return value * value;
    }
}
