package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.monster.model.AnimatedModel;
import de.ethicbuilds.monsters.monster.model.BoneModel;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;

/***
 * Amethyst weaver from the boss wave: invisible spider carrying the Blockbench model of the user
 */
public class MonsterSpider extends EnemyMonster {
    private static BoneModel weaver;

    protected AnimatedModel model;
    // Model size and hitbox of the spider underneath
    protected float modelSize = 0.28F;
    protected double hitboxScale = 0.8;
    // Room the spider needs around and above it
    protected int room = 1;
    protected int attackCooldown = 20;
    private int nextAttack;

    public MonsterSpider() {
        this.health = 40;
        this.damage = 3;
        this.speed = 1;
        this.name = "§dAmethyst-Weberin";
        this.type = EntityType.SPIDER;
        this.coin = 40;
    }

    static BoneModel weaver() {
        if (weaver == null) {
            try {
                weaver = BoneModel.load(MonsterSpider.class.getResourceAsStream("/mobs/weberin.json"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return weaver;
    }

    @Override
    protected void onSpawn() {
        monster.setInvisible(true);
        var scale = monster.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(hitboxScale);
        model = new AnimatedModel(weaver(), monster, modelSize);
        var attack = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(damage);
        // Spiders do not attack while something rides on them, the model parts do, so the bite is done here
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!monster.isValid() || monster.isDead()) {
                    cancel();
                    return;
                }
                if (de.ethicbuilds.monsters.gameplay.manager.PauseManager.isPaused() || --nextAttack > 0) return;
                if (!(monster.getTarget() instanceof Player player) || !player.isValid() || !player.getWorld().equals(monster.getWorld())) return;
                if (!monster.getBoundingBox().expand(0.6).overlaps(player.getBoundingBox())) return;
                nextAttack = attackCooldown / 5;
                monster.attack(player);
            }
        }.runTaskTimer(Main.getINSTANCE(), 5L, 5L);
    }

    public int getRoom() {
        return room;
    }

    // The small weavers are a bit quicker than zombies, still slower than a walking player
    @Override
    protected double maxSpeed() {
        return 0.3;
    }

    @Override
    public void onDeath() {
        if (model != null) model.die();
    }

    @Override
    public void onAttack() {
        if (model != null) model.play(Math.random() < 0.3 ? "kristall" : "beissen");
    }
}
