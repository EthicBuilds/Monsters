package de.ethicbuilds.monsters.player;

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


    public GamePlayer(Player player) {
        super(player, GameMode.SURVIVAL);
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

        ItemStack melee = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meleeMeta = melee.getItemMeta();
        meleeMeta.setDisplayName("§aAxt");
        meleeMeta.setUnbreakable(true);
        melee.setItemMeta(meleeMeta);

        ItemStack weaponSlot1 = new ItemStack(Material.LIGHT_GRAY_DYE);
        ItemStack weaponSlot2 = new ItemStack(Material.LIGHT_GRAY_DYE);

        ItemMeta weaponSlot1Meta = weaponSlot1.getItemMeta();
        weaponSlot1Meta.setDisplayName("§aWaffen Slot 1");

        ItemMeta weaponSlot2Meta = weaponSlot2.getItemMeta();
        weaponSlot1Meta.setDisplayName("§aWaffen Slot 2");

        player.getInventory().setItem(0, melee);
        player.getInventory().setItem(2, weaponSlot1);
        player.getInventory().setItem(3, weaponSlot2);

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
        if (weapons.containsKey(weapon.getItem())) return;
        weapons.remove(weapon.getItem());
    }

    public boolean isWeapon(ItemStack item) {
        return weapons.containsKey(item);
    }

    public void addCoins(int coins) {
        this.coins += coins;
    }
}
