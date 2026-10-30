package de.ethicbuilds.monsters.player.manager;


import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.ArmorSet;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.GameSpectator;
import org.bukkit.*;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class UserManager {
    @Inject private MapManager mapManager;
    @Inject private GameManager gameManager;
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
        ChatStatus.send(player, true);
        Overlay.medium(player, Overlay.GREY,
                Overlay.line(Overlay.NEXT, "#3CDC6E", "Zuschauen", "ab der nächsten Welle spielst du mit"),
                Overlay.line(Overlay.NEXT, "#3CDC6E", "Spectating", "you join in from the next wave"));
    }

    public void killPlayer(UUID uuid) {
        if (isGamePlayer(uuid)) {
            GamePlayer gamePlayer = gamePlayers.get(uuid);
            spectators.put(uuid, new GameSpectator(gamePlayer.getPlayer()));
            reviveManager.down(gamePlayer.getPlayer());

            if (!allPlayersDead()) {
                if (reviveManager.revivesSelf(uuid)) return;
                Player player = gamePlayer.getPlayer();
                String cost = Overlay.num(reviveManager.cost(uuid)) + " " + Overlay.coin();
                Overlay.big(player, Overlay.RED,
                        Overlay.line(Overlay.REVIVE, Overlay.RED, "AM BODEN", null),
                        Overlay.line(Overlay.REVIVE, Overlay.RED, "DOWNED", null),
                        "<color:" + Overlay.GREY + ">Ein Mitspieler belebt dich für </color>" + cost,
                        "<color:" + Overlay.GREY + ">A teammate can revive you for </color>" + cost);
                return;
            }

            gameManager.gameEnd();
        }
    }

    /** After a medkit that could not help: the round ends if nobody is up any more */
    public void endIfAllDown() {
        if (gameManager.getCurrentPhase() == GamePhase.WAVE && allPlayersDead()) gameManager.gameEnd();
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
            ArmorSet.equip(player, gamePlayers.get(uuid).getArmorLevel());
            player.teleport(location);
            Overlay.medium(player, "#3CDC6E", Overlay.line(Overlay.REVIVE, "#3CDC6E", "WIEDERBELEBT", null),
                    Overlay.line(Overlay.REVIVE, "#3CDC6E", "REVIVED", null));
            player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.8F, 1.2F);
        }
    }

    public boolean isPlayerDead(UUID uuid) {
        return gamePlayers.containsKey(uuid) && spectators.containsKey(uuid);
    }

    public boolean allPlayersDead() {
        for (GamePlayer gamePlayer : gamePlayers.values()) {
            // Who left the round does not keep it running, a medkit about to get someone up does
            UUID uuid = gamePlayer.getPlayer().getUniqueId();
            if ((!isPlayerDead(uuid) || reviveManager.revivesSelf(uuid)) && gamePlayer.getPlayer().isOnline()) {
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

    /** Players of the round who are online and not down */
    public List<Player> getLivingPlayers() {
        return gamePlayers.values().stream().map(GamePlayer::getPlayer)
                .filter(player -> player.isOnline() && !isPlayerDead(player.getUniqueId())).toList();
    }

    public void startMonsterTrackerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // One look at the monsters per world, not one per player
                Map<World, Collection<Monster>> monsters = new HashMap<>();
                for (GamePlayer gamePlayer : gamePlayers.values()) {
                    Player player = gamePlayer.getPlayer();
                    Monster nearestZombie = null;
                    double minDistance = Double.MAX_VALUE;

                    // Only monsters, the map is full of decoration displays
                    Location at = player.getLocation();
                    for (Monster entity : monsters.computeIfAbsent(player.getWorld(), world -> world.getEntitiesByClass(Monster.class))) {
                        double distance = entity.getLocation().distanceSquared(at);
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestZombie = entity;
                        }
                    }

                    if (nearestZombie == null) {
                        player.setCompassTarget(new Location(player.getWorld(), 0, 72, 0));
                    } else {
                        player.setCompassTarget(nearestZombie.getLocation());
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
