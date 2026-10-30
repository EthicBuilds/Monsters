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
    @Getter
    protected Map<Material, Weapon> weapons = new HashMap<>();
    @Getter
    protected int[] weaponSlots = {1, 2, 3};

    @Getter @Setter
    protected Player player;
    @Getter @Setter
    protected GameMode mode;
    @Getter @Setter
    protected int coins;
    @Getter @Setter
    protected int killedZombies;

    public GameUser(Player player, GameMode mode) {
        this.player = player;
        this.mode = mode;
        player.setGameMode(mode);

        initialize();
    }

    public abstract void initialize();

    protected abstract void initInventory();
}
