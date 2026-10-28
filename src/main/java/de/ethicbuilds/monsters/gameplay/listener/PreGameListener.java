package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.RoundLoader;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PreGameListener implements Listener {
    @Inject private UserManager userManager;
    @Inject private GameManager gameManager;
    @Inject private ScoreboardManager scoreboardManager;
    @Inject private MapManager mapManager;
    @Inject private Main plugin;
    @Inject private RoundLoader roundLoader;

    private static final java.util.UUID PACK_ID = java.util.UUID.nameUUIDFromBytes("monsters-pack".getBytes());

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!roundLoader.isLoaded()) {
            event.getPlayer().kickPlayer("§cDie Runde wird gerade vorbereitet, versuch es gleich nochmal!");
            return;
        }
        reset(event.getPlayer());
        if (gameManager.getCurrentPhase() == GamePhase.WAVE) {
            joinRunning(event);
            return;
        }
        if (gameManager.getCurrentPhase() != GamePhase.PRE_GAME) {
            event.getPlayer().kickPlayer("§cDie Runde ist gerade vorbei!");
            return;
        }
        event.getPlayer().teleport(mapManager.getMapConfiguration().getSpawn());
        if (userManager.isFull() || !userManager.canJoin) {
            event.getPlayer().kickPlayer("§cDer Server ist Bereits voll!");
            return;
        }
        userManager.addPlayer(event.getPlayer());

        if (userManager.isFull()) {
            gameManager.gameStart();
            userManager.canJoin = false;
        }

        Player player = event.getPlayer();
        sendPack(player);
        player.setScoreboard(scoreboardManager.getScoreboard());

        player.sendMessage(String.format("%sMap: §6%s", plugin.getMonstersPrefix(), gameManager.getGameConfig().getMapName()));
        player.sendMessage(String.format("%s§aGebaut von §2%s", plugin.getMonstersPrefix(),  String.join("§7, §2", gameManager.getGameConfig().getMapBuilder())));
        player.setHealth(20);
        player.setFlying(false);

        Bukkit.broadcastMessage(String.format("%s§a%s §7hat die Runde betreten!", plugin.getMonstersPrefix(), event.getPlayer().getDisplayName()));

        event.setJoinMessage("");
    }

    // The server keeps player data between rounds: nothing from the last round stays (tiny from lying down,
    // no speed, effects, old position). A teleport during the join event does not always take, so again a tick later.
    private void reset(Player player) {
        setBase(player, Attribute.SCALE, 1.0);
        setBase(player, Attribute.MOVEMENT_SPEED, 0.1);
        setBase(player, Attribute.JUMP_STRENGTH, 0.42);
        // Far enough for the team ping with the mouse wheel, doors and stations check their own distance
        setBase(player, Attribute.BLOCK_INTERACTION_RANGE, de.ethicbuilds.monsters.player.manager.PingManager.RANGE);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.getInventory().setHeldItemSlot(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.teleport(mapManager.getMapConfiguration().getSpawn());
        });
    }

    private static void setBase(Player player, Attribute attribute, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    // Rejoin or late join during the waves
    private void joinRunning(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage("");
        if (!userManager.canJoinRunning(player.getUniqueId())) {
            player.kickPlayer("§cDer Server ist Bereits voll!");
            return;
        }
        userManager.joinRunning(player);
        sendPack(player);
        player.setScoreboard(scoreboardManager.getScoreboard());
        Bukkit.broadcastMessage(String.format("%s§a%s §7ist wieder dabei und spielt ab der nächsten Welle mit!", plugin.getMonstersPrefix(), player.getName()));
    }

    // Own pack for Monsters models (workbench, HUD), the lobby pack stays active
    private void sendPack(Player player) {
        String packUrl = plugin.getConfig().getString("resource-pack.url");
        String packSha1 = plugin.getConfig().getString("resource-pack.sha1");
        if (packUrl != null && !packUrl.isBlank() && packSha1 != null && packSha1.length() == 40) {
            player.addResourcePack(PACK_ID, packUrl, java.util.HexFormat.of().parseHex(packSha1), null, false);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage("");
        if (gameManager.getCurrentPhase() != GamePhase.PRE_GAME) return;

        userManager.removePlayer(event.getPlayer().getUniqueId());

        userManager.canJoin = true;
        gameManager.interruptGameStart();
        Bukkit.broadcastMessage(String.format("%s§a%s §7hat die Runde verlassen!", plugin.getMonstersPrefix(), event.getPlayer().getDisplayName()));
    }

    @EventHandler
    public void preventPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || gameManager.getCurrentPhase() != GamePhase.PRE_GAME) return;
        event.setCancelled(true);
    }
}
