package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Pistol extends Weapon {
    public Pistol() {
        this.item = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = this.item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        this.name = "§6Pistole";
        this.damage = 5.0;
        this.fireRate = 15;
        this.maxAmmu = 1000;
        this.maxMagazine = 10;
        this.particle = Particle.WAX_ON;
        initialize();
    }
}
