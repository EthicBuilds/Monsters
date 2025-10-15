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

            @Override
            public void run() {
                distanceTravelled += 0.5; // Schrittweite pro Tick
                Location current = start.clone().add(direction.clone().multiply(distanceTravelled));

                // Partikel spawnen
                player.getWorld().spawnParticle(
                        Particle.FLAME, // Partikeltyp
                        current,        // Position
                        5,              // Anzahl
                        0, 0, 0,        // Offset
                        0.01            // Geschwindigkeit
                );

                // Abbruchbedingung nach 20 Blöcken
                if (distanceTravelled > 20) {
                    this.cancel();
                }
            }
        }.runTaskTimer(Main.getInstance(), 0L, 1L);
    }
}
