package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.MenuTitle;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import net.kyori.adventure.text.Component;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/***
 * Upgrade menu of a build part (user 31.10., layout by DESIGN): the part big on the left, levels as dots below,
 * level, name and material on a card and one button to upgrade, repair or rebuild. Everything but the object and the
 * button area is drawn into the title like the workbench (MenuTitle).
 */
public class UpgradeMenu implements Listener {
    public enum Kind { UPGRADE, REPAIR, REBUILD, MAX }

    /**
     * What the menu shows. Names come per level (index 1..max), health in percent of the current level.
     */
    public record View(String objectDe, String objectEn, String model, int max, int level, Kind kind, int health, int cost,
                       String[] namesDe, String[] namesEn) {}

    private static final int OBJECT_SLOT = 10;
    // Level dots per number of levels (index = max), DESIGN 01.11.
    private static final char[] DOT_START = {0, 0, '', '', '', ''};
    private static final int[] DOT_X = {0, 0, 21, 15, 9, 3};
    private static final int[][] DOT_WIDTHS = {{}, {}, {24, 27, 27}, {36, 36, 39, 39}, {48, 48, 48, 51, 51}, {60, 60, 60, 60, 63, 63}};
    private static final int[] BUTTON_SLOTS = {21, 22, 23, 24, 25, 26, 30, 31, 32, 33, 34, 35};
    private static final char PART = '';
    private static final String ARROW = " → ";
    private static final int TEXT_LEFT = 69, TEXT_RIGHT = 165, TEXT_WIDTH = 96, BUTTON_MIDDLE = 116;
    private static final TextColor GREY = TextColor.color(0x9AA6B8), LIGHT = TextColor.color(0xF2F5F8), ORANGE = TextColor.color(0xF9A11E),
            RED = TextColor.color(0xFF6B5E), ENOUGH = TextColor.color(0x5BE39A), WHITE = TextColor.color(0xFFFFFF),
            LOCKED = TextColor.color(0x8A94A4), NEXT = TextColor.color(0xD8F5E4), FULL = TextColor.color(0xFFE7C2),
            MAX_TEXT = TextColor.color(0x3A2A00), MAX_DETAIL = TextColor.color(0x5A4300);

    @Inject private UserManager userManager;

