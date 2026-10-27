package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

public class Sniper extends Weapon {
    public Sniper() {
        this.item = new ItemStack(Material.ARROW);
        this.name = "§6Sniper";
        // Goes through 5 monsters and kills each of them
        this.damage = 1000.0;
        this.pierce = 5;
        this.fireRate = 35;
        this.maxAmmu = 100;
        this.maxMagazine = 10;
        this.particle = Particle.WAX_ON;
        this.model = "sniper_3";
        this.price = 10000;
        this.upgradePrice = 30000;

        initialize();
    }
}
