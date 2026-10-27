package de.ethicbuilds.monsters.scoreboard;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

public class ScoreboardManager {

    @Inject private UserManager userManager;
    @Inject private WaveManager waveManager;
    @Inject private MonsterManager monsterManager;
    @Inject private Main plugin;

    private Thread timeThread = null;
    private int time = 0;

    @Getter
    private Scoreboard scoreboard;

    public void init() {
        createScoreboard();

        startScoreBoardUpdater();
    }

    public void startTime() {
        this.timeThread = new Thread(() -> {
            try {
                while (Thread.currentThread().isAlive()) {
                    time++;
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        this.timeThread.start();
    }

    private void createScoreboard() {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        Objective objective = scoreboard.registerNewObjective(
                "punkte",
                "dummy",
                "§x§0§B§7§2§1§2§lM§x§0§B§8§1§1§4§lo§x§0§A§9§1§1§6§ln§x§0§A§A§0§1§8§ls§x§0§A§B§0§1§A§lt§x§0§A§B§F§1§C§le§x§0§9§C§F§1§E§lr§x§0§9§D§E§2§0§ls"
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int scoreValue = 11;

        var gamePlayers = userManager.getGamePlayers();

        addLine(scoreboard, objective, "§1", " ", "", scoreValue--);
        addLine(scoreboard, objective, "§2", "Runde ", String.format("§e%d", waveManager.getCurrentWave()), scoreValue--);
        addLine(scoreboard, objective, "§3", "Verbleibende Monster: ", String.format("§c%d", monsterManager.getMonsters().size()), scoreValue--);
        addLine(scoreboard, objective, "§4", " ", "", scoreValue--);
        addLine(scoreboard, objective, "§5",
                String.format("%s: ", !gamePlayers.isEmpty() ? gamePlayers.getFirst().getPlayer().getName() : "§c- §r"),
                String.format("§6%d", !gamePlayers.isEmpty() ? gamePlayers.getFirst().getCoins() : 0),
                scoreValue--);
        addLine(scoreboard, objective, "§6",
                String.format("%s: ", gamePlayers.size() >= 2 ? gamePlayers.get(1).getPlayer().getName() : "§c- §r"),
                String.format("§6%d", gamePlayers.size() >= 2 ? gamePlayers.get(1).getCoins() : 0),
                scoreValue--);
        addLine(scoreboard, objective, "§7",
                String.format("%s: ", gamePlayers.size() >= 3 ? gamePlayers.get(2).getPlayer().getName() : "§c- §r"),
                String.format("§6%d", gamePlayers.size() >= 3 ? gamePlayers.get(2).getCoins() : 0),
                scoreValue--);
        addLine(scoreboard, objective, "§8",
                String.format("%s: ", gamePlayers.size() >= 4 ? gamePlayers.get(3).getPlayer().getName() : "§c- §r"),
                String.format("§6%d", gamePlayers.size() >= 4 ? gamePlayers.get(3).getCoins() : 0),
                scoreValue--);
        addLine(scoreboard, objective, "§9", " ", "", scoreValue--);
        addLine(scoreboard, objective, "§a", "Zeit: ", formatSeconds(time), scoreValue--);
        addLine(scoreboard, objective, "§b", " ", "", scoreValue--);
        addLine(scoreboard, objective, "§c", "§x§2§A§E§2§F§CE§x§3§A§D§D§E§Bt§x§4§A§D§8§D§Ah§x§5§A§D§3§C§9i§x§6§A§C§E§B§8c§x§7§A§C§9§A§7B§x§8§A§C§4§9§6u§x§9§9§B§F§8§4i§x§A§9§B§A§7§3l§x§B§9§B§5§6§2d§x§C§9§B§0§5§1s§x§D§9§A§B§4§0.§x§E§9§A§6§2§Fd§x§F§9§A§1§1§Ee", "", scoreValue--);
    }

    private void startScoreBoardUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (scoreboard == null) return;

                var gamePlayers = userManager.getGamePlayers();

                scoreboard.getTeam("§2").setSuffix(String.format("§e%d", waveManager.getCurrentWave()));
                scoreboard.getTeam("§3").setSuffix(String.format("§c%d", monsterManager.getMonsters().size()));

                String[] lines = {"§5", "§6", "§7", "§8"};
                for (int i = 0; i < lines.length; i++) {
                    Team team = scoreboard.getTeam(lines[i]);
                    // Empty slot without a player stays blank
                    if (i >= gamePlayers.size()) {
                        team.setPrefix(" ");
                        team.setSuffix("");
                        continue;
                    }
                    var gamePlayer = gamePlayers.get(i);
                    boolean dead = userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId());
                    team.setPrefix(String.format("§%s%s: ", dead ? "c" : "a", gamePlayer.getPlayer().getName()));
                    team.setSuffix(String.format("§6%d", gamePlayer.getCoins()));
                }

                scoreboard.getTeam("§a").setSuffix(formatSeconds(time));
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void addLine(Scoreboard board, Objective obj, String entry, String prefix, String suffix, int score) {
        Team team = board.registerNewTeam(entry);
        team.addEntry(entry);
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        obj.getScore(entry).setScore(score);
    }

    private String formatSeconds(int totalSeconds) {
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}