package de.ethicbuilds.monsters.monster;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.GoalType;
import de.ethicbuilds.monsters.Main;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/***
 * Spitter from wave 12: keeps 6 to 10 blocks away from its player while it sees him and spits an acid ball
 * every 4 seconds. Up close it still bites.
 */
public class MonsterSpitter extends MonsterZombie {
    private static final double KEEP_MIN = 6, KEEP_MAX = 10, MELEE = 2.5, SPIT_RANGE = 16, BACK_OFF = 5;
    // Checked every half second
    private static final int SPIT_EVERY = 8, SPIT_DELAY = 6;
    private static final Color SLIME = Color.fromRGB(0x6ABE30);

    private int spitIn = SPIT_EVERY;

    public MonsterSpitter() {
        super(EntityType.ZOMBIE, "§aSpucker");
        this.health = 16;
    }

    @Override
    protected String modelId() {
        return "spucker";
    }

    @Override
    protected void onSpawn() {
        super.onSpawn();
        // The distance is kept below, the vanilla target search would walk it right up to the player
        Bukkit.getMobGoals().removeAllGoals((Mob) monster, GoalType.TARGET);
        if (model() == null) vanillaLook();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!monster.isValid() || monster.isDead()) {
                    cancel();
                    return;
                }
                if (!LivingPlayers.waveRunning() || --spitIn > 0) return;
                Player target = getTarget();
                if (!LivingPlayers.alive(target) || !target.getWorld().equals(monster.getWorld())
                        || target.getLocation().distanceSquared(monster.getLocation()) > SPIT_RANGE * SPIT_RANGE
                        || !monster.hasLineOfSight(target)) {
                    spitIn = 1;
                    return;
                }
                spitIn = SPIT_EVERY;
                spit(target);
            }
        }.runTaskTimer(Main.getINSTANCE(), 20L + ThreadLocalRandom.current().nextInt(20), 10L);
    }

    // Until the design session has its model: zombie with a slime green cap (elite tiers keep their helmet)
    private void vanillaLook() {
        var equipment = monster.getEquipment();
        if (equipment == null) return;
        ItemStack leather = new ItemStack(getTier() > 0 ? Material.LEATHER_CHESTPLATE : Material.LEATHER_HELMET);
        leather.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(SLIME));
        if (getTier() > 0) {
            equipment.setChestplate(leather);
            equipment.setChestplateDropChance(0);
        } else {
            equipment.setHelmet(leather);
            equipment.setHelmetDropChance(0);
        }
    }

    // Leans back with a gurgle, the ball leaves a moment later
    private void spit(Player target) {
        if (monster instanceof Mob mob) mob.lookAt(target);
        if (model() != null) model().play("spucken");
        monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_DROWNED_AMBIENT, 1.0F, 1.4F);
        monster.getWorld().spawnParticle(Particle.ITEM_SLIME, monster.getEyeLocation(), 5, 0.15, 0.1, 0.15, 0);
        Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (!monster.isValid() || monster.isDead() || !LivingPlayers.waveRunning() || !LivingPlayers.alive(target)
                    || !target.getWorld().equals(monster.getWorld())) return;
            Location to = GroundMark.ground(target.getLocation());
            AcidShot.fire(monster, to != null ? to : target.getLocation());
        }, SPIT_DELAY);
    }

    // Keeps its distance while it sees its player, too close it backs off
    @Override
    protected boolean flee(Pathfinder pathfinder) {
        Player target = getTarget();
        if (!LivingPlayers.alive(target) || !target.getWorld().equals(monster.getWorld())) return false;
        double distance = target.getLocation().distance(monster.getLocation());
        if (distance < MELEE || distance > KEEP_MAX || !monster.hasLineOfSight(target)) return false;
        monster.setTarget(null);
        if (distance >= KEEP_MIN) {
            pathfinder.stopPathfinding();
            if (monster instanceof Mob mob) mob.lookAt(target);
            return true;
        }
        Vector away = monster.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.01) return false;
        if (!pathfinder.moveTo(monster.getLocation().add(away.normalize().multiply(BACK_OFF)), speed)) pathfinder.stopPathfinding();
        return true;
    }
}
