package de.ethicbuilds.monsters.monster;

import org.bukkit.entity.EntityType;

public class MonsterZombie extends EnemyMonster {
    public MonsterZombie() {
        this(EntityType.ZOMBIE, "§7Zombie");
    }

    /** Same zombie values for the other kinds of the later waves (husk, parched, zombie villager, vindicator) */
    public MonsterZombie(EntityType type, String name) {
        this.health = 20;
        this.damage = 2;
        this.speed = 1;
        this.name = name;
        this.type = type;
        this.coin = 20;
    }
}
