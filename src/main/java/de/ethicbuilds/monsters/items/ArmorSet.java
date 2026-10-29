package de.ethicbuilds.monsters.items;

import de.ethicbuilds.monsters.lang.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/***
 * Armor from the workbench, always a whole set: leather, chain, iron, diamond.
 * 2000 coins for the first, then double each level.
 */
public final class ArmorSet {
    public static final int MAX_LEVEL = 4;
    private static final int FIRST_PRICE = 2000;
    private static final String[] NAMES = {"Keine", "Leder", "Kette", "Eisen", "Diamant"};
    private static final String[] NAMES_EN = {"None", "Leather", "Chain", "Iron", "Diamond"};
    private static final int[] PROTECTION = {0, 7, 12, 15, 20};
    private static final Material[][] PARTS = {
            {},
            {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
            {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
            {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
            {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}};

    private ArmorSet() {}

    public static int price(int level) {
        return FIRST_PRICE * (1 << level);
    }

    public static String name(Player player, int level) {
        return (Lang.english(player) ? NAMES_EN : NAMES)[Math.max(0, Math.min(level, MAX_LEVEL))];
    }

    public static int protection(int level) {
        return PROTECTION[Math.max(0, Math.min(level, MAX_LEVEL))];
    }

    /** Chestplate as icon, leather for "no armor yet" */
    public static ItemStack icon(int level) {
        return new ItemStack(PARTS[Math.max(1, Math.min(level, MAX_LEVEL))][1]);
    }

    public static void equip(Player player, int level) {
        PlayerInventory inventory = player.getInventory();
        if (level <= 0) {
            inventory.setArmorContents(new ItemStack[4]);
            return;
        }
        Material[] parts = PARTS[Math.min(level, MAX_LEVEL)];
        inventory.setHelmet(part(parts[0]));
        inventory.setChestplate(part(parts[1]));
        inventory.setLeggings(part(parts[2]));
        inventory.setBoots(part(parts[3]));
    }

    private static ItemStack part(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setUnbreakable(true));
        return item;
    }
}
