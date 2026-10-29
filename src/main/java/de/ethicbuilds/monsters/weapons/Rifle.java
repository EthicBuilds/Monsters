package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

public class Rifle extends Weapon {
    public Rifle() {
        this.item = new ItemStack(Material.FEATHER);
        this.name = "§6Sturmgewehr";
        this.nameEn = "§6Assault Rifle";
        this.damage = 4.0;
        this.fireRate = 2;
        this.maxAmmu = 800;
        this.maxMagazine = 50;
        this.particle = Particle.WAX_ON;
        this.model = "sturm_v2";
        this.price = 5000;
        this.upgradePrice = 20000;

        initialize();
    }
}
