package de.ethicbuilds.monsters.weapons.manager;

import de.ethicbuilds.monsters.sound.Sounds;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
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
import org.bukkit.event.player.PlayerJoinEvent;
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
 * (price per weapon, then 1.5 times each level). Menu design from the design session: one background,
 * tab, stars, button and texts are drawn into the title (WorkbenchTitle).
 */
public class WorkbenchManager implements Listener {
    public static final int UNLOCK_WAVE = 5;
    public static final int UNLOCK_PRICE = 5000;

    private static final int[] WEAPON_SLOTS = {0, 9, 18};
    private static final int[] BUTTON_SLOTS = {32, 33, 34, 35, 41, 42, 43, 44};
    private static final int BIG_SLOT = 11, LEVEL_SLOT = 5, DAMAGE_SLOT = 14, MAGAZINE_SLOT = 23, CLOSE_SLOT = 36;

    private static final Key HUD_FONT = Key.key("monsters", "hud");
    private static final String COIN = "\uE017", ARROW = " \u2192 ";
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor NEW_VALUE = TextColor.color(0x6CF09A);
    private static final TextColor BUTTON_GREY = TextColor.color(0xC8CED8);
    private static final TextColor TOO_EXPENSIVE = TextColor.color(0xFF8A80);
    // Level colours of the ammo bar: I green, II blue, III purple, IV gold
    private static final int[] LEVEL_COLORS = {0x3BDB9A, 0x49A2E8, 0x9678F0, 0xFFCD28};
    private static final TextColor LIGHT = TextColor.color(0xF2F5F8);
    private static final TextColor GREY = TextColor.color(0x9AA6B8);
    private static final TextColor GREEN = TextColor.color(0x3BDB9A);
    private static final TextColor ORANGE = TextColor.color(0xF9A11E);
    private static final TextColor RED = TextColor.color(0xE84848);
    private static final TextColor GOLD = TextColor.color(0xFFD700);

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private WaveManager waveManager;
    @Inject private DecorationManager decorationManager;

    // Unlocked once for the whole team, the coins can be put together
    private boolean unlocked;
    private int unlockPaid;
    private final Map<UUID, Integer> selected = new HashMap<>();

    // Each level 1.5 times the last one, rounded to 500
    public static int upgradePrice(Weapon weapon) {
        return (int) Math.round(weapon.getUpgradePrice() * Math.pow(1.5, weapon.getLevel()) / 500) * 500;
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
        Inventory inventory = Bukkit.createInventory(menu, 45, title(gamePlayer, weapon, index));
        menu.inventory = inventory;

        for (int i = 0; i < WEAPON_SLOTS.length && i < weapons.size(); i++) {
            ItemStack item = weapons.get(i).getItem().clone();
            item.setAmount(1);
            int finalI = i;
            item.editMeta(meta -> {
                meta.displayName(text(weapons.get(finalI).getName().replaceAll("§.", ""), LIGHT));
                meta.lore(List.of(text("Stufe " + weapons.get(finalI).getLevel() + "/" + Weapon.MAX_LEVEL, GREY)));
            });
            inventory.setItem(WEAPON_SLOTS[i], item);
        }

        if (weapon != null) {
            boolean max = weapon.getLevel() >= Weapon.MAX_LEVEL;
            inventory.setItem(BIG_SLOT, icon(Material.PAPER, "waffe_" + weapon.getModel(), text(weapon.getName().replaceAll("§.", ""), LIGHT)));
            inventory.setItem(LEVEL_SLOT, icon(Material.PAPER, "werkbank_stufe", text("Stufe", GREY),
                    change(weapon.getLevel() + "/" + Weapon.MAX_LEVEL, (weapon.getLevel() + 1) + "/" + Weapon.MAX_LEVEL, max)));
            inventory.setItem(DAMAGE_SLOT, icon(Material.PAPER, "werkbank_schaden", text("Schaden", GREY),
                    change(number(weapon.getDamage()), number(weapon.nextDamage()), max)));
            inventory.setItem(MAGAZINE_SLOT, icon(Material.PAPER, "werkbank_magazin", text("Magazin", GREY),
                    change(String.valueOf(weapon.getMaxMagazine()), String.valueOf(weapon.nextMagazine()), max)));
            ItemStack button = actionButton(gamePlayer, weapon);
            for (int slot : BUTTON_SLOTS) inventory.setItem(slot, button);
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
            return icon(Material.PAPER, "leer", text("Gesperrt", LIGHT), text("Ab Welle " + UNLOCK_WAVE, RED));
        }
        if (!unlocked) {
            int remaining = UNLOCK_PRICE - unlockPaid;
            return icon(Material.PAPER, "leer", text("Freischalten", LIGHT), price(remaining, gamePlayer.getCoins() >= remaining),
                    text("Zu wenig? Dann legst du alles ein, das Team legt zusammen", GREY));
        }
        if (weapon.getLevel() >= Weapon.MAX_LEVEL) {
            return icon(Material.PAPER, "leer", text("Maximal", GOLD));
        }
        int price = upgradePrice(weapon);
        boolean affordable = gamePlayer.getCoins() >= price;
        return icon(Material.PAPER, "leer", text("Verbessern", GREEN).decoration(TextDecoration.BOLD, true), price(price, affordable));
    }

