package de.ethicbuilds.monsters.monster;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/***
 * Every five waves a new armor tier (bronze from wave 5 up to netherite from 25): armored monsters have more health and
 * give more coins. Within a stage more and more of them wear the new tier, the others the one before.
 */
public final class EliteTier {
    private static final String[] NAMES = {"", "Bronze", "Silber", "Gold", "Diamant", "Netherit"};
    private static final String[] COLORS = {"", "§x§C§D§7§F§3§2", "§x§C§8§C§E§D§8", "§x§F§F§C§D§2§8", "§x§4§F§D§3§E§6", "§x§6§B§5§A§6§E"};
    public static final int MAX = 5;
    private static final double HEALTH_PER_TIER = 0.3;
    private static final int COINS_PER_TIER = 5;
    private static final int STAGE = 5;
    private static final double FIRST_SHARE = 0.25, SHARE_PER_WAVE = 0.15, MAX_SHARE = 1.0;

    private EliteTier() {}

    public static int of(int wave) {
        return Math.min(wave / STAGE, NAMES.length - 1);
    }

    /** First zombie wave of a stage (the multiples of five are spider waves), for the announcement */
    public static boolean starts(int wave) {
        return wave > STAGE && wave % STAGE == 1 && wave / STAGE < NAMES.length;
    }

    /** Tier for one monster: the new one with a share rising over the stage, otherwise the one before */
    public static int roll(int wave) {
        int tier = of(wave);
        if (tier == 0) return 0;
        double share = Math.min(MAX_SHARE, FIRST_SHARE + SHARE_PER_WAVE * Math.max(0, wave - tier * STAGE - 1));
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() < share ? tier : tier - 1;
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
