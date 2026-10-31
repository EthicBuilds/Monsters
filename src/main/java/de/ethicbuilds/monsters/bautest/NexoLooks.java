package de.ethicbuilds.monsters.bautest;

import de.ethicbuilds.monsters.Main;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/***
 * Nexo sends the model of its furniture itself (and its pack paths are scrambled): a model change in the function
 * test goes through Nexo with the other furniture id, again whenever a player starts to see the furniture
 */
public final class NexoLooks {
    private static final Map<ItemDisplay, String> shown = new HashMap<>();

    private NexoLooks() {}

    public static void show(ItemDisplay display, String id) {
        if (!NexoBarriers.nexo() || !NexoHook.isFurniture(display)) return;
        shown.put(display, id);
        NexoHook.showAs(display, id, null);
    }

    /** Nexo sent its own model to a player who just started to see it: ours again right after */
    static void tracked(Player player, Entity entity) {
        if (!(entity instanceof ItemDisplay display)) return;
        String id = shown.get(display);
        if (id == null) return;
        Main plugin = Main.getINSTANCE();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid() && player.isOnline() && id.equals(shown.get(display))) NexoHook.showAs(display, id, player);
        }, 2L);
    }

    static void clear() {
        shown.clear();
    }
}
