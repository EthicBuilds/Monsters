package de.ethicbuilds.monsters.monster;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

/***
 * Whether a player could see a spot: in front of them (wide field of view) and no block in between.
 * Big monsters only jump somewhere nobody looks.
 */
public final class Sight {
    // Displays are drawn this far, beyond nobody sees anything
    private static final double VIEW_DISTANCE = 96;
    // This close it counts as seen anyway, one turn of the head is enough
    private static final double CLOSE = 8;
    private static final double FIELD_OF_VIEW = Math.toRadians(80);

    private Sight() {}

    /** true when any of the players could see something standing here up to this height */
    public static boolean seen(Collection<Player> players, Location at, double height) {
        for (Player player : players) {
            if (!player.getWorld().equals(at.getWorld())) continue;
            Location eye = player.getEyeLocation();
            double distance = eye.distance(at);
            if (distance > VIEW_DISTANCE) continue;
            if (distance < CLOSE) return true;
            for (double y : new double[]{0.5, height / 2, height}) {
                Vector way = at.clone().add(0, y, 0).toVector().subtract(eye.toVector());
                if (way.angle(eye.getDirection()) > FIELD_OF_VIEW) continue;
                if (at.getWorld().rayTraceBlocks(eye, way.clone().normalize(), way.length(), FluidCollisionMode.NEVER, true) == null) return true;
            }
        }
        return false;
    }
}
