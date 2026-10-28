package de.ethicbuilds.monsters.weapons.manager;

import de.ethicbuilds.monsters.sound.Sounds;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.items.ArmorSet;
import de.ethicbuilds.monsters.items.CrowbarLevel;
import de.ethicbuilds.monsters.items.GameItems;
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
    // Armor is the fourth entry under the weapons
    private static final int ARMOR_SLOT = 27, ARMOR_INDEX = 3;
    // Crowbar entry, place until the design session gives it its own tile
    private static final int CROWBAR_SLOT = 28, CROWBAR_INDEX = 4;
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
        int index = selected.getOrDefault(player.getUniqueId(), 0);
        if (index != ARMOR_INDEX && index != CROWBAR_INDEX) index = Math.min(index, Math.max(0, weapons.size() - 1));
        boolean armor = index == ARMOR_INDEX;
        boolean crowbar = index == CROWBAR_INDEX;
        Weapon weapon = armor || crowbar || weapons.isEmpty() ? null : weapons.get(index);
        Card card = armor ? armorCard(gamePlayer) : crowbar ? crowbarCard(gamePlayer) : weapon != null ? card(weapon) : null;

        Menu menu = new Menu(weapons);
        Inventory inventory = Bukkit.createInventory(menu, 45, title(gamePlayer, card, index));
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
        int armorLevel = gamePlayer.getArmorLevel();
        ItemStack armorIcon = ArmorSet.icon(armorLevel);
        armorIcon.editMeta(meta -> {
            meta.displayName(text("Rüstung", LIGHT));
            meta.lore(List.of(text(ArmorSet.name(armorLevel) + " · Stufe " + armorLevel + "/" + ArmorSet.MAX_LEVEL, GREY)));
        });
        inventory.setItem(ARMOR_SLOT, armorIcon);
        int crowbarLevel = gamePlayer.getCrowbarLevel();
        ItemStack crowbarIcon = GameItems.crowbar();
        crowbarIcon.editMeta(meta -> {
            meta.displayName(text("Brechstange", LIGHT));
            meta.lore(List.of(text("Stufe " + crowbarLevel + "/" + CrowbarLevel.MAX_LEVEL, GREY)));
        });
        inventory.setItem(CROWBAR_SLOT, crowbarIcon);

        if (armor) {
            boolean max = armorLevel >= ArmorSet.MAX_LEVEL;
            ItemStack big = ArmorSet.icon(max ? armorLevel : armorLevel + 1);
            big.editMeta(meta -> meta.displayName(text("Rüstung: " + ArmorSet.name(max ? armorLevel : armorLevel + 1), LIGHT)));
            inventory.setItem(BIG_SLOT, big);
            inventory.setItem(LEVEL_SLOT, icon(Material.PAPER, "werkbank_stufe", text("Stufe", GREY),
                    change(armorLevel + "/" + ArmorSet.MAX_LEVEL, (armorLevel + 1) + "/" + ArmorSet.MAX_LEVEL, max)));
            inventory.setItem(DAMAGE_SLOT, icon(Material.PAPER, "werkbank_schaden", text("Schutz", GREY),
                    change(String.valueOf(ArmorSet.protection(armorLevel)), String.valueOf(ArmorSet.protection(armorLevel + 1)), max)));
            inventory.setItem(MAGAZINE_SLOT, icon(Material.PAPER, "werkbank_magazin", text("Set", GREY),
                    change(ArmorSet.name(armorLevel), ArmorSet.name(armorLevel + 1), max)));
            ItemStack button = button(gamePlayer, card.level(), card.price());
            for (int slot : BUTTON_SLOTS) inventory.setItem(slot, button);
        } else if (crowbar) {
            boolean max = crowbarLevel >= CrowbarLevel.MAX_LEVEL;
            ItemStack big = GameItems.crowbar();
            big.editMeta(meta -> meta.displayName(text("Brechstange", LIGHT)));
            inventory.setItem(BIG_SLOT, big);
            inventory.setItem(LEVEL_SLOT, icon(Material.PAPER, "werkbank_stufe", text("Stufe", GREY),
                    change(crowbarLevel + "/" + CrowbarLevel.MAX_LEVEL, (crowbarLevel + 1) + "/" + CrowbarLevel.MAX_LEVEL, max)));
            inventory.setItem(DAMAGE_SLOT, icon(Material.PAPER, "werkbank_schaden", text("Tempo bei Kisten und Türen", GREY),
                    change(factor(CrowbarLevel.speed(crowbarLevel)), factor(CrowbarLevel.speed(crowbarLevel + 1)), max)));
            inventory.setItem(MAGAZINE_SLOT, icon(Material.PAPER, "werkbank_magazin", text("Beute-Chance in Kisten (%)", GREY),
                    change(loot(crowbarLevel), loot(crowbarLevel + 1), max)));
            ItemStack button = button(gamePlayer, card.level(), card.price());
            for (int slot : BUTTON_SLOTS) inventory.setItem(slot, button);
        } else if (weapon != null) {
            boolean max = weapon.getLevel() >= Weapon.MAX_LEVEL;
            inventory.setItem(BIG_SLOT, icon(Material.PAPER, "waffe_" + weapon.getModel(), text(weapon.getName().replaceAll("§.", ""), LIGHT)));
            inventory.setItem(LEVEL_SLOT, icon(Material.PAPER, "werkbank_stufe", text("Stufe", GREY),
                    change(weapon.getLevel() + "/" + Weapon.MAX_LEVEL, (weapon.getLevel() + 1) + "/" + Weapon.MAX_LEVEL, max)));
            inventory.setItem(DAMAGE_SLOT, weapon.upgradesDamage()
                    ? icon(Material.PAPER, "werkbank_schaden", text("Schaden", GREY), change(number(weapon.getDamage()), number(weapon.nextDamage()), max))
                    : icon(Material.PAPER, "werkbank_schaden", text("Nachladen in Sekunden", GREY), change(seconds(weapon.getFireRate()), seconds(weapon.nextFireRate()), max)));
            inventory.setItem(MAGAZINE_SLOT, icon(Material.PAPER, "werkbank_magazin", text("Magazin", GREY), weapon.upgradesDamage()
                    ? change(String.valueOf(weapon.getMaxMagazine()), String.valueOf(weapon.nextMagazine()), max)
                    : change(String.valueOf(weapon.getMaxMagazine()), "", true)));
            ItemStack button = button(gamePlayer, weapon.getLevel(), upgradePrice(weapon));
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

    private ItemStack button(GamePlayer gamePlayer, int level, int price) {
        if (waveManager.getCurrentWave() < UNLOCK_WAVE) {
            return icon(Material.PAPER, "leer", text("Gesperrt", LIGHT), text("Ab Welle " + UNLOCK_WAVE, RED));
        }
        if (!unlocked) {
            int remaining = UNLOCK_PRICE - unlockPaid;
            return icon(Material.PAPER, "leer", text("Freischalten", LIGHT), price(remaining, gamePlayer.getCoins() >= remaining),
                    text("Zu wenig? Dann legst du alles ein, das Team legt zusammen", GREY));
        }
        if (level >= Weapon.MAX_LEVEL) {
            return icon(Material.PAPER, "leer", text("Maximal", GOLD));
        }
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
        if (slot == CROWBAR_SLOT) {
            selected.put(player.getUniqueId(), CROWBAR_INDEX);
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8F, 1.2F);
            open(gamePlayer);
        } else if (slot == ARMOR_SLOT) {
            selected.put(player.getUniqueId(), ARMOR_INDEX);
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8F, 1.2F);
            open(gamePlayer);
        } else if (slot == CLOSE_SLOT) {
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
        if (index == ARMOR_INDEX) {
            upgradeArmor(gamePlayer);
            open(gamePlayer);
            return;
        }
        if (index == CROWBAR_INDEX) {
            upgradeCrowbar(gamePlayer);
            open(gamePlayer);
            return;
        }
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

    private void upgradeCrowbar(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        int level = gamePlayer.getCrowbarLevel();
        if (level >= CrowbarLevel.MAX_LEVEL) {
            Sounds.deny(player);
            return;
        }
        int price = CrowbarLevel.price(level);
        if (!pay(gamePlayer, price)) return;
        gamePlayer.setCrowbarLevel(level + 1);
        Sounds.upgrade(player, level + 1 >= CrowbarLevel.MAX_LEVEL);
        player.sendMessage(String.format("%s§7Deine §aBrechstange §7ist jetzt Stufe §6%d§7! §c-%d Coins", plugin.getMonstersPrefix(), level + 1, price));
    }

    private void upgradeArmor(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        int level = gamePlayer.getArmorLevel();
        if (level >= ArmorSet.MAX_LEVEL) {
            Sounds.deny(player);
            return;
        }
        int price = ArmorSet.price(level);
        if (!pay(gamePlayer, price)) return;
        gamePlayer.setArmorLevel(level + 1);
        ArmorSet.equip(player, level + 1);
        Sounds.upgrade(player, level + 1 >= ArmorSet.MAX_LEVEL);
        player.sendMessage(String.format("%s§7Deine Rüstung ist jetzt §a%s §7(Stufe §6%d§7)! §c-%d Coins", plugin.getMonstersPrefix(),
                ArmorSet.name(level + 1), level + 1, price));
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
    // What one upgrade gives, the same card for weapons and armor
    private record Card(int level, String name1, String now1, String next1, boolean arrow1,
                        String name2, String now2, String next2, boolean arrow2, int price) {}

    private static Card card(Weapon weapon) {
        if (weapon.upgradesDamage()) {
            return new Card(weapon.getLevel(), "Schaden", number(weapon.getDamage()), number(weapon.nextDamage()), true,
                    "Magazin", String.valueOf(weapon.getMaxMagazine()), String.valueOf(weapon.nextMagazine()), true, upgradePrice(weapon));
        }
        // Sniper: only reloads faster, damage and magazine stay
        return new Card(weapon.getLevel(), "Nachladen", seconds(weapon.getFireRate()), seconds(weapon.nextFireRate()), true,
                "Magazin", String.valueOf(weapon.getMaxMagazine()), "", false, upgradePrice(weapon));
    }

    private static Card armorCard(GamePlayer gamePlayer) {
        int level = gamePlayer.getArmorLevel();
        return new Card(level, "Schutz", String.valueOf(ArmorSet.protection(level)), String.valueOf(ArmorSet.protection(level + 1)), true,
                "Set", "", ArmorSet.name(Math.min(level + 1, ArmorSet.MAX_LEVEL)), level < ArmorSet.MAX_LEVEL, ArmorSet.price(level));
    }

    private static Card crowbarCard(GamePlayer gamePlayer) {
        int level = gamePlayer.getCrowbarLevel();
        return new Card(level, "Tempo", factor(CrowbarLevel.speed(level)), factor(CrowbarLevel.speed(level + 1)), true,
                "Beute", loot(level), loot(level + 1), true, CrowbarLevel.price(level));
    }

    private static String factor(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }

    // Chance in percent that a crate gives a grenade or coins
    private static String loot(int level) {
        return String.valueOf((int) Math.round((0.5 + 2 * CrowbarLevel.extraLoot(Math.min(level, CrowbarLevel.MAX_LEVEL))) * 100));
    }

    private Component title(GamePlayer gamePlayer, Card card, int row) {
        WorkbenchTitle title = new WorkbenchTitle().name("Werkbank");
        int coins = gamePlayer.getCoins();
        title.centered("guthaben_name", 52, new WorkbenchTitle.Part("Guthaben", GREY))
                .centered("guthaben", 52, new WorkbenchTitle.Part(COIN + " " + coins(coins), ORANGE));
        if (card == null) return title.build();

        int level = card.level();
        boolean max = level >= Weapon.MAX_LEVEL;
        boolean locked = waveManager.getCurrentWave() < UNLOCK_WAVE;
        boolean open = unlocked;
        boolean values = open && !max || locked;

        // Tab of the selected entry: weapons in rows 0-2, armor in row 3 (closed on the right)
        if (row <= ARMOR_INDEX) title.glyph((char) (0xE010 + row), 6, row == ARMOR_INDEX ? 20 : 21);
        title.glyph((char) (0xE030 + level), level == 0 ? 30 : 27, level == 0 ? 46 : level == Weapon.MAX_LEVEL ? 52 : 49);

        // Card: what you get
        title.left("name_0", 117, new WorkbenchTitle.Part("Stufe", GREY));
        if (max) title.left("wert_0", 117, new WorkbenchTitle.Part(level + "/" + Weapon.MAX_LEVEL, LIGHT));
        else if (values) title.left("wert_0", 117, new WorkbenchTitle.Part(String.valueOf(level), LIGHT),
                new WorkbenchTitle.Part(ARROW, ORANGE), new WorkbenchTitle.Part(String.valueOf(level + 1), TextColor.color(LEVEL_COLORS[level])));
        else title.left("wert_0", 117, new WorkbenchTitle.Part("-", LIGHT));
        title.left("name_1", 117, new WorkbenchTitle.Part(card.name1(), GREY));
        cardValue(title, "wert_1", card.now1(), card.next1(), values && card.arrow1());
        title.left("name_2", 117, new WorkbenchTitle.Part(card.name2(), GREY));
        cardValue(title, "wert_2", card.now2().isEmpty() && !(values && card.arrow2()) ? card.next2() : card.now2(), card.next2(), values && card.arrow2());

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
            int price = card.price();
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

    // Seconds between shots, short enough for the card
    private static String seconds(int ticks) {
        return String.format(Locale.GERMANY, "%.1f", ticks / 20.0);
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
