package de.ethicbuilds.monsters.player.manager;


import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.GameSpectator;
import de.ethicbuilds.monsters.player.GameUser;
import org.bukkit.entity.Player;

import java.util.*;

public class UserManager {
    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    private final Map<UUID, GameSpectator> spectators = new HashMap<>();

    public void addPlayer(Player player) {
        if (!isFull()) {
            gamePlayers.put(player.getUniqueId(), new GamePlayer(player));
        } else {
            spectators.put(player.getUniqueId(), new GameSpectator(player));
        }
    }

    public void removePlayer(UUID uuid) {
        if (isGamePlayer(uuid)) {
            gamePlayers.remove(uuid);
        } else if (isSpectator(uuid)) {
            spectators.remove(uuid);
        }
    }

    public GamePlayer getGamePlayer(UUID uuid) {
        return gamePlayers.get(uuid);
    }

    public boolean isFull() {
        //TODO: Change Players size
        return gamePlayers.size() >= 1;
    }

    public boolean isGamePlayer(UUID uuid) {
        return gamePlayers.containsKey(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.containsKey(uuid);
    }

    public List<GamePlayer> getGamePlayers() {
        return gamePlayers.values().stream().toList();
    }

    public Collection<GameSpectator> getSpectators() {
        return spectators.values();
    }
}
