package de.ethicbuilds.monsters.monster;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;

/***
 * Scarab from the swarm event: small, quick, little health, crawls out of the sand near the players
 */
public class MonsterScarab extends EnemyMonster {

    public MonsterScarab() {
        this.health = 6;
        this.damage = 1;
        this.speed = 1.3;
        this.name = "§6Skarabäus";
        this.type = EntityType.SILVERFISH;
        this.coin = 5;
    }

    @Override
    protected void onSpawn() {
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
    public boolean showsHealthBar() {
        return false;
    }
}
