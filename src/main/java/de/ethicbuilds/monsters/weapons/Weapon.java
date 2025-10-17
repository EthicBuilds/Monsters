package de.ethicbuilds.monsters.weapons;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

@Getter @Setter
public abstract class Weapon {
    private Main plugin;

    protected ItemStack item;
    protected String name;
    protected double damage;
    protected int fireRate;
    protected int amo;
    protected int magazine;

    public void shoot(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();

        new BukkitRunnable() {
            double distanceTravelled = 0;
            final double stepSize = 0.5;

            @Override
            public void run() {
                distanceTravelled += stepSize;
                Location current = start.clone().add(direction.clone().multiply(distanceTravelled));

                player.getWorld().spawnParticle(
                        Particle.FLAME,
                        current,
                        5,
                        0, 0, 0,
                        0.01
                );

                if (current.getBlock().getType().isSolid()) {
                    this.cancel();
                    return;
                }

                for (org.bukkit.entity.Entity entity : current.getWorld().getNearbyEntities(current, 0.5, 0.5, 0.5)) {
                    if (entity instanceof Player) continue;
                    if (entity instanceof org.bukkit.entity.LivingEntity) {
                        ((org.bukkit.entity.LivingEntity) entity).damage(damage, player);
                        this.cancel();
                        return;
                    }
                }

                if (distanceTravelled > 20) {
                    this.cancel();
                }
            }
        }.runTaskTimer(Main.getInstance(), 0, 1);
    }

}
