package de.ethicbuilds.monsters.player;

import de.ethicbuilds.monsters.items.GameItems;
import lombok.Getter;
import lombok.Setter;
import de.ethicbuilds.monsters.weapons.Pistol;
import de.ethicbuilds.monsters.weapons.Weapon;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class GamePlayer extends GameUser {
    // For the summary at the end of the round
    @Getter private int earnedCoins;
    @Getter @Setter private int headshots;
    @Getter @Setter private int revives;
    // Armor set from the workbench, 0 = none
    @Getter @Setter private int armorLevel;
    @Getter @Setter private int crowbarLevel;


    public GamePlayer(Player player) {
        super(player, GameMode.ADVENTURE);
    }

    @Override
    public void intialize() {
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 3));

        initInventory();
    }

    @Override
    protected void initInventory() {
        player.getInventory().clear();

        ItemStack melee = GameItems.knife();

        ItemStack weaponSlot1 = new ItemStack(Material.LIGHT_GRAY_DYE);
        ItemStack weaponSlot2 = new ItemStack(Material.LIGHT_GRAY_DYE);
        ItemStack monsterTracker = new ItemStack(Material.COMPASS);

        ItemMeta weaponSlot1Meta = weaponSlot1.getItemMeta();
        weaponSlot1Meta.setDisplayName("§aWaffen Slot 1");
        weaponSlot1.setItemMeta(weaponSlot1Meta);

        ItemMeta weaponSlot2Meta = weaponSlot2.getItemMeta();
        weaponSlot2Meta.setDisplayName("§aWaffen Slot 2");
        weaponSlot2.setItemMeta(weaponSlot2Meta);

        ItemMeta monsterTrackerMeta = monsterTracker.getItemMeta();
        monsterTrackerMeta.setDisplayName("§cMonster Tracker");
        monsterTracker.setItemMeta(monsterTrackerMeta);

        player.getInventory().setItem(0, melee);
        player.getInventory().setItem(2, weaponSlot1);
        player.getInventory().setItem(3, weaponSlot2);
        player.getInventory().setItem(5, monsterTracker);
        player.getInventory().setItem(GameItems.CROWBAR_SLOT, GameItems.crowbar());

        Pistol pistol = new Pistol();
        addWeapon(pistol);

        player.getInventory().setItem(1, pistol.getItem());
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

    public boolean isWeapon(ItemStack item) {
        return weapons.containsKey(item);
    }

    public void addCoins(int coins) {
        this.coins += coins;
        earnedCoins += coins;
    }
}
