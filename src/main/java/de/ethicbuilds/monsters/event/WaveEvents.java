package de.ethicbuilds.monsters.event;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.DualBar;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Events during the waves, always announced: sandstorm, scarab swarm, gold rush, blood moon for a whole wave
 * and the camping brake that drives a team out of a spot it holds for too long
 */
public class WaveEvents {
    private static final int FROM_WAVE = 6;
    private static final int MIN_GAP = 90, MAX_GAP = 150;
    private static final int GOLD_SECONDS = 30;
    private static final double BLOOD_MOON_CHANCE = 0.2;
    private static final int BLOOD_MOON_FROM = 10;
    private static final double BLOOD_MOON_SPEED = 1.25;
    // Camping: the middle of the team stays this close for this long
    private static final double CAMP_RADIUS = 10;
    private static final int CAMP_WARN = 90, CAMP_PUNISH = 120;
    private static final int CAMP_SPEED_TICKS = 20 * 20;
    // Sandstorm
    private static final double STORM_RADIUS = 8;
    private static final int STORM_WARNING = 4 * 20, STORM_TICKS = 20 * 20;
    private static final double STORM_DAMAGE = 2;
    private static final Particle.DustOptions SAND = new Particle.DustOptions(Color.fromRGB(0xD9B26F), 1.6F);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(0x8B1A1A), 1.2F);
    public static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xFFCC2E), 1.0F);
    private static final String SAND_COLOR = "#D9B26F", BLOOD_COLOR = "#E04848";

    private static long goldUntil;
    private static boolean bloodMoon;

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private WaveManager waveManager;
    @Inject private UserManager userManager;
    @Inject private MonsterManager monsterManager;

    private int nextEvent;
    private Location campAnchor;
    private int campSeconds;

    /** Coins for hits count double during gold rush and blood moon */
    public static double coinFactor() {
        return (System.currentTimeMillis() < goldUntil ? 2 : 1) * (bloodMoon ? 2 : 1);
    }

    public static boolean isGoldRush() {
        return System.currentTimeMillis() < goldUntil;
    }

    public static double powerupFactor() {
        return System.currentTimeMillis() < goldUntil ? 2 : 1;
    }

    public static double speedFactor() {
        return bloodMoon ? BLOOD_MOON_SPEED : 1;
    }

    public void start() {
        goldUntil = 0;
        bloodMoon = false;
        nextEvent = gap();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    setBloodMoon(false);
                    cancel();
                    return;
                }
                if (de.ethicbuilds.monsters.gameplay.manager.PauseManager.isPaused()) return;
                checkCamping();
                if (bloodMoon) {
                    // Red dust drifting far around the players
                    for (Player player : living()) player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 2, 0), 6, 10, 3, 10, BLOOD);
                }
                if (waveManager.getCurrentWave() < FROM_WAVE || --nextEvent > 0) return;
                nextEvent = gap();
                switch (ThreadLocalRandom.current().nextInt(3)) {
                    case 0 -> {
                        Location middle = teamMiddle();
                        if (middle != null) sandstorm(middle, "Raus aus dem Sand", "get out of the sand");
                    }
                    case 1 -> scarabs();
                    default -> goldRush();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Called at every wave start, before the monsters are made */
    public void onWaveStart(int wave) {
        boolean moon = wave >= BLOOD_MOON_FROM && wave % MonsterManager.SPIDER_WAVE != 0
                && ThreadLocalRandom.current().nextDouble() < BLOOD_MOON_CHANCE;
        Bukkit.getScheduler().runTask(plugin, () -> {
            setBloodMoon(moon);
            if (!moon) return;
            Overlay.bigAll(BLOOD_COLOR, Overlay.line(Overlay.MOON, BLOOD_COLOR, "BLUTMOND", null),
                    Overlay.line(Overlay.MOON, BLOOD_COLOR, "BLOOD MOON", null),
                    "<color:" + Overlay.GREY + ">Monster werden stärker</color>", "<color:" + Overlay.GREY + ">Monsters grow stronger</color>");
            sound(Sound.ENTITY_WITHER_SPAWN, 0.4F, 0.6F);
        });
    }

    private void setBloodMoon(boolean on) {
        bloodMoon = on;
        // A storm darkens the sky, in the desert it does not rain
        World world = plugin.getWorld();
        if (world != null) world.setStorm(on);
    }

    private int gap() {
        return ThreadLocalRandom.current().nextInt(MIN_GAP, MAX_GAP + 1);
    }

    private void goldRush() {
        goldUntil = System.currentTimeMillis() + GOLD_SECONDS * 1000L;
        String seconds = Overlay.num(GOLD_SECONDS + " s");
        Overlay.mediumAll(Overlay.GOLD, Overlay.line(Overlay.COINS, Overlay.GOLD, "GOLDRAUSCH", "Doppelte Coins · " + seconds),
                Overlay.line(Overlay.COINS, Overlay.GOLD, "GOLD RUSH", "double coins · " + seconds));
        sound(Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.4F);
        countdown(GOLD_SECONDS * 20);
    }

    // No title, the beetles simply come out of the sand now and then
    private void scarabs() {
        monsterManager.addScarabs(waveManager.getCurrentWave());
        for (Player player : living()) player.playSound(player.getLocation(), Sound.ENTITY_SILVERFISH_AMBIENT, 0.6F, 0.6F);
    }

    // The team holds one spot for long: warning first, then the desert drives them out
    private void checkCamping() {
        Location middle = teamMiddle();
        if (middle == null) return;
        if (campAnchor == null || !campAnchor.getWorld().equals(middle.getWorld()) || campAnchor.distanceSquared(middle) > CAMP_RADIUS * CAMP_RADIUS) {
            campAnchor = middle;
            campSeconds = 0;
            return;
        }
        campSeconds++;
        if (campSeconds == CAMP_WARN) {
            for (Player player : living()) {
                Overlay.medium(player, SAND_COLOR, Overlay.line(Overlay.STORM, SAND_COLOR, "ZU LANGE HIER", "Die Wüste wird unruhig"),
                        Overlay.line(Overlay.STORM, SAND_COLOR, "TOO LONG HERE", "the desert is getting restless"));
            }
        }
        if (campSeconds < CAMP_PUNISH) return;
        campSeconds = 0;
        sandstorm(middle, "Ihr habt zu lange hier ausgeharrt", "you held out here too long");
        for (EnemyMonster monster : List.copyOf(monsterManager.getMonsters().values())) {
            if (monster.getMonster() != null) monster.getMonster().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, CAMP_SPEED_TICKS, 0, false, false));
        }
    }

    /** Sand around the spot, warned first, then it hurts and slows whoever stays inside. The extra text is plain */
    public void sandstorm(Location center, String extra, String extraEn) {
        Overlay.mediumAll(SAND_COLOR, Overlay.line(Overlay.STORM, SAND_COLOR, "SANDSTURM", extra),
                Overlay.line(Overlay.STORM, SAND_COLOR, "SANDSTORM", extraEn));
        sound(Sound.ENTITY_BREEZE_WIND_BURST, 1.0F, 0.6F);
        World world = center.getWorld();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (gameManager.getCurrentPhase() != GamePhase.WAVE || ticks > STORM_WARNING + STORM_TICKS) {
                    cancel();
                    return;
                }
                boolean active = ticks >= STORM_WARNING;
                if (ticks % 5 == 0) {
                    // Ring as warning, sand everywhere inside once it blows
                    for (int i = 0; i < 24; i++) {
                        double a = Math.PI * 2 * i / 24;
                        world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * STORM_RADIUS, 0.3, Math.sin(a) * STORM_RADIUS), 2, 0.1, 0.2, 0.1, SAND);
                    }
                    if (active) {
                        world.spawnParticle(Particle.DUST, center.clone().add(0, 1.5, 0), 60, STORM_RADIUS * 0.6, 1.2, STORM_RADIUS * 0.6, SAND);
                        world.spawnParticle(Particle.FALLING_DUST, center.clone().add(0, 3, 0), 8, STORM_RADIUS * 0.6, 0.5, STORM_RADIUS * 0.6, Material.SAND.createBlockData());
                    }
                }
                if (active && ticks % 20 == 0) {
                    world.playSound(center, Sound.ITEM_ELYTRA_FLYING, 0.5F, 0.6F);
                    for (Player player : living()) {
                        if (!player.getWorld().equals(world) || player.getLocation().distanceSquared(center) > STORM_RADIUS * STORM_RADIUS) continue;
                        player.damage(STORM_DAMAGE);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, false));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Location teamMiddle() {
        List<Player> players = living();
        if (players.isEmpty()) return null;
        double x = 0, y = 0, z = 0;
        for (Player player : players) {
            x += player.getLocation().getX();
            y += player.getLocation().getY();
            z += player.getLocation().getZ();
        }
        return new Location(players.getFirst().getWorld(), x / players.size(), y / players.size(), z / players.size());
    }

    private List<Player> living() {
        return userManager.getLivingPlayers();
    }

    private void sound(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) player.playSound(player.getLocation(), sound, volume, pitch);
    }

    // Gold rush bar with the time left
    private void countdown(int ticks) {
        DualBar bar = new DualBar("", "", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        goldTitle(bar, ticks);
        Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
        new BukkitRunnable() {
            int left = ticks;

            @Override
            public void run() {
                left -= 5;
                if (left <= 0 || gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                bar.setProgress(left / (double) ticks);
                goldTitle(bar, left);
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private static void goldTitle(DualBar bar, int ticks) {
        int seconds = (ticks + 19) / 20;
        String time = Overlay.num(String.format("%d:%02d", seconds / 60, seconds % 60));
        bar.setTitle(Overlay.parse(Overlay.line(Overlay.COINS, Overlay.GOLD, "Goldrausch", time)),
                Overlay.parse(Overlay.line(Overlay.COINS, Overlay.GOLD, "Gold rush", time)));
    }
}
