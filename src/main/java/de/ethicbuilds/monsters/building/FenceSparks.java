package de.ethicbuilds.monsters.building;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Electricity on the electric fence (user 31.10.): shocks run along the three wires, over a gap of one block
 * to the next fence arcs jump from wire to wire
 */
final class FenceSparks {
    // Wire heights and where they leave the post, from the model (pixels / 16)
    private static final double[] HEIGHTS = {4.5 / 16, 8.5 / 16, 12.5 / 16};
    private static final double POST = 1.75 / 16;
    private static final int STEPS = 6;
    private static final double JITTER = 0.06, ARC_JITTER = 0.16;
    private static final Particle.DustOptions CORE = new Particle.DustOptions(Color.WHITE, 0.35F);
    private static final Particle.DustOptions GLOW = new Particle.DustOptions(Color.fromRGB(0x7FD8FF), 0.6F);

    private FenceSparks() {}

    /** World directions of the wires of a shape: straight to both sides, corner, t, end */
    static List<Vector> wires(ItemDisplay display, String base) {
        // The item display turns the model by 180° and then by its yaw: model +x and +z in the world
        double yaw = Math.toRadians(display.getLocation().getYaw());
        Vector east = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vector south = new Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        List<Vector> wires = new ArrayList<>();
        wires.add(east);
        if (!base.endsWith("ecke_") && !base.endsWith("ende_")) wires.add(east.clone().multiply(-1));
        if (base.endsWith("ecke_") || base.endsWith("t_")) wires.add(south);
        return wires;
    }

    /** Where the fence stands: middle of the post at its floor */
    static Location base(ItemDisplay display) {
        Location at = display.getLocation();
        return new Location(at.getWorld(), at.getBlockX() + 0.5, at.getBlockY(), at.getBlockZ() + 0.5);
    }

    /** One step of the shock that runs from the post along one wire after the other */
    static void pulse(Location base, List<Vector> wires, int step, int seed) {
        int run = step / STEPS + seed;
        Vector wire = wires.get(Math.floorMod(run, wires.size()));
        double height = HEIGHTS[Math.floorMod(run / wires.size(), HEIGHTS.length)];
        double head = POST + (0.5 - POST) * (step % STEPS + 1) / STEPS;
        Location start = base.clone().add(0, height, 0);
        List<Location> points = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double along = Math.max(POST, head - i * 0.07);
            points.add(jitter(start.clone().add(wire.clone().multiply(along)), wire, i == 0 ? 0 : JITTER));
        }
        draw(points);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location spark = points.get(random.nextInt(points.size()));
        base.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, spark, 1, 0.02, 0.02, 0.02, 0.02);
        if (random.nextDouble() < 0.02) base.getWorld().playSound(spark, Sound.BLOCK_FIRE_AMBIENT, 0.25F, 2.0F);
    }

    /** Wire end of a fence towards the other one, or its post if no wire points there */
    static Location end(Location base, List<Vector> wires, Vector towards) {
        for (Vector wire : wires) if (wire.dot(towards) > 0.9) return base.clone().add(wire.clone().multiply(0.5));
        return base.clone().add(towards.clone().multiply(1.0 / 16));
    }

    static boolean faces(List<Vector> wires, Vector towards) {
        for (Vector wire : wires) if (wire.dot(towards) > 0.9) return true;
        return false;
    }

    /** Arc over the gap, it flickers a few ticks with a new zigzag each time */
    static void arc(Location from, Location to) {
        double height = HEIGHTS[ThreadLocalRandom.current().nextInt(HEIGHTS.length)];
        Location a = from.clone().add(0, height, 0), b = to.clone().add(0, height, 0);
        World world = a.getWorld();
        world.playSound(a, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3F, 2.0F);
        for (int flash = 0; flash < 3; flash++) {
            Main plugin = Main.getINSTANCE();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Vector line = b.toVector().subtract(a.toVector());
                List<Location> points = new ArrayList<>();
                points.add(a);
                for (int i = 1; i < 6; i++) points.add(jitter(a.clone().add(line.clone().multiply(i / 6.0)), line, ARC_JITTER));
                points.add(b);
                draw(points);
                world.spawnParticle(Particle.ELECTRIC_SPARK, a, 3, 0.05, 0.05, 0.05, 0.05);
                world.spawnParticle(Particle.ELECTRIC_SPARK, b, 3, 0.05, 0.05, 0.05, 0.05);
            }, flash);
        }
    }

    // Off the line sideways and up or down
    private static Location jitter(Location at, Vector line, double amount) {
        if (amount == 0) return at;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector side = new Vector(-line.getZ(), 0, line.getX());
        if (side.lengthSquared() > 0) side.normalize();
        return at.add(side.multiply(random.nextDouble(-amount, amount))).add(0, random.nextDouble(-amount, amount), 0);
    }

    // Glow on the corners, a thin white core between them
    private static void draw(List<Location> points) {
        World world = points.getFirst().getWorld();
        for (int i = 0; i < points.size(); i++) {
            Location point = points.get(i);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, GLOW);
            if (i == 0) continue;
            Location previous = points.get(i - 1);
            Vector step = point.toVector().subtract(previous.toVector());
            int samples = Math.max(1, (int) (step.length() / 0.05));
            for (int s = 1; s < samples; s++) {
                world.spawnParticle(Particle.DUST, previous.clone().add(step.clone().multiply((double) s / samples)), 1, 0, 0, 0, 0, CORE);
            }
        }
    }
}
