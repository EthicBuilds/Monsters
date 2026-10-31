package de.ethicbuilds.monsters.reward;

import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.GamePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/***
 * EthicCoins for everyone at the end of a round: active minutes, waves, kills and revives, the best player a bit more.
 * Only minutes with hits on monsters count (no AFK), nothing for rounds under 5 minutes, at most 150 per round.
 * Normal play gives about 80 per hour before the rank bonus (halved 31.10., the long rounds gave too much).
 */
public final class RoundReward {
    private static final double PER_ACTIVE_MINUTE = 0.75;
    private static final int PER_WAVE = 1, KILLS_PER_COIN = 100, PER_REVIVE = 2;
    private static final int BEST_BONUS = 10, MAX_PER_ROUND = 150;
    private static final long MIN_ROUND_MILLIS = 5 * 60_000;

    private static final Set<UUID> activeThisMinute = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> activeMinutes = new ConcurrentHashMap<>();
    private static long roundStart;

    private RoundReward() {}

    /** What one player got: base (minutes, revives, best player), waves and kills before the rank bonus */
    public record Parts(int base, int waves, int kills, long bonus, boolean tooShort, CompletableFuture<Payout> firstWin) {
        static Parts none(boolean tooShort) {
            return new Parts(0, 0, 0, 0, tooShort, CompletableFuture.completedFuture(Payout.NONE));
        }

        public long paid() {
            return base + waves + kills + bonus;
        }
    }

    private record Split(int base, int waves, int kills) {}

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
        Split split = split(gamePlayer, wave, best);
        return split.base() + split.waves() + split.kills();
    }

    // The cap comes off the round part, the kills part stays
    private static Split split(GamePlayer gamePlayer, int wave, boolean best) {
        int minutes = activeMinutes.getOrDefault(gamePlayer.getPlayer().getUniqueId(), 0);
        if (minutes == 0) return new Split(0, 0, 0);
        int kills = Math.min(MAX_PER_ROUND, gamePlayer.getKilledZombies() / KILLS_PER_COIN);
        int base = (int) Math.round(minutes * PER_ACTIVE_MINUTE) + PER_REVIVE * gamePlayer.getRevives() + (best ? BEST_BONUS : 0);
        int round = Math.min(base + PER_WAVE * wave, MAX_PER_ROUND - kills);
        int waves = Math.min(PER_WAVE * wave, round);
        return new Split(round - waves, waves, kills);
    }

    /** At the round end, best player first in the list. Returns what each online player got */
    public static Map<UUID, Parts> payOut(List<GamePlayer> ranked, int wave, String prefix) {
        countMinute();
        Map<UUID, Parts> parts = new HashMap<>();
        if (System.currentTimeMillis() - roundStart < MIN_ROUND_MILLIS) {
            ranked.forEach(p -> { if (p.getPlayer().isOnline()) p.getPlayer().sendMessage(prefix + Lang.t(p.getPlayer(), "§7Die Runde war zu kurz für EthicCoins.", "§7The round was too short for EthicCoins.")); });
            ranked.forEach(p -> parts.put(p.getPlayer().getUniqueId(), Parts.none(true)));
            return parts;
        }
        for (int i = 0; i < ranked.size(); i++) {
            Player player = ranked.get(i).getPlayer();
            if (!player.isOnline()) continue;
            parts.put(player.getUniqueId(), Parts.none(false));
            Split split = split(ranked.get(i), wave, i == 0);
            int base = split.base() + split.waves() + split.kills();
            if (base <= 0) continue;
            long coins = EthicCoins.withRankBonus(player, base);
            if (!EthicCoins.deposit(player.getUniqueId(), coins, "Monsters Runde")) continue;
            player.sendMessage(Lang.f(player, "%s§7Du bekommst §6%d EthicCoins §7für diese Runde%s%s", "%s§7You get §6%d EthicCoins §7for this round%s%s", prefix, coins,
                    EthicCoins.bonusNote(player, base, coins), i == 0 ? Lang.t(player, " §8(§6Rundenbester§8)", " §8(§6Best player§8)") : ""));
            CompletableFuture<Payout> firstWin = CompletableFuture.completedFuture(Payout.NONE);
            if (i == 0) {
                Lang.broadcast("%s§6%s §7war der beste Spieler der Runde!", "%s§6%s §7was the best player of the round!", prefix, player.getName());
                firstWin = DailyWin.reward(player, prefix);
            }
            parts.put(player.getUniqueId(), new Parts(split.base(), split.waves(), split.kills(), coins - base, false, firstWin));
        }
        return parts;
    }
}
