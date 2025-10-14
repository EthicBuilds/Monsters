package de.ethicbuilds.monsters.weapons;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

@Getter @Setter
public abstract class Weapon {
    protected ItemStack item;
    protected String name;
    protected double damage;
    protected int fireRate;
}
