package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/***
 * Acid ball of the spitter: flies slowly in an arc to where the player stood, the circle shows the spot from the
 * start. Where it lands a small puddle stays and burns whoever stands in it.
 */
public final class AcidShot {
    public static final double RADIUS = 1.5;
    private static final double SPEED = 0.6, ARC = 1.5, ARC_PER_BLOCK = 0.15;
    private static final int MIN_FLIGHT = 12, PUDDLE_TICKS = 60, PUDDLE_STEP = 5;
    private static final double PUDDLE_DAMAGE = 1;
    private static final Color ACID = Color.fromRGB(0x7CD43A);

    private AcidShot() {}

    // Model of the design session (stored at half size like every bone), the slime ball if the pack misses it
    private static final float BALL_SCALE = 3.0F;

    private static ItemStack ball() {
        ItemStack item = new ItemStack(Material.SLIME_BALL);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("monsters", "saeureball/kugel")));
        return item;
    }

    public static void fire(LivingEntity spitter, Location to) {
        Location from = spitter.getEyeLocation();
        World world = spitter.getWorld();
        double distance = from.distance(to);
        int flight = Math.max(MIN_FLIGHT, (int) Math.round(distance / SPEED));
        double arc = ARC + distance * ARC_PER_BLOCK;
        GroundMark mark = GroundMark.warn(to, GroundMark.DANGER, RADIUS, flight);
        world.playSound(from, Sound.ENTITY_LLAMA_SPIT, 1.0F, 0.6F);
        ItemDisplay ball = world.spawn(from, ItemDisplay.class, display -> {
            display.setItemStack(ball());
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(1);
            display.setPersistent(false);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(BALL_SCALE), new AxisAngle4f()));
        });
        Particle.DustOptions dust = new Particle.DustOptions(ACID, 1.2F);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                double t = tick / (double) flight;
                Location at = from.clone().add(to.clone().subtract(from).toVector().multiply(t));
                at.add(0, Math.sin(Math.PI * t) * arc, 0);
                // A wall on the way stops it early
                boolean blocked = tick > 2 && tick < flight && !at.getBlock().isPassable();
                if (!blocked) ball.teleport(at);
                world.spawnParticle(Particle.DUST, at, 2, 0.05, 0.05, 0.05, 0, dust);
                if (tick % 2 == 0) world.spawnParticle(Particle.ITEM_SLIME, at, 1, 0.05, 0.05, 0.05, 0);
                if (tick < flight && !blocked) return;
                ball.remove();
                cancel();
                if (blocked) {
                    mark.clear();
                    Location ground = GroundMark.ground(ball.getLocation());
                    puddle(ground != null ? ground : ball.getLocation());
                } else {
                    puddle(to);
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 1L, 1L);
    }

    private static void puddle(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_SLIME_SQUISH, 1.0F, 0.6F);
        world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.5F, 1.4F);
        world.spawnParticle(Particle.ITEM_SLIME, at.clone().add(0, 0.2, 0), 14, 0.5, 0.1, 0.5, 0.05);
        GroundMark.still(at, GroundMark.DANGER, RADIUS, PUDDLE_TICKS);
        Particle.DustOptions dust = new Particle.DustOptions(ACID, 1.0F);
        Set<UUID> told = new HashSet<>();
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= PUDDLE_TICKS) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, at.clone().add(0, 0.15, 0), 6, RADIUS * 0.5, 0.05, RADIUS * 0.5, 0, dust);
                if (tick % 20 == 0 && LivingPlayers.waveRunning()) {
                    world.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.2, 0), 4, 0.6, 0.05, 0.6, 0.01);
                    for (Player player : LivingPlayers.near(at, RADIUS + 1)) {
                        Location feet = player.getLocation();
                        double dx = feet.getX() - at.getX(), dz = feet.getZ() - at.getZ();
                        if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(feet.getY() - at.getY()) > 1.2) continue;
                        // No source: no knockback out of the puddle
                        player.damage(PUDDLE_DAMAGE);
                        if (told.add(player.getUniqueId())) Lang.note(player, Lang.t(player, "§aSäure!", "§aAcid!"));
                    }
                }
                tick += PUDDLE_STEP;
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, PUDDLE_STEP);
    }
}
