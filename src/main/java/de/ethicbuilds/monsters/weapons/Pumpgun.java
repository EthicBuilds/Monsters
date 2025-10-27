package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Pumpgun extends Weapon {
    public Pumpgun() {
        this.item = new ItemStack(Material.FLINT);
        this.name = "Schrotflinte";
        this.damage = 2.0;
        this.fireRate = 10;
        this.maxAmmo = 50;
        this.maxMagazine = 10;

        initialize();
    }
}