    // Back in the round: the sign without the unlock price again
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (unlocked) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> decorationManager.showWorkbenchOpen(player), 5L);
        }
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
        } else if (java.util.Arrays.stream(BUTTON_SLOTS).anyMatch(s -> s == slot)) {
            clickAction(gamePlayer, menu);
        }
    }

    private void clickAction(GamePlayer gamePlayer, Menu menu) {
        Player player = gamePlayer.getPlayer();
        if (waveManager.getCurrentWave() < UNLOCK_WAVE) {
            Sounds.deny(player);
            return;
        }
        if (!unlocked) {
            unlock(gamePlayer);
            open(gamePlayer);
            return;
        }

        int index = selected.getOrDefault(player.getUniqueId(), 0);
        if (index >= menu.weapons.size()) return;
        Weapon weapon = menu.weapons.get(index);
        if (weapon.getLevel() >= Weapon.MAX_LEVEL) {
            Sounds.deny(player);
            return;
        }
        int price = upgradePrice(weapon);
        if (!pay(gamePlayer, price)) return;

        weapon.upgrade();
        weapon.refill();
        weapon.reload(player);
        if (player.getInventory().getItemInMainHand().equals(weapon.getItem())) AmmoHud.refresh(player, weapon);
        Sounds.upgrade(player, weapon.getLevel() >= Weapon.MAX_LEVEL);
        player.sendMessage(String.format("%s§a%s §7ist jetzt Stufe §6%d§7! §c-%d Coins", plugin.getMonstersPrefix(),
                weapon.getName(), weapon.getLevel(), price));
        open(gamePlayer);
    }

    // Whoever has the rest unlocks it for everyone, otherwise all own coins go in
    private void unlock(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        int remaining = UNLOCK_PRICE - unlockPaid;
        int coins = gamePlayer.getCoins();
        if (coins <= 0) {
            player.sendMessage(String.format("§cNoch §6%d Coins §cfür die Werkbank, legt zusammen!", remaining));
            Sounds.deny(player);
            return;
        }
        int paid = Math.min(coins, remaining);
        gamePlayer.setCoins(coins - paid);
        unlockPaid += paid;
        if (unlockPaid < UNLOCK_PRICE) {
            decorationManager.setWorkbenchPrice(UNLOCK_PRICE - unlockPaid);
            Bukkit.broadcastMessage(String.format("%s§a%s §7legt §6%d Coins §7für die §aWerkbank §7ein (§6%d§7/§6%d§7). Noch §6%d Coins§7, legt zusammen!",
                    plugin.getMonstersPrefix(), player.getName(), paid, unlockPaid, UNLOCK_PRICE, UNLOCK_PRICE - unlockPaid));
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 1.2F);
            return;
        }
        unlocked = true;
        Bukkit.getOnlinePlayers().forEach(decorationManager::showWorkbenchOpen);
        Bukkit.broadcastMessage(String.format("%s§a%s §7hat die §aWerkbank §7für alle freigeschaltet!", plugin.getMonstersPrefix(), player.getName()));
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0F, 1.0F));
    }

    private boolean pay(GamePlayer gamePlayer, int price) {
        Player player = gamePlayer.getPlayer();
        if (gamePlayer.getCoins() < price) {
            player.sendMessage(String.format("§cDu brauchst noch §6%d Coins", price - gamePlayer.getCoins()));
            Sounds.deny(player);
            return false;
        }
        gamePlayer.setCoins(gamePlayer.getCoins() - price);
        return true;
    }

    // Everything that changes is drawn into the title: tab, stars, button, card and balance
    private Component title(GamePlayer gamePlayer, Weapon weapon, int row) {
        WorkbenchTitle title = new WorkbenchTitle().name("Werkbank");
        int coins = gamePlayer.getCoins();
        title.centered("guthaben_name", 52, new WorkbenchTitle.Part("Guthaben", GREY))
                .centered("guthaben", 52, new WorkbenchTitle.Part(COIN + " " + coins(coins), ORANGE));
        if (weapon == null) return title.build();

        int level = weapon.getLevel();
        boolean max = level >= Weapon.MAX_LEVEL;
        boolean locked = waveManager.getCurrentWave() < UNLOCK_WAVE;
        boolean open = unlocked;
        boolean values = open && !max || locked;

        title.glyph((char) (0xE010 + row), 6, 21)
                .glyph((char) (0xE030 + level), level == 0 ? 25 : 22, level == 0 ? 56 : level == Weapon.MAX_LEVEL ? 62 : 59);

        // Card: what you get
        title.left("name_0", 117, new WorkbenchTitle.Part("Stufe", GREY));
        if (max) title.left("wert_0", 117, new WorkbenchTitle.Part(level + "/" + Weapon.MAX_LEVEL, LIGHT));
        else if (values) title.left("wert_0", 117, new WorkbenchTitle.Part(String.valueOf(level), LIGHT),
                new WorkbenchTitle.Part(ARROW, ORANGE), new WorkbenchTitle.Part(String.valueOf(level + 1), TextColor.color(LEVEL_COLORS[level])));
        else title.left("wert_0", 117, new WorkbenchTitle.Part("-", LIGHT));
        title.left("name_1", 117, new WorkbenchTitle.Part("Schaden", GREY));
        cardValue(title, "wert_1", number(weapon.getDamage()), number(weapon.nextDamage()), values);
        title.left("name_2", 117, new WorkbenchTitle.Part("Magazin", GREY));
        cardValue(title, "wert_2", String.valueOf(weapon.getMaxMagazine()), String.valueOf(weapon.nextMagazine()), values);

        // Button with price
        if (locked) {
            title.glyph('\uE024', 95, 74)
                    .centered("knopf", 132, new WorkbenchTitle.Part("Gesperrt", BUTTON_GREY))
                    .centered("knopf_2", 132, new WorkbenchTitle.Part("Ab Welle " + UNLOCK_WAVE, ORANGE));
        } else if (!open) {
            title.glyph('\uE022', 93, 78)
                    .centered("knopf", 132, new WorkbenchTitle.Part("Freischalten", WHITE))
                    .centered("preis", 132, new WorkbenchTitle.Part(COIN + " " + coins(UNLOCK_PRICE - unlockPaid), WHITE));
        } else if (max) {
            title.glyph('\uE023', 93, 78)
                    .centered("knopf", 132, new WorkbenchTitle.Part("Maximal", TextColor.color(0x3B2A00)))
                    .centered("knopf_2", 132, new WorkbenchTitle.Part("Stufe " + level + "/" + Weapon.MAX_LEVEL, TextColor.color(0x5A4200)));
        } else {
            int price = upgradePrice(weapon);
            boolean affordable = coins >= price;
            title.glyph(affordable ? '\uE020' : '\uE021', affordable ? 93 : 95, affordable ? 78 : 74)
                    .centered("knopf", 132, new WorkbenchTitle.Part("Verbessern", affordable ? WHITE : BUTTON_GREY))
                    .centered("preis", 132, new WorkbenchTitle.Part(COIN, affordable ? TextColor.color(0xFFD36B) : TOO_EXPENSIVE),
                            new WorkbenchTitle.Part(" " + coins(price), affordable ? WHITE : TOO_EXPENSIVE));
        }
        return title.build();
    }

    private static void cardValue(WorkbenchTitle title, String line, String now, String next, boolean arrow) {
        if (arrow) {
            title.left(line, 117, new WorkbenchTitle.Part(now, LIGHT), new WorkbenchTitle.Part(ARROW, ORANGE), new WorkbenchTitle.Part(next, NEW_VALUE));
        } else {
            title.left(line, 117, new WorkbenchTitle.Part(now, LIGHT));
        }
    }

    private static String coins(int coins) {
        return String.format(Locale.GERMANY, "%,d", coins);
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
