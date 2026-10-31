package de.ethicbuilds.monsters.building;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.Restore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/***
 * Visible break-outs in the wall or the floor: a monster coming out near one climbs out of it in front of the wall
 * or on top of the hatch, with dust, a crash and a shaking model
 */
public final class Breakouts {
    private static final double NEAR = 4;
    private static final long PAUSE = 1500;
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final Map<ItemDisplay, Long> breakouts = new HashMap<>();

    private Breakouts() {}

    static void add(ItemDisplay display) {
        breakouts.put(display, 0L);
    }

    /** Called where a monster comes out: with a break-out nearby it comes out of that instead */
    public static Location burst(Location at) {
        ItemDisplay nearest = null;
        double best = NEAR * NEAR;
        for (ItemDisplay display : breakouts.keySet()) {
            if (!display.isValid() || !display.getWorld().equals(at.getWorld())) continue;
            double distance = display.getLocation().distanceSquared(at);
            if (distance <= best) {
                best = distance;
                nearest = display;
            }
        }
        return nearest == null ? at : out(nearest);
    }

    /** Break-out the player looks at, for test monsters on the build server */
    public static ItemDisplay lookedAt(Player player, double reach) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        ItemDisplay found = null;
        double best = reach;
        for (ItemDisplay display : breakouts.keySet()) {
            if (!display.isValid() || !display.getWorld().equals(eye.getWorld())) continue;
            Vector to = display.getLocation().add(0, 0.5, 0).toVector().subtract(eye.toVector());
            double distance = to.length();
            if (distance > best || distance < 0.5 || to.normalize().dot(look) < 0.97) continue;
            best = distance;
            found = display;
        }
        return found;
    }

    /** Effects at the break-out and the spot where the monster stands */
    public static Location out(ItemDisplay display) {
        long now = System.currentTimeMillis();
        if (now - breakouts.getOrDefault(display, 0L) >= PAUSE) {
            breakouts.put(display, now);
            Location center = display.getLocation().add(0, 1, 0);
            center.getWorld().spawnParticle(Particle.BLOCK, center, 30, 0.6, 0.8, 0.6, Material.COBBLESTONE.createBlockData());
            center.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 2, 0.4, 0.4, 0.4, 0.01);
            center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.9F, 0.6F);
            shake(display);
        }
        return exit(display);
    }

    // The open block of the furniture, else below or above it, else the free side in front of the wall
    private static Location exit(ItemDisplay display) {
        Block block = display.getLocation().getBlock();
        for (Block spot : new Block[]{block, block.getRelative(BlockFace.DOWN), block.getRelative(BlockFace.UP)}) {
            if (free(spot)) return stand(spot, display);
        }
        // A wall piece looks away from its wall, that side wins when both are open
        Vector facing = display.getLocation().getDirection().setY(0);
        Block side = null;
        double best = -Double.MAX_VALUE;
        for (BlockFace face : SIDES) {
            Block next = block.getRelative(face);
            if (!free(next)) continue;
            double score = face.getDirection().dot(facing) + (block.getRelative(face.getOppositeFace()).isPassable() ? 0 : 2);
            if (score > best) {
                best = score;
                side = next;
            }
        }
        return side != null ? stand(side, display) : display.getLocation();
    }

    private static boolean free(Block block) {
        return block.isPassable() && block.getRelative(BlockFace.UP).isPassable();
    }

    private static Location stand(Block block, ItemDisplay display) {
        Location at = block.getLocation().add(0.5, 0, 0.5);
        at.setYaw(display.getLocation().getYaw());
        return at;
    }

    public static void clear() {
        breakouts.clear();
    }

    private static void shake(ItemDisplay display) {
        Restore.display(display);
        Transformation rest = display.getTransformation();
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTransformation(new Transformation(rest.getTranslation(), new Quaternionf(rest.getLeftRotation()).rotateZ((float) Math.toRadians(4)),
                rest.getScale(), rest.getRightRotation()));
        display.getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(4);
            display.setTransformation(rest);
        }, 3L);
    }
}
