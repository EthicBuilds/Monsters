package de.ethicbuilds.monsters.bautest;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/***
 * Nexo only sends the barriers of its furniture to the players, the world has air there. Where a part is open in the
 * function test (gate up, rubble away), Nexo takes them away for the testers, again and again since it sends them anew.
 */
public final class NexoBarriers {
    private static final double RANGE = 64;

    private static final Set<ItemDisplay> open = new HashSet<>();
    // Every furniture we changed: its barriers and model go back to everyone after the test
    private static final Set<ItemDisplay> touched = new HashSet<>();

    private NexoBarriers() {}

    static boolean nexo() {
        return BauTest.on() && Bukkit.getPluginManager().isPluginEnabled("Nexo");
    }

    public static void open(ItemDisplay display) {
        if (!nexo() || !NexoHook.isFurniture(display)) return;
        open.add(display);
        touched.add(display);
        BauTest.audience().forEach(player -> NexoHook.removeBarriers(display, player));
    }

    public static void close(ItemDisplay display) {
        if (!nexo() || !NexoHook.isFurniture(display)) return;
        open.remove(display);
        touched.add(display);
        BauTest.audience().forEach(player -> NexoHook.sendBarriers(display, player));
    }

    static void resend() {
        if (!nexo()) return;
        open.removeIf(display -> !display.isValid());
        Collection<? extends Player> testers = BauTest.audience();
        for (ItemDisplay display : open) {
            for (Player player : testers) if (near(player, display)) NexoHook.removeBarriers(display, player);
        }
    }

    /** A tester leaves while others stay: Nexo's barriers for them again */
    static void forget(Player player) {
        if (!nexo()) return;
        for (ItemDisplay display : open) if (display.isValid()) NexoHook.sendBarriers(display, player);
    }

    /** After the world is back: Nexo's barriers and models for everyone, a tick after our item updates */
    static void all(Collection<ItemDisplay> restored) {
        if (!nexo()) return;
        Set<ItemDisplay> back = new HashSet<>(touched);
        back.addAll(restored);
        NexoLooks.clear();
        NexoStandIns.clear();
        open.clear();
        touched.clear();
        Main plugin = Main.getINSTANCE();
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> back.forEach(display -> {
            if (!display.isValid() || !NexoHook.isFurniture(display)) return;
            NexoHook.showModel(display);
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (near(player, display)) NexoHook.sendBarriers(display, player);
            });
        }), 1L);
    }

    private static boolean near(Player player, ItemDisplay display) {
        return player.getWorld().equals(display.getWorld()) && player.getLocation().distanceSquared(display.getLocation()) <= RANGE * RANGE;
    }
}
