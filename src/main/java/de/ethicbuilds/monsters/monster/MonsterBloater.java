package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.weapons.ShotDamage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/***
 * Bloater from wave 9: slow, close to a player it hisses and swells for 1.2 seconds and bursts. Only players are hurt,
 * the closer the more. Killed before it bursts smaller, a headshot keeps it from bursting.
 */
public class MonsterBloater extends MonsterZombie {
    private static final double TRIGGER = 2.5, RADIUS = 3, MIN_FACTOR = 0.3, PUSH = 0.9, KILLED = 0.5;
    // Burst damage in multiples of its (wave scaled) attack
    private static final double BURST_ATTACK = 2.5, BURST_FALLBACK = 8;
    private static final int SWELL_TICKS = 24, STEP = 2, CHECK_EVERY = 3;
    private static final double SWELL_SCALE = 1.3;
    private static final Color BILE = Color.fromRGB(0x9BB83A), SICK = Color.fromRGB(0x8A9A3B);

    private boolean swelling, burst;
    private int swell, check;
    private GroundMark mark;

    public MonsterBloater() {
        super(EntityType.ZOMBIE, "§2Explodierer");
        this.health = 26;
        this.speed = 0.75;
    }

    @Override
    protected String modelId() {
        return "explodierer";
    }

    @Override
    protected void onSpawn() {
        super.onSpawn();
        if (model() == null) vanillaLook();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (burst || !monster.isValid() || monster.isDead()) {
                    cancel();
                    return;
                }
                if (!LivingPlayers.waveRunning()) return;
                if (!swelling) {
                    if (++check % CHECK_EVERY == 0 && !LivingPlayers.near(monster.getLocation(), TRIGGER).isEmpty()) startSwell();
                    return;
                }
                swell += STEP;
                grow();
                if (swell < SWELL_TICKS) return;
                cancel();
                explode(1);
                // Without a killer: no coins, the wave counts it as gone
                var equipment = monster.getEquipment();
                if (equipment != null) equipment.clear();
                monster.setInvisible(true);
                monster.setHealth(0);
            }
        }.runTaskTimer(Main.getINSTANCE(), 20L, STEP);
    }

    // Until the design session has its model: sickly green body (scale stays, it has to fit through the spawn holes)
    private void vanillaLook() {
        var equipment = monster.getEquipment();
        if (equipment == null) return;
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        chest.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(SICK));
        equipment.setChestplate(chest);
        equipment.setChestplateDropChance(0);
    }

    private void startSwell() {
        swelling = true;
        if (monster instanceof Mob mob) mob.setAware(false);
        if (model() != null) model().play("aufblaehen");
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0F, 0.6F);
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.8F, 0.5F);
        Location ground = GroundMark.ground(monster.getLocation());
        mark = GroundMark.warn(ground != null ? ground : monster.getLocation(), GroundMark.DANGER, RADIUS, SWELL_TICKS);
    }

    private void grow() {
        Location body = monster.getLocation().add(0, 1, 0);
        body.getWorld().spawnParticle(Particle.DUST, body, 4, 0.35, 0.5, 0.35, 0, new Particle.DustOptions(BILE, 1.3F));
        if (swell % 6 == 0) body.getWorld().spawnParticle(Particle.SMOKE, body, 3, 0.2, 0.3, 0.2, 0.01);
        if (model() != null) return;
        var scale = monster.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(1 + (SWELL_SCALE - 1) * swell / SWELL_TICKS);
    }

    // Damage falls off from the middle to the edge, players only
    private void explode(double factor) {
        burst = true;
        if (mark != null) mark.clear();
        Location at = monster.getLocation();
        World world = at.getWorld();
        Location body = at.clone().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION, body, 1);
        world.spawnParticle(Particle.ITEM_SLIME, body, (int) (30 * factor), 1.0, 0.6, 1.0, 0.1);
        world.spawnParticle(Particle.DUST, body, (int) (40 * factor), 1.2, 0.6, 1.2, 0, new Particle.DustOptions(BILE, 1.8F));
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, (float) (0.5 + 0.4 * factor), 1.3F);
        world.playSound(at, Sound.ENTITY_SLIME_DEATH, 1.0F, 0.5F);
        var attack = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        double damage = (attack != null ? attack.getValue() * BURST_ATTACK : BURST_FALLBACK) * factor;
        for (Player player : LivingPlayers.near(at, RADIUS)) {
            double distance = player.getLocation().distance(at);
            player.damage(damage * (1 - (1 - MIN_FACTOR) * distance / RADIUS));
            Vector away = player.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 0);
            else away.normalize().multiply(PUSH * factor);
            player.setVelocity(away.setY(0.35 + 0.15 * factor));
        }
    }

    @Override
    public void onDeath() {
        if (!burst) {
            burst = true;
            if (mark != null) mark.clear();
            // Shot in the head it just falls over
            if (ShotDamage.isHeadshot()) {
                monster.getWorld().spawnParticle(Particle.SMOKE, monster.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.02);
                super.onDeath();
                return;
            }
            explode(KILLED);
        }
        // Burst there is nothing left to fall over
        if (model() != null) model().remove();
    }
}
