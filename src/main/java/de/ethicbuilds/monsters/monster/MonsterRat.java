package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.monster.model.AnimatedModel;
import de.ethicbuilds.monsters.monster.model.BoneModel;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;

import java.io.IOException;

/***
 * Sewer rat (user 31.10.): a few come out of a crate that is broken open now and then and attack whoever disturbed
 * them. An invisible baby zombie carries the model, small and quick, little health.
 */
public class MonsterRat extends EnemyMonster {
    private static BoneModel rat;

    private AnimatedModel model;

    public MonsterRat() {
        this.health = 6;
        this.damage = 1;
        this.speed = 1.3;
        this.name = "§7Ratte";
        this.type = EntityType.ZOMBIE;
        this.coin = 5;
    }

    private static BoneModel rat() {
        if (rat == null) {
            try {
                rat = BoneModel.load(MonsterRat.class.getResourceAsStream("/mobs/ratte.json"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return rat;
    }

    @Override
    protected void onSpawn() {
        if (monster instanceof Zombie zombie) zombie.setBaby();
        monster.setInvisible(true);
        monster.setCanPickupItems(false);
        monster.setSilent(true);
        if (monster.getEquipment() != null) monster.getEquipment().clear();
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (monster.isValid() && monster.getEquipment() != null) monster.getEquipment().clear();
        }, 1L);
        model = new AnimatedModel(rat(), monster, 1.0F);
        model.enableSteering(false);
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_SILVERFISH_AMBIENT, 1.0F, 0.8F);
    }

    @Override
    public void onDeath() {
        if (model == null) return;
        model.die();
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_SILVERFISH_DEATH, 0.8F, 0.8F);
    }

    @Override
    public void onHit() {
        if (model != null) monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_SILVERFISH_HURT, 0.8F, 0.8F);
    }

    @Override
    public void onAttack() {
        if (model != null) model.play("beissen");
    }
}
