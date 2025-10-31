package de.ethicbuilds.monsters.player.manager;


import com.google.auto.service.AutoService;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.GameSpectator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class UserManager {
    @Inject private MapManager mapManager;
    @Inject private GameManager gameManager;
    @Inject private MonsterManager monsterManager;
    @Inject private Main plugin;

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

    public void startMonsterTrackerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (GamePlayer gamePlayer : gamePlayers.values()) {
                    List<Entity> entities = gamePlayer.getPlayer().getWorld().getEntities();
                    Zombie nearestZombie = null;
                    double minDistance = Double.MAX_VALUE;

                    for (org.bukkit.entity.Entity entity : entities) {
                        if (entity instanceof Zombie) {
                            double distance = entity.getLocation().distance(gamePlayer.getPlayer().getLocation());
                            if (distance < minDistance) {
                                minDistance = distance;
                                nearestZombie = (Zombie) entity;
                            }
                        }
                    }

                    if (nearestZombie == null) {
                        gamePlayer.getPlayer().setCompassTarget(new Location(gamePlayer.getPlayer().getWorld(), 0, 72, 0));
                    } else {
                        gamePlayer.getPlayer().setCompassTarget(nearestZombie.getLocation());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
