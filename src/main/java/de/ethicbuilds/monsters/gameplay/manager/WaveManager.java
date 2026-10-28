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
    private static final int HORDE_FROM = 8;
    private static final int BOUNTY_FROM = 5;
    private static final double BOUNTY_CHANCE = 0.35, BOUNTY_AT = 0.8;
    private static final double HORDE_CHANCE = 0.5, HORDE_AT = 0.6;
    private static final long HORDE_WARNING = 3000;
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
                ? MiniMessage.miniMessage().deserialize("<#8B4BD8>Boss-Welle: die Amethyst-Weberin kommt! <#D58BFF>Stufe " + currentWave / MonsterManager.BOSS_WAVE)
                : currentWave % MonsterManager.SPIDER_WAVE == 0
                ? MiniMessage.miniMessage().deserialize("<#8B4BD8>Spinnen-Welle: die Weberinnen kommen!")
                : currentWave > 10 && currentWave % 10 == 1
                ? Component.text("Neue Elite-Stufe: " + EliteTier.name(EliteTier.of(currentWave)).replaceAll("§.", "")).color(net.kyori.adventure.text.format.TextColor.color(0xF9A11E))
                : Component.empty();
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(Title.title(title, subtitle)));
        userManager.broadcastSound(Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS, 2.0F, 1.0F);

        startOnMinecraftThread(crateManager::resetAll);

        // Only who is still there, players who left stay out until they come back
        startOnMinecraftThread(() -> userManager.getGamePlayers().stream().filter(gamePlayer -> gamePlayer.getPlayer().isOnline())
                .forEach(gamePlayer -> userManager.revivePlayer(gamePlayer.getPlayer().getUniqueId())));


        plugin.getInjector().getInstance(de.ethicbuilds.monsters.event.WaveEvents.class).onWaveStart(currentWave);
        monsterManager.createMonsters(currentWave);

        // A few monsters per tick, never more than MAX_ALIVE at once: the rest comes when others die
        // From wave 8 every second wave on average: once a good third is beaten, a quick horde storms out
        int total = monsterManager.remaining();
        boolean horde = currentWave >= HORDE_FROM && currentWave % de.ethicbuilds.monsters.monster.manager.MonsterManager.SPIDER_WAVE != 0
                && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < HORDE_CHANCE;
        // From wave 5 now and then a bounty zombie once a fifth is beaten
        boolean bounty = currentWave >= BOUNTY_FROM && currentWave % de.ethicbuilds.monsters.monster.manager.MonsterManager.SPIDER_WAVE != 0
                && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < BOUNTY_CHANCE;
        int cap = currentWave % de.ethicbuilds.monsters.monster.manager.MonsterManager.SPIDER_WAVE == 0 ? MAX_SPIDERS_ALIVE : MAX_ALIVE;
        while (monsterManager.hasPendingSpawns() || !monsterManager.getMonsters().isEmpty()) {
            if (bounty && monsterManager.remaining() <= total * BOUNTY_AT) {
                bounty = false;
                startOnMinecraftThread(() -> monsterManager.addBounty(currentWave));
            }
            if (horde && monsterManager.remaining() <= total * HORDE_AT) {
                horde = false;
                startOnMinecraftThread(this::announceHorde);
                Thread.sleep(HORDE_WARNING);
                startOnMinecraftThread(() -> monsterManager.addHorde(currentWave));
            }
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

    private void announceHorde() {
        for (var player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§c§lHORDE!", "§7Gleich stürmt eine schnelle Horde los", 5, 40, 10);
            player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.5F, 1.2F);
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
