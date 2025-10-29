package de.ethicbuilds.monsters.player.manager;


import com.google.auto.service.AutoService;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.GameSpectator;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class UserManager {
    @Inject private MapManager mapManager;
    @Inject private GameManager gameManager;

    public boolean canJoin = true;

    private final Map<UUID, GamePlayer> gamePlayers = new HashMap<>();
    private final Map<UUID, GameSpectator> spectators = new HashMap<>();

    public void addPlayer(Player player) {
        if (!isFull()) {
            gamePlayers.put(player.getUniqueId(), new GamePlayer(player));
        }
    }

    public void removePlayer(UUID uuid) {
        if (isGamePlayer(uuid)) {
            gamePlayers.remove(uuid);
        } else if (isSpectator(uuid)) {
            spectators.remove(uuid);
        }
    }

    public void killPlayer(UUID uuid) {
        if (isGamePlayer(uuid)) {
            GamePlayer gamePlayer = gamePlayers.get(uuid);
            spectators.put(uuid, new GameSpectator(gamePlayer.getPlayer()));

            if (!allPlayersDead()) {
                gamePlayer.getPlayer().sendTitle("§4Du bist Tot!", "§cDu Respawnst in der nächsten Runde wieder");
                return;
            }

            gameManager.gameEnd();
        }
    }

    public void revivePlayer(UUID uuid) {
        if (isGamePlayer(uuid) && isSpectator(uuid)) {
            spectators.remove(uuid);

            Player player = gamePlayers.get(uuid).getPlayer();

            player.setGameMode(GameMode.SURVIVAL);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 3));


            player.heal(20);
            player.teleport(mapManager.getMapConfiguration().getSpawn());
            player.sendTitle("§aWiederbelebt!", "");
        }
    }

    public boolean isPlayerDead(UUID uuid) {
        return gamePlayers.containsKey(uuid) && spectators.containsKey(uuid);
    }

    public boolean allPlayersDead() {
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            if (!isPlayerDead(gamePlayer.getPlayer().getUniqueId())) {
                return false;
            }
        }
        return true;
    }

    public GamePlayer getGamePlayer(UUID uuid) {
        return gamePlayers.get(uuid);
    }

    public boolean isFull() {
        return gamePlayers.size() >= gameManager.getGameConfig().getPlayerCount();
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
