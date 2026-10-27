package de.ethicbuilds.monsters.items;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/***
 * Items with models from the lobby Nexo pack (nexo:<id>)
 */
public final class GameItems {
    public static final int CROWBAR_SLOT = 4;
    public static final int GRENADE_SLOT = 6;

    private GameItems() {}

    public static ItemStack knife() {
        ItemStack item = create(Material.WOODEN_AXE, "§aMesser", "messer_v2");
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack crowbar() {
        return create(Material.STICK, "§aBrechstange", "brechstange");
    }

    public static ItemStack grenade(int amount) {
        ItemStack item = create(Material.FIREWORK_STAR, "§aGranate", "granate");
        item.setAmount(amount);
        return item;
    }

    public static ItemStack medkit() {
        return create(Material.PAPER, "§aErste Hilfe", "erstehilfe_1");
    }

    public static boolean isCrowbar(ItemStack item) {
        return item != null && item.getType() == Material.STICK;
    }

    public static boolean isGrenade(ItemStack item) {
        return item != null && item.getType() == Material.FIREWORK_STAR;
    }

    public static void setModel(ItemStack item, String nexoId) {
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("nexo", nexoId));
        item.setItemMeta(meta);
    }

    private static ItemStack create(Material material, String name, String nexoId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        setModel(item, nexoId);
        return item;
    }
}
