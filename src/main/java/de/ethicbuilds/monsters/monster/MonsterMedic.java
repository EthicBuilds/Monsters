package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/***
 * Field medic zombie from wave 14 (user 31.10.): stops now and then and heals the hurt monsters around it,
 * a target to take out first
 */
public class MonsterMedic extends MonsterZombie {
    private static final int HEAL_EVERY = 5 * 20;
    private static final double RADIUS = 6, SHARE = 0.25;

    public MonsterMedic() {
        super(EntityType.ZOMBIE, "§aFeldsanitäter");
        this.health = 24;
    }

    @Override
    protected String modelId() {
        return "feldsanitaeter";
    }

    @Override
    protected void onSpawn() {
        super.onSpawn();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!monster.isValid() || monster.isDead()) {
                    cancel();
                    return;
                }
                if (LivingPlayers.waveRunning()) heal();
            }
        }.runTaskTimer(Main.getINSTANCE(), HEAL_EVERY + ThreadLocalRandom.current().nextInt(40), HEAL_EVERY);
    }

    private void heal() {
        boolean healed = false;
        for (Monster other : monster.getWorld().getNearbyEntitiesByType(Monster.class, monster.getLocation(), RADIUS)) {
            if (other == monster || !other.isValid() || other.isDead()) continue;
            var max = other.getAttribute(Attribute.MAX_HEALTH);
            if (max == null || other.getHealth() >= max.getValue()) continue;
            other.setHealth(Math.min(max.getValue(), other.getHealth() + max.getValue() * SHARE));
            HealthBars.updateLater(other);
            other.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, other.getLocation().add(0, 1.2, 0), 5, 0.3, 0.4, 0.3, 0);
            healed = true;
        }
        if (!healed) return;
        if (model() != null) model().play("heilen");
        monster.getWorld().playSound(monster.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8F, 1.3F);
        monster.getWorld().spawnParticle(Particle.HEART, monster.getEyeLocation().add(0, 0.4, 0), 2, 0.2, 0.1, 0.2, 0);
    }
}
