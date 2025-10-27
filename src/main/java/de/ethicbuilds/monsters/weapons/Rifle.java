package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Rifle extends Weapon {
    public Rifle() {
        this.item = new ItemStack(Material.FEATHER);
        this.name = "Sturmgewehr";
        this.damage = 2.0;
        this.fireRate = 10;
        this.maxAmmo = 50;
        this.maxMagazine = 10;

        initialize();
    }
}
