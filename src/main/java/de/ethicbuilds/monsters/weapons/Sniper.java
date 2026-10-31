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
    // Kills normal monsters at once until about wave 15, later it needs upgrades (balance 31.10.)
    private static final double BASE_DAMAGE = 60, BOSS_SHARE = 0.5, BEHIND_SHARE = 0.5;
    private static final int FULL_HITS = 2;
    // Time between shots per level, each level fires a bit quicker besides the damage
    private static final int[] FIRE_RATES = {35, 32, 29, 26, 23, 20};
    private static final double HIP_SPREAD = 0.06;

    public Sniper() {
        this.item = new ItemStack(Material.ARROW);
        this.name = "§6Sniper";
        // Goes through 5 monsters: the first two take the full damage, the ones behind half
        this.damage = BASE_DAMAGE;
        this.pierce = 5;
        this.fireRate = FIRE_RATES[0];
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

    @Override
    protected void onUpgrade() {
        fireRate = FIRE_RATES[Math.min(level, FIRE_RATES.length - 1)];
    }

    @Override
    protected double damageFor(Monster monster, int index) {
        EnemyMonster enemy = Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).getEnemyMonster(monster);
        // Bosses take half, so upgrades help against them too without blowing them away
        if (enemy instanceof MonsterSpiderBoss || enemy instanceof MonsterMiniBoss) return damage * BOSS_SHARE;
        return index < FULL_HITS ? damage : damage * BEHIND_SHARE;
    }
}
