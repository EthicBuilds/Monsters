package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class WaveManager {

    @Inject
    private UserManager userManager;
    @Inject
    private MonsterManager monsterManager;
    private Thread phaseThread = null;
    private int currentWave = 0;

    public void start() {
        this.phaseThread = new Thread(() -> {
            try {
                while (Thread.currentThread().isAlive()) {
                    startWave();
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void startWave() {
        currentWave++;
        broadCastMessage(String.format("Starting Wave %d", currentWave));

        int monsterCount = userManager.getGamePlayers().size() * 10 * currentWave;

        monsterManager.createMonsters(monsterCount);
        monsterManager.summonMonsters();

        while (!monsterManager.getMonsters().isEmpty()) {
            //wait
        }
    }

    private void broadCastMessage(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }
}
