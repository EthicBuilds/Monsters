package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.MonsterMiniBoss;
import de.ethicbuilds.monsters.monster.MonsterSpiderBoss;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import org.bukkit.Material;
import org.bukkit.entity.Monster;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

public class Sniper extends Weapon {
    private static final double BASE_DAMAGE = 1000, BOSS_DAMAGE = 50;
    private static final int KILLS = 2;
    private static final double HIP_SPREAD = 0.06;

    public Sniper() {
        this.item = new ItemStack(Material.ARROW);
        this.name = "§6Sniper";
        // Goes through 5 monsters: kills the first two, the ones behind lose half their health
        this.damage = 1000.0;
        this.pierce = 5;
        this.fireRate = 35;
        this.maxAmmu = 100;
        this.maxMagazine = 10;
        this.particle = Particle.WAX_ON;
        this.model = "sniper_3";
        this.price = 10000;
        this.upgradePrice = 30000;

        initialize();
    }

    // From the hip the shot scatters a little, through the scope it goes exactly to the crosshair
    @Override
    protected org.bukkit.util.Vector aim(org.bukkit.entity.Player player) {
        org.bukkit.util.Vector direction = super.aim(player);
        if (SniperScope.isAiming(player)) return direction;
        var random = java.util.concurrent.ThreadLocalRandom.current();
        return direction.add(new org.bukkit.util.Vector(random.nextDouble(-HIP_SPREAD, HIP_SPREAD),
                random.nextDouble(-HIP_SPREAD, HIP_SPREAD), random.nextDouble(-HIP_SPREAD, HIP_SPREAD))).normalize();
    }

    // At the workbench only the time between shots gets shorter
    @Override
    public boolean upgradesDamage() {
        return false;
    }

    @Override
    protected double damageFor(Monster monster, int index) {
        EnemyMonster enemy = Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).getEnemyMonster(monster);
        // Bosses take a fixed hit (more with upgrades) instead of dying at once
        if (enemy instanceof MonsterSpiderBoss || enemy instanceof MonsterMiniBoss) return BOSS_DAMAGE * damage / BASE_DAMAGE;
        if (index < KILLS) return damage;
        return monster.getHealth() / 2;
    }
}
