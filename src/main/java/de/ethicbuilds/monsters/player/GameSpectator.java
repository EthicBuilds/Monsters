package de.ethicbuilds.monsters.player;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class GameSpectator extends GameUser {
    public GameSpectator(Player player) {
        super(player, GameMode.ADVENTURE);
    }

    @Override
    public void initialize() {
        // Lies on the ground as mannequin (ReviveManager), untouchable until revived
        player.setInvulnerable(true);

        initInventory();
    }

    @Override
    protected void initInventory() {
    }
}
