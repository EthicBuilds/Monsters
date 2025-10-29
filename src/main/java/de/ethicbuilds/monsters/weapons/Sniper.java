package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

public class Sniper extends Weapon {
    public Sniper() {
        this.item = new ItemStack(Material.ARROW);
        this.name = "Sniper";
        this.damage = 10.0;
        this.fireRate = 40;
        this.maxAmmu = 100;
        this.maxMagazine = 10;
        this.particle = Particle.WAX_ON;

        initialize();
    }
}
