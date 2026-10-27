package de.ethicbuilds.monsters.weapons.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import de.ethicbuilds.monsters.weapons.Weapon;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/***
 * Weapon workbench: opens from wave 5 for 5000 coins, then every weapon can be upgraded 4 times
 * (15000, then double each time). Menu design from the design session (font monsters:menu, 5 rows).
 */
public class WorkbenchManager implements Listener {
    public static final int UNLOCK_WAVE = 5;
    public static final int UNLOCK_PRICE = 5000;
    private static final int FIRST_UPGRADE_PRICE = 15000;

    private static final int[] WEAPON_SLOTS = {10, 19, 28};
    private static final int BIG_SLOT = 22, DAMAGE_SLOT = 16, MAGAZINE_SLOT = 25, ACTION_SLOT = 34, CLOSE_SLOT = 36;

    private static final Key MENU_FONT = Key.key("monsters", "menu");
    private static final Key UI_FONT = Key.key("ethicbuilds", "ui");
    private static final Key HUD_FONT = Key.key("monsters", "hud");
    private static final TextColor LIGHT = TextColor.color(0xF2F5F8);
    private static final TextColor GREY = TextColor.color(0x9AA6B8);
    private static final TextColor GREEN = TextColor.color(0x3BDB9A);
    private static final TextColor ORANGE = TextColor.color(0xF9A11E);
    private static final TextColor RED = TextColor.color(0xE84848);
    private static final TextColor GOLD = TextColor.color(0xFFD700);

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private WaveManager waveManager;

    private final Set<UUID> unlocked = new HashSet<>();
    private final Map<UUID, Integer> selected = new HashMap<>();

    public static int upgradePrice(int level) {
        return FIRST_UPGRADE_PRICE * (1 << level);
    }

    public void use(GamePlayer gamePlayer) {
        AmmoHud.pause(gamePlayer.getPlayer());
        open(gamePlayer);
    }

