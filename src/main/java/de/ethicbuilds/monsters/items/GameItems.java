package de.ethicbuilds.monsters.items;

import de.ethicbuilds.monsters.lang.Lang;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/***
 * Items with models from the lobby Nexo pack (nexo:<id>)
 */
public final class GameItems {
    public static final int CROWBAR_SLOT = 4;
    public static final int GRENADE_SLOT = 6;
    public static final Material KNIFE = Material.WOODEN_SWORD;

    private GameItems() {}

    public static ItemStack knife(Player player) {
        ItemStack item = create(KNIFE, Lang.t(player, "§aMesser", "§aKnife"), "messer_v2");
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        // Same hit as the old wooden axe: 7 damage, 0.8 attacks per second
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(new NamespacedKey("monsters", "knife_damage"),
                6, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(new NamespacedKey("monsters", "knife_speed"),
                -3.2, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack crowbar(Player player) {
        return create(Material.BLAZE_ROD, Lang.t(player, "§aBrechstange", "§aCrowbar"), "brechstange");
    }

    public static ItemStack grenade(Player player, int amount) {
        ItemStack item = create(Material.FIREWORK_STAR, Lang.t(player, "§aGranate", "§aGrenade"), "granate");
        item.setAmount(amount);
        return item;
    }

    public static ItemStack medkit(Player player) {
        return create(Material.PAPER, Lang.t(player, "§aErste Hilfe", "§aMedkit"), "erstehilfe_1");
    }

    public static boolean isCrowbar(ItemStack item) {
        return item != null && item.getType() == Material.BLAZE_ROD;
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
