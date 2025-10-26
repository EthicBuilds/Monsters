package de.ethicbuilds.monsters.player;

import de.ethicbuilds.monsters.weapons.Weapon;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public abstract class GameUser {
    protected Map<Material, Weapon> weapons = new HashMap<>();

    @Getter
    protected Player player;
    @Getter @Setter
    protected GameMode mode;
    @Getter @Setter
    protected int coins;

    public GameUser(Player player, GameMode mode) {
        this.player = player;
        this.mode = mode;
        player.setGameMode(mode);

        intialize();
        player.sendMessage(String.format("Created new %s with %s as Player", this.getClass().getSimpleName(), player.getName()));
    }

    public abstract void intialize();

    protected abstract void initInventory();
}
