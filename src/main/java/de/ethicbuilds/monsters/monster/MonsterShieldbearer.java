package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.weapons.ShotDamage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/***
 * Shieldbearer from wave 15: hits from the front land on its shield and do only a quarter of the damage.
 * Headshots, hits from the side or back and grenades go through.
 */
public class MonsterShieldbearer extends MonsterZombie {
    private static final double FRONT = Math.toRadians(60), BLOCKED = 0.25;

    private int lastBlock = -1;

    public MonsterShieldbearer() {
        super(EntityType.ZOMBIE, "§7Schildträger");
        this.health = 26;
        this.speed = 0.9;
    }

    @Override
    protected String modelId() {
        return "schildtraeger";
    }

    @Override
    protected void onSpawn() {
        super.onSpawn();
        // Until the design session has its model: a vanilla shield in the other hand
        if (model() != null) return;
        var equipment = monster.getEquipment();
        if (equipment == null) return;
        equipment.setItemInOffHand(new ItemStack(Material.SHIELD));
        equipment.setItemInOffHandDropChance(0);
    }

    @Override
    public double damageFactor(Player by) {
        if (ShotDamage.isHeadshot() || ShotDamage.isActive() && !ShotDamage.isAimed()) return 1;
        double yaw = Math.toRadians(monster.getBodyYaw());
        Vector facing = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector toShooter = by.getLocation().toVector().subtract(monster.getLocation().toVector()).setY(0);
        if (toShooter.lengthSquared() < 0.01 || facing.angle(toShooter) > FRONT) return 1;
        // Once per tick, the pellets of the pump gun come together
        int tick = Bukkit.getCurrentTick();
        if (tick != lastBlock) {
            lastBlock = tick;
            if (model() != null) model().play("blocken");
            monster.getWorld().playSound(monster.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.9F);
            monster.getWorld().spawnParticle(Particle.CRIT, monster.getLocation().add(facing.clone().multiply(0.5)).add(0, 1.1, 0),
                    6, 0.15, 0.25, 0.15, 0.1);
        }
        return BLOCKED;
    }
}
