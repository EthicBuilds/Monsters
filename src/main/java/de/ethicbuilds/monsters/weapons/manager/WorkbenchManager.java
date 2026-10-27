package de.ethicbuilds.monsters.weapons.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import de.ethicbuilds.monsters.weapons.Weapon;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/***
 * Weapon workbench: opens from wave 5 for 5000 coins, then every weapon can be upgraded 4 times
 * (15000, then double each time)
 */
public class WorkbenchManager implements Listener {
    public static final int UNLOCK_WAVE = 5;
    public static final int UNLOCK_PRICE = 5000;
    private static final int FIRST_UPGRADE_PRICE = 15000;
    private static final int[] WEAPON_SLOTS = {11, 13, 15};

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private WaveManager waveManager;

    private final Set<UUID> unlocked = new HashSet<>();

    public static int upgradePrice(int level) {
        return FIRST_UPGRADE_PRICE * (1 << level);
    }

    public void use(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        AmmoHud.pause(player);

        if (waveManager.getCurrentWave() < UNLOCK_WAVE) {
            player.sendActionBar(String.format("§7Die Werkbank öffnet ab §6Welle %d", UNLOCK_WAVE));
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0F, 0.6F);
            return;
        }
        if (!unlocked.contains(player.getUniqueId())) {
            if (gamePlayer.getCoins() < UNLOCK_PRICE) {
                player.sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum die Werkbank freizuschalten", UNLOCK_PRICE - gamePlayer.getCoins()));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                return;
            }
            gamePlayer.setCoins(gamePlayer.getCoins() - UNLOCK_PRICE);
            unlocked.add(player.getUniqueId());
            player.sendMessage(String.format("%s§7Werkbank §afreigeschaltet! §c-%d Coins", plugin.getMonstersPrefix(), UNLOCK_PRICE));
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0F, 1.0F);
        }
        open(gamePlayer);
    }

    private void open(GamePlayer gamePlayer) {
        Menu menu = new Menu();
        Inventory inventory = Bukkit.createInventory(menu, 27, "Werkbank");
        menu.inventory = inventory;

        List<Weapon> weapons = new ArrayList<>(gamePlayer.getWeapons().values());
        for (int i = 0; i < WEAPON_SLOTS.length && i < weapons.size(); i++) {
            inventory.setItem(WEAPON_SLOTS[i], icon(weapons.get(i), gamePlayer.getCoins()));
            menu.weapons.add(weapons.get(i));
        }
        gamePlayer.getPlayer().openInventory(inventory);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int index = -1;
        for (int i = 0; i < WEAPON_SLOTS.length; i++) if (WEAPON_SLOTS[i] == event.getSlot()) index = i;
        if (index < 0 || index >= menu.weapons.size()) return;

        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null) return;
        Weapon weapon = menu.weapons.get(index);

        if (weapon.getLevel() >= Weapon.MAX_LEVEL) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }
        int price = upgradePrice(weapon.getLevel());
        if (gamePlayer.getCoins() < price) {
            player.sendMessage(String.format("§cDu brauchst noch §6%d Coins §cfür das Upgrade", price - gamePlayer.getCoins()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }

        gamePlayer.setCoins(gamePlayer.getCoins() - price);
        weapon.upgrade();
        weapon.refill();
        weapon.reload(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8F, 1.2F);
        player.sendMessage(String.format("%s§a%s §7ist jetzt Stufe §6%d§7! §c-%d Coins", plugin.getMonstersPrefix(),
                weapon.getName(), weapon.getLevel(), price));
        event.getView().getTopInventory().setItem(event.getSlot(), icon(weapon, gamePlayer.getCoins()));
    }

    private ItemStack icon(Weapon weapon, int coins) {
        ItemStack item = weapon.getItem().clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(String.format("§7Stufe §6%d§7/%d", weapon.getLevel(), Weapon.MAX_LEVEL));
        lore.add(String.format("§7Schaden §f%.1f §7· Magazin §f%d", weapon.getDamage(), weapon.getMaxMagazine()));
        if (weapon.getLevel() < Weapon.MAX_LEVEL) {
            int price = upgradePrice(weapon.getLevel());
            lore.add("");
            lore.add(String.format("%sUpgrade: %d Coins", coins >= price ? "§6" : "§c", price));
            lore.add("§7Klicken zum Verbessern");
        } else {
            lore.add("§aMaximal verbessert");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static class Menu implements InventoryHolder {
        private Inventory inventory;
        @Getter private final List<Weapon> weapons = new ArrayList<>();

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
