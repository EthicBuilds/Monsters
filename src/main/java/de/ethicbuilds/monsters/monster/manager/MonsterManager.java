package de.ethicbuilds.monsters.monster.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.MonsterZombie;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Location;
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

    @Getter
    private final Map<Monster, EnemyMonster> monsters = new ConcurrentHashMap<>();
    private final List<EnemyMonster> initialMonsters = new ArrayList<>();

    public void createMonsters(int amount) {
        monsters.clear();
        initialMonsters.clear();
        for (int i = 0; i < amount; i++) {
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
        List<Player> players = userManager.getGamePlayers()
                .stream()
                .map(GamePlayer::getPlayer)
                .toList();

        if (activeSpawners.isEmpty() || players.isEmpty()) {
            return;
        }

        int spawnerCount = activeSpawners.size();
        int playerCount = players.size();

        Map<Monster, EnemyMonster> newMonsters = new HashMap<>();

        int index = 0;
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
