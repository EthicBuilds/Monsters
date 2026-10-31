package de.ethicbuilds.monsters.bautest;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/***
 * Nexo puts its own model, barriers and hitboxes on every packet of its furniture. Parts that move and change in the
 * function test (gate, switch) get a display of our own for the testers instead: the Nexo furniture is hidden for them,
 * so Nexo takes its model and barriers away, everything else comes from the plugin. Builders keep seeing the original.
 */
public final class NexoStandIns {
    private static final Set<ItemDisplay> standIns = new HashSet<>();

    private NexoStandIns() {}

    /** The display to show this furniture with: our own on the build server, else the furniture itself */
    public static ItemDisplay of(ItemDisplay furniture, String id) {
        if (!NexoBarriers.nexo() || !NexoHook.isFurniture(furniture)) return furniture;
        ItemDisplay standIn = furniture.getWorld().spawn(furniture.getLocation(), ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemDisplayTransform(furniture.getItemDisplayTransform());
            display.setTransformation(furniture.getTransformation());
            display.setItemStack(item(id));
        });
        BauTest.limit(standIn);
        standIns.add(standIn);
        Restore.hide(furniture);
        return standIn;
    }

    public static boolean is(ItemDisplay display) {
        return standIns.contains(display);
    }

    /** The real Nexo item of an id, its pack paths are scrambled */
    public static ItemStack item(String id) {
        return NexoHook.item(id);
    }

    static void clear() {
        standIns.clear();
    }
}
