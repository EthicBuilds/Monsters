package de.ethicbuilds.monsters.player.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.CrowbarLevel;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.PlayerClass;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Classes: chosen in the waiting time with the item in hotbar slot 8, they can be changed until the start.
 * At the start the item goes and the class takes effect.
 */
public class ClassManager implements Listener {
    public static final int SLOT = 7;
    private static final int[] MENU_SLOTS = {10, 12, 14, 16};
    private static final double REVIVE_FACTOR = 1.4, CROWBAR_FACTOR = 1.3, HEADSHOT = 1.3, NORMAL_HEADSHOT = 1.2;
    private static final double RUNNER_SPEED = 0.08, RUNNER_HEALTH = 4, RUNNER_KNIFE = 13;
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("monsters", "klasse_tempo"),
            HEALTH_KEY = new NamespacedKey("monsters", "klasse_leben"), ITEM_KEY = new NamespacedKey("monsters", "klasse");
    private static final Map<UUID, PlayerClass> chosen = new ConcurrentHashMap<>();

    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;

    public static PlayerClass of(UUID player) {
        return chosen.get(player);
    }

    public static double reviveFactor(Player helper) {
        return of(helper.getUniqueId()) == PlayerClass.SANITAETER ? REVIVE_FACTOR : 1;
    }

    public static double crowbarSpeed(GamePlayer gamePlayer) {
        double speed = CrowbarLevel.speed(gamePlayer.getCrowbarLevel());
        return of(gamePlayer.getPlayer().getUniqueId()) == PlayerClass.TECHNIKER ? speed * CROWBAR_FACTOR : speed;
    }

    public static double headshotMultiplier(Player shooter) {
        return shooter != null && of(shooter.getUniqueId()) == PlayerClass.SCHUETZE ? HEADSHOT : NORMAL_HEADSHOT;
    }

    /** Item to choose, in the waiting time */
    public void give(Player player) {
        player.getInventory().setItem(SLOT, selector(player));
    }

    private ItemStack selector(Player player) {
        PlayerClass current = of(player.getUniqueId());
        ItemStack item = new ItemStack(Material.PAPER);
        if (current != null) GameItems.setModel(item, current.getModel());
        else item = new ItemStack(Material.NETHER_STAR);
        item.editMeta(meta -> {
            meta.displayName(text(Lang.t(player, "Klasse wählen", "Choose class"), "#F2F5F8").decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(text(current == null ? Lang.t(player, "Noch keine gewählt", "None chosen yet")
                    : Lang.t(player, "Gewählt: ", "Chosen: ") + name(player, current), "#8A93A3")));
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    // Runs after GameListener cancels every interaction
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(ITEM_KEY)) return;
        if (gameManager.getCurrentPhase() != GamePhase.PRE_GAME) return;
        open(event.getPlayer());
    }

    private void open(Player player) {
        Menu menu = new Menu();
        Inventory inventory = Bukkit.createInventory(menu, 27, Component.text(Lang.t(player, "Klasse wählen", "Choose class")));
        menu.inventory = inventory;
        PlayerClass current = of(player.getUniqueId());
        PlayerClass[] classes = PlayerClass.values();
        for (int i = 0; i < classes.length; i++) inventory.setItem(MENU_SLOTS[i], icon(player, classes[i], classes[i] == current));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
    }

    private static ItemStack icon(Player player, PlayerClass type, boolean active) {
        ItemStack item = new ItemStack(Material.PAPER);
        GameItems.setModel(item, type.getModel());
        if (active) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name(player, type), type.getColor()).decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(text(Lang.t(player, type.getMainDe(), type.getMainEn()), "#F2F5F8"),
                text(Lang.t(player, type.getExtraDe(), type.getExtraEn()), "#8A93A3"),
                text(active ? Lang.t(player, "Gewählt", "Chosen") : Lang.t(player, "Klick zum Wählen", "Click to choose"), active ? "#3CDC6E" : "#8A93A3")));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        for (int i = 0; i < MENU_SLOTS.length; i++) {
            if (MENU_SLOTS[i] != event.getSlot()) continue;
            PlayerClass type = PlayerClass.values()[i];
            chosen.put(player.getUniqueId(), type);
            player.closeInventory();
            if (gameManager.getCurrentPhase() == GamePhase.PRE_GAME) give(player);
            player.sendMessage(Main.getINSTANCE().getMonstersPrefix() + Lang.f(player, "§7Deine Klasse: §f%s", "§7Your class: §f%s", name(player, type)));
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8F, 1.2F);
        }
    }

    /** At the start: the item goes, the class takes effect */
    public void apply(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        player.getInventory().setItem(SLOT, null);
        PlayerClass type = of(player.getUniqueId());
        if (type == null) return;
        switch (type) {
            case SANITAETER -> player.getInventory().setItem(ReviveManager.MEDKIT_SLOT, GameItems.medkit(player));
            case STURMLAEUFER -> {
                modifier(player, Attribute.MOVEMENT_SPEED, SPEED_KEY, RUNNER_SPEED, AttributeModifier.Operation.ADD_SCALAR);
                modifier(player, Attribute.MAX_HEALTH, HEALTH_KEY, RUNNER_HEALTH, AttributeModifier.Operation.ADD_NUMBER);
                player.setHealth(Math.min(player.getHealth() + RUNNER_HEALTH, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
                player.getInventory().setItem(0, GameItems.knife(player, RUNNER_KNIFE));
            }
            default -> {}
        }
    }

    private static void modifier(Player player, Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(key);
        instance.addModifier(new AttributeModifier(key, amount, operation));
    }

    private static String name(Player player, PlayerClass type) {
        return Lang.t(player, type.getNameDe(), type.getNameEn());
    }

    private static Component text(String content, String color) {
        return Component.text(content, TextColor.fromHexString(color)).decoration(TextDecoration.ITALIC, false);
    }

    private static class Menu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
