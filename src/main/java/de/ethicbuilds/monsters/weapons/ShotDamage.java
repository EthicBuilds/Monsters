package de.ethicbuilds.monsters.weapons;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/***
 * Damage from shots and grenades. While it is dealt the damage event knows that it was no melee hit,
 * only those (and knife hits) give coins.
 */
public final class ShotDamage {
    private static boolean active;

    private ShotDamage() {}

    public static void deal(LivingEntity target, double amount, Player shooter) {
        active = true;
        try {
            target.damage(amount, shooter);
        } finally {
            active = false;
        }
    }

    public static boolean isActive() {
        return active;
    }
}
