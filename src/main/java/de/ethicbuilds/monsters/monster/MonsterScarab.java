package de.ethicbuilds.monsters.monster;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import de.ethicbuilds.monsters.monster.model.AnimatedModel;
import de.ethicbuilds.monsters.monster.model.BoneModel;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;

/***
 * Scarab from the swarm event: small, quick, little health, crawls out of the sand near the players.
 * An invisible silverfish carries the beetle model from the design session.
 */
public class MonsterScarab extends EnemyMonster {
    private static final float MODEL_SIZE = 1.0F;
    private static BoneModel beetle;

    private AnimatedModel model;

    public MonsterScarab() {
        this.health = 6;
        this.damage = 1;
        this.speed = 1.3;
        this.name = "§6Skarabäus";
        this.type = EntityType.SILVERFISH;
        this.coin = 5;
    }

    private static BoneModel beetle() {
        if (beetle == null) {
            try {
                beetle = BoneModel.load(MonsterScarab.class.getResourceAsStream("/mobs/skarabaeus.json"));
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
        return beetle;
    }

    @Override
    protected void onSpawn() {
        monster.setInvisible(true);
        model = new AnimatedModel(beetle(), monster, MODEL_SIZE);
        // Not into the walls and no friends out of the stone
        var goals = Bukkit.getMobGoals();
        goals.removeGoal((org.bukkit.entity.Silverfish) monster, VanillaGoal.SILVERFISH_MERGE_WITH_STONE);
        goals.removeGoal((org.bukkit.entity.Silverfish) monster, VanillaGoal.SILVERFISH_WAKE_UP_FRIENDS);
        var attack = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(damage);
    }

    @Override
    protected double maxSpeed() {
        return 0.28;
    }

    @Override
    public void onDeath() {
        if (model != null) model.die();
    }

    @Override
    public void onAttack() {
        if (model != null) model.play("beissen");
    }

    @Override
    public boolean showsHealthBar() {
        return false;
    }
}
