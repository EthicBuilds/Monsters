package de.ethicbuilds.monsters.building;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/***
 * Visible break-outs in the wall: a monster coming out near one makes dust, a crash and the model shakes
 */
public final class Breakouts {
    private static final double NEAR = 4;
    private static final long PAUSE = 1500;
    private static final Map<ItemDisplay, Long> breakouts = new HashMap<>();

    private Breakouts() {}

    static void add(ItemDisplay display) {
        breakouts.put(display, 0L);
    }

    /** Called where a monster comes out */
    public static void burst(Location at) {
        long now = System.currentTimeMillis();
        for (Map.Entry<ItemDisplay, Long> entry : breakouts.entrySet()) {
            ItemDisplay display = entry.getKey();
            if (!display.isValid() || !display.getWorld().equals(at.getWorld())
                    || display.getLocation().distanceSquared(at) > NEAR * NEAR || now - entry.getValue() < PAUSE) continue;
            entry.setValue(now);
            Location center = display.getLocation().add(0, 1, 0);
            center.getWorld().spawnParticle(Particle.BLOCK, center, 30, 0.6, 0.8, 0.6, Material.COBBLESTONE.createBlockData());
            center.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 2, 0.4, 0.4, 0.4, 0.01);
            center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.9F, 0.6F);
            shake(display);
        }
    }

    private static void shake(ItemDisplay display) {
        Transformation rest = display.getTransformation();
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTransformation(new Transformation(rest.getTranslation(), new Quaternionf(rest.getLeftRotation()).rotateZ((float) Math.toRadians(4)),
                rest.getScale(), rest.getRightRotation()));
        display.getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(4);
            display.setTransformation(rest);
        }, 3L);
    }
}
