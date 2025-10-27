package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Sniper extends Weapon {
    public Sniper() {
        this.item = new ItemStack(Material.ARROW);
        this.name = "Sniper";
        this.damage = 2.0;
        this.fireRate = 10;
        this.maxAmmo = 50;
        this.maxMagazine = 10;

        initialize();
    }
}
