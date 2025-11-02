package de.ethicbuilds.monsters.gameplay.listener;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.dto.StopServerDto;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.Weapon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.eclipse.sisu.Priority;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/***
 * Listener for the whole Game (Pregame, Wave, AfterGame)
 */
public class GameListener implements Listener {
    @Inject
    private UserManager userManager;
    @Inject
    private Main plugin;

    private final Gson gson;
    public GameListener() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    //Weapon Shoot
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(true);

        var gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());

        if (userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;


        ItemStack item = event.getItem();
        if (item == null) return;

        var weapon = gamePlayer.getWeapon(event.getItem());

        if (weapon == null) return;

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

        if (item == null) return;

        Weapon weapon = gamePlayer.getWeapon(item);

        gamePlayer.getPlayer().setLevel(0);
        gamePlayer.getPlayer().setExp(0);

        if (weapon == null) return;

        setPlayerXPToWeaponAmo(gamePlayer.getPlayer(), weapon);
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
        event.setCancelled(true);
        if (event.getItemDrop().getItemStack().getType() == Material.NETHERITE_HOE) {
            Weapon weapon = userManager.getGamePlayer(event.getPlayer().getUniqueId()).getWeapon(event.getItemDrop().getItemStack());
            if (weapon == null) return;

            Player player = event.getPlayer();

            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && item.getType() == Material.NETHERITE_HOE) {
                    player.getInventory().setItem(i, null);
                }
            }

            event.getPlayer().getInventory().setItem(1, weapon.getItem());
        }
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
        if (Bukkit.getOnlinePlayers().size() == 1) {
            HttpClient client = HttpClient.newHttpClient();

            var dto = new StopServerDto(plugin.getConfig().getString("server-name"));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/gameManager/api/server/stop"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(dto)))
                    .build();

            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }


            Bukkit.shutdown();
        }
    }


    private void setPlayerXPToWeaponAmo(Player player, Weapon weapon) {
        player.setLevel(Math.max((weapon.getAmmu() + weapon.getMagazine()), 0));
        float current = Math.max(weapon.getAmmu() + weapon.getMagazine(), 0);
        player.setExp(current / (weapon.getMaxAmmu() + weapon.getMaxMagazine()));
    }
}
