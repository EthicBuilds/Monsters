package de.ethicbuilds.monsters.player.manager;


import com.google.auto.service.AutoService;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.GameSpectator;
import org.bukkit.*;
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
    @Inject private ReviveManager reviveManager;

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

    /** Left during the round: stays in it as spectator and can come back */
    public void leave(UUID uuid) {
        GamePlayer gamePlayer = gamePlayers.get(uuid);
        if (gamePlayer == null || spectators.containsKey(uuid)) return;
        spectators.put(uuid, new GameSpectator(gamePlayer.getPlayer()));
    }

    /** Players of this round may always come back, new ones while there is a free slot */
    public boolean canJoinRunning(UUID uuid) {
        return gamePlayers.containsKey(uuid) || gamePlayers.size() < gameManager.getGameConfig().getPlayerCount();
    }

    /** Joins or rejoins a running round: watches until the next wave, then plays again */
    public void joinRunning(Player player) {
        GamePlayer gamePlayer = gamePlayers.get(player.getUniqueId());
        if (gamePlayer == null) {
            gamePlayer = new GamePlayer(player);
            gamePlayers.put(player.getUniqueId(), gamePlayer);
        } else {
            gamePlayer.setPlayer(player);
        }
        spectators.put(player.getUniqueId(), new GameSpectator(player));

        // Lay on the ground before leaving: the body goes, from the next wave the player plays again
        if (reviveManager.isDown(player.getUniqueId())) reviveManager.clear(player);
        // Watches in spectator mode, so the others see the player in the tab list as spectator
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(mapManager.getMapConfiguration().getSpawn());
        player.sendTitle("§7Du schaust zu", "§aAb der nächsten Welle spielst du mit", 10, 60, 10);
    }

    public void killPlayer(UUID uuid) {
        if (isGamePlayer(uuid)) {
            GamePlayer gamePlayer = gamePlayers.get(uuid);
            spectators.put(uuid, new GameSpectator(gamePlayer.getPlayer()));
            reviveManager.down(gamePlayer.getPlayer());

            if (!allPlayersDead()) {
                gamePlayer.getPlayer().sendTitle("§4Du liegst am Boden!", String.format("§7Ein Mitspieler belebt dich für §6%d Coins §7wieder", reviveManager.cost(uuid)));
                return;
            }

            gameManager.gameEnd();
        }
    }

    public void revivePlayer(UUID uuid) {
        revivePlayer(uuid, mapManager.getMapConfiguration().getSpawn());
    }

    public void revivePlayer(UUID uuid, Location location) {
        if (isGamePlayer(uuid) && isSpectator(uuid)) {
            spectators.remove(uuid);

            Player player = gamePlayers.get(uuid).getPlayer();
            reviveManager.clear(player);

            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setInvulnerable(false);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 3));


            player.heal(20);
            de.ethicbuilds.monsters.items.ArmorSet.equip(player, gamePlayers.get(uuid).getArmorLevel());
            player.teleport(location);
            player.sendTitle("§aWiederbelebt!", "");
            player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.8F, 1.2F);
        }
    }

    public boolean isPlayerDead(UUID uuid) {
        return gamePlayers.containsKey(uuid) && spectators.containsKey(uuid);
    }

    public boolean allPlayersDead() {
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            // Who left the round does not keep it running
            if (!isPlayerDead(gamePlayer.getPlayer().getUniqueId()) && gamePlayer.getPlayer().isOnline()) {
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
                    org.bukkit.entity.Monster nearestZombie = null;
                    double minDistance = Double.MAX_VALUE;

                    // Only monsters, the map is full of decoration displays
                    for (org.bukkit.entity.Monster entity : gamePlayer.getPlayer().getWorld().getEntitiesByClass(org.bukkit.entity.Monster.class)) {
                        double distance = entity.getLocation().distanceSquared(gamePlayer.getPlayer().getLocation());
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestZombie = entity;
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

    public void broadcastSound(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }
}
