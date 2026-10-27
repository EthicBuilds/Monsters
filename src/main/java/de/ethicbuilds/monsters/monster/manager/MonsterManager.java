package de.ethicbuilds.monsters.monster.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.HealthBars;
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
    public static final int BOSS_WAVE = 10;
    private static final int SPIDERS_PER_PLAYER = 3;

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

    private final Map<EnemyMonster, Location> lastLocation = new HashMap<>();
    private final Map<EnemyMonster, Long> lastMoveTime = new HashMap<>();;

    public void createMonsters(int wave) {
        monsters.clear();
        initialMonsters.clear();

        int players = Math.max(1, userManager.getGamePlayers().size());
        int monsterCount = players * (2 + 4 * (wave - 1));

        // Every tenth wave: small amethyst weavers and one big boss weaver
        if (wave % BOSS_WAVE == 0) {
            for (int i = 0; i < players * SPIDERS_PER_PLAYER; i++) initialMonsters.add(new MonsterSpider());
            initialMonsters.add(new MonsterSpiderBoss(players));
            return;
        }
        // Same number, but more kinds the longer the round goes
        for (int i = 0; i < monsterCount; i++) {
            initialMonsters.add(randomMonster(wave));
        }
    }

    private EnemyMonster randomMonster(int wave) {
        double roll = Math.random();
        if (wave >= 48 && (roll -= 0.10) < 0) return new MonsterZombie(EntityType.VINDICATOR, "§7Diener");
        if (wave >= 24 && (roll -= 0.15) < 0) return new MonsterZombie(EntityType.ZOMBIE_VILLAGER, "§7Zombiedorfbewohner");
        if (wave >= 12 && (roll -= 0.15) < 0) return new MonsterZombie(EntityType.PARCHED, "§7Vertrockneter");
        if (wave >= 6 && (roll -= 0.25) < 0) return new MonsterZombie(EntityType.HUSK, "§7Wüstenzombie");
        return new MonsterZombie();
    }

    public void removeMonster(Monster monster) {
        monsters.remove(monster);
    }

    public void removeMonster(EnemyMonster monster) {
        removeMonster(monster.getMonster());
    }

    public void summonMonsters() {
        List<Location> activeSpawners = getActiveSpawnerLocations();
        List<Player> players = userManager.getGamePlayers() .stream() .map(GamePlayer::getPlayer) .toList();
        if (activeSpawners.isEmpty() || players.isEmpty()) { return; }
        int spawnerCount = activeSpawners.size();
        int playerCount = players.size();
        Map<Monster, EnemyMonster> newMonsters = new HashMap<>();
        int index = 0; int delay = 0;

        for (EnemyMonster enemyMonster : initialMonsters) {
            Location location = activeSpawners.get(index % spawnerCount).clone().add(0, 1, 0);
            // Spiders are too big for the spawner windows, they come out on open ground in the play area
            if (enemyMonster instanceof MonsterSpider spider) {
                Location open = spiderSpot(spider, players);
                if (open != null) location = open;
            }
            Player player = players.get(index % playerCount);
            enemyMonster.summon(player, location);
            newMonsters.put(enemyMonster.getMonster(), enemyMonster);
            index++;
        }

        monsters.clear();
        monsters.putAll(newMonsters);
        initialMonsters.clear();
    }


    private Location spiderSpot(MonsterSpider spider, List<Player> players) {
        int height = spider.getRoom() + 1;
        if (spider instanceof MonsterSpiderBoss) return OpenGround.middle(players, spider.getRoom(), height);
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
            long now = System.currentTimeMillis();
            HealthBars.removeOrphans(plugin.getWorld());

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

                Location lastLoc = lastLocation.get(enemyMonster);

                if (lastLoc != null && lastLoc.equals(loc)) {
                    long lastMoved = lastMoveTime.getOrDefault(enemyMonster, now);
                    // A stuck spider gets free ground instead of being removed, the boss ends the wave
                    if (now - lastMoved >= 10_000 && enemyMonster instanceof MonsterSpider spider) {
                        Location open = spiderSpot(spider, livingPlayers());
                        if (open != null) entity.teleport(open);
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
