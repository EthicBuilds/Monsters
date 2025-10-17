package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class GameManager {
    @Inject
    private Main plugin;
    @Inject
    private UserManager userManager;
    @Inject
    private GameStates gameStates;

    private BukkitTask gameStartTask;

    public void gameStart() {
        broadcastMessage(String.format("GameManager Started the game"));
        gameStartTask = new BukkitRunnable() {
            int i = 20;
            @Override
            public void run() {
                if(!userManager.isFull()) this.cancel();

                if (i <= 5) {
                    broadcastMessage(String.format("Game starts in %d Seconds", i));
                }

                if (i <= 1) {
                    //TODO: Start the Wave

                    gameStates.setCurrentPhase(GamePhase.WAVE);
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
        broadcastMessage(String.format("GameManager End the game"));
        new BukkitRunnable() {
            int i = 10;

            @Override
            public void run() {
                if(i <= 5) {
                    broadcastMessage(String.format("Server restarts in %d Seconds", i));
                }

                if(i <= 1) {
                    broadcastMessage(String.format("Server restart"));
                    //TODO: Find alternative for that
                    Bukkit.spigot().restart();
                }
                i--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public GamePhase getCurrentPhase() {
        return gameStates.getCurrentPhase();
    }


    private void broadcastMessage(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }
}
