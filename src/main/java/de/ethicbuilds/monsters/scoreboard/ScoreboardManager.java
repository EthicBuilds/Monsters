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
        addLine(scoreboard, objective, "§c", "§x§F§F§0§0§1§9E§x§F§D§1§4§1§7t§x§F§C§2§7§1§5h§x§F§A§3§B§1§3i§x§F§9§4§E§1§1c§x§F§7§6§2§0§FB§x§F§5§7§6§0§Du§x§F§4§8§9§0§Ci§x§F§2§9§D§0§Al§x§F§0§B§1§0§8d§x§E§F§C§4§0§6s§x§E§D§D§8§0§4.§x§E§C§E§B§0§2d§x§E§A§F§F§0§0e", "", scoreValue--);
    }

    private void startScoreBoardUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {

                if (scoreboard == null) return;

                var gamePlayers = userManager.getGamePlayers();

                scoreboard.getTeam("§2").setSuffix(String.format("§e%d", waveManager.getCurrentWave()));
                scoreboard.getTeam("§3").setSuffix(String.format("§c%d", monsterManager.getMonsters().size()));

                scoreboard.getTeam("§5").setPrefix(String.format("%s: ", !gamePlayers.isEmpty() ? gamePlayers.getFirst().getPlayer().getName() : "§c- §r"));
                scoreboard.getTeam("§5").setSuffix(String.format("§6%d", !gamePlayers.isEmpty() ? gamePlayers.getFirst().getCoins() : 0));

                scoreboard.getTeam("§6").setPrefix(String.format("%s: ", gamePlayers.size() >= 2 ? gamePlayers.get(1).getPlayer().getName() : "§c- §r"));
                scoreboard.getTeam("§6").setSuffix(String.format("§6%d", gamePlayers.size() >= 2 ? gamePlayers.get(1).getCoins() : 0));

                scoreboard.getTeam("§7").setPrefix(String.format("%s: ", gamePlayers.size() >= 2 ? gamePlayers.get(2).getPlayer().getName() : "§c- §r"));
                scoreboard.getTeam("§7").setSuffix(String.format("§6%d", gamePlayers.size() >= 3 ? gamePlayers.get(2).getCoins() : 0));

                scoreboard.getTeam("§8").setPrefix(String.format("%s: ", gamePlayers.size() >= 2 ? gamePlayers.get(3).getPlayer().getName() : "§c- §r"));
                scoreboard.getTeam("§8").setSuffix(String.format("§6%d", gamePlayers.size() >= 4 ? gamePlayers.get(3).getCoins() : 0));

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