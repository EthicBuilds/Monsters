package de.ethicbuilds.monsters.items;

/***
 * Crowbar upgrades from the workbench: crates and doors break faster and crates give more loot.
 * 3000 coins for the first level, then double each level.
 */
public final class CrowbarLevel {
    public static final int MAX_LEVEL = 4;
    private static final int FIRST_PRICE = 3000;
    private static final double SPEED_PER_LEVEL = 0.3;
    private static final double LOOT_PER_LEVEL = 0.05;
    private static final int COINS_PER_LEVEL = 25;

    private CrowbarLevel() {}

    public static int price(int level) {
        return FIRST_PRICE * (1 << level);
    }

    /** How many times faster than without upgrade */
    public static double speed(int level) {
        return 1 + SPEED_PER_LEVEL * Math.min(level, MAX_LEVEL);
    }

    /** Extra chance each for a grenade and for coins in a crate */
    public static double extraLoot(int level) {
        return LOOT_PER_LEVEL * Math.min(level, MAX_LEVEL);
    }

    public static int extraCoins(int level) {
        return COINS_PER_LEVEL * Math.min(level, MAX_LEVEL);
    }
}