    /** Opens the menu; a click on the button runs the action, then the menu shows the new state */
    public void open(GamePlayer gamePlayer, Supplier<View> view, Consumer<GamePlayer> action) {
        Player player = gamePlayer.getPlayer();
        View now = view.get();
        Menu menu = new Menu(view, action);
        Inventory inventory = Bukkit.createInventory(menu, 36, title(gamePlayer, now));
        menu.inventory = inventory;
        inventory.setItem(OBJECT_SLOT, icon(now.model(), text(Lang.t(player, now.objectDe(), now.objectEn()), LIGHT)));
        ItemStack button = button(gamePlayer, now);
        for (int slot : BUTTON_SLOTS) inventory.setItem(slot, button);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getSlot();
        boolean button = false;
        for (int s : BUTTON_SLOTS) button |= s == slot;
        if (!button) return;
        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null || menu.view.get().kind() == Kind.MAX) return;
        menu.action.accept(gamePlayer);
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8F, 1.2F);
        open(gamePlayer, menu.view, menu.action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private Component title(GamePlayer gamePlayer, View view) {
        Player player = gamePlayer.getPlayer();
        boolean english = Lang.english(player);
        int have = gamePlayer.getParts();
        boolean enough = have >= view.cost();
        String object = english ? view.objectEn() : view.objectDe();
        String[] names = english ? view.namesEn() : view.namesDe();
        MenuTitle title = MenuTitle.upgrade().name(switch (view.kind()) {
            case REPAIR -> english ? "Repair " + object : object + " reparieren";
            case REBUILD -> english ? "Rebuild " + object : object + " aufbauen";
            default -> english ? "Upgrade " + object : object + " ausbauen";
        });

        // Level dots under the object, from DESIGN's script: first glyph, x and the width of each state
        int dots = view.kind() == Kind.MAX ? view.max() : view.level();
        int[] widths = DOT_WIDTHS.length > view.max() ? DOT_WIDTHS[view.max()] : null;
        if (widths != null && dots < widths.length) title.glyph((char) (DOT_START[view.max()] + dots), DOT_X[view.max()], widths[dots]);

        String level = english ? "Level " : "Stufe ";
        switch (view.kind()) {
            case UPGRADE -> {
                title.left("stufe", TEXT_LEFT, part(level, GREY), part(String.valueOf(view.level()), LIGHT), part(ARROW, GREY),
                        part(String.valueOf(view.level() + 1), ORANGE));
                String now = names[view.level()], next = names[view.level() + 1];
                if (title.width("name", now + ARROW + next) <= TEXT_WIDTH) {
                    title.left("name", TEXT_LEFT, part(now, LIGHT), part(ARROW, GREY), part(next, ORANGE));
                } else {
                    title.left("name", TEXT_LEFT, part(english ? "New: " : "Neu: ", GREY), part(next, ORANGE));
                }
            }
            case REPAIR, REBUILD -> {
                title.left("stufe", TEXT_LEFT, part(english ? "Condition " : "Zustand ", GREY), part(view.health() + " %", RED));
                String state = view.kind() == Kind.REPAIR ? (english ? "damaged" : "beschädigt") : (english ? "destroyed" : "zerstört");
                title.left("name", TEXT_LEFT, part(level + view.level() + " ", LIGHT), part(state, RED));
            }
            case MAX -> {
                title.left("stufe", TEXT_LEFT, part(level, GREY), part(String.valueOf(view.max()), ORANGE));
                title.left("name", TEXT_LEFT, part(names[view.max()], LIGHT), part(english ? " · max. level" : " · max. Stufe", GREY));
            }
        }

        if (view.kind() == Kind.MAX) {
            title.left("material", TEXT_LEFT, part(english ? "Fully upgraded" : "Voll ausgebaut", GREY));
        } else {
            title.left("material", TEXT_LEFT, part(PART + (english ? " Parts" : " Bauteile"), GREY))
                    .right("material", TEXT_RIGHT, part(have + "/" + view.cost(), enough ? ENOUGH : RED));
            int step = view.cost() <= 0 ? 24 : Math.min(24, (int) Math.round(24.0 * have / view.cost()));
            if (step > 0) title.glyph((char) ('' + step - 1), TEXT_LEFT, 4 * step);
        }

        int missing = view.cost() - have;
        String missingText = english ? "Missing " + missing + (missing == 1 ? " part" : " parts")
                : (missing == 1 ? "Fehlt 1 Bauteil" : "Fehlen " + missing + " Bauteile");
        switch (view.kind()) {
            case UPGRADE -> button(title, enough, '')
                    .centered("knopf", BUTTON_MIDDLE, part(english ? "Upgrade" : "Ausbauen", enough ? WHITE : LOCKED))
                    .centered("knopf_2", BUTTON_MIDDLE, enough ? part((english ? "to level " : "auf Stufe ") + (view.level() + 1), NEXT) : part(missingText, RED));
            case REPAIR -> button(title, enough, '')
                    .centered("knopf", BUTTON_MIDDLE, part(english ? "Repair" : "Reparieren", enough ? WHITE : LOCKED))
                    .centered("knopf_2", BUTTON_MIDDLE, enough ? part("100 %", FULL) : part(missingText, RED));
            case REBUILD -> button(title, enough, '')
                    .centered("knopf", BUTTON_MIDDLE, part(english ? "Rebuild" : "Wieder aufbauen", enough ? WHITE : LOCKED))
                    .centered("knopf_2", BUTTON_MIDDLE, enough ? part(level + view.level(), FULL) : part(missingText, RED));
            case MAX -> button(title, true, '')
                    .centered("knopf", BUTTON_MIDDLE, part("max.", MAX_TEXT))
                    .centered("knopf_2", BUTTON_MIDDLE, part(level + view.max() + (english ? " of " : " von ") + view.max(), MAX_DETAIL));
        }
        return title.build();
    }

    // The lit buttons glow 2 px further left than the locked one
    private static MenuTitle button(MenuTitle title, boolean enough, char lit) {
        return enough ? title.glyph(lit, 62, 109) : title.glyph('', 64, 105);
    }

    // Name and costs again as tooltip of the button area
    private static ItemStack button(GamePlayer gamePlayer, View view) {
        Player player = gamePlayer.getPlayer();
        if (view.kind() == Kind.MAX) return icon("leer", text(Lang.t(player, "Voll ausgebaut", "Fully upgraded"), GREY));
        String name = switch (view.kind()) {
            case REPAIR -> Lang.t(player, "Reparieren", "Repair");
            case REBUILD -> Lang.t(player, "Wieder aufbauen", "Rebuild");
            default -> Lang.t(player, "Ausbauen", "Upgrade");
        };
        boolean enough = gamePlayer.getParts() >= view.cost();
        return icon("leer", text(name, enough ? WHITE : LOCKED),
                text(Lang.t(player, "Kosten: ", "Cost: "), GREY).append(text(view.cost() + " " + Lang.t(player, "Bauteile", "parts"), enough ? ENOUGH : RED)));
    }

    private static ItemStack icon(String model, Component name, Component... lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("monsters", "gui/" + model));
            meta.displayName(name);
            if (lore.length > 0) meta.lore(List.of(lore));
        });
        return item;
    }

    private static MenuTitle.Part part(String text, TextColor color) {
        return new MenuTitle.Part(text, color);
    }

    private static Component text(String content, TextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }

    private static class Menu implements InventoryHolder {
        private Inventory inventory;
        private final Supplier<View> view;
        private final Consumer<GamePlayer> action;

        private Menu(Supplier<View> view, Consumer<GamePlayer> action) {
            this.view = view;
            this.action = action;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
