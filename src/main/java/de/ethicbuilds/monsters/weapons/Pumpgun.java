package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Pumpgun extends Weapon {
    public Pumpgun() {
        this.item = new ItemStack(Material.FLINT);
        this.name = "Schrotflinte";
        this.damage = 7.5;
        this.fireRate = 20;
        this.maxAmmu = 100;
        this.maxMagazine = 5;
        this.particle = Particle.SQUID_INK;

        this.maxDistance = 5;

        initialize();
    }

    @Override
    public void shoot(Player player) {
        if (isCoolDown) return;

        startCoolDown();

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();

        magazine--;
        player.setLevel(Math.max((ammu + magazine), 0));
        float current = Math.max(ammu + magazine, 0);
        player.setExp(current / (maxAmmu + maxMagazine));

        if (magazine <= 0) {
            reload(player);
            return;
        }

        item.setAmount(magazine);
        player.getInventory().setItem(player.getInventory().getHeldItemSlot(), item);

        int pelletCount = 8;
        double spread = 0.5;

        for (int i = 0; i < pelletCount; i++) {
            Vector pelletDirection = direction.clone();

            double angle = (Math.random() - 0.5) * spread;
            double upAngle = (Math.random() - 0.5) * spread;

            pelletDirection = pelletDirection.add(
                    new Vector(angle, upAngle, angle)
            ).normalize();

            Vector finalPelletDirection = pelletDirection;
            new BukkitRunnable() {
                double distanceTravelled = 0;
                final double stepSize = 2.0;
                @Override
                public void run() {
                    distanceTravelled += stepSize;
                    Location current = start.clone().add(finalPelletDirection.clone().multiply(distanceTravelled));

                    player.getWorld().spawnParticle(
                            particle,
                            current,
                            3,
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

                    if (distanceTravelled > maxDistance) {
                        this.cancel();
                    }
                }
            }.runTaskTimer(Main.getINSTANCE(), 0, 1);
        }
    }

}
