package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
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

        player.setScoreboard(scoreboardManager.getScoreboard());

        player.sendMessage(String.format("%sMap: §6%s", plugin.getMonstersPrefix(), gameManager.getGameConfig().getMapName()));
        player.sendMessage(String.format("%s§aGebaut von §2%s", plugin.getMonstersPrefix(),  String.join("§7, §2", gameManager.getGameConfig().getMapBuilder())));
        player.setHealth(20);
        player.setFlying(false);

        Bukkit.broadcastMessage(String.format("%s§a%s §7hat die Runde betreten!", plugin.getMonstersPrefix(), event.getPlayer().getDisplayName()));

        event.setJoinMessage("");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage("");
        userManager.removePlayer(event.getPlayer().getUniqueId());

        if (gameManager.getCurrentPhase() != GamePhase.PRE_GAME) return;

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
