package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.CountDownLatch;

public class WaveManager {

    @Inject
    private UserManager userManager;
    @Inject
    private MonsterManager monsterManager;
    @Inject
    private Main plugin;
    private Thread waveThread = null;
    @Getter
    private int currentWave = 0;

    public void start() {
        this.waveThread = new Thread(() -> {
            try {
                while (Thread.currentThread().isAlive()) {
                    startWave();
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        this.waveThread.start();
    }

    private void startWave() {
        currentWave++;
        broadCastMessage(String.format("%s§aStarte Welle §4%d", plugin.getMonstersPrefix(), currentWave));
        userManager.broadcastSound(Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS, 2.0F, 1.0F);

        startOnMinecraftThread(() -> userManager.getGamePlayers()
                .forEach(gamePlayer -> userManager.revivePlayer(gamePlayer.getPlayer().getUniqueId())));


        monsterManager.createMonsters(currentWave);

        startOnMinecraftThread(() -> monsterManager.summonMonsters());

        while (true) {
            if (monsterManager.getMonsters().isEmpty()) {
                break;
            }
        }
    }

    private void broadCastMessage(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    private void startOnMinecraftThread(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }
}
