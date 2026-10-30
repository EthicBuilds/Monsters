package de.ethicbuilds.monsters.weapons.manager;

import de.ethicbuilds.monsters.sound.Sounds;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.items.ArmorSet;
import de.ethicbuilds.monsters.items.CrowbarLevel;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.MenuTitle;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import de.ethicbuilds.monsters.weapons.Pistol;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/***
 * Weapon workbench: opens from wave 5 for 5000 coins, then every weapon can be upgraded 4 times
 * (price per weapon, then 1.5 times each level). Menu design from the design session: one background,
 * tab, stars, button and texts are drawn into the title (MenuTitle).
 */
public class WorkbenchManager implements Listener {
    public static final int UNLOCK_WAVE = 5;
    public static final int UNLOCK_PRICE = 5000;

    private static final int[] WEAPON_SLOTS = {0, 9, 18};
    // Armor is the fourth entry under the weapons
    private static final int ARMOR_SLOT = 27, ARMOR_INDEX = 3;
    private static final int CROWBAR_SLOT = 36, CROWBAR_INDEX = 4;
    private static final int[] BUTTON_SLOTS = {32, 33, 34, 35, 41, 42, 43, 44};
    private static final int BIG_SLOT = 11, LEVEL_SLOT = 5, DAMAGE_SLOT = 14, MAGAZINE_SLOT = 23, CLOSE_SLOT = 45;
    // Scrapping: second click within 3 seconds confirms
    private static final int[] SCRAP_SLOTS = {46, 47, 48, 49, 50, 51, 52, 53};
    private static final TextColor SCRAP_GOLD = TextColor.color(0xFFCC2E), SCRAP_GREY = TextColor.color(0x8A93A3);
    private static final double SCRAP_REFUND = 0.75;
    private static final long SCRAP_CONFIRM_MILLIS = 3000;

    private static final Key HUD_FONT = Key.key("monsters", "hud");
    private static final String COIN = "\uE017", ARROW = " \u2192 ";
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor NEW_VALUE = TextColor.color(0x6CF09A);
    private static final TextColor BUTTON_GREY = TextColor.color(0xC8CED8);
    private static final TextColor TOO_EXPENSIVE = TextColor.color(0xFF8A80);
    // Level colours of the ammo bar: I green, II blue, III purple, IV gold
    // Level V from the design session
    private static final int LEVEL_5_COLOR = 0xFF5CCD, STAR_5_WIDTH = 64;
    private static final int[] LEVEL_COLORS = {0x3BDB9A, 0x49A2E8, 0x9678F0, 0xFFCD28, LEVEL_5_COLOR};
    // Width of the star glyphs per level (no stars, 1-3, 4 and 5 stars)
    private static final int[] STAR_WIDTHS = {46, 49, 49, 49, 52, STAR_5_WIDTH};
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
    private final Map<UUID, Long> scrapAsked = new HashMap<>();

    // Each level 1.5 times the last one, rounded to 500
    public static int upgradePrice(Weapon weapon) {
        return upgradePrice(weapon, weapon.getLevel());
    }

    private static int upgradePrice(Weapon weapon, int level) {
        return (int) Math.round(weapon.getUpgradePrice() * Math.pow(1.5, level) / 500) * 500;
    }

