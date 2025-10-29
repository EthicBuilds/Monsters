package de.ethicbuilds.monsters.gameplay.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.model.GameConfig;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.map.MapConfiguration;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.adapter.LocationAdapter;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.FileReader;


public class GameManager {
    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private GameStates gameStates;
    @Inject private WaveManager waveManager;
    @Inject private ScoreboardManager scoreboardManager;
    @Inject private MapManager mapManager;

    private final Gson gson;

    @Getter
    private GameConfig gameConfig;
    private BukkitTask gameStartTask;

    public GameManager() {
        gson = new GsonBuilder()
                .registerTypeAdapter(Location.class, new LocationAdapter())
                .setPrettyPrinting()
                .create();
    }

    public void gameStart() {
        broadcastMessage(String.format("%s§aDas Spiel beginnt in 20 Sekunden!", plugin.getMonstersPrefix()));
        gameStartTask = new BukkitRunnable() {
            int i = 20;
            @Override
            public void run() {
                if(!userManager.isFull()) this.cancel();

                if (i <= 5) {
                    broadcastMessage(String.format("%s§aDas spiel startet in %d Sekunden!", plugin.getMonstersPrefix(), i));
                }

                if (i <= 1) {
                    gameConfig.setPlayerCount(Bukkit.getOnlinePlayers().size());
                    waveManager.start();

                    gameStates.setCurrentPhase(GamePhase.WAVE);
                    scoreboardManager.startTime();

                    broadcastTitle(plugin.getMonstersPrefix(), "§cÜberlebe so lange wie möglich!");

                    this.cancel();
                }

                i--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void interruptGameStart() {
        if (gameStartTask != null) {
            gameStartTask.cancel();
        }
    }

    public void gameEnd() {
        gameStates.setCurrentPhase(GamePhase.AFTER_GAME);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§4Spiel Vorbei", String.format("§cIhr habt es bis Runde §7%d §cgeschaft!", waveManager.getCurrentWave()));
        }

        mapManager.getMapConfiguration().getSpawn().getWorld().strikeLightning(mapManager.getMapConfiguration().getSpawn());

        new BukkitRunnable() {
            int i = 10;

            @Override
            public void run() {
                if(i <= 5) {
                    broadcastMessage(String.format("%s§cDer Server startet in §4%d §cSekuden neu", plugin.getMonstersPrefix(), i));
                }

                if(Bukkit.getOnlinePlayers().isEmpty() || i <= 1) {
                    broadcastMessage(String.format("%s§cDer Server staret §4jetzt §cneu!", plugin.getMonstersPrefix()));
                    Bukkit.spigot().restart();
                }
                i--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void loadGameConfig() {
        try(FileReader reader = new FileReader("gameConfig.json")) {
            gameConfig = gson.fromJson(reader, GameConfig.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public GamePhase getCurrentPhase() {
        return gameStates.getCurrentPhase();
    }

    private void broadcastMessage(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    private void broadcastTitle(String s, String s2) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(s, s2);
        }
    }
}
