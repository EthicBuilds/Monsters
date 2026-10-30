package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.monster.model.AnimatedModel;
import de.ethicbuilds.monsters.monster.model.BoneModel;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Crawler of the horde (design session): small, on all fours, quick. An invisible baby zombie carries the model,
 * so the hitbox is small as well.
 */
public class MonsterCrawler extends EnemyMonster {
    private static final long SPAWN_TICKS = 20;
    private static final long HISS_MIN = 5 * 20, HISS_MAX = 9 * 20;
    private static BoneModel crawler;

    private AnimatedModel model;

    public MonsterCrawler() {
        this.health = 10;
        this.damage = 2;
        this.speed = 1;
        this.name = "§7Krabbler";
        this.type = EntityType.ZOMBIE;
        this.coin = 20;
    }

    private static BoneModel crawler() {
        if (crawler == null) {
            try {
                crawler = BoneModel.load(MonsterCrawler.class.getResourceAsStream("/mobs/krabbler.json"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return crawler;
    }

    @Override
    protected void onSpawn() {
        if (monster instanceof Zombie zombie) zombie.setBaby();
        monster.setInvisible(true);
        monster.setCanPickupItems(false);
        var equipment = monster.getEquipment();
        if (equipment != null) equipment.clear();
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (monster.isValid() && monster.getEquipment() != null) monster.getEquipment().clear();
        }, 1L);
        model = new AnimatedModel(crawler(), monster, 1.0F);
        model.enableSteering(false);
        model.play("spawn");
        // Own sounds: the hiss together with the open mouth
        monster.setSilent(true);
        hissLater();
        if (monster instanceof Mob mob) {
            mob.setAware(false);
            Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
                if (mob.isValid() && !PauseManager.isPaused()) mob.setAware(true);
            }, SPAWN_TICKS);
        }
    }

    private void hissLater() {
        long delay = ThreadLocalRandom.current().nextLong(HISS_MIN, HISS_MAX);
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (!monster.isValid() || monster.isDead()) return;
            if (!PauseManager.isPaused()) {
                model.play("fauchen");
                monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_CAT_HISS, 0.9F, 0.6F);
                monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.4F, 0.6F);
            }
            hissLater();
        }, delay);
    }

    @Override
    public void onDeath() {
        if (model == null) return;
        model.die();
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_ZOMBIE_DEATH, 0.8F, 1.6F);
    }

    @Override
    public void onHit() {
        if (model != null) monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_ZOMBIE_HURT, 0.8F, 1.6F);
    }

    @Override
    public void onAttack() {
        if (model != null) model.play("beissen");
    }
}
