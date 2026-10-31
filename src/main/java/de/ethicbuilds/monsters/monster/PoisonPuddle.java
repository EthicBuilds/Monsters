package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GasMask;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/***
 * Green poison puddle of the poison zombie (model of the design session): hurts players standing in it for a few seconds
 */
public final class PoisonPuddle {
    private static final int TICKS = 6 * 20, HURT_EVERY = 20;
    private static final double RADIUS = 1.4, DAMAGE = 1.5;
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(0x7CD34A), 1.0F);

    private PoisonPuddle() {}

    public static void spill(Location where) {
        Location ground = GroundMark.ground(where);
        if (ground == null) return;
        Location at = ground.clone().add(0, 0.02, 0);
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("monsters", "giftpfuetze")));
        ItemDisplay puddle = at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setBrightness(new Display.Brightness(10, 10));
            display.setPersistent(false);
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf()));
        });
        at.getWorld().playSound(at, Sound.BLOCK_MUD_PLACE, 0.8F, 0.7F);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!puddle.isValid() || ++ticks > TICKS || !LivingPlayers.waveRunning()) {
                    puddle.remove();
                    cancel();
                    return;
                }
                if (ticks % 5 == 0) at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.2, 0), 2, 0.5, 0.05, 0.5, GREEN);
                if (ticks % HURT_EVERY != 0) return;
                for (Player player : at.getWorld().getPlayers()) {
                    if (!LivingPlayers.alive(player) || player.getLocation().distanceSquared(at) > RADIUS * RADIUS
                            || Math.abs(player.getLocation().getY() - at.getY()) > 1) continue;
                    if (GasMask.protects(player)) continue;
                    player.damage(DAMAGE);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false));
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 1L, 1L);
    }
}
