package de.ethicbuilds.monsters.monster;

import org.bukkit.entity.EntityType;

public class MonsterZombie extends EnemyMonster {
    public MonsterZombie() {
        this.health = 20;
        this.damage = 2;
        this.speed = 1;
        this.name = "§7Zombie";
        this.type = EntityType.ZOMBIE;
        this.coin = 20;
    }
}
