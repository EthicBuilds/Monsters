package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PreGameListener implements Listener {
    @Inject
    private UserManager userManager;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        userManager.addPlayer(event.getPlayer());
    }
}
