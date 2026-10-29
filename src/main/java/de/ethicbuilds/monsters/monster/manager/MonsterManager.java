package de.ethicbuilds.monsters.monster.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
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
    private static final int HORDE_PER_PLAYER = 6;
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
    private final Map<EnemyMonster, Long> lastMoveTime = new HashMap<>();;

    public void createMonsters(int wave) {
        monsters.clear();
        initialMonsters.clear();
        spawnIndex = 0;

        int players = Math.max(1, userManager.getGamePlayers().size());
        // Up to wave 10 the waves grow fast, after that only a little, from wave 15 the monsters get stronger instead
        int monsterCount = players * (2 + 4 * (Math.min(wave, GROWTH_UNTIL) - 1) + Math.max(0, wave - GROWTH_UNTIL));

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
        int tier = EliteTier.of(wave);
        for (int i = 0; i < monsterCount; i++) {
            EnemyMonster monster = randomMonster(wave);
            monster.setTier(tier);
            monster.setWave(wave);
            initialMonsters.add(monster);
        }
        // From the second wave one of them is a mini boss, it comes last so it is out when the wave is under way
        if (wave >= MINI_BOSS_WAVE) initialMonsters.set(initialMonsters.size() - 1, new MonsterMiniBoss(wave));
    }

    private EnemyMonster randomMonster(int wave) {
        double roll = Math.random();
        if (wave >= 48 && (roll -= 0.10) < 0) return new MonsterZombie(EntityType.VINDICATOR, "§7Diener");
        if (wave >= 24 && (roll -= 0.15) < 0) return new MonsterZombie(EntityType.ZOMBIE_VILLAGER, "§7Zombiedorfbewohner");
        if (wave >= 12 && (roll -= 0.10) < 0) return new MonsterZombie(EntityType.PARCHED, "§7Vertrockneter");
        if (wave >= 6 && (roll -= 0.25) < 0) return new MonsterZombie(EntityType.HUSK, "§7Wüstenzombie");
        return new MonsterZombie();
    }

    /** A quick horde that comes out right now, before the rest of the wave */
    public void addHorde(int wave) {
        int players = Math.max(1, livingPlayers().size());
        int tier = EliteTier.of(wave);
        for (int i = 0; i < players * HORDE_PER_PLAYER; i++) {
            EnemyMonster monster = new MonsterZombie();
            monster.setTier(tier);
            monster.setWave(wave);
            monster.setSprinter(true);
            initialMonsters.addFirst(monster);
        }
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

    public void removeMonster(Monster monster) {
        monsters.remove(monster);
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
            HealthBars.removeOrphans(plugin.getWorld());
            HealthBars.hideIdle(plugin.getWorld());

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

                Location lastLoc = lastLocation.get(enemyMonster);

                if (lastLoc != null && lastLoc.equals(loc)) {
                    long lastMoved = lastMoveTime.getOrDefault(enemyMonster, now);
                    // A stuck spider gets free ground instead of being removed, the boss ends the wave
                    if (now - lastMoved >= 10_000 && enemyMonster instanceof MonsterSpider spider) {
                        Location open = spiderSpot(spider, livingPlayers());
                        // The model parts ride on it and come along
                        if (open != null) entity.teleport(open, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                        lastMoveTime.put(enemyMonster, now);
                        continue;
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
