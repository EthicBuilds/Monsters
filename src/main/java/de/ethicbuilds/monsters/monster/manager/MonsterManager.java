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

public class MonsterManager {
    @Inject
    private MapManager mapManager;
    @Inject
    private UserManager userManager;

    @Getter
    private Map<Monster, EnemyMonster> monsters = new HashMap<>();

    public void createMonsters(int amount) {
        for (int i = 0; i < amount; i++) {
            MonsterZombie monster = new MonsterZombie();
            monsters.put(monster.getMonster(), monster);
        }
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

        int index = 0;
        for (EnemyMonster monster : monsters.values()) {
            Location location = activeSpawners.get(index % spawnerCount);
            Player player = players.get(index % playerCount);

            monster.summon(player, location);
            index++;
        }
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
