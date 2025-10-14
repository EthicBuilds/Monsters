package de.ethicbuilds.monsters.player;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class GameSpectator extends GameUser {
    public GameSpectator(Player player) {
        super(player, GameMode.CREATIVE);
    }

    @Override
    public void intialize() {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1));

        initInventory();
    }

    @Override
    protected void initInventory() {
        ItemStack navigator = new ItemStack(Material.COMPASS);
        ItemMeta navigatorMeta = navigator.getItemMeta();
        navigatorMeta.setDisplayName("§cNavigator");
        navigator.setItemMeta(navigatorMeta);

        player.getInventory().setItem(4, navigator);
    }
}
