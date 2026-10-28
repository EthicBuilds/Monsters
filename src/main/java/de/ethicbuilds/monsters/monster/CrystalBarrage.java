package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Attack of the boss weaver: it shoots the crystals from its back in an arc. One second before they land, an amethyst
 * circle marks the spot on the ground. The impact hits hard, shakes the camera nearby and stuns for a moment.
 */
public final class CrystalBarrage {
    private static final int FLIGHT_TICKS = 20;
    private static final double ARC_HEIGHT = 6;
    private static final double RADIUS = 2.2;
    private static final double DAMAGE = 10;
    private static final double SHAKE_RANGE = 6;
    private static final double STUN_RANGE = 3.5;
    private static final double SPREAD = 3.5;
    private static final Color AMETHYST = Color.fromRGB(0xA970FF);
    // Bright edge of the marker image at 28.75 px, 0.025 blocks per px
    private static final float MARK_SCALE = (float) (RADIUS / 0.71875);
    private static final Key MARK_FONT = Key.key("monsters", "ziel");

    private CrystalBarrage() {}

    /** One volley: a crystal at every living player plus a few close to them */
    public static void fire(LivingEntity boss, int extra) {
        UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
        List<Player> targets = new ArrayList<>();
        for (GamePlayer gamePlayer : users.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (player.isOnline() && !users.isPlayerDead(player.getUniqueId()) && player.getWorld().equals(boss.getWorld())
                    && player.getLocation().distance(boss.getLocation()) < 40) {
                targets.add(player);
            }
        }
        if (targets.isEmpty()) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Location> spots = new ArrayList<>();
        for (Player target : targets) spots.add(target.getLocation());
        for (int i = 0; i < extra; i++) {
            Location near = targets.get(random.nextInt(targets.size())).getLocation().add(random.nextDouble(-SPREAD, SPREAD), 0, random.nextDouble(-SPREAD, SPREAD));
            spots.add(near);
        }

        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.6F, 0.6F);
        for (int i = 0; i < spots.size(); i++) {
            Location ground = ground(spots.get(i));
            if (ground == null) continue;
            // Shot one after the other from the back, a few ticks apart
            Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> launch(boss, ground), 6L + i * 3L);
        }
    }

    private static void launch(LivingEntity boss, Location target) {
        if (!boss.isValid()) return;
        World world = boss.getWorld();
        Location start = boss.getLocation().add(0, boss.getHeight() * 0.9, 0);
        world.playSound(start, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0F, 1.4F);
        world.playSound(start, Sound.ENTITY_BREEZE_SHOOT, 0.6F, 1.6F);
        world.spawnParticle(Particle.DUST, start, 12, 0.4, 0.3, 0.4, new Particle.DustOptions(AMETHYST, 1.4F));

        ItemDisplay crystal = world.spawn(start, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.AMETHYST_CLUSTER));
            display.setTeleportDuration(1);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setGlowing(true);
            display.setGlowColorOverride(AMETHYST);
            display.setPersistent(false);
        });

        // Marker on the ground from the design session: the circle shows where it lands, the thin ring grows up to it
        TextDisplay circle = decal(target, '', MARK_SCALE, 0.02);
        TextDisplay countdown = decal(target, '', 0.3F, 0.03);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                double t = tick / (double) FLIGHT_TICKS;
                if (tick % 2 == 0) {
                    float scale = (float) (0.3 + (MARK_SCALE - 0.3) * t);
                    countdown.setInterpolationDelay(0);
                    countdown.setInterpolationDuration(2);
                    countdown.setTransformation(flat(scale));
                }
                if (tick == FLIGHT_TICKS - 6) circle.text(mark(''));

                Vector way = target.toVector().subtract(start.toVector());
                Location at = start.clone().add(way.multiply(t)).add(0, ARC_HEIGHT * 4 * t * (1 - t), 0);
                crystal.teleport(at);
                float spin = (float) (tick * 0.5);
                crystal.setInterpolationDelay(0);
                crystal.setInterpolationDuration(1);
                crystal.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(spin, 0.7071F, 0, 0.7071F),
                        new Vector3f(1.4F), new AxisAngle4f()));
                world.spawnParticle(Particle.DUST, at, 3, 0.1, 0.1, 0.1, new Particle.DustOptions(AMETHYST, 1.0F));

                if (tick >= FLIGHT_TICKS) {
                    crystal.remove();
                    circle.remove();
                    countdown.remove();
                    impact(boss, target);
                    cancel();
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 1L);
    }

    private static void impact(LivingEntity boss, Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.6F, 0.7F);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 1.5F);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2F, 0.6F);
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), 60, 0.8, 0.3, 0.8, 0.2, Material.AMETHYST_BLOCK.createBlockData());
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(0, 0.4, 0), 40, 1.2, 0.4, 1.2,
                new Particle.DustTransition(AMETHYST, Color.WHITE, 1.6F));
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.15);
        // Burst ring outwards
        for (double r = 0.5; r <= RADIUS + 0.6; r += 0.6) ring(world, at, r, AMETHYST, 20);

        UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
        for (GamePlayer gamePlayer : users.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline() || users.isPlayerDead(player.getUniqueId()) || !player.getWorld().equals(world)) continue;
            double distance = player.getLocation().distance(at);
            if (distance <= RADIUS) player.damage(DAMAGE * (1 - 0.4 * distance / RADIUS), boss);
            if (distance <= SHAKE_RANGE) shake(player, distance);
            if (distance <= STUN_RANGE) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false, true));
                player.sendActionBar("§5§lErschüttert!");
            }
        }
    }

    // Camera shake: hurt tilts from alternating sides, weaker further away
    private static void shake(Player player, double distance) {
        int times = distance < STUN_RANGE ? 4 : 2;
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline() || count >= times) {
                    cancel();
                    return;
                }
                player.sendHurtAnimation(count % 2 == 0 ? 60 : -60);
                count++;
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 2L);
    }

    private static TextDisplay decal(Location at, char glyph, float scale, double lift) {
        Location spot = at.clone().add(0, lift, 0);
        spot.setYaw(0);
        spot.setPitch(0);
        return at.getWorld().spawn(spot, TextDisplay.class, display -> {
            display.text(mark(glyph));
            display.setBillboard(Display.Billboard.FIXED);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setDefaultBackground(false);
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(flat(scale));
            display.setPersistent(false);
        });
    }

    private static Component mark(char glyph) {
        return Component.text(glyph, NamedTextColor.WHITE).font(MARK_FONT);
    }

    // Lies flat facing up; the baseline sits 1.5 text pixels above the origin, moved back so the centre is on the spot
    private static Transformation flat(float scale) {
        return new Transformation(new Vector3f(0, 0, 1.5F * 0.025F * scale), new AxisAngle4f((float) -Math.PI / 2, 1, 0, 0),
                new Vector3f(scale), new AxisAngle4f());
    }

    private static void ring(World world, Location center, double radius, Color color, int points) {
        if (radius < 0.15) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.1F);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius), 1, 0, 0, 0, 0, dust);
        }
    }

    // Top of the ground below the spot, so the circle lies on the floor
    private static Location ground(Location spot) {
        Block block = spot.getBlock();
        for (int i = 0; i < 6 && !block.getRelative(0, -1, 0).isSolid(); i++) block = block.getRelative(0, -1, 0);
        if (!block.getRelative(0, -1, 0).isSolid()) return null;
        return block.getLocation().add(0.5, 0.02, 0.5);
    }
}
