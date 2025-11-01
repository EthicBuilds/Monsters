package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

public class Rifle extends Weapon {
    public Rifle() {
        this.item = new ItemStack(Material.FEATHER);
        this.name = "§6Sturmgewehr";
        this.damage = 2.0;
        this.fireRate = 2;
        this.maxAmmu = 800;
        this.maxMagazine = 50;
        this.particle = Particle.WAX_ON;

        initialize();
    }
}
