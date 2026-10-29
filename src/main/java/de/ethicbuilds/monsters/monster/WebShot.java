package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/***
 * Web shot of the boss weaver from level 2: a web flies in an arc to where the player stood, whoever is hit
 * there is spun in and very slow for a moment
 */
public final class WebShot {
    public static final int FLIGHT = 16;
    public static final double RADIUS = 2.5;
    private static final double ARC = 3;
    private static final int SLOW_TICKS = 60;

    private WebShot() {}

    public static void fire(LivingEntity boss, Location to) {
        Location from = boss.getLocation().add(0, 1.5, 0);
        boss.getWorld().playSound(from, Sound.ENTITY_SPIDER_AMBIENT, 1.0F, 0.5F);
        ItemDisplay web = boss.getWorld().spawn(from, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.COBWEB));
            display.setBillboard(Display.Billboard.CENTER);
            display.setTeleportDuration(1);
            display.setPersistent(false);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.2F), new AxisAngle4f()));
        });
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                double t = tick / (double) FLIGHT;
                Location at = from.clone().add(to.clone().subtract(from).toVector().multiply(t));
                at.add(0, Math.sin(Math.PI * t) * ARC, 0);
                web.teleport(at);
                if (tick < FLIGHT) return;
                web.remove();
                impact(to);
                cancel();
            }
        }.runTaskTimer(Main.getINSTANCE(), 1L, 1L);
    }

    private static void impact(Location at) {
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 40, 1.2, 0.4, 1.2, Material.COBWEB.createBlockData());
        at.getWorld().playSound(at, Sound.BLOCK_WOOL_PLACE, 1.0F, 0.6F);
        UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
        for (Player player : at.getWorld().getPlayers()) {
            if (users.isPlayerDead(player.getUniqueId()) || player.getLocation().distance(at) > RADIUS) continue;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_TICKS, 2, false, false));
            player.sendActionBar("§fEingesponnen!");
        }
    }
}
