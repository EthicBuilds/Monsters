package de.ethicbuilds.monsters.weapons;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/***
 * Damage from shots and grenades. While it is dealt the damage event knows that it was no melee hit,
 * only those (and knife hits) give coins.
 */
public final class ShotDamage {
    private static final double HEADSHOT_MULTIPLIER = 1.2;
    // The head starts this far below the eyes
    private static final double HEAD_SIZE = 0.35;

    private static boolean active;
    private static boolean headshot;
    // A gun shot, not a grenade
    private static boolean aimed;
    private static de.ethicbuilds.monsters.monster.manager.MonsterManager monsters;

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
        // Invisible zombies carry a model with the head at eye height, the other models (spiders) do not
        headshot = target.getEyeLocation().getY() - point.getY() < HEAD_SIZE
                && (!target.isInvisible() || target.getType() == org.bukkit.entity.EntityType.ZOMBIE);
        aimed = true;
        try {
            boolean armor = armored(target);
            if (headshot) {
                de.ethicbuilds.monsters.sound.Sounds.headshot(shooter);
                target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.1);
            } else if (armor) {
                de.ethicbuilds.monsters.sound.Sounds.armorHit(shooter);
            }
            deal(target, headshot ? amount * HEADSHOT_MULTIPLIER : amount, shooter);
            de.ethicbuilds.monsters.lang.Overlay.hit(shooter, headshot, armor, target.isDead() || target.getHealth() <= 0);
        } finally {
            headshot = false;
            aimed = false;
        }
    }

    private static boolean armored(LivingEntity target) {
        if (!(target instanceof org.bukkit.entity.Monster monster)) return false;
        if (monsters == null) monsters = de.ethicbuilds.monsters.Main.getINSTANCE().getInjector()
                .getInstance(de.ethicbuilds.monsters.monster.manager.MonsterManager.class);
        var enemy = monsters.getEnemyMonster(monster);
        return enemy != null && enemy.armored();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isHeadshot() {
        return headshot;
    }

    public static boolean isAimed() {
        return aimed;
    }
}
