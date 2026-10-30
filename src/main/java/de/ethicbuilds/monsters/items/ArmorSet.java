package de.ethicbuilds.monsters.items;

import de.ethicbuilds.monsters.lang.Lang;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/***
 * Armor from the workbench, always a whole set: bronze, silver, gold, diamond, netherite. The same armor the elite
 * zombies wear (design session): own textures over the vanilla pieces, whose protection stays.
 * 2000 coins for the first, then double each level.
 */
public final class ArmorSet {
    public static final int MAX_LEVEL = 5;
    private static final int FIRST_PRICE = 2000;
    private static final String[] NAMES = {"Keine", "Bronze", "Silber", "Gold", "Diamant", "Netherit"};
    private static final String[] NAMES_EN = {"None", "Bronze", "Silver", "Gold", "Diamond", "Netherite"};
    // Asset of the design session per level, in monsters:equipment and as inventory icons monsters:ruestung/<set>_<piece>
    private static final String[] SETS = {null, "bronze", "silber", "gold", "diamant", "netherit"};
    private static final String[] PIECES = {"helm", "brust", "hose", "stiefel"};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    // Netherite: same armor points as diamond, more toughness
    private static final int[] PROTECTION = {0, 7, 12, 15, 20, 20};
    private static final Material[][] PARTS = {
            {},
            {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
            {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
            {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
            {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS},
            {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS}};

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

    /** Chestplate as icon, the first set for "no armor yet" */
    public static ItemStack icon(int level) {
        int set = Math.max(1, Math.min(level, MAX_LEVEL));
        return part(set, 1);
    }

    public static void equip(Player player, int level) {
        PlayerInventory inventory = player.getInventory();
        if (level <= 0) {
            inventory.setArmorContents(new ItemStack[4]);
            return;
        }
        int set = Math.min(level, MAX_LEVEL);
        inventory.setHelmet(part(set, 0));
        inventory.setChestplate(part(set, 1));
        inventory.setLeggings(part(set, 2));
        inventory.setBoots(part(set, 3));
    }

    // Vanilla piece for the protection, look and icon from the Monsters pack (once it has them, config custom-armor)
    private static ItemStack part(int set, int piece) {
        ItemStack item = new ItemStack(PARTS[set][piece]);
        if (!de.ethicbuilds.monsters.Main.getINSTANCE().getConfig().getBoolean("custom-armor", false)) {
            item.editMeta(meta -> meta.setUnbreakable(true));
            return item;
        }
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(SLOTS[piece])
                .assetId(Key.key("monsters", SETS[set]))
                .equipSound(Key.key("minecraft", "item.armor.equip_generic"))
                .damageOnHurt(false)
                .build());
        item.editMeta(meta -> {
            meta.setUnbreakable(true);
            meta.setItemModel(new NamespacedKey("monsters", "ruestung/" + SETS[set] + "_" + PIECES[piece]));
        });
        return item;
    }
}
