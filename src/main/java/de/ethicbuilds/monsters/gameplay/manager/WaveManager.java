package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.map.decoration.CrateManager;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
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
    private CrateManager crateManager;
    @Inject
    private Main plugin;
    private Thread waveThread = null;
    @Getter
    private int currentWave = 0;

    /** For testing: the next wave is the boss wave */
    public void skipToBossWave() {
        currentWave = (currentWave / MonsterManager.BOSS_WAVE + 1) * MonsterManager.BOSS_WAVE - 1;
    }

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
        Component title = MiniMessage.miniMessage().deserialize("<bold><gradient:#F9A11E:#8B4BD8>Welle " + currentWave + "</gradient></bold>");
        Component subtitle = currentWave % MonsterManager.BOSS_WAVE == 0
                ? MiniMessage.miniMessage().deserialize("<#8B4BD8>Boss-Welle: die Amethyst-Weberin kommt!") : Component.empty();
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(Title.title(title, subtitle)));
        userManager.broadcastSound(Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS, 2.0F, 1.0F);

        startOnMinecraftThread(crateManager::resetAll);

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
