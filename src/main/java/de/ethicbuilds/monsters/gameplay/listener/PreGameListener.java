package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.manager.ClassManager;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.RoundLoader;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.player.manager.PackStatus;
import de.ethicbuilds.monsters.player.manager.PingManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.player.manager.WatcherManager;
import de.ethicbuilds.monsters.reward.DailyChallenge;
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

import java.util.HexFormat;

public class PreGameListener implements Listener {
    @Inject private UserManager userManager;
    @Inject private ClassManager classManager;
    @Inject private GameManager gameManager;
    @Inject private ScoreboardManager scoreboardManager;
    @Inject private MapManager mapManager;
    @Inject private Main plugin;
    @Inject private RoundLoader roundLoader;
    @Inject private WatcherManager watcherManager;


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!roundLoader.isLoaded()) {
            event.getPlayer().kickPlayer(Lang.t(event.getPlayer(), "§cDie Runde wird gerade vorbereitet, versuch es gleich nochmal!", "§cThe round is being prepared, try again in a moment!"));
            return;
        }
        reset(event.getPlayer());
        if (gameManager.getCurrentPhase() == GamePhase.WAVE) {
            joinRunning(event);
            return;
        }
        if (gameManager.getCurrentPhase() != GamePhase.PRE_GAME) {
            event.getPlayer().kickPlayer(Lang.t(event.getPlayer(), "§cDie Runde ist gerade vorbei!", "§cThe round just ended!"));
            return;
        }
        event.getPlayer().teleport(mapManager.getMapConfiguration().getSpawn());
        if (userManager.isFull() || !userManager.canJoin) {
            event.getPlayer().kickPlayer(Lang.t(event.getPlayer(), "§cDer Server ist Bereits voll!", "§cThe server is already full!"));
            return;
        }
        userManager.addPlayer(event.getPlayer());

        if (userManager.isFull()) {
            gameManager.gameStart();
            userManager.canJoin = false;
        }

        Player player = event.getPlayer();
        sendPack(player);
        player.setScoreboard(scoreboardManager.getScoreboard(player));
        if (!userManager.isFull()) player.getInventory().setItem(JoinMeListener.SLOT, JoinMeListener.item(player));
        classManager.give(player);

        player.sendMessage(String.format("%sMap: §6%s", plugin.getMonstersPrefix(), gameManager.getGameConfig().getMapName()));
        player.sendMessage(Lang.f(player, "%s§aGebaut von §2%s", "%s§aBuilt by §2%s", plugin.getMonstersPrefix(),  String.join("§7, §2", gameManager.getGameConfig().getMapBuilder())));
        DailyChallenge.announce(player);
        player.setHealth(20);
        player.setFlying(false);

        Lang.broadcast("%s§a%s §7hat die Runde betreten!", "%s§a%s §7joined the round!", plugin.getMonstersPrefix(), event.getPlayer().getDisplayName());

        event.setJoinMessage("");
    }

    // The server keeps player data between rounds: nothing from the last round stays (tiny from lying down,
    // no speed, effects, old position). A teleport during the join event does not always take, so again a tick later.
    private void reset(Player player) {
        setBase(player, Attribute.SCALE, 1.0);
        setBase(player, Attribute.MOVEMENT_SPEED, 0.1);
        setBase(player, Attribute.JUMP_STRENGTH, 0.42);
        setBase(player, Attribute.GRAVITY, 0.08);
        player.setCollidable(true);
        // Far enough for the team ping with the mouse wheel, doors and stations check their own distance
        setBase(player, Attribute.BLOCK_INTERACTION_RANGE, PingManager.RANGE);
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
        // New here: only watches, players of this round come back in
        if (!userManager.isGamePlayer(player.getUniqueId())) {
            watcherManager.join(player);
            sendPack(player);
            player.setScoreboard(scoreboardManager.getScoreboard(player));
            Lang.broadcast("%s§7%s schaut jetzt zu", "%s§7%s is now spectating", plugin.getMonstersPrefix(), player.getName());
            return;
        }
        userManager.joinRunning(player);
        sendPack(player);
        player.setScoreboard(scoreboardManager.getScoreboard(player));
        Lang.broadcast("%s§a%s §7ist wieder dabei und spielt ab der nächsten Welle mit!", "%s§a%s §7is back and plays from the next wave!", plugin.getMonstersPrefix(), player.getName());
    }

    // Own pack for Monsters models (workbench, HUD), the lobby pack stays active
    private void sendPack(Player player) {
        String packUrl = plugin.getConfig().getString("resource-pack.url");
        String packSha1 = plugin.getConfig().getString("resource-pack.sha1");
        if (packUrl != null && !packUrl.isBlank() && packSha1 != null && packSha1.length() == 40) {
            player.addResourcePack(PackStatus.PACK_ID, packUrl, HexFormat.of().parseHex(packSha1), null, false);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage("");
        if (gameManager.getCurrentPhase() != GamePhase.PRE_GAME) return;

        userManager.removePlayer(event.getPlayer().getUniqueId());

        userManager.canJoin = true;
        gameManager.interruptGameStart();
        Lang.broadcast("%s§a%s §7hat die Runde verlassen!", "%s§a%s §7left the round!", plugin.getMonstersPrefix(), event.getPlayer().getDisplayName());
    }

    @EventHandler
    public void preventPlayerDamage(EntityDamageEvent event) {
        // After the round nobody takes damage any more, otherwise the vanilla death screen could come
        if (!(event.getEntity() instanceof Player) || gameManager.getCurrentPhase() == GamePhase.WAVE) return;
        event.setCancelled(true);
    }
}
