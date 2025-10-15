package de.ethicbuilds.monsters.test;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class TestListener implements Listener {
    @Inject
    private UserManager userManager;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        var gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;


        if (event.getItem().equals(gamePlayer.getWeapon(item).getItem())) {
            gamePlayer.getWeapon(event.getItem()).shoot(gamePlayer.getPlayer());
        }
    }
}
