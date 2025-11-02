package de.ethicbuilds.monsters.monster.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.MonsterZombie;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MonsterManager {
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

        int monsterCount = userManager.getGamePlayers().size() + 10 * wave;

        for (int i = 0; i < monsterCount; i++) {
            initialMonsters.add(new MonsterZombie());
        }
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
            Player player = players.get(index % playerCount);
            enemyMonster.summon(player, location);
            newMonsters.put(enemyMonster.getMonster(), enemyMonster);
            index++;
        }

        monsters.clear();
        monsters.putAll(newMonsters);
        initialMonsters.clear();
    }


    public EnemyMonster getEnemyMonster(Monster monster) {
        return monsters.get(monster);
    }

    public void startMonsterCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

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
                    if (now - lastMoved >= 30_000) {
                        toRemove.add(enemyMonster);
                        continue;
                    }
                } else {
                    lastMoveTime.put(enemyMonster, now);
                    lastLocation.put(enemyMonster, loc.clone());
                }

                Block block = loc.getBlock();
                if (block.getType().isSolid()) {
                    entity.teleport(new Location(plugin.getWorld(), 0, 73, 0));
                }
            }

            for (EnemyMonster monster : toRemove) {
                Monster entity = monster.getMonster();

                if (entity != null) {
                    if (entity.isValid()) {
                        entity.remove();
                    }

                    monsters.remove(entity);
                }

                lastLocation.remove(monster);
                lastMoveTime.remove(monster);
            }
        }, 0L, 40L);
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
