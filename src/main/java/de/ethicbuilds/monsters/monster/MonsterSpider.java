package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.monster.model.AnimatedModel;
import de.ethicbuilds.monsters.monster.model.BoneModel;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;

import java.io.IOException;

/***
 * Amethyst weaver from the boss wave: invisible spider carrying the Blockbench model of the user
 */
public class MonsterSpider extends EnemyMonster {
    private static BoneModel weaver;

    protected AnimatedModel model;
    // Model size and hitbox of the spider underneath
    protected float modelSize = 0.45F;
    protected double hitboxScale = 1.3;

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
