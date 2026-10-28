package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

@Getter @Setter
public abstract class Weapon {
    private Main plugin;

    protected ItemStack item;
    protected String name;
    protected double damage;
    protected int fireRate;
    protected int ammu;
    protected int magazine;
    protected int maxAmmu;
    protected int maxMagazine;
    protected int price;
    // First workbench upgrade, then 1.5 times each level
    protected int upgradePrice = 10000;
    protected String model;

    protected Sound sound = Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR;
    protected float pitch = 2.0F;

    protected int maxDistance = 50;
    // How many monsters one shot goes through
    protected int pierce = 1;

    // Workbench upgrades: every level +25 % damage and magazine
    public static final int MAX_LEVEL = 4;
    protected int level = 0;
    private double baseDamage;
    private int baseMagazine;

    protected Particle particle;

    protected boolean isCoolDown = false;

    protected void initialize() {
        baseDamage = damage;
        baseMagazine = maxMagazine;
        ammu = maxAmmu;
        magazine = maxMagazine;

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        // One weapon per slot, ammo is not shown through the stack size
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        if (model != null) GameItems.setModel(item, model);
    }

    public void shoot(Player player) {
        if (isCoolDown) return;

        startCoolDown();

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();

        magazine--;
        AmmoHud.show(player, this);

        if (magazine <= 0) {
            reload(player);
            return;
        }

        player.playSound(player.getLocation(), sound, 0.5F, pitch);

        Set<org.bukkit.entity.Entity> hit = new HashSet<>();
        new BukkitRunnable() {
            double distanceTravelled = 0;
            @Override
            public void run() {
                // 2 blocks per tick, checked every block so no monster is skipped
                for (int step = 0; step < 2; step++) {
                    distanceTravelled += 1.0;
                    Location current = start.clone().add(direction.clone().multiply(distanceTravelled));

                    if (step == 1) player.getWorld().spawnParticle(particle, current, 5, 0, 0, 0, 0.01);

                    if (current.getBlock().getType().isSolid() || distanceTravelled > maxDistance) {
                        this.cancel();
                        return;
                    }

                    for (org.bukkit.entity.Entity entity : current.getWorld().getNearbyEntities(current, 0.5, 0.5, 0.5)) {
                        if (!(entity instanceof Monster monster) || !hit.add(entity)) continue;
                        ShotDamage.shoot(monster, damage, player, current);
                        if (hit.size() >= pierce) {
                            this.cancel();
                            return;
                        }
                    }
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 0, 1);
    }

    public void reload(Player player) {
        int itemSlot = player.getInventory().getHeldItemSlot();

        if (!item.equals(player.getInventory().getItem(itemSlot))) {
            return;
        }
        if (ammu <= 0) {
            AmmoHud.pause(player);
            player.sendTitle("§4Keine Munition", "§7Kaufe Munition an der §aMunitionskiste", 0, 20, 0);
            return;
        }

        while (magazine < maxMagazine) {
            magazine++;
            ammu--;

            if (ammu <= 0) {
                break;
            }
        }
        AmmoHud.show(player, this);
    }

    /** Full again: after the next reload both magazine and stock are at their maximum */
    public void refill() {
        ammu = maxAmmu + (maxMagazine - magazine);
    }

    public void upgrade() {
        if (level >= MAX_LEVEL) return;
        level++;
        damage = baseDamage * (1 + 0.25 * level);
        maxMagazine = (int) Math.round(baseMagazine * (1 + 0.25 * level));
    }

    public double nextDamage() {
        return baseDamage * (1 + 0.25 * Math.min(level + 1, MAX_LEVEL));
    }

    public int nextMagazine() {
        return (int) Math.round(baseMagazine * (1 + 0.25 * Math.min(level + 1, MAX_LEVEL)));
    }

    public boolean isFull() {
        return ammu >= maxAmmu && magazine >= maxMagazine;
    }

    protected void startCoolDown() {
        isCoolDown = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                isCoolDown = false;
            }
        }.runTaskLater(Main.getINSTANCE(), fireRate);
    }
}
