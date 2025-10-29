package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.Weapon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

/***
 * Listener for the whole Game (Pregame, Wave, AfterGame)
 */
public class GameListener implements Listener {
    @Inject
    private UserManager userManager;

    //Weapon Shoot
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);

        var gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());

        if (userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;


        ItemStack item = event.getItem();
        if (item == null) return;

        var weapon = gamePlayer.getWeapon(event.getItem());

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            weapon.reload(gamePlayer.getPlayer());
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (weapon == null) return;

        if (event.getItem().equals(gamePlayer.getWeapon(item).getItem())) {
            weapon.shoot(gamePlayer.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerChangeWeapon(PlayerItemHeldEvent event) {
        GamePlayer gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());
        if (gamePlayer == null) return;

        ItemStack item = gamePlayer.getPlayer().getInventory().getItem(event.getNewSlot());

        if (item == null) return;

        Weapon weapon = gamePlayer.getWeapon(item);

        gamePlayer.getPlayer().setLevel(0);
        gamePlayer.getPlayer().setExp(0);

        if (weapon == null) return;

        setPlayerXPToWeaponAmo(gamePlayer.getPlayer(), weapon);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        event.message(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }



    private void setPlayerXPToWeaponAmo(Player player, Weapon weapon) {
        player.setLevel(Math.max((weapon.getAmmu() + weapon.getMagazine()), 0));
        float current = Math.max(weapon.getAmmu() + weapon.getMagazine(), 0);
        player.setExp(current / (weapon.getMaxAmmu() + weapon.getMaxMagazine()));
    }
}
