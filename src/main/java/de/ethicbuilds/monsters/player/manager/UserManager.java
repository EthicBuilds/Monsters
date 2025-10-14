package de.ethicbuilds.monsters.player.manager;


import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.GameSpectator;
import de.ethicbuilds.monsters.player.GameUser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UserManager {
    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    private final Map<UUID, GameSpectator> spectators = new HashMap<>();

    public void addPlayer(GameUser gameUser) {
        if (isFull()) {
            gamePlayers.put(gameUser.getPlayer().getUniqueId(), new GamePlayer(gameUser.getPlayer()));
        } else {
            spectators.put(gameUser.getPlayer().getUniqueId(), new GameSpectator(gameUser.getPlayer()));
        }
    }

    public void removePlayer(UUID uuid) {
        if (isGamePlayer(uuid)) {
            gamePlayers.remove(uuid);
        } else if (isSpectator(uuid)) {
            spectators.remove(uuid);
        }
    }

    public boolean isFull() {
        return gamePlayers.size() >= 2;
    }

    public boolean isGamePlayer(UUID uuid) {
        return gamePlayers.containsKey(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.containsKey(uuid);
    }

    public Iterable<GamePlayer> getGamePlayers() {
        return gamePlayers.values();
    }

    public Iterable<GameSpectator> getSpectators() {
        return spectators.values();
    }
}
