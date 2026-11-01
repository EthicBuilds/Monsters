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

import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;

/***
 * Visible break-outs in the wall or the floor: a monster coming out near one climbs out of it in front of the wall
 * or on top of the hatch, with dust, a crash and a shaking model. The big ones (DESIGN 01.11.) have a start inside the
 * opening and an exit in front of it: zombies crawl out of the wall or climb up out of the pit.
 */
public final class Breakouts {
    private static final double NEAR = 4;
    private static final long PAUSE = 1500;
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final Map<ItemDisplay, Long> breakouts = new HashMap<>();
    private static final Map<ItemDisplay, Shape> shapes = new HashMap<>();
    /** How fast a model climbs out of a pit (AnimatedModel buries 0.12 blocks per tick) */
    public static final double CLIMB_PER_TICK = 0.12;

    /** Model frame of DESIGN (blocks, from the bottom middle of the furniture block, before the yaw), clear width of the opening */
    private record Shape(boolean pit, Vector start, Vector exit, double width, Sound sound, BlockData dust) {}

    /** Where a monster starts and where it stands free, in the world */
    public record Opening(Location start, Location exit, boolean pit, double width) {}

    private static final Map<String, Shape> SHAPES = Map.of(
            "monsters_ausbruch_mauer", wall(0.625, 0.25, 1.0, Sound.BLOCK_GRAVEL_BREAK, Material.BRICKS),
            "monsters_ausbruch_betonwand", wall(0.375, 0.25, 1.0, Sound.BLOCK_STONE_BREAK, Material.GRAY_CONCRETE),
            "monsters_ausbruch_garage", wall(0.4375, 0.0625, 0.875, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, Material.IRON_BLOCK),
            "monsters_ausbruch_krater", pit(0.5, 0.5, 1.1, Sound.BLOCK_ROOTED_DIRT_BREAK, Material.DIRT),
            "monsters_ausbruch_schacht", pit(0.5, 0.5, 1.25, Sound.ENTITY_GENERIC_SPLASH, Material.GRAY_CONCRETE),
            "monsters_ausbruch_lichtschacht", pit(0.83, -0.06, 0.69, Sound.BLOCK_GLASS_BREAK, Material.GLASS),
            "monsters_ausbruch_gruft", pit(0.125, 0.44, 0.75, Sound.BLOCK_GRINDSTONE_USE, Material.STONE));
    private static final double WALL_EXIT_Z = -0.9, PIT_EXIT_Z = -0.75, PIT_DEPTH = 1.95;

    private static Shape wall(double x, double z, double width, Sound sound, Material dust) {
        return new Shape(false, new Vector(x, 0, z), new Vector(x, 0, WALL_EXIT_Z), width, sound, dust.createBlockData());
    }

    private static Shape pit(double x, double z, double width, Sound sound, Material dust) {
        return new Shape(true, new Vector(x, -PIT_DEPTH, z), new Vector(x, 0, PIT_EXIT_Z), width, sound, dust.createBlockData());
    }

    private Breakouts() {}

    static void add(ItemDisplay display, String id) {
        breakouts.put(display, 0L);
        Shape shape = SHAPES.get(id);
        if (shape != null) shapes.put(display, shape);
    }

    /** The nearest break-out is a big one: start and exit for the monster, else null */
    public static Opening opening(Location at) {
        ItemDisplay nearest = nearest(at);
        return nearest == null ? null : opening(nearest);
    }

    /** A big break-out: its effects, start and exit; null for a small one */
    public static Opening opening(ItemDisplay display) {
        Shape shape = shapes.get(display);
        if (shape == null) return null;
        long now = System.currentTimeMillis();
        Location exit = world(display, shape.exit());
        if (now - breakouts.getOrDefault(display, 0L) >= PAUSE) {
            breakouts.put(display, now);
            Location dust = world(display, shape.start()).add(0, shape.pit() ? 2.0 : 1.0, 0);
            dust.getWorld().spawnParticle(Particle.BLOCK, dust, 30, 0.5, 0.6, 0.5, shape.dust());
            dust.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, dust, 2, 0.3, 0.3, 0.3, 0.01);
            dust.getWorld().playSound(dust, shape.sound(), 1.0F, 0.7F);
        }
        return new Opening(world(display, shape.start()), exit, shape.pit(), shape.width());
    }

    // Model frame to the world: an item display draws its model turned by 180°, model +x is (-cos, -sin), +z is (sin, -cos)
    private static Location world(ItemDisplay display, Vector model) {
        Location furniture = display.getLocation();
        double yaw = Math.toRadians(furniture.getYaw());
        double x = -model.getX() * Math.cos(yaw) + model.getZ() * Math.sin(yaw);
        double z = -model.getX() * Math.sin(yaw) - model.getZ() * Math.cos(yaw);
        Location at = furniture.getBlock().getLocation().add(0.5 + x, model.getY(), 0.5 + z);
        // Out of the opening the monster looks forward, model -z
        at.setYaw(furniture.getYaw());
        return at;
    }

    private static ItemDisplay nearest(Location at) {
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
        return nearest;
    }

    /** Called where a monster comes out: with a break-out nearby it comes out of that instead */
    public static Location burst(Location at) {
        ItemDisplay nearest = nearest(at);
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
        shapes.clear();
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
