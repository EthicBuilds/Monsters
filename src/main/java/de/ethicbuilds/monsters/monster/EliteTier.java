package de.ethicbuilds.monsters.monster;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/***
 * Every ten waves the monsters reach a new elite tier: a helmet shows it, they have more health and give more coins
 */
public final class EliteTier {
    private static final String[] NAMES = {"", "Bronze", "Silber", "Gold", "Diamant", "Netherit"};
    private static final String[] COLORS = {"", "§x§C§D§7§F§3§2", "§x§C§8§C§E§D§8", "§x§F§F§C§D§2§8", "§x§4§F§D§3§E§6", "§x§6§B§5§A§6§E"};
    private static final double HEALTH_PER_TIER = 0.3;
    private static final int COINS_PER_TIER = 5;

    private EliteTier() {}

    public static int of(int wave) {
        return Math.min((wave - 1) / 10, NAMES.length - 1);
    }

    public static String name(int tier) {
        return COLORS[tier] + NAMES[tier];
    }

    public static double healthFactor(int tier) {
        return 1 + HEALTH_PER_TIER * tier;
    }

    public static int extraCoins(int tier) {
        return COINS_PER_TIER * tier;
    }

    public static void dress(LivingEntity monster, int tier) {
        EntityEquipment equipment = monster.getEquipment();
        if (tier <= 0 || equipment == null) return;
        ItemStack helmet = switch (tier) {
            case 1 -> bronze();
            case 2 -> new ItemStack(Material.IRON_HELMET);
            case 3 -> new ItemStack(Material.GOLDEN_HELMET);
            case 4 -> new ItemStack(Material.DIAMOND_HELMET);
            default -> new ItemStack(Material.NETHERITE_HELMET);
        };
        equipment.setHelmet(helmet);
        equipment.setHelmetDropChance(0);
    }

    private static ItemStack bronze() {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        helmet.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0xCD7F32)));
        return helmet;
    }
}