    /** 3/4 of what went into the weapon: price and all upgrades, 0 for the start pistol without upgrades */
    public static int scrapValue(Weapon weapon) {
        int invested = weapon.getPrice();
        for (int level = 0; level < weapon.getLevel(); level++) invested += upgradePrice(weapon, level);
        return (int) Math.round(invested * SCRAP_REFUND);
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
        Card card = armor ? armorCard(gamePlayer) : crowbar ? crowbarCard(gamePlayer) : weapon != null ? card(player, weapon) : null;

        Menu menu = new Menu(weapons);
        Inventory inventory = Bukkit.createInventory(menu, 54, title(gamePlayer, card, index, weapon));
        menu.inventory = inventory;

        String levelLabel = Lang.t(player, "Stufe", "Level");
        for (int i = 0; i < WEAPON_SLOTS.length && i < weapons.size(); i++) {
            ItemStack item = weapons.get(i).getItem().clone();
            item.setAmount(1);
            int finalI = i;
            item.editMeta(meta -> {
                meta.displayName(text(weapons.get(finalI).getName(player).replaceAll("§.", ""), LIGHT));
                meta.lore(List.of(text(levelLabel + " " + weapons.get(finalI).getLevel() + "/" + Weapon.MAX_LEVEL, GREY)));
            });
            inventory.setItem(WEAPON_SLOTS[i], item);
        }
        int armorLevel = gamePlayer.getArmorLevel();
        ItemStack armorIcon = ArmorSet.icon(armorLevel);
        armorIcon.editMeta(meta -> {
            meta.displayName(text(Lang.t(player, "Rüstung", "Armor"), LIGHT));
            meta.lore(List.of(text(ArmorSet.name(player, armorLevel) + " · " + levelLabel + " " + armorLevel + "/" + ArmorSet.MAX_LEVEL, GREY)));
        });
        inventory.setItem(ARMOR_SLOT, armorIcon);
        int crowbarLevel = gamePlayer.getCrowbarLevel();
        String crowbarName = Lang.t(player, "Brechstange", "Crowbar");
        ItemStack crowbarIcon = GameItems.crowbar(player);
        crowbarIcon.editMeta(meta -> {
            meta.displayName(text(crowbarName, LIGHT));
            meta.lore(List.of(text(levelLabel + " " + crowbarLevel + "/" + CrowbarLevel.MAX_LEVEL, GREY)));
        });
        inventory.setItem(CROWBAR_SLOT, crowbarIcon);

        if (armor) {
            boolean max = armorLevel >= ArmorSet.MAX_LEVEL;
            ItemStack big = ArmorSet.icon(max ? armorLevel : armorLevel + 1);
            big.editMeta(meta -> meta.displayName(text(Lang.t(player, "Rüstung: ", "Armor: ") + ArmorSet.name(player, max ? armorLevel : armorLevel + 1), LIGHT)));
            inventory.setItem(BIG_SLOT, big);
            inventory.setItem(LEVEL_SLOT, icon(Material.PAPER, "werkbank_stufe", text(levelLabel, GREY),
                    change(armorLevel + "/" + ArmorSet.MAX_LEVEL, (armorLevel + 1) + "/" + ArmorSet.MAX_LEVEL, max)));
            inventory.setItem(DAMAGE_SLOT, icon(Material.PAPER, "werkbank_schaden", text(Lang.t(player, "Schutz", "Armor"), GREY),
                    change(String.valueOf(ArmorSet.protection(armorLevel)), String.valueOf(ArmorSet.protection(armorLevel + 1)), max)));
            inventory.setItem(MAGAZINE_SLOT, icon(Material.PAPER, "werkbank_magazin", text("Set", GREY),
                    change(ArmorSet.name(player, armorLevel), ArmorSet.name(player, armorLevel + 1), max)));
            ItemStack button = button(gamePlayer, card.level(), card.price(), card.max());
            for (int slot : BUTTON_SLOTS) inventory.setItem(slot, button);
        } else if (crowbar) {
            boolean max = crowbarLevel >= CrowbarLevel.MAX_LEVEL;
            ItemStack big = GameItems.crowbar(player);
            big.editMeta(meta -> meta.displayName(text(crowbarName, LIGHT)));
            inventory.setItem(BIG_SLOT, big);
            inventory.setItem(LEVEL_SLOT, icon(Material.PAPER, "werkbank_stufe", text(levelLabel, GREY),
                    change(crowbarLevel + "/" + CrowbarLevel.MAX_LEVEL, (crowbarLevel + 1) + "/" + CrowbarLevel.MAX_LEVEL, max)));
            inventory.setItem(DAMAGE_SLOT, icon(Material.PAPER, "werkbank_schaden", text(Lang.t(player, "Tempo bei Kisten und Türen", "Speed at crates and doors"), GREY),
                    change(factor(player, CrowbarLevel.speed(crowbarLevel)), factor(player, CrowbarLevel.speed(crowbarLevel + 1)), max)));
            inventory.setItem(MAGAZINE_SLOT, icon(Material.PAPER, "werkbank_magazin", text(Lang.t(player, "Beute-Chance in Kisten (%)", "Loot chance in crates (%)"), GREY),
                    change(loot(crowbarLevel), loot(crowbarLevel + 1), max)));
            ItemStack button = button(gamePlayer, card.level(), card.price(), card.max());
            for (int slot : BUTTON_SLOTS) inventory.setItem(slot, button);
        } else if (weapon != null) {
            boolean max = weapon.getLevel() >= Weapon.MAX_LEVEL;
            inventory.setItem(BIG_SLOT, icon(Material.PAPER, "waffe_" + weapon.getModel(), text(weapon.getName(player).replaceAll("§.", ""), LIGHT)));
            inventory.setItem(LEVEL_SLOT, icon(Material.PAPER, "werkbank_stufe", text(levelLabel, GREY),
                    change(weapon.getLevel() + "/" + Weapon.MAX_LEVEL, (weapon.getLevel() + 1) + "/" + Weapon.MAX_LEVEL, max)));
            inventory.setItem(DAMAGE_SLOT, weapon.upgradesDamage()
                    ? icon(Material.PAPER, "werkbank_schaden", text(Lang.t(player, "Schaden", "Damage"), GREY),
                            change(number(player, weapon.getDamage()), number(player, weapon.nextDamage()), max))
                    : icon(Material.PAPER, "werkbank_schaden", text(Lang.t(player, "Nachladen in Sekunden", "Reload in seconds"), GREY),
                            change(seconds(player, weapon.getFireRate()), seconds(player, weapon.nextFireRate()), max)));
            inventory.setItem(MAGAZINE_SLOT, icon(Material.PAPER, "werkbank_magazin", text(Lang.t(player, "Magazin", "Magazine"), GREY), weapon.upgradesDamage()
                    ? change(String.valueOf(weapon.getMaxMagazine()), String.valueOf(weapon.nextMagazine()), max)
                    : change(String.valueOf(weapon.getMaxMagazine()), "", true)));
            ItemStack button = button(gamePlayer, weapon.getLevel(), upgradePrice(weapon), Weapon.MAX_LEVEL);
            for (int slot : BUTTON_SLOTS) inventory.setItem(slot, button);
        }
        if (card != null) {
            ItemStack scrap = scrapButton(player, weapon);
            for (int slot : SCRAP_SLOTS) inventory.setItem(slot, scrap);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        close.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("ethicbuilds", "gui/icon_zurueck"));
            meta.displayName(text(Lang.t(player, "Schließen", "Close"), LIGHT));
        });
        inventory.setItem(CLOSE_SLOT, close);

        selected.put(player.getUniqueId(), index);
        player.openInventory(inventory);
    }

    private ItemStack button(GamePlayer gamePlayer, int level, int price, int maxLevel) {
        Player player = gamePlayer.getPlayer();
        if (waveManager.getCurrentWave() < UNLOCK_WAVE) {
            return icon(Material.PAPER, "leer", text(Lang.t(player, "Gesperrt", "Locked"), LIGHT),
                    text(Lang.t(player, "Ab Welle ", "From wave ") + UNLOCK_WAVE, RED));
        }
        if (!unlocked) {
            int remaining = UNLOCK_PRICE - unlockPaid;
            return icon(Material.PAPER, "leer", text(Lang.t(player, "Freischalten", "Unlock"), LIGHT), price(player, remaining, gamePlayer.getCoins() >= remaining),
                    text(Lang.t(player, "Zu wenig? Dann legst du alles ein, das Team legt zusammen", "Not enough? You put in all you have, the team chips in"), GREY));
        }
        if (level >= maxLevel) {
            return icon(Material.PAPER, "leer", text(Lang.t(player, "Maximal", "Max"), GOLD));
        }
        boolean affordable = gamePlayer.getCoins() >= price;
        return icon(Material.PAPER, "leer", text(Lang.t(player, "Verbessern", "Upgrade"), GREEN).decoration(TextDecoration.BOLD, true),
                price(player, price, affordable));
    }

    private ItemStack scrapButton(Player player, Weapon weapon) {
        int value = weapon == null ? 0 : scrapValue(weapon);
        if (value <= 0) return icon(Material.PAPER, "leer", text(Lang.t(player, "Nicht möglich", "Not possible"), BUTTON_GREY),
                text(weapon == null ? Lang.t(player, "Nur Waffen lassen sich verschrotten", "Only weapons can be scrapped")
                        : Lang.t(player, "Die Startpistole ohne Upgrades nicht", "Not the start pistol without upgrades"), GREY));
        String label = isScrapAsked(player) ? Lang.t(player, "Sicher?", "Sure?") : Lang.t(player, "Verschrotten", "Scrap");
        return icon(Material.PAPER, "leer", text(label, RED).decoration(TextDecoration.BOLD, true),
                price(player, value, true), text(Lang.t(player, "3/4 von Kaufpreis und Upgrades zurück", "3/4 of price and upgrades back"), GREY));
    }

    private boolean isScrapAsked(Player player) {
        Long asked = scrapAsked.get(player.getUniqueId());
        return asked != null && System.currentTimeMillis() - asked < SCRAP_CONFIRM_MILLIS;
    }

    // Weapon gone, coins back. The start pistol only loses its upgrades
    private void scrap(GamePlayer gamePlayer, Menu menu) {
        Player player = gamePlayer.getPlayer();
        int index = selected.getOrDefault(player.getUniqueId(), 0);
        Weapon weapon = index < menu.weapons.size() ? menu.weapons.get(index) : null;
        int value = weapon == null ? 0 : scrapValue(weapon);
        if (value <= 0) {
            Sounds.deny(player);
            return;
        }
        if (!isScrapAsked(player)) {
            scrapAsked.put(player.getUniqueId(), System.currentTimeMillis());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.8F);
            open(gamePlayer);
            return;
        }
        scrapAsked.remove(player.getUniqueId());

        int slot = -1;
        for (int i : gamePlayer.getWeaponSlots()) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == weapon.getItem().getType()) slot = i;
        }
        gamePlayer.removeWeapon(weapon);
        // Hotbar 1 belongs to the pistol, it comes back there
        boolean pistol = weapon instanceof Pistol || slot == 1 && gamePlayer.getWeapons().values().stream().noneMatch(w -> w instanceof Pistol);
        ItemStack replacement;
        if (pistol) {
            Pistol fresh = new Pistol();
            fresh.localize(player);
            gamePlayer.addWeapon(fresh);
            replacement = fresh.getItem();
        } else {
            replacement = GamePlayer.emptySlot(slot, player);
        }
        if (slot >= 0) player.getInventory().setItem(slot, replacement);
        gamePlayer.setCoins(gamePlayer.getCoins() + value);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0F, 0.9F);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5F, 0.8F);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.2F);
        Lang.note(player, Lang.f(player, "%s§a%s §7verschrottet! §6+%d Coins", "%s§a%s §7scrapped! §6+%d Coins",
                plugin.getMonstersPrefix(), weapon.getName(player), value));
        open(gamePlayer);
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
        if (java.util.Arrays.stream(SCRAP_SLOTS).anyMatch(s -> s == slot)) {
            scrap(gamePlayer, menu);
            return;
        }
        // Another entry: the scrap question starts again
        scrapAsked.remove(player.getUniqueId());

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
        Lang.note(player, Lang.f(player, "%s§a%s §7ist jetzt Stufe §6%d§7! §c-%d Coins", "%s§a%s §7is now level §6%d§7! §c-%d Coins",
                plugin.getMonstersPrefix(), weapon.getName(player), weapon.getLevel(), price));
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
        Lang.note(player, Lang.f(player, "%s§7Deine §aBrechstange §7ist jetzt Stufe §6%d§7! §c-%d Coins",
                "%s§7Your §aCrowbar §7is now level §6%d§7! §c-%d Coins", plugin.getMonstersPrefix(), level + 1, price));
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
        Lang.note(player, Lang.f(player, "%s§7Deine Rüstung ist jetzt §a%s §7(Stufe §6%d§7)! §c-%d Coins",
                "%s§7Your armor is now §a%s §7(level §6%d§7)! §c-%d Coins", plugin.getMonstersPrefix(),
                ArmorSet.name(player, level + 1), level + 1, price));
    }

    // Whoever has the rest unlocks it for everyone, otherwise all own coins go in
    private void unlock(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        int remaining = UNLOCK_PRICE - unlockPaid;
        int coins = gamePlayer.getCoins();
        if (coins <= 0) {
            Lang.note(player, Lang.f(player, "§cNoch §6%d Coins §cfür die Werkbank, legt zusammen!",
                    "§cStill §6%d Coins §cneeded for the Workbench, chip in!", remaining));
            Sounds.deny(player);
            return;
        }
        int paid = Math.min(coins, remaining);
        gamePlayer.setCoins(coins - paid);
        unlockPaid += paid;
        if (unlockPaid < UNLOCK_PRICE) {
            decorationManager.setWorkbenchPrice(UNLOCK_PRICE - unlockPaid);
            Lang.noteAll("%s§a%s §7legt §6%d Coins §7für die §aWerkbank §7ein (§6%d§7/§6%d§7). Noch §6%d Coins§7, legt zusammen!",
                    "%s§a%s §7puts §6%d Coins §7into the §aWorkbench §7(§6%d§7/§6%d§7). Still §6%d Coins§7 needed, chip in!",
                    plugin.getMonstersPrefix(), player.getName(), paid, unlockPaid, UNLOCK_PRICE, UNLOCK_PRICE - unlockPaid);
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 1.2F);
            return;
        }
        unlocked = true;
        Bukkit.getOnlinePlayers().forEach(decorationManager::showWorkbenchOpen);
        Lang.broadcast("%s§a%s §7hat die §aWerkbank §7für alle freigeschaltet!", "%s§a%s §7unlocked the §aWorkbench §7for everyone!",
                plugin.getMonstersPrefix(), player.getName());
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0F, 1.0F));
    }

    private boolean pay(GamePlayer gamePlayer, int price) {
        Player player = gamePlayer.getPlayer();
        if (gamePlayer.getCoins() < price) {
            Lang.note(player, Lang.f(player, "§cDu brauchst noch §6%d Coins", "§cNot enough coins, §6%d Coins §cmissing", price - gamePlayer.getCoins()));
            Sounds.deny(player);
            return false;
        }
        gamePlayer.setCoins(gamePlayer.getCoins() - price);
        return true;
    }

    // Everything that changes is drawn into the title: tab, stars, button, card and balance
    // What one upgrade gives, the same card for weapons and armor
    private record Card(int level, String name1, String now1, String next1, boolean arrow1,
                        String name2, String now2, String next2, boolean arrow2, int price, int max) {}

    private static Card card(Player player, Weapon weapon) {
        String magazine = Lang.t(player, "Magazin", "Magazine");
        if (weapon.upgradesDamage()) {
            return new Card(weapon.getLevel(), Lang.t(player, "Schaden", "Damage"), number(player, weapon.getDamage()), number(player, weapon.nextDamage()), true,
                    magazine, String.valueOf(weapon.getMaxMagazine()), String.valueOf(weapon.nextMagazine()), true, upgradePrice(weapon), Weapon.MAX_LEVEL);
        }
        // Sniper: only reloads faster, damage and magazine stay
        return new Card(weapon.getLevel(), Lang.t(player, "Nachladen", "Reload"), seconds(player, weapon.getFireRate()), seconds(player, weapon.nextFireRate()), true,
                magazine, String.valueOf(weapon.getMaxMagazine()), "", false, upgradePrice(weapon), Weapon.MAX_LEVEL);
    }

    private static Card armorCard(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        int level = gamePlayer.getArmorLevel();
        return new Card(level, Lang.t(player, "Schutz", "Armor"), String.valueOf(ArmorSet.protection(level)), String.valueOf(ArmorSet.protection(level + 1)), true,
                "Set", "", ArmorSet.name(player, Math.min(level + 1, ArmorSet.MAX_LEVEL)), level < ArmorSet.MAX_LEVEL, ArmorSet.price(level), ArmorSet.MAX_LEVEL);
    }

    private static Card crowbarCard(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        int level = gamePlayer.getCrowbarLevel();
        return new Card(level, Lang.t(player, "Tempo", "Speed"), factor(player, CrowbarLevel.speed(level)), factor(player, CrowbarLevel.speed(level + 1)), true,
                Lang.t(player, "Beute", "Loot"), loot(level), loot(level + 1), true, CrowbarLevel.price(level), CrowbarLevel.MAX_LEVEL);
    }

    private static String factor(Player player, double value) {
        return Lang.f(player, "%.1f", "%.1f", value);
    }

    // Chance in percent that a crate gives a grenade or coins
    private static String loot(int level) {
        return String.valueOf(de.ethicbuilds.monsters.map.decoration.CrateManager.lootPercent(Math.min(level, CrowbarLevel.MAX_LEVEL)));
    }

    private Component title(GamePlayer gamePlayer, Card card, int row, Weapon weapon) {
        Player player = gamePlayer.getPlayer();
        MenuTitle title = MenuTitle.workbench().name(Lang.t(player, "Werkbank", "Workbench"));
        int coins = gamePlayer.getCoins();
        title.centered("guthaben_name", 52, new MenuTitle.Part(Lang.t(player, "Guthaben", "Balance"), GREY))
                .centered("guthaben", 52, new MenuTitle.Part(COIN + " " + shortCoins(player, coins, 1_000_000), ORANGE));
        if (card == null) return title.build();

        int level = card.level();
        boolean max = level >= card.max();
        boolean locked = waveManager.getCurrentWave() < UNLOCK_WAVE;
        boolean open = unlocked;
        boolean values = open && !max || locked;

        // Tab of the selected entry: weapons in rows 0-2, armor in row 3, crowbar in row 4 (both closed on the right)
        if (row <= CROWBAR_INDEX) title.glyph((char) (0xE010 + row), 6, row >= ARMOR_INDEX ? 20 : 21);
        title.glyph((char) (0xE030 + level), level == 0 ? 30 : 27, STAR_WIDTHS[level]);

        // Card: what you get
        title.left("name_0", 117, new MenuTitle.Part(Lang.t(player, "Stufe", "Level"), GREY));
        if (max) title.left("wert_0", 117, new MenuTitle.Part(level + "/" + card.max(), LIGHT));
        else if (values) title.left("wert_0", 117, new MenuTitle.Part(String.valueOf(level), LIGHT),
                new MenuTitle.Part(ARROW, ORANGE), new MenuTitle.Part(String.valueOf(level + 1), TextColor.color(LEVEL_COLORS[level])));
        else title.left("wert_0", 117, new MenuTitle.Part("-", LIGHT));
        title.left("name_1", 117, new MenuTitle.Part(card.name1(), GREY));
        cardValue(title, "wert_1", card.now1(), card.next1(), values && card.arrow1());
        title.left("name_2", 117, new MenuTitle.Part(card.name2(), GREY));
        cardValue(title, "wert_2", card.now2().isEmpty() && !(values && card.arrow2()) ? card.next2() : card.now2(), card.next2(), values && card.arrow2());

        // Button with price
        if (locked) {
            title.glyph('\uE024', 95, 74)
                    .centered("knopf", 132, new MenuTitle.Part(Lang.t(player, "Gesperrt", "Locked"), BUTTON_GREY))
                    .centered("knopf_2", 132, new MenuTitle.Part(Lang.t(player, "Ab Welle ", "From wave ") + UNLOCK_WAVE, ORANGE));
        } else if (!open) {
            title.glyph('\uE022', 93, 78)
                    .centered("knopf", 132, new MenuTitle.Part(Lang.t(player, "Freischalten", "Unlock"), WHITE))
                    .centered("preis", 132, new MenuTitle.Part(COIN + " " + coins(player, UNLOCK_PRICE - unlockPaid), WHITE));
        } else if (max) {
            title.glyph('\uE023', 93, 78)
                    .centered("knopf", 132, new MenuTitle.Part(Lang.t(player, "Maximal", "Max"), TextColor.color(0x3B2A00)))
                    .centered("knopf_2", 132, new MenuTitle.Part(Lang.t(player, "Stufe ", "Level ") + level + "/" + card.max(), TextColor.color(0x5A4200)));
        } else {
            int price = card.price();
            boolean affordable = coins >= price;
            title.glyph(affordable ? '\uE020' : '\uE021', affordable ? 93 : 95, affordable ? 78 : 74)
                    .centered("knopf", 132, new MenuTitle.Part(Lang.t(player, "Verbessern", "Upgrade"), affordable ? WHITE : BUTTON_GREY))
                    .centered("preis", 132, new MenuTitle.Part(COIN, affordable ? TextColor.color(0xFFD36B) : TOO_EXPENSIVE),
                            new MenuTitle.Part(" " + shortCoins(player, price, 100_000), affordable ? WHITE : TOO_EXPENSIVE));
        }

        // Scrap button over the whole bottom row, one line of text
        int scrap = weapon == null ? 0 : scrapValue(weapon);
        if (scrap <= 0) {
            title.glyph('', 26, 143).centered("schrott", 97, new MenuTitle.Part(Lang.t(player, "Nicht möglich", "Not possible"), SCRAP_GREY));
        } else {
            boolean asked = isScrapAsked(player);
            title.glyph(asked ? '' : '', asked ? 24 : 26, asked ? 147 : 143)
                    .centered("schrott", 97, new MenuTitle.Part(asked ? Lang.t(player, "Sicher? ", "Sure? ") : Lang.t(player, "Verschrotten ", "Scrap "), WHITE),
                            new MenuTitle.Part(COIN + " " + coins(player, scrap), SCRAP_GOLD));
        }
        return title.build();
    }

    // A card line has 48 px (design session): when "old -> new" is longer, only the new value is shown
    private static final int CARD_WIDTH = 48;

    private static void cardValue(MenuTitle title, String line, String now, String next, boolean arrow) {
        if (arrow && title.width(line, now + ARROW + next) > CARD_WIDTH) {
            title.left(line, 117, new MenuTitle.Part(next, NEW_VALUE));
        } else if (arrow) {
            title.left(line, 117, new MenuTitle.Part(now, LIGHT), new MenuTitle.Part(ARROW, ORANGE), new MenuTitle.Part(next, NEW_VALUE));
        } else {
            title.left(line, 117, new MenuTitle.Part(now, LIGHT));
        }
    }

    // Seconds between shots, short enough for the card
    private static String seconds(Player player, int ticks) {
        return Lang.f(player, "%.1f", "%.1f", ticks / 20.0);
    }

    private static String coins(Player player, int coins) {
        return Lang.f(player, "%,d", "%,d", coins);
    }

    // Short form from the limit on, so it fits the field: 152k, 1,2M
    private static String shortCoins(Player player, int coins, int from) {
        if (coins < from) return coins(player, coins);
        if (coins >= 1_000_000) return Lang.f(player, "%.1fM", "%.1fM", coins / 100_000 / 10.0);
        return coins / 1000 + "k";
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

    private static Component price(Player player, int coins, boolean affordable) {
        return text(coins(player, coins), affordable ? ORANGE : RED)
                .append(text(" "))
                .append(Component.text("", NamedTextColor.WHITE).font(HUD_FONT).decoration(TextDecoration.ITALIC, false))
                .append(text(" Coins", GREY));
    }

    private static String number(Player player, double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : Lang.f(player, "%.2f", "%.2f", value).replaceAll("0$", "");
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
