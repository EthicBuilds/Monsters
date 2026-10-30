package de.ethicbuilds.monsters.monster;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;

import java.util.concurrent.ThreadLocalRandom;

/***
 * Moan, hurt and death sound of a modeled monster (the entity under the model is silent). Every monster gets its
 * own pitch, so a crowd does not sound like one voice.
 */
public record Voice(Sound ambient, Sound hurt, Sound death, float pitch) {
    private static final float SPREAD = 0.12F;

    public static Voice of(EntityType type) {
        float pitch = 1 + (float) ThreadLocalRandom.current().nextDouble(-SPREAD, SPREAD);
        return switch (type) {
            case HUSK -> new Voice(Sound.ENTITY_HUSK_AMBIENT, Sound.ENTITY_HUSK_HURT, Sound.ENTITY_HUSK_DEATH, pitch);
            case PARCHED -> new Voice(Sound.ENTITY_PARCHED_AMBIENT, Sound.ENTITY_PARCHED_HURT, Sound.ENTITY_PARCHED_DEATH, pitch);
            case ZOMBIE_VILLAGER -> new Voice(Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT, Sound.ENTITY_ZOMBIE_VILLAGER_HURT, Sound.ENTITY_ZOMBIE_VILLAGER_DEATH, pitch);
            case VINDICATOR -> new Voice(Sound.ENTITY_VINDICATOR_AMBIENT, Sound.ENTITY_VINDICATOR_HURT, Sound.ENTITY_VINDICATOR_DEATH, pitch);
            default -> new Voice(Sound.ENTITY_ZOMBIE_AMBIENT, Sound.ENTITY_ZOMBIE_HURT, Sound.ENTITY_ZOMBIE_DEATH, pitch);
        };
    }

    /** Deeper and with a roar for bosses */
    public static Voice boss() {
        return new Voice(Sound.ENTITY_RAVAGER_AMBIENT, Sound.ENTITY_RAVAGER_HURT, Sound.ENTITY_RAVAGER_DEATH, 0.8F);
    }

    public void moan(Location at) {
        at.getWorld().playSound(at, ambient, 1.0F, pitch);
    }

    public void hurt(Location at) {
        at.getWorld().playSound(at, hurt, 1.0F, pitch);
    }

    public void death(Location at) {
        at.getWorld().playSound(at, death, 1.0F, pitch);
    }
}
