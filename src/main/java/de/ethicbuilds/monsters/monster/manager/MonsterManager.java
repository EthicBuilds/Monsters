package de.ethicbuilds.monsters.monster.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.monster.EliteTier;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.HealthBars;
import de.ethicbuilds.monsters.monster.MonsterMiniBoss;
import de.ethicbuilds.monsters.monster.MonsterBounty;
import de.ethicbuilds.monsters.monster.MonsterScarab;
import de.ethicbuilds.monsters.monster.MonsterSpider;
import de.ethicbuilds.monsters.monster.MonsterSpiderBoss;
import de.ethicbuilds.monsters.monster.MonsterZombie;
import de.ethicbuilds.monsters.monster.OpenGround;
import de.ethicbuilds.monsters.monster.PlayerTrail;
import de.ethicbuilds.monsters.monster.WaveScaling;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MonsterManager {
    public static final int SPIDER_WAVE = 5;
    private static final int GROWTH_UNTIL = 10;
    private static final int HORDE_PER_PLAYER = 10, HORDE_BONUS = 100;
    private static final String HORDE_COLOR = "#FF4838", BEATEN_COLOR = "#3CDC6E";
    private static final int SCARABS_PER_PLAYER = 8;
    private static final double BOSS_LEASH = 40;
    public static final int BOSS_WAVE = 10;
    private static final int MINI_BOSS_WAVE = 2;

    @Inject
    private MapManager mapManager;
    @Inject
    private UserManager userManager;
    @Inject
    private Main plugin;

    @Getter
    private final Map<Monster, EnemyMonster> monsters = new ConcurrentHashMap<>();
    @Getter
    private final List<EnemyMonster> initialMonsters = new ArrayList<>();
    private int spawnIndex;

    private final Map<EnemyMonster, Location> lastLocation = new HashMap<>();
    private final Map<EnemyMonster, Long> lastMoveTime = new HashMap<>();
    private static final double STUCK_RADIUS = 2, STUCK_PLAYER_DISTANCE = 4;
    private static final long STUCK_MILLIS = 12_000;

    public void createMonsters(int wave) {
        monsters.clear();
        initialMonsters.clear();
        lastLocation.clear();
        lastMoveTime.clear();
        spawnIndex = 0;

        int players = Math.max(1, userManager.getGamePlayers().size());
        // Up to wave 10 the waves grow fast, after that only a little, from wave 15 the monsters get stronger instead
        int perPlayer = 2 + 4 * (Math.min(wave, GROWTH_UNTIL) - 1) + Math.max(0, wave - GROWTH_UNTIL);
        int monsterCount = Math.max(1, (int) Math.round(perPlayer * WaveScaling.count(players)));

        // Every fifth wave only small amethyst weavers, more each time, every tenth with the big boss weaver
        if (wave % SPIDER_WAVE == 0) {
            int spidersPerPlayer = 3 + 2 * (wave / SPIDER_WAVE);
            for (int i = 0; i < players * spidersPerPlayer; i++) {
                MonsterSpider spider = new MonsterSpider();
                spider.setWave(wave);
                initialMonsters.add(spider);
            }
            if (wave % BOSS_WAVE == 0) initialMonsters.add(new MonsterSpiderBoss(players, wave));
            return;
        }
        // Same number, but more kinds the longer the round goes
        for (int i = 0; i < monsterCount; i++) {
            EnemyMonster monster = randomMonster(wave);
            monster.setTier(EliteTier.roll(wave));
            monster.setWave(wave);
            initialMonsters.add(monster);
        }
        // Big groups get one elite more: a zombie one tier above the wave
        if (players >= WaveScaling.ELITE_FROM_PLAYERS) {
            MonsterZombie elite = new MonsterZombie();
            elite.setTier(Math.min(EliteTier.of(wave) + 1, EliteTier.MAX));
            elite.setWave(wave);
            initialMonsters.add(elite);
        }
        // From the second wave one of them is a mini boss, it comes last so it is out when the wave is under way
        if (wave >= MINI_BOSS_WAVE) initialMonsters.set(initialMonsters.size() - 1, new MonsterMiniBoss(wave));
    }

    private EnemyMonster randomMonster(int wave) {
        double roll = Math.random();
        if (wave >= 48 && (roll -= 0.10) < 0) return new MonsterZombie(EntityType.VINDICATOR, "§7Diener");
        if (wave >= 24 && (roll -= 0.15) < 0) return new MonsterZombie(EntityType.ZOMBIE_VILLAGER, "§7Zombiedorfbewohner");
        if (wave >= 15 && (roll -= 0.06) < 0) return new de.ethicbuilds.monsters.monster.MonsterShieldbearer();
        if (wave >= 12 && (roll -= 0.07) < 0) return new de.ethicbuilds.monsters.monster.MonsterSpitter();
        if (wave >= 12 && (roll -= 0.10) < 0) return new MonsterZombie(EntityType.PARCHED, "§7Vertrockneter");
        if (wave >= 9 && (roll -= 0.07) < 0) return new de.ethicbuilds.monsters.monster.MonsterBloater();
        if (wave >= 6 && (roll -= 0.25) < 0) return new MonsterZombie(EntityType.HUSK, "§7Wüstenzombie");
        return new MonsterZombie();
    }

    /** A quick horde that comes out right now, before the rest of the wave */
    public void addHorde(int wave) {
        int players = Math.max(1, livingPlayers().size());
        List<EnemyMonster> horde = new ArrayList<>();
        for (int i = 0; i < players * HORDE_PER_PLAYER; i++) {
            EnemyMonster monster = new de.ethicbuilds.monsters.monster.MonsterCrawler();
            monster.setTier(EliteTier.roll(wave));
            monster.setWave(wave);
            monster.setSprinter(true);
            initialMonsters.addFirst(monster);
            horde.add(monster);
        }
        trackHorde(horde);
    }

    // A bar counts the horde down, beaten it gives coins for everyone still standing
    private void trackHorde(List<EnemyMonster> horde) {
        de.ethicbuilds.monsters.lang.DualBar bar = new de.ethicbuilds.monsters.lang.DualBar("", "", org.bukkit.boss.BarColor.RED, org.bukkit.boss.BarStyle.SEGMENTED_10);
        int total = horde.size();
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                long left = horde.stream().filter(m -> initialMonsters.contains(m) || m.getMonster() != null && m.getMonster().isValid() && !m.getMonster().isDead()).count();
                if (plugin.getInjector().getInstance(de.ethicbuilds.monsters.gameplay.manager.GameManager.class).getCurrentPhase() != de.ethicbuilds.monsters.gameplay.model.GamePhase.WAVE) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                if (left == 0) {
                    bar.removeAll();
                    cancel();
                    for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                        Player player = gamePlayer.getPlayer();
                        if (!player.isOnline()) continue;
                        if (!userManager.isPlayerDead(player.getUniqueId())) gamePlayer.addCoins(HORDE_BONUS);
                        String bonus = Overlay.num("+" + HORDE_BONUS) + " " + Overlay.coin();
                        Overlay.medium(player, BEATEN_COLOR, Overlay.line(Overlay.HORDE, BEATEN_COLOR, "HORDE BESIEGT", bonus),
                                Overlay.line(Overlay.HORDE, BEATEN_COLOR, "HORDE BEATEN", bonus));
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.2F);
                    }
                    return;
                }
                bar.setTitle(Overlay.parse(Overlay.line(Overlay.HORDE, HORDE_COLOR, "Horde", Overlay.num(left) + " übrig")),
                        Overlay.parse(Overlay.line(Overlay.HORDE, HORDE_COLOR, "Horde", Overlay.num(left) + " left")));
                bar.setProgress(Math.max(0, Math.min(1, left / (double) total)));
                Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    /** Swarm event: scarabs crawl out of the sand near the players */
    public void addScarabs(int wave) {
        int players = Math.max(1, livingPlayers().size());
        for (int i = 0; i < players * SCARABS_PER_PLAYER; i++) {
            MonsterScarab scarab = new MonsterScarab();
            scarab.setWave(wave);
            initialMonsters.addFirst(scarab);
        }
    }

    public void addBounty(int wave) {
        MonsterBounty bounty = new MonsterBounty(wave);
        bounty.setWave(wave);
        initialMonsters.addFirst(bounty);
    }

    /** Small weavers the boss calls */
    public void addBrood(int count) {
        for (int i = 0; i < count; i++) initialMonsters.addFirst(new MonsterSpider());
    }

    /** Comes out right here instead of a spawner, e.g. called by a boss; counts for the wave like the others */
    public void summonAt(EnemyMonster enemy, Player target, Location at) {
        enemy.summon(target, at);
        monsters.put(enemy.getMonster(), enemy);
    }

    public void removeMonster(Monster monster) {
        EnemyMonster removed = monsters.remove(monster);
        if (removed == null) return;
        lastLocation.remove(removed);
        lastMoveTime.remove(removed);
    }

    public void removeMonster(EnemyMonster monster) {
        removeMonster(monster.getMonster());
    }

    /** Alive plus still to come in this wave */
    public int remaining() {
        return monsters.size() + initialMonsters.size();
    }

    public boolean hasPendingSpawns() {
        return !initialMonsters.isEmpty();
    }

    /** Summons the next monsters of the wave, a few per tick, so the server does not hang at the wave start */
    public void summonBatch(int count) {
        List<Location> activeSpawners = getActiveSpawnerLocations();
        List<Player> players = userManager.getGamePlayers().stream().map(GamePlayer::getPlayer).toList();
        if (activeSpawners.isEmpty() || players.isEmpty()) {
            initialMonsters.clear();
            return;
        }

        for (int i = 0; i < count && !initialMonsters.isEmpty(); i++) {
            EnemyMonster enemyMonster = initialMonsters.removeFirst();
            Location location = activeSpawners.get(spawnIndex % activeSpawners.size()).clone().add(0, 1, 0);
            // The horde storms out of the spawners closest to the team
            if (enemyMonster.isSprinter()) location = nearestSpawner(activeSpawners, players.get(spawnIndex % players.size())).clone().add(0, 1, 0);
            // Spiders are too big for the spawner windows, they come out on open ground in the play area
            if (enemyMonster instanceof MonsterSpider spider) {
                Location open = spiderSpot(spider, players);
                if (open != null) location = open;
            }
            if (enemyMonster instanceof MonsterScarab) {
                Location open = OpenGround.random(players, 0, 1);
                if (open != null) {
                    location = open;
                    open.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, open.clone().add(0, 0.3, 0), 12, 0.3, 0.1, 0.3, open.clone().subtract(0, 1, 0).getBlock().getBlockData());
                }
            }
            enemyMonster.summon(players.get(spawnIndex % players.size()), location);
            monsters.put(enemyMonster.getMonster(), enemyMonster);
            spawnIndex++;
        }
    }


    private static Location nearestSpawner(List<Location> spawners, Player player) {
        Location best = spawners.getFirst();
        for (Location spawner : spawners) {
            if (spawner.getWorld().equals(player.getWorld()) && spawner.distanceSquared(player.getLocation()) < best.distanceSquared(player.getLocation())) best = spawner;
        }
        return best;
    }

    private Location spiderSpot(MonsterSpider spider, List<Player> players) {
        int height = spider.getRoom() + 1;
        if (spider instanceof MonsterSpiderBoss) return OpenGround.nearPlayers(players, spider.getRoom(), height);
        return OpenGround.random(players, spider.getRoom(), height);
    }

    /** The living player that the fewest monsters are chasing */
    public Player leastChasedPlayer() {
        Player best = null;
        long fewest = Long.MAX_VALUE;
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline() || userManager.isPlayerDead(player.getUniqueId())) continue;
            long chasing = monsters.values().stream().filter(m -> player.equals(m.getTarget())).count();
            if (chasing < fewest) {
                fewest = chasing;
                best = player;
            }
        }
        return best;
    }

    private static final double SHOT_NOISE = 16, GRENADE_NOISE = 28;
    private final Map<java.util.UUID, Long> lastShotNoise = new java.util.HashMap<>();

    /** A shot, at most once a second per player */
    public void shotNoise(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastShotNoise.get(player.getUniqueId());
        if (last != null && now - last < 1000) return;
        lastShotNoise.put(player.getUniqueId(), now);
        noise(player.getLocation(), SHOT_NOISE);
    }

    public void grenadeNoise(Location at) {
        noise(at, GRENADE_NOISE);
    }

    // Zombies and crawlers that have not found their player yet go there (design session)
    private void noise(Location at, double radius) {
        for (EnemyMonster enemy : monsters.values()) {
            if (!(enemy instanceof MonsterZombie || enemy instanceof de.ethicbuilds.monsters.monster.MonsterCrawler) || enemy instanceof MonsterMiniBoss) continue;
            Monster entity = enemy.getMonster();
            if (entity == null || !entity.getWorld().equals(at.getWorld()) || entity.getLocation().distanceSquared(at) > radius * radius) continue;
            enemy.hear(at);
        }
    }

    public EnemyMonster getEnemyMonster(Monster monster) {
        return monsters.get(monster);
    }

    public void startMonsterCheck() {
        PlayerTrail.clear();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                Player player = gamePlayer.getPlayer();
                if (player.isOnline() && !userManager.isPlayerDead(player.getUniqueId())) PlayerTrail.record(player);
            }
        }, 0L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Frozen monsters during a pause are not stuck
            if (de.ethicbuilds.monsters.gameplay.manager.PauseManager.isPaused()) return;
            long now = System.currentTimeMillis();
            HealthBars.tidy(plugin.getWorld());

            List<EnemyMonster> toRemove = new ArrayList<>();

            for (EnemyMonster enemyMonster : monsters.values()) {
                LivingEntity entity = enemyMonster.getMonster();


                if (entity == null || !entity.isValid()) {
                    toRemove.add(enemyMonster);
                    continue;
                }

                Location loc = entity.getLocation();

                if (loc.getBlockY() < 0) {
                    toRemove.add(enemyMonster);
                    continue;
                }

                // The boss weaver never stays far away from the players
                if (enemyMonster instanceof MonsterSpiderBoss boss) {
                    Player nearest = nearestPlayer(loc);
                    if (nearest != null && nearest.getLocation().distance(loc) > BOSS_LEASH) {
                        Location open = spiderSpot(boss, livingPlayers());
                        if (open != null) entity.teleport(open, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                    }
                }

                // Stuck: stayed within a small spot (also wobbling back and forth) without a player next to it
                Location anchor = lastLocation.get(enemyMonster);
                Player nearest = nearestPlayer(loc);
                boolean atPlayer = nearest != null && nearest.getLocation().distanceSquared(loc) < STUCK_PLAYER_DISTANCE * STUCK_PLAYER_DISTANCE;
                if (anchor != null && anchor.getWorld().equals(loc.getWorld()) && anchor.distanceSquared(loc) < STUCK_RADIUS * STUCK_RADIUS && !atPlayer) {
                    long lastMoved = lastMoveTime.getOrDefault(enemyMonster, now);
                    // A stuck spider gets free ground instead of being removed, the boss ends the wave
                    if (now - lastMoved >= 10_000 && enemyMonster instanceof MonsterSpider spider) {
                        Location open = spiderSpot(spider, livingPlayers());
                        // The model parts ride on it and come along
                        if (open != null) entity.teleport(open, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                        lastMoveTime.put(enemyMonster, now);
                        continue;
                    }
                    // Others come out again at the spawner nearest to the players, only if none is around
                    if (now - lastMoved >= STUCK_MILLIS && nearest != null && !(enemyMonster instanceof MonsterSpiderBoss)) {
                        List<Location> spawners = getActiveSpawnerLocations();
                        if (!spawners.isEmpty()) {
                            Location spawner = nearestSpawner(spawners, nearest).clone().add(0, 1, 0);
                            entity.teleport(spawner, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                            lastMoveTime.put(enemyMonster, now);
                            lastLocation.put(enemyMonster, spawner);
                            continue;
                        }
                    }
                    if (now - lastMoved >= 30_000) {
                        toRemove.add(enemyMonster);
                        continue;
                    }
                } else {
                    lastMoveTime.put(enemyMonster, now);
                    lastLocation.put(enemyMonster, loc.clone());
                }
            }

            for (EnemyMonster monster : toRemove) {
                Monster entity = monster.getMonster();

                if (entity != null) {
                    if (entity.isValid()) {
                        HealthBars.remove(entity);
                        entity.remove();
                    }

                    monsters.remove(entity);
                }

                lastLocation.remove(monster);
                lastMoveTime.remove(monster);
            }
        }, 0L, 40L);
    }


    public Player nearestPlayer(Location location) {
        Player best = null;
        double closest = Double.MAX_VALUE;
        for (Player player : livingPlayers()) {
            if (!player.getWorld().equals(location.getWorld())) continue;
            double distance = player.getLocation().distanceSquared(location);
            if (distance < closest) {
                closest = distance;
                best = player;
            }
        }
        return best;
    }

    private List<Player> livingPlayers() {
        return userManager.getGamePlayers().stream().map(GamePlayer::getPlayer)
                .filter(p -> p.isOnline() && !userManager.isPlayerDead(p.getUniqueId())).toList();
    }

    private List<Location> getActiveSpawnerLocations() {
        List<Location> activeSpawnerLocations = new ArrayList<>();
        for (MonsterSpawner monsterSpawner : mapManager.getMapConfiguration().getSpawners()) {
            if (monsterSpawner.isActive()) {
                activeSpawnerLocations.add(monsterSpawner.getLocations().getFirst());
            }
        }
        return activeSpawnerLocations;
    }
}
