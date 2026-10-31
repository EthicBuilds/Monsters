package de.ethicbuilds.monsters.bautest;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/***
 * Nexo only sends the barriers of its furniture to the players, the world has air there. Where a part is open in the
 * function test (gate up, rubble away), the testers get air sent instead, again and again since Nexo sends its barriers anew.
 */
public final class NexoBarriers {
    private static final double RANGE = 64;
    private static final BlockData AIR = Material.AIR.createBlockData(), BARRIER = Material.BARRIER.createBlockData();

    private static final Set<Block> open = new HashSet<>();
    // Every Nexo barrier we changed, it is sent back to everyone after the test
    private static final Set<Block> touched = new HashSet<>();

    private NexoBarriers() {}

    public static void open(Collection<Block> blocks) {
        if (!BauTest.on()) return;
        open.addAll(blocks);
        touched.addAll(blocks);
        BauTest.audience().forEach(player -> send(player, blocks));
    }

    /** Closed again: the real barrier the part sets stands there for everyone */
    public static void close(Collection<Block> blocks) {
        if (!BauTest.on()) return;
        open.removeAll(blocks);
        touched.addAll(blocks);
    }

    static void resend() {
        BauTest.audience().forEach(player -> send(player, open));
    }

    /** A tester leaves while others stay: Nexo's barriers for them again */
    static void forget(Player player) {
        for (Block block : open) if (near(player, block)) player.sendBlockChange(block.getLocation(), BARRIER);
    }

    /** After the world is back: Nexo's barriers for everyone, a tick later than the block updates */
    static void all() {
        Set<Block> back = new HashSet<>(touched);
        open.clear();
        touched.clear();
        Runnable send = () -> back.forEach(block -> {
            if (!block.getType().isAir()) return;
            Location at = block.getLocation();
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (near(player, block)) player.sendBlockChange(at, BARRIER);
            });
        });
        Main plugin = Main.getINSTANCE();
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, send, 1L);
    }

    private static void send(Player player, Collection<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType().isAir() && near(player, block)) player.sendBlockChange(block.getLocation(), AIR);
        }
    }

    private static boolean near(Player player, Block block) {
        return player.getWorld().equals(block.getWorld()) && player.getLocation().distanceSquared(block.getLocation()) <= RANGE * RANGE;
    }
}
