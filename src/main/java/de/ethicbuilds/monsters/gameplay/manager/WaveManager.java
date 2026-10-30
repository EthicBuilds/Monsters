package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.event.WaveEvents;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.CrateManager;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.EliteTier;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
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
    private static final String[] TIERS = {"", "Bronze", "Silber", "Gold", "Diamant", "Netherit"};
    private static final String[] TIERS_EN = {"", "Bronze", "Silver", "Gold", "Diamond", "Netherite"};
    private static final String[] TIER_COLORS = {"", "#E3A46A", "#C0C8D4", "#FFCC2E", "#5EE0E0", "#8B7A8E"};
    private static final String WAVE_COLOR = "#F9A11E", BOSS_COLOR = "#A970FF", HORDE_COLOR = "#FF4838";
    private static final long ELITE_DELAY = 60;

    @Inject
    private UserManager userManager;
    @Inject
    private MonsterManager monsterManager;
    @Inject
    private CrateManager crateManager;
    @Inject
    private Main plugin;
    @Inject
    private PauseManager pauseManager;
    @Getter
    private volatile int currentWave = 0;

    /** For testing: the next wave is the boss wave */
    public void skipToBossWave() {
        currentWave = (currentWave / MonsterManager.BOSS_WAVE + 1) * MonsterManager.BOSS_WAVE - 1;
    }

    public void start() {
        de.ethicbuilds.monsters.gameplay.LagGuard.start(plugin);
        Thread waveThread = new Thread(() -> {
            try {
                while (true) {
                    while (PauseManager.isPaused()) Thread.sleep(200);
                    startWave();
                    // After every boss wave the creator of the round may call a pause
                    if (currentWave % MonsterManager.BOSS_WAVE == 0) startOnMinecraftThread(pauseManager::giveItem);
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "Monsters-Wellen");
        // Does not keep the server from shutting down
        waveThread.setDaemon(true);
        waveThread.start();
    }

    private void startWave() throws InterruptedException {
        currentWave++;
        plugin.getLogger().info("Welle " + currentWave);
        startOnMinecraftThread(() -> {
            userManager.broadcastSound(Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS, 2.0F, 1.0F);
            crateManager.resetAll();
        });

        // Only who is still there, players who left stay out until they come back
        startOnMinecraftThread(() -> userManager.getGamePlayers().stream().filter(gamePlayer -> gamePlayer.getPlayer().isOnline())
                .forEach(gamePlayer -> userManager.revivePlayer(gamePlayer.getPlayer().getUniqueId())));


        // Bukkit and the player and monster lists only on the main thread
        int wave = currentWave;
        startOnMinecraftThread(() -> {
            plugin.getInjector().getInstance(WaveEvents.class).onWaveStart(wave);
            monsterManager.createMonsters(wave);
        });
        startOnMinecraftThread(this::announceWave);

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
            if (PauseManager.isPaused()) {
                Thread.sleep(200);
                continue;
            }
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
            int room = (int) (cap * de.ethicbuilds.monsters.gameplay.LagGuard.spawnShare()) - monsterManager.getMonsters().size();
            if (monsterManager.hasPendingSpawns() && room > 0) {
                int count = Math.min(SPAWNS_PER_TICK, room);
                startOnMinecraftThread(() -> monsterManager.summonBatch(count));
            } else {
                Thread.sleep(100);
            }
        }
    }

    // Boss wave big, every other wave medium, a new armor tier follows once the wave text is gone
    private void announceWave() {
        int wave = currentWave;
        if (wave % MonsterManager.BOSS_WAVE == 0) {
            String stage = Overlay.num(wave / MonsterManager.BOSS_WAVE);
            Overlay.bigAll(BOSS_COLOR, Overlay.line(Overlay.BOSS, BOSS_COLOR, "BOSS-WELLE", null), Overlay.line(Overlay.BOSS, BOSS_COLOR, "BOSS WAVE", null),
                    Overlay.line((char) 0, BOSS_COLOR, "Amethyst-Weberin", "Stufe " + stage), Overlay.line((char) 0, BOSS_COLOR, "Amethyst Weaver", "Level " + stage));
            return;
        }
        String count = Overlay.num(monsterManager.remaining());
        boolean spiders = wave % MonsterManager.SPIDER_WAVE == 0;
        Overlay.mediumAll(WAVE_COLOR, Overlay.line(Overlay.WAVE, WAVE_COLOR, "WELLE " + wave, spiders ? "Die Weberinnen kommen" : count + " Monster"),
                Overlay.line(Overlay.WAVE, WAVE_COLOR, "WAVE " + wave, spiders ? "the weavers are coming" : count + " monsters"));
        if (spiders || !EliteTier.starts(wave)) return;
        int tier = EliteTier.of(wave);
        String color = TIER_COLORS[tier];
        Bukkit.getScheduler().runTaskLater(plugin, () -> Overlay.mediumAll(color,
                Overlay.line(Overlay.ELITE, color, "ELITE: " + TIERS[tier].toUpperCase(Locale.ROOT), "Monster tragen Rüstung"),
                Overlay.line(Overlay.ELITE, color, "ELITE: " + TIERS_EN[tier].toUpperCase(Locale.ROOT), "monsters wear armor")), ELITE_DELAY);
    }

    private void announceHorde() {
        Overlay.bigAll(HORDE_COLOR, Overlay.line(Overlay.HORDE, HORDE_COLOR, "HORDE!", null), Overlay.line(Overlay.HORDE, HORDE_COLOR, "HORDE!", null),
                "<color:" + Overlay.GREY + ">Haltet zusammen</color>", "<color:" + Overlay.GREY + ">Stick together</color>");
        for (Player player : Bukkit.getOnlinePlayers()) player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.5F, 1.2F);
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
