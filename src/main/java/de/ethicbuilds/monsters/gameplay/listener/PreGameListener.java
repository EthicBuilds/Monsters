package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PreGameListener implements Listener {
    @Inject
    private UserManager userManager;
    @Inject
    private GameManager gameManager;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (userManager.isFull()) {
            event.getPlayer().kickPlayer("Server is already full.");
            return;
        }
        userManager.addPlayer(event.getPlayer());

        if (userManager.isFull()) {
            gameManager.gameStart();
        }

        event.setJoinMessage("");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        userManager.removePlayer(event.getPlayer().getUniqueId());
        gameManager.interruptGameStart();
        event.setQuitMessage("");
    }
}
