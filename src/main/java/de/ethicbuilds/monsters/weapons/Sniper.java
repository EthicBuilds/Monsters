package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.MonsterMiniBoss;
import de.ethicbuilds.monsters.monster.MonsterSpiderBoss;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import org.bukkit.Material;
import org.bukkit.entity.Monster;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

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
        // Left click shoots, the arm does not swing forward
        item.setData(DataComponentTypes.SWING_ANIMATION,
                SwingAnimation.swingAnimation().type(SwingAnimation.Animation.NONE).build());

        initialize();
    }

    // From the hip the shot scatters a little, through the scope it goes exactly to the crosshair
    @Override
    protected Vector aim(Player player) {
        Vector direction = super.aim(player);
        if (SniperScope.isAiming(player)) return direction;
        var random = ThreadLocalRandom.current();
        return direction.add(new Vector(random.nextDouble(-HIP_SPREAD, HIP_SPREAD),
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
