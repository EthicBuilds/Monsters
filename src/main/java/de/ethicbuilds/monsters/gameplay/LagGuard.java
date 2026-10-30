package de.ethicbuilds.monsters.gameplay;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/***
 * Measures the tick time every second: under load the models move less often and fewer monsters are out at once,
 * before the TPS drop below 18
 */
public final class LagGuard {
    private static final double STRAINED_MSPT = 45, HEAVY_MSPT = 60;
    private static final double STRAINED_TPS = 18;
    private static int level;
    private static boolean started;

    private LagGuard() {}

    public static void start(Plugin plugin) {
        if (started) return;
        started = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double mspt = Bukkit.getAverageTickTime();
            int was = level;
            level = mspt > HEAVY_MSPT ? 2 : mspt > STRAINED_MSPT || Bukkit.getTPS()[0] < STRAINED_TPS ? 1 : 0;
            if (level != was) plugin.getLogger().info("Lag-Schutz Stufe " + level + " (" + Math.round(mspt) + " ms pro Tick)");
        }, 20L, 20L);
    }

    /** 0 normal, 1 strained, 2 heavy */
    public static int level() {
        return level;
    }

    /** Models update this many times less often */
    public static int slowdown() {
        return 1 + level;
    }

    /** Share of the usual number of monsters out at once */
    public static double spawnShare() {
        return level == 0 ? 1 : level == 1 ? 0.75 : 0.5;
    }
}
