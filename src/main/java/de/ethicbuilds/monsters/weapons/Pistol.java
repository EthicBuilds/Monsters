package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Pistol extends Weapon {
    public Pistol() {
        this.item = new ItemStack(Material.NETHERITE_HOE);
        this.name = "Pistole";
        this.damage = 2.0;
        this.fireRate = 10;
        this.maxAmmo = 50;
        this.maxMagazine = 10;

        initialize();
    }
}
