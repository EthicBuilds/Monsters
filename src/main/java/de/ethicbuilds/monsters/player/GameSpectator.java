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
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1));
        // Adventure instead of creative: flying and untouchable until the next wave
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);

        initInventory();
    }

    @Override
    protected void initInventory() {
    }
}
