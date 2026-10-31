package de.ethicbuilds.monsters.items;

import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Gas mask (user 31.10.): now and then loot of a mini boss, keeps out the poison for 60 seconds in it all together.
 * It sits on the head over the armour helmet, whose protection stays.
 */
public final class GasMask {
    private static final double CHANCE = 0.2;
    private static final int SECONDS = 60;
    private static final String COLOR = "#7CD34A";
    private static final NamespacedKey MODEL = new NamespacedKey("nexo", "monsters_gasmaske");
    private static final Map<UUID, Integer> left = new ConcurrentHashMap<>();
    // The helmet under the mask, it comes back when the mask breaks
    private static final Map<UUID, ItemStack> under = new ConcurrentHashMap<>();

    private GasMask() {}

    /** A mini boss died, with luck its killer gets a mask */
    public static void roll(Player killer) {
        if (killer == null || ThreadLocalRandom.current().nextDouble() >= CHANCE) return;
        left.put(killer.getUniqueId(), SECONDS);
        dress(killer);
        killer.playSound(killer.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0F, 0.8F);
        Overlay.medium(killer, COLOR, Overlay.line((char) 0, COLOR, "GASMASKE", SECONDS + " Sekunden Schutz vor Gift"),
                Overlay.line((char) 0, COLOR, "GAS MASK", SECONDS + " seconds of poison protection"));
    }

    /** One second in the poison: true if the mask kept it out */
    public static boolean protects(Player player) {
        Integer seconds = left.get(player.getUniqueId());
        if (seconds == null) return false;
        if (seconds <= 1) {
            left.remove(player.getUniqueId());
            undress(player);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 0.8F);
            Lang.note(player, Lang.t(player, "§cDeine Gasmaske ist kaputt", "§cYour gas mask broke"));
            return true;
        }
        left.put(player.getUniqueId(), seconds - 1);
        if (seconds % 10 == 0) Lang.note(player, Lang.f(player, "§aGasmaske §7noch %d s", "§aGas mask §7%d s left", seconds - 1));
        return true;
    }

    /** After new armour the mask goes back on top */
    public static void dress(Player player) {
        if (!left.containsKey(player.getUniqueId())) return;
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && helmet.hasItemMeta() && MODEL.equals(helmet.getItemMeta().getItemModel())) return;
        under.put(player.getUniqueId(), helmet == null ? new ItemStack(Material.AIR) : helmet.clone());
        ItemStack mask = helmet == null || helmet.getType().isAir() ? new ItemStack(Material.PAPER) : helmet.clone();
        mask.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
                .assetId(Key.key("monsters", "gasmaske")).damageOnHurt(false).build());
        mask.editMeta(meta -> meta.setItemModel(MODEL));
        player.getInventory().setHelmet(mask);
    }

    private static void undress(Player player) {
        ItemStack helmet = under.remove(player.getUniqueId());
        player.getInventory().setHelmet(helmet == null || helmet.getType().isAir() ? null : helmet);
    }
}
