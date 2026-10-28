package de.ethicbuilds.monsters.monster;

/***
 * From wave 15 on the monsters get stronger instead of more: more health, harder hits and a bit quicker,
 * never faster than a walking player
 */
public final class WaveScaling {
    public static final int FROM_WAVE = 15;
    private static final double HEALTH_PER_WAVE = 0.08;
    private static final double DAMAGE_PER_WAVE = 0.06;
    private static final double SPEED_PER_WAVE = 0.02;
    // Zombies walk about 2.3 blocks per second, 1.6 times that is still slower than a walking player
    public static final double MAX_SPEED = 1.6;

    private WaveScaling() {}

    public static double health(int wave) {
        return 1 + HEALTH_PER_WAVE * over(wave);
    }

    public static double damage(int wave) {
        return 1 + DAMAGE_PER_WAVE * over(wave);
    }

    public static double speed(int wave) {
        return Math.min(MAX_SPEED, 1 + SPEED_PER_WAVE * over(wave));
    }

    private static int over(int wave) {
        return Math.max(0, wave - FROM_WAVE + 1);
    }
}
