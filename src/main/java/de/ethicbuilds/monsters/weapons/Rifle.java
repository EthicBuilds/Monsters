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

    // Stronger than the other weapons per level, so at level V it keeps up with the sniper
    private static final double[] BOOST = {1, 1.3, 1.75, 2.75, 4.5, 7};

    @Override
    protected double boost(int level) {
        return BOOST[Math.min(level, BOOST.length - 1)];
    }

    // From level 3 through 2 monsters, at level V through 3
    @Override
    protected void onUpgrade() {
        pierce = level >= MAX_LEVEL ? 3 : level >= 3 ? 2 : 1;
    }
}
