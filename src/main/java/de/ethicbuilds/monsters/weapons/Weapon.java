package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.perk.PerkManager;
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
    // English name, without one the German name counts for both
    protected String nameEn;
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
    private static final double LUCKY_PIERCE = 0.15;

    // Workbench upgrades: damage +25 % for levels 1-2 and x1.5 for levels 3-5, magazine +25 % per level,
    // level V also goes through one monster more
    public static final int MAX_LEVEL = 5;
    protected int level = 0;
    private double baseDamage;
    private int baseMagazine;
    private int baseFireRate;
    private int basePierce;

    protected Particle particle;

    protected boolean isCoolDown = false;

    private static MonsterManager monsterManager;
    private static PerkManager perkManager;

    protected void initialize() {
        baseDamage = damage;
        baseMagazine = maxMagazine;
        baseFireRate = fireRate;
        basePierce = pierce;
        ammu = maxAmmu;
        magazine = maxMagazine;

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        // One weapon per slot, ammo is not shown through the stack size
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        if (model != null) GameItems.setModel(item, model);
    }

    /** Name in the player's language */
    public String getName(Player player) {
        return Lang.t(player, name, nameEn != null ? nameEn : name);
    }

    /** Item name in the owner's language, before the item goes into the inventory */
    public void localize(Player player) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getName(player));
        item.setItemMeta(meta);
    }

    public void shoot(Player player) {
        if (isCoolDown) return;

        startCoolDown(player);

        Location start = player.getEyeLocation();
        Vector direction = aim(player);

        magazine--;
        AmmoHud.show(player, this);

        if (magazine <= 0) {
            reload(player);
            return;
        }

        player.playSound(player.getLocation(), sound, 0.5F, pitch);
        monsterManager().shotNoise(player);

        Set<org.bukkit.entity.Entity> hit = new HashSet<>();
        // With a bit of luck a normal shot goes through into a second monster
        int maxHits = pierce > 1 ? pierce : Math.random() < LUCKY_PIERCE ? 2 : 1;
        new BukkitRunnable() {
            double distanceTravelled = 0;
            int kills = 0;

            @Override
            public void run() {
                // 2 blocks per tick, checked every block so no monster is skipped
                for (int step = 0; step < 2; step++) {
                    distanceTravelled += 1.0;
                    Location current = start.clone().add(direction.clone().multiply(distanceTravelled));

                    if (step == 1) player.getWorld().spawnParticle(particle, current, 5, 0, 0, 0, 0.01);

                    if (current.getBlock().getType().isSolid() || distanceTravelled > maxDistance) {
                        finish();
                        return;
                    }

                    for (org.bukkit.entity.Entity entity : current.getWorld().getNearbyEntities(current, 0.5, 0.5, 0.5)) {
                        if (!(entity instanceof Monster monster) || !hit.add(entity)) continue;
                        ShotDamage.shoot(monster, damageFor(monster, hit.size() - 1), player, current);
                        if (monster.isDead() || monster.getHealth() <= 0) kills++;
                        if (hit.size() >= maxHits) {
                            finish();
                            return;
                        }
                    }
                }
            }

            private void finish() {
                cancel();
                if (kills >= 2) MultiKill.reward(player, kills);
            }
        }.runTaskTimer(Main.getINSTANCE(), 0, 1);
    }

    /** Direction of the shot */
    protected Vector aim(Player player) {
        return player.getEyeLocation().getDirection().normalize();
    }

    public void reload(Player player) {
        int itemSlot = player.getInventory().getHeldItemSlot();

        if (!item.equals(player.getInventory().getItem(itemSlot))) {
            return;
        }
        if (ammu <= 0) {
            AmmoHud.pause(player);
            de.ethicbuilds.monsters.lang.Overlay.small(player,
                    de.ethicbuilds.monsters.lang.Overlay.line(de.ethicbuilds.monsters.lang.Overlay.AMMO, de.ethicbuilds.monsters.lang.Overlay.RED, "Keine Munition", "an der Munitionskiste kaufen"),
                    de.ethicbuilds.monsters.lang.Overlay.line(de.ethicbuilds.monsters.lang.Overlay.AMMO, de.ethicbuilds.monsters.lang.Overlay.RED, "No ammo", "buy it at the ammo crate"));
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

    /** Damage for the monster hit as number index (0 = first) by one shot */
    protected double damageFor(Monster monster, int index) {
        return damage;
    }

    /** Full again: after the next reload both magazine and stock are at their maximum */
    public void refill() {
        ammu = maxAmmu + (maxMagazine - magazine);
    }

    public void upgrade() {
        if (level >= MAX_LEVEL) return;
        level++;
        if (level == MAX_LEVEL) pierce = basePierce + 1;
        if (!upgradesDamage()) {
            fireRate = fireRateAt(level);
            return;
        }
        damage = baseDamage * boost(level);
        maxMagazine = (int) Math.round(baseMagazine * (1 + 0.25 * level));
        onUpgrade();
    }

    /** Extras of a weapon per level, after damage and magazine */
    protected void onUpgrade() {}

    // +25 % for the first two levels, the others each give 1.5 times as much
    protected double boost(int level) {
        return level <= 2 ? 1 + 0.25 * level : 1.5 * boost(level - 1);
    }

    /** false: the workbench only makes it fire faster (sniper), damage and magazine stay */
    public boolean upgradesDamage() {
        return true;
    }

    public int nextFireRate() {
        return fireRateAt(Math.min(level + 1, MAX_LEVEL));
    }

    // 15 % less time between shots for the first two levels, the others each fire 1.5 times as fast
    private int fireRateAt(int level) {
        double rate = level <= 2 ? baseFireRate * (1 - 0.15 * level) : fireRateAt(level - 1) / 1.5;
        return Math.max(1, (int) Math.round(rate));
    }

    public double nextDamage() {
        return baseDamage * boost(Math.min(level + 1, MAX_LEVEL));
    }

    public int nextMagazine() {
        return (int) Math.round(baseMagazine * (1 + 0.25 * Math.min(level + 1, MAX_LEVEL)));
    }

    public boolean isFull() {
        return ammu >= maxAmmu && magazine >= maxMagazine;
    }

    // Quick Reload makes the weapon ready sooner
    protected void startCoolDown(Player player) {
        isCoolDown = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                isCoolDown = false;
            }
        }.runTaskLater(Main.getINSTANCE(), perkManager().cooldown(player, fireRate));
    }

    private static MonsterManager monsterManager() {
        if (monsterManager == null) monsterManager = Main.getINSTANCE().getInjector().getInstance(MonsterManager.class);
        return monsterManager;
    }

    private static PerkManager perkManager() {
        if (perkManager == null) perkManager = Main.getINSTANCE().getInjector().getInstance(PerkManager.class);
        return perkManager;
    }
}
