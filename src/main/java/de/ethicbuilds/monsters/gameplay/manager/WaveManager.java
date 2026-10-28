package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.map.decoration.CrateManager;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.EliteTier;
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
    private static final int SPAWNS_PER_TICK = 3;
    // Measured 28.10.: up to about 170 monsters the server runs smoothly, 120 leaves room
    private static final int MAX_ALIVE = 120;
    // Every weaver is about 40 animated displays, so fewer of them at once (8 players would bring over 100)
    private static final int MAX_SPIDERS_ALIVE = 40;

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

    private void startWave() throws InterruptedException {
        currentWave++;
        broadCastMessage(String.format("%s§aStarte Welle §4%d", plugin.getMonstersPrefix(), currentWave));
        Component title = MiniMessage.miniMessage().deserialize("<bold><gradient:#F9A11E:#8B4BD8>Welle " + currentWave + "</gradient></bold>");
        Component subtitle = currentWave % MonsterManager.BOSS_WAVE == 0
                ? MiniMessage.miniMessage().deserialize("<#8B4BD8>Boss-Welle: die Amethyst-Weberin kommt!")
                : currentWave % MonsterManager.SPIDER_WAVE == 0
                ? MiniMessage.miniMessage().deserialize("<#8B4BD8>Spinnen-Welle: die Weberinnen kommen!")
                : currentWave > 10 && currentWave % 10 == 1
                ? Component.text("Neue Elite-Stufe: " + EliteTier.name(EliteTier.of(currentWave)).replaceAll("§.", "")).color(net.kyori.adventure.text.format.TextColor.color(0xF9A11E))
                : Component.empty();
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(Title.title(title, subtitle)));
        userManager.broadcastSound(Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS, 2.0F, 1.0F);

        startOnMinecraftThread(crateManager::resetAll);

        startOnMinecraftThread(() -> userManager.getGamePlayers()
                .forEach(gamePlayer -> userManager.revivePlayer(gamePlayer.getPlayer().getUniqueId())));


        monsterManager.createMonsters(currentWave);

        // A few monsters per tick, never more than MAX_ALIVE at once: the rest comes when others die
        int cap = currentWave % de.ethicbuilds.monsters.monster.manager.MonsterManager.SPIDER_WAVE == 0 ? MAX_SPIDERS_ALIVE : MAX_ALIVE;
        while (monsterManager.hasPendingSpawns() || !monsterManager.getMonsters().isEmpty()) {
            int room = cap - monsterManager.getMonsters().size();
            if (monsterManager.hasPendingSpawns() && room > 0) {
                int count = Math.min(SPAWNS_PER_TICK, room);
                startOnMinecraftThread(() -> monsterManager.summonBatch(count));
            } else {
                Thread.sleep(100);
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
