package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

public class Pistol extends Weapon {
    public Pistol() {
        this.item = new ItemStack(Material.NETHERITE_HOE);
        this.name = "Pistole";
        this.damage = 3.0;
        this.fireRate = 20;
        this.maxAmmu = 1000;
        this.maxMagazine = 10;
        this.particle = Particle.WAX_ON;
        initialize();
    }
}
