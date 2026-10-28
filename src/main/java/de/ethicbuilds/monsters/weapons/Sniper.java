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

    @Override
    protected double damageFor(Monster monster, int index) {
        EnemyMonster enemy = Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).getEnemyMonster(monster);
        // Bosses take a fixed hit (more with upgrades) instead of dying at once
        if (enemy instanceof MonsterSpiderBoss || enemy instanceof MonsterMiniBoss) return BOSS_DAMAGE * damage / BASE_DAMAGE;
        if (index < KILLS) return damage;
        return monster.getHealth() / 2;
    }
}
