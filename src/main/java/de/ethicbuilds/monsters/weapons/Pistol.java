package de.ethicbuilds.monsters.weapons;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Pistol extends Weapon {
    public Pistol() {
        this.item = new ItemStack(Material.NETHERITE_HOE);
        this.name = "Pistole";
        this.damage = 2.0;
        this.fireRate = 10;
        this.amo = 50;
        this.magazine = 10;

        ItemMeta meta = this.item.getItemMeta();
        meta.setDisplayName(this.name);
        this.item.setItemMeta(meta);
    }
}
