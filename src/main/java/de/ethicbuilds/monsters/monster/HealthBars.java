package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/***
 * Health bar above the monsters like in EggBattle: pixel bar from the font monsters:healthbar
 * (21 steps from empty to full), riding on the monster as passenger
 */
public final class HealthBars {
    private static final NamespacedKey KEY = new NamespacedKey("monsters", "health_bar");
    private static final Key FONT = Key.key("monsters", "healthbar");
    // U+E000 (empty) to U+E014 (full)
    private static final int STEPS = 20;
    private static final char EMPTY = '';
    private static final float HEIGHT_ABOVE_HEAD = 0.35F;

    private HealthBars() {}

    public static void attach(LivingEntity mob) {
        TextDisplay bar = mob.getWorld().spawn(mob.getLocation(), TextDisplay.class, display -> {
            display.text(text(mob));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
            display.setTransformation(new Transformation(new Vector3f(0, HEIGHT_ABOVE_HEAD, 0), new AxisAngle4f(),
                    new Vector3f(1), new AxisAngle4f()));
            display.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        });
        mob.addPassenger(bar);
    }

    /** After damage or healing, once the new health is known */
    public static void updateLater(LivingEntity mob) {
        Bukkit.getScheduler().runTask(Main.getINSTANCE(), () -> {
            if (!mob.isValid()) return;
            for (Entity passenger : mob.getPassengers()) {
                if (passenger instanceof TextDisplay display && isBar(display)) display.text(text(mob));
            }
        });
    }

    public static void remove(Entity mob) {
        for (Entity passenger : mob.getPassengers()) {
            if (isBar(passenger)) passenger.remove();
        }
    }

    /** Bars whose monster is gone (died, removed, unloaded) */
    public static void removeOrphans(World world) {
        for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            if (!isBar(display)) continue;
            Entity vehicle = display.getVehicle();
            if (vehicle == null || !vehicle.isValid() || vehicle.isDead()) display.remove();
        }
    }

    private static boolean isBar(Entity entity) {
        return entity.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    private static Component text(LivingEntity mob) {
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : mob.getHealth();
        double ratio = Math.max(0, Math.min(1, max > 0 ? mob.getHealth() / max : 0));
        // A small hit already shows, and a living monster keeps a filled rest
        int step = ratio >= 1 ? STEPS : ratio <= 0 ? 0 : Math.max(1, (int) Math.floor(ratio * STEPS));
        return Component.text(String.valueOf((char) (EMPTY + step))).font(FONT);
    }
}
