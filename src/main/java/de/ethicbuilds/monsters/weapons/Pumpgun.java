package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Pumpgun extends Weapon {
    public Pumpgun() {
        this.item = new ItemStack(Material.FLINT);
        this.name = "§6Schrotflinte";
        this.nameEn = "§6Shotgun";
        this.damage = 10.0;
        this.fireRate = BASE_FIRE_RATE;
        this.maxAmmu = 300;
        this.maxMagazine = 5;
        this.particle = Particle.SQUID_INK;
        this.model = "schrot_v1";
        this.price = 2500;
        this.upgradePrice = 15000;

        this.sound = Sound.ENTITY_FIREWORK_ROCKET_BLAST;
        this.pitch = 0.6F;

        this.maxDistance = BASE_DISTANCE;

        initialize();
    }

    private static final int BASE_FIRE_RATE = 20, BASE_DISTANCE = 7;

    // Each level fires 10 % quicker and reaches one block further
    @Override
    protected void onUpgrade() {
        fireRate = Math.max(1, (int) Math.round(BASE_FIRE_RATE * Math.pow(0.9, level)));
        maxDistance = BASE_DISTANCE + level;
    }

    @Override
    public void shoot(Player player) {
        if (isCoolDown) return;

        startCoolDown(player);

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();

        magazine--;
        AmmoHud.show(player, this);

        if (magazine <= 0) {
            reload(player);
            return;
        }

        player.playSound(player.getLocation(), sound, 0.5F, pitch);
        Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).shotNoise(player);

        int pelletCount = 8;
        double spread = 0.7;

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

                    for (Entity entity : current.getWorld().getNearbyEntities(current, 0.5, 0.5, 0.5)) {
                        if (!(entity instanceof Monster)) continue;
                        {
                            ShotDamage.shoot((LivingEntity) entity, damage, player, current);
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
