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
        super(player, GameMode.ADVENTURE);
    }

    @Override
    public void intialize() {
        // Lies on the ground as mannequin (ReviveManager), untouchable until revived
        player.setInvulnerable(true);

        initInventory();
    }

    @Override
    protected void initInventory() {
    }
}
