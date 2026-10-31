package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/***
 * Climber from wave 10 (user 31.10.): when its player stands on a roof or ledge above it, it jumps up there
 * instead of walking around. Thin and a bit quicker than a zombie.
 */
public class MonsterClimber extends MonsterZombie {
    private static final double MIN_HEIGHT = 2.5, MAX_HEIGHT = 7, MAX_REACH = 9;
    private static final int CHECK_EVERY = 20, COOLDOWN = 5 * 20;

    private int cooldown;

    public MonsterClimber() {
        super(EntityType.ZOMBIE, "§7Kletterer");
        this.health = 18;
        this.speed = 1.15;
    }

    @Override
    protected String modelId() {
        return "kletterer";
    }

    @Override
    protected void onSpawn() {
        super.onSpawn();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!monster.isValid() || monster.isDead()) {
                    cancel();
                    return;
                }
                if ((cooldown -= CHECK_EVERY) > 0 || !LivingPlayers.waveRunning() || !monster.isOnGround()) return;
                Player target = getTarget();
                if (!LivingPlayers.alive(target) || !target.getWorld().equals(monster.getWorld())) return;
                Location from = monster.getLocation(), to = target.getLocation();
                double height = to.getY() - from.getY();
                double flat = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
                if (height < MIN_HEIGHT || height > MAX_HEIGHT || flat > MAX_REACH) return;
                leap(from, to, height, flat);
            }
        }.runTaskTimer(Main.getINSTANCE(), 20L + ThreadLocalRandom.current().nextInt(20), CHECK_EVERY);
    }

    // High enough to clear the edge, forward enough to land up there
    private void leap(Location from, Location to, double height, double flat) {
        cooldown = COOLDOWN;
        if (model() != null) model().play("springen");
        Vector way = to.toVector().subtract(from.toVector()).setY(0);
        if (way.lengthSquared() > 0.01) way.normalize().multiply(Math.min(1.0, 0.25 + flat * 0.08));
        monster.setVelocity(way.setY(Math.min(1.25, 0.55 + height * 0.12)));
        monster.getWorld().playSound(from, Sound.ENTITY_SPIDER_STEP, 1.0F, 0.6F);
        monster.getWorld().spawnParticle(Particle.BLOCK, from.clone().add(0, 0.1, 0), 10, 0.3, 0.05, 0.3,
                from.clone().subtract(0, 0.5, 0).getBlock().getBlockData());
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (monster.isValid() && !monster.isOnGround() && model() != null) model().play("klettern");
        }, 6L);
    }
}
