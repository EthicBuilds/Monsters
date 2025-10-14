package de.ethicbuilds.monsters.player;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public abstract class GameUser {
    @Getter
    protected Player player;
    @Getter @Setter
    protected GameMode mode;

    public GameUser(Player player, GameMode mode) {
        this.player = player;
        this.mode = mode;

        intialize();
    }

    public abstract void intialize();

    protected abstract void initInventory();
}
