package de.ethicbuilds.monsters.reward;

import de.ethicbuilds.monsters.player.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/***
 * EthicCoins for everyone at the end of a round: active minutes, waves, kills and revives, the best player a bit more.
 * Only minutes with hits on monsters count (no AFK), nothing for rounds under 5 minutes, at most 300 per round.
 * Normal play gives about 100-130 per hour before the rank bonus.
 */
public final class RoundReward {
    private static final double PER_ACTIVE_MINUTE = 1.5;
    private static final int PER_WAVE = 1, KILLS_PER_COIN = 50, PER_REVIVE = 3;
    private static final int BEST_BONUS = 20, MAX_PER_ROUND = 300;
    private static final long MIN_ROUND_MILLIS = 5 * 60_000;

    private static final Set<UUID> activeThisMinute = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> activeMinutes = new ConcurrentHashMap<>();
    private static long roundStart;

    private RoundReward() {}

    public static void start(Plugin plugin, BooleanSupplier running) {
        roundStart = System.currentTimeMillis();
        activeThisMinute.clear();
        activeMinutes.clear();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!running.getAsBoolean()) {
                    cancel();
                    return;
                }
                countMinute();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    /** The player hit a monster, this minute counts */
    public static void markActive(UUID uuid) {
        activeThisMinute.add(uuid);
    }

    private static void countMinute() {
        for (UUID uuid : activeThisMinute) activeMinutes.merge(uuid, 1, Integer::sum);
        activeThisMinute.clear();
    }

    public static int amount(GamePlayer gamePlayer, int wave, boolean best) {
        int minutes = activeMinutes.getOrDefault(gamePlayer.getPlayer().getUniqueId(), 0);
        if (minutes == 0) return 0;
        int coins = (int) Math.round(minutes * PER_ACTIVE_MINUTE) + PER_WAVE * wave
                + gamePlayer.getKilledZombies() / KILLS_PER_COIN + PER_REVIVE * gamePlayer.getRevives();
        if (best) coins += BEST_BONUS;
        return Math.min(MAX_PER_ROUND, coins);
    }

    /** At the round end, best player first in the list */
    public static void payOut(List<GamePlayer> ranked, int wave, String prefix) {
        countMinute();
        if (System.currentTimeMillis() - roundStart < MIN_ROUND_MILLIS) {
            ranked.forEach(p -> { if (p.getPlayer().isOnline()) p.getPlayer().sendMessage(prefix + "§7Die Runde war zu kurz für EthicCoins."); });
            return;
        }
        for (int i = 0; i < ranked.size(); i++) {
            Player player = ranked.get(i).getPlayer();
            if (!player.isOnline()) continue;
            int base = amount(ranked.get(i), wave, i == 0);
            if (base <= 0) continue;
            long coins = EthicCoins.withRankBonus(player, base);
            if (!EthicCoins.deposit(player.getUniqueId(), coins, "Monsters Runde")) continue;
            player.sendMessage(String.format("%s§7Du bekommst §6%d EthicCoins §7für diese Runde%s", prefix, coins, i == 0 ? " §8(§6Rundenbester§8)" : ""));
            if (i == 0) {
                Bukkit.broadcastMessage(String.format("%s§6%s §7war der beste Spieler der Runde!", prefix, player.getName()));
                DailyWin.reward(player, prefix);
            }
        }
    }
}
