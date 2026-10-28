package de.ethicbuilds.monsters.weapons;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/***
 * Damage from shots and grenades. While it is dealt the damage event knows that it was no melee hit,
 * only those (and knife hits) give coins.
 */
public final class ShotDamage {
    private static final double HEADSHOT_MULTIPLIER = 1.5;
    // The head starts this far below the eyes
    private static final double HEAD_SIZE = 0.35;

    private static boolean active;
    private static boolean headshot;

    private ShotDamage() {}

    public static void deal(LivingEntity target, double amount, Player shooter) {
        active = true;
        try {
            target.damage(amount, shooter);
        } finally {
            active = false;
        }
    }

    /** A shot that hit at this point: at the head it does more damage and gives more coins */
    public static void shoot(LivingEntity target, double amount, Player shooter, org.bukkit.Location point) {
        headshot = target.getEyeLocation().getY() - point.getY() < HEAD_SIZE && !target.isInvisible();
        try {
            if (headshot) {
                shooter.playSound(shooter.getLocation(), org.bukkit.Sound.ENTITY_ARROW_HIT_PLAYER, 0.6F, 1.6F);
                target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.1);
            }
            deal(target, headshot ? amount * HEADSHOT_MULTIPLIER : amount, shooter);
        } finally {
            headshot = false;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isHeadshot() {
        return headshot;
    }
}