    private void open(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        List<Weapon> weapons = new ArrayList<>(gamePlayer.getWeapons().values());
        int index = Math.min(selected.getOrDefault(player.getUniqueId(), 0), Math.max(0, weapons.size() - 1));
        Weapon weapon = weapons.isEmpty() ? null : weapons.get(index);

        Menu menu = new Menu(weapons);
        Inventory inventory = Bukkit.createInventory(menu, 45, title(weapon != null ? weapon.getLevel() : 0));
        menu.inventory = inventory;

        for (int i = 0; i < WEAPON_SLOTS.length && i < weapons.size(); i++) {
            ItemStack item = weapons.get(i).getItem().clone();
            item.setAmount(1);
            int finalI = i;
            item.editMeta(meta -> {
                meta.displayName(text(weapons.get(finalI).getName().replaceAll("§.", ""), LIGHT));
                meta.lore(List.of(text("Stufe " + weapons.get(finalI).getLevel() + "/" + Weapon.MAX_LEVEL, GREY)));
                meta.setEnchantmentGlintOverride(finalI == index);
            });
            inventory.setItem(WEAPON_SLOTS[i], item);
        }

        if (weapon != null) {
            boolean max = weapon.getLevel() >= Weapon.MAX_LEVEL;
            inventory.setItem(BIG_SLOT, icon(Material.PAPER, "waffe_" + weapon.getModel(), text(weapon.getName().replaceAll("§.", ""), LIGHT)));
            inventory.setItem(DAMAGE_SLOT, icon(Material.PAPER, "werkbank_schaden", text("Schaden", GREY),
                    change(number(weapon.getDamage()), number(weapon.nextDamage()), max)));
            inventory.setItem(MAGAZINE_SLOT, icon(Material.PAPER, "werkbank_magazin", text("Magazin", GREY),
                    change(String.valueOf(weapon.getMaxMagazine()), String.valueOf(weapon.nextMagazine()), max)));
            inventory.setItem(ACTION_SLOT, actionButton(gamePlayer, weapon));
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        close.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("ethicbuilds", "gui/icon_zurueck"));
            meta.displayName(text("Schließen", LIGHT));
        });
        inventory.setItem(CLOSE_SLOT, close);

        selected.put(player.getUniqueId(), index);
        player.openInventory(inventory);
    }

    private ItemStack actionButton(GamePlayer gamePlayer, Weapon weapon) {
        if (waveManager.getCurrentWave() < UNLOCK_WAVE) {
            return icon(Material.PAPER, "werkbank_gesperrt", text("Freischalten", LIGHT), text("Ab Welle " + UNLOCK_WAVE, RED));
        }
        if (!unlocked.contains(gamePlayer.getPlayer().getUniqueId())) {
            return icon(Material.PAPER, "werkbank_gesperrt", text("Freischalten", LIGHT), price(UNLOCK_PRICE, gamePlayer.getCoins() >= UNLOCK_PRICE));
        }
        if (weapon.getLevel() >= Weapon.MAX_LEVEL) {
            return icon(Material.PAPER, "werkbank_maximal", text("Maximal", GOLD));
        }
        int price = upgradePrice(weapon.getLevel());
        boolean affordable = gamePlayer.getCoins() >= price;
        return icon(Material.PAPER, affordable ? "werkbank_verbessern" : "werkbank_verbessern_aus",
                text("Verbessern", GREEN).decoration(TextDecoration.BOLD, true), price(price, affordable));
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null) return;
        int slot = event.getSlot();

        for (int i = 0; i < WEAPON_SLOTS.length; i++) {
            if (WEAPON_SLOTS[i] == slot && i < menu.weapons.size()) {
                selected.put(player.getUniqueId(), i);
                player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8F, 1.2F);
                open(gamePlayer);
                return;
            }
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
        } else if (slot == ACTION_SLOT) {
            clickAction(gamePlayer, menu);
        }
    }

    private void clickAction(GamePlayer gamePlayer, Menu menu) {
        Player player = gamePlayer.getPlayer();
        if (waveManager.getCurrentWave() < UNLOCK_WAVE) {
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0F, 0.6F);
            return;
        }
        if (!unlocked.contains(player.getUniqueId())) {
            if (!pay(gamePlayer, UNLOCK_PRICE)) return;
            unlocked.add(player.getUniqueId());
            player.sendMessage(String.format("%s§7Werkbank §afreigeschaltet! §c-%d Coins", plugin.getMonstersPrefix(), UNLOCK_PRICE));
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0F, 1.0F);
            open(gamePlayer);
            return;
        }

        int index = selected.getOrDefault(player.getUniqueId(), 0);
        if (index >= menu.weapons.size()) return;
        Weapon weapon = menu.weapons.get(index);
        if (weapon.getLevel() >= Weapon.MAX_LEVEL) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }
        int price = upgradePrice(weapon.getLevel());
        if (!pay(gamePlayer, price)) return;

        weapon.upgrade();
        weapon.refill();
        weapon.reload(player);
        if (player.getInventory().getItemInMainHand().equals(weapon.getItem())) AmmoHud.refresh(player, weapon);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8F, 1.2F);
        player.sendMessage(String.format("%s§a%s §7ist jetzt Stufe §6%d§7! §c-%d Coins", plugin.getMonstersPrefix(),
                weapon.getName(), weapon.getLevel(), price));
        open(gamePlayer);
    }

    private boolean pay(GamePlayer gamePlayer, int price) {
        Player player = gamePlayer.getPlayer();
        if (gamePlayer.getCoins() < price) {
            player.sendMessage(String.format("§cDu brauchst noch §6%d Coins", price - gamePlayer.getCoins()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return false;
        }
        gamePlayer.setCoins(gamePlayer.getCoins() - price);
        return true;
    }

    // Background per level as glyph in the title, the text in the HUD font
    private static Component title(int level) {
        return Component.text()
                .append(Component.text("" + (char) ('' + level) + "", NamedTextColor.WHITE).font(MENU_FONT))
                .append(Component.text("Werkbank", LIGHT).font(UI_FONT))
                .build();
    }

    private static ItemStack icon(Material material, String model, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("monsters", "gui/" + model));
            meta.displayName(name);
            if (lore.length > 0) meta.lore(List.of(lore));
        });
        return item;
    }

    private static Component change(String now, String next, boolean max) {
        if (max) return text(now, LIGHT);
        return text(now, LIGHT).append(text(" → ", GREY)).append(text(next, GREEN));
    }

    private static Component price(int coins, boolean affordable) {
        return text(String.format(Locale.GERMANY, "%,d", coins), affordable ? ORANGE : RED)
                .append(text(" "))
                .append(Component.text("", NamedTextColor.WHITE).font(HUD_FONT).decoration(TextDecoration.ITALIC, false))
                .append(text(" Coins", GREY));
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.GERMANY, "%.2f", value).replaceAll("0$", "");
    }

    private static Component text(String content, TextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component text(String content) {
        return Component.text(content).decoration(TextDecoration.ITALIC, false);
    }

    private static class Menu implements InventoryHolder {
        private Inventory inventory;
        private final List<Weapon> weapons;

        private Menu(List<Weapon> weapons) {
            this.weapons = weapons;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
