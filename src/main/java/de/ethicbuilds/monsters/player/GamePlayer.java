package de.ethicbuilds.monsters.player;

import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import lombok.Getter;
import lombok.Setter;
import de.ethicbuilds.monsters.weapons.Pistol;
import de.ethicbuilds.monsters.weapons.Weapon;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.HashMap;
import java.util.List;


public class GamePlayer extends GameUser {
    // For the summary at the end of the round
    @Getter private int earnedCoins;
    @Getter @Setter private int headshots;
    @Getter @Setter private int revives;
    // Armor set from the workbench, 0 = none
    @Getter @Setter private int armorLevel;
    @Getter @Setter private int crowbarLevel;
    // For the round end card: kills per weapon ("German|English" name) and defeated bosses
    @Getter private final Map<String, Integer> weaponKills = new HashMap<>();
    @Getter @Setter private int bossKills;


    public GamePlayer(Player player) {
        super(player, GameMode.ADVENTURE);
    }

    @Override
    public void initialize() {
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 3));

        initInventory();
    }

    @Override
    protected void initInventory() {
        player.getInventory().clear();

        ItemStack melee = GameItems.knife(player);

        ItemStack weaponSlot1 = emptySlot(2, player);
        ItemStack weaponSlot2 = emptySlot(3, player);
        ItemStack monsterTracker = new ItemStack(Material.COMPASS);

        ItemMeta monsterTrackerMeta = monsterTracker.getItemMeta();
        monsterTrackerMeta.setItemModel(new NamespacedKey("monsters", "tracker"));
        monsterTrackerMeta.displayName(Lang.mm(player, "<!italic><bold><#3CDC6E>Monster-Tracker</#3CDC6E></bold>",
                "<!italic><bold><#3CDC6E>Monster Tracker</#3CDC6E></bold>"));
        monsterTrackerMeta.lore(List.of(
                Lang.mm(player, "<!italic><#8A93A3>In der Hand:</#8A93A3> <#FF6B6B>Monster</#FF6B6B> <#8A93A3>und</#8A93A3> <#3CDC6E>Mitspieler</#3CDC6E> <#8A93A3>leuchten</#8A93A3>",
                        "<!italic><#8A93A3>While held:</#8A93A3> <#FF6B6B>monsters</#FF6B6B> <#8A93A3>and</#8A93A3> <#3CDC6E>teammates</#3CDC6E> <#8A93A3>glow</#8A93A3>"),
                Lang.mm(player, "<!italic><#5A616E>Durch Wände sichtbar</#5A616E>", "<!italic><#5A616E>Visible through walls</#5A616E>")));
        monsterTracker.setItemMeta(monsterTrackerMeta);

        player.getInventory().setItem(0, melee);
        player.getInventory().setItem(2, weaponSlot1);
        player.getInventory().setItem(3, weaponSlot2);
        player.getInventory().setItem(5, monsterTracker);
        player.getInventory().setItem(GameItems.CROWBAR_SLOT, GameItems.crowbar(player));

        Pistol pistol = new Pistol();
        pistol.localize(player);
        addWeapon(pistol);

        player.getInventory().setItem(1, pistol.getItem());
    }

    /** Grey dye marking a free weapon slot (hotbar 2 and 3) */
    public static ItemStack emptySlot(int slot, Player player) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        String name = Lang.t(player, "§aWaffen Slot", "§aWeapon Slot");
        meta.setDisplayName(slot >= 2 ? name + " " + (slot - 1) : name);
        item.setItemMeta(meta);
        return item;
    }

    public Weapon getWeapon(ItemStack item) {
        return weapons.get(item.getType());
    }

    public void addWeapon(Weapon weapon) {
        weapons.put(weapon.getItem().getType(), weapon);
    }

    public void removeWeapon(Weapon weapon) {
        weapons.remove(weapon.getItem().getType());
    }

    /** Fills the reserve ammo of every weapon, false if all were full */
    public boolean refillWeapons() {
        boolean filled = false;
        for (Weapon weapon : weapons.values()) {
            if (weapon.isFull()) continue;
            weapon.refill();
            filled = true;
        }
        Weapon held = getWeapon(player.getInventory().getItemInMainHand());
        if (filled && held != null) AmmoHud.refresh(player, held);
        return filled;
    }

    public void addCoins(int coins) {
        this.coins += coins;
        earnedCoins += coins;
    }
}
