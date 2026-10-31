package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/***
 * Poison zombie from wave 16 (user 31.10.): leaves a poison puddle now and then while it walks, and one where it dies.
 * The team cannot just stay in one spot.
 */
public class MonsterToxic extends MonsterZombie {
    private static final int PUDDLE_EVERY = 6 * 20;

    public MonsterToxic() {
        super(EntityType.ZOMBIE, "§2Giftzombie");
        this.health = 22;
    }

    @Override
    protected String modelId() {
        return "giftzombie";
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
                if (LivingPlayers.waveRunning() && monster.isOnGround()) PoisonPuddle.spill(monster.getLocation());
            }
        }.runTaskTimer(Main.getINSTANCE(), PUDDLE_EVERY + ThreadLocalRandom.current().nextInt(40), PUDDLE_EVERY);
    }

    @Override
    public void onDeath() {
        super.onDeath();
        PoisonPuddle.spill(monster.getLocation());
    }
}
