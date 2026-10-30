package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.RoundLoader;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import de.ethicbuilds.monsters.weapons.Weapon;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;


/***
 * Listener for the whole Game (Pregame, Wave, AfterGame)
 */
public class GameListener implements Listener {
    @Inject
    private UserManager userManager;
    @Inject
    private Main plugin;
    @Inject
    private RoundLoader roundLoader;
    @Inject
    private GameManager gameManager;

    //Weapon Shoot
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);

        // Watchers of a running round are no game players
        var gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;


        ItemStack item = event.getItem();
        if (item == null) return;

        var weapon = gamePlayer.getWeapon(event.getItem());

        if (weapon == null) return;

        // Sniper: left click fires from the hip, right click looks through the scope, reloading goes by itself
        if (weapon instanceof de.ethicbuilds.monsters.weapons.Sniper) {
            if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) weapon.shoot(gamePlayer.getPlayer());
            else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) de.ethicbuilds.monsters.weapons.SniperScope.rightClick(gamePlayer.getPlayer());
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            weapon.reload(gamePlayer.getPlayer());
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (event.getItem().equals(gamePlayer.getWeapon(item).getItem())) {
            weapon.shoot(gamePlayer.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerChangeWeapon(PlayerItemHeldEvent event) {
        GamePlayer gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());
        if (gamePlayer == null) return;

        ItemStack item = gamePlayer.getPlayer().getInventory().getItem(event.getNewSlot());
        Weapon weapon = item != null ? gamePlayer.getWeapon(item) : null;
        if (weapon == null) {
            AmmoHud.clear(gamePlayer.getPlayer());
            return;
        }

        AmmoHud.show(gamePlayer.getPlayer(), weapon);
    }

    // Everyone stays in adventure mode, also the team; only players who rejoined watch in spectator mode until the next wave
    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.ADVENTURE) return;
        if (event.getNewGameMode() == GameMode.SPECTATOR && userManager.isSpectator(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        event.message(null);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // Nothing can be dropped, the item stays where it was
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, event.getPlayer()::updateInventory);
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) { event.setCancelled(true); }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            event.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // A waiting server without a round keeps running
        if (Bukkit.getOnlinePlayers().size() == 1 && roundLoader.isLoaded()) {
            gameManager.requestStop();
            Bukkit.shutdown();
        }
    }
}
