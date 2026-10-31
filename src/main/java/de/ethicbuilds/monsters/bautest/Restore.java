package de.ethicbuilds.monsters.bautest;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/***
 * The build server is the source of the live maps: everything a function test changes in the world
 * is noted here first and set back exactly, at the latest before its chunk is saved.
 */
public final class Restore {
    private record Look(ItemStack item, Location at, Transformation transformation) {}

    private static final Map<ItemDisplay, Look> displays = new HashMap<>();
    private static final Map<Block, BlockData> blocks = new HashMap<>();
    private static final Set<Entity> hidden = new HashSet<>();

    private Restore() {}

    public static void display(ItemDisplay display) {
        if (!BauTest.on() || displays.containsKey(display)) return;
        ItemStack item = display.getItemStack();
        displays.put(display, new Look(item == null ? null : item.clone(), display.getLocation(), display.getTransformation()));
    }

    public static void block(Block block) {
        if (BauTest.on()) blocks.putIfAbsent(block, block.getBlockData().clone());
    }

    /** Gone for the testers, still there in the world */
    public static void hide(Entity entity) {
        hidden.add(entity);
        BauTest.audience().forEach(player -> player.hideEntity(Main.getINSTANCE(), entity));
    }

    /** A tester comes or goes: what is hidden for testers */
    static void hideFor(Player player, boolean tester) {
        Main plugin = Main.getINSTANCE();
        hidden.forEach(entity -> {
            if (tester) player.hideEntity(plugin, entity);
            else player.showEntity(plugin, entity);
        });
    }

    /** Sets everything back, returns the displays whose look came back */
    public static Set<ItemDisplay> all() {
        showHidden();
        displays.forEach(Restore::back);
        Set<ItemDisplay> restored = new HashSet<>(displays.keySet());
        displays.clear();
        blocks.forEach((block, data) -> block.setBlockData(data, false));
        blocks.clear();
        return restored;
    }

    /** Before a chunk is saved */
    public static void chunk(Chunk chunk) {
        for (Iterator<Map.Entry<ItemDisplay, Look>> it = displays.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ItemDisplay, Look> entry = it.next();
            // A driven car may stand in another chunk than its own
            ItemDisplay display = entry.getKey();
            boolean here = entry.getValue().at().getChunk().equals(chunk) || display.isValid() && display.getLocation().getChunk().equals(chunk);
            if (!here) continue;
            back(entry.getKey(), entry.getValue());
            it.remove();
        }
        for (Iterator<Map.Entry<Block, BlockData>> it = blocks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Block, BlockData> entry = it.next();
            if (!entry.getKey().getChunk().equals(chunk)) continue;
            entry.getKey().setBlockData(entry.getValue(), false);
            it.remove();
        }
    }

    private static void showHidden() {
        Main plugin = Main.getINSTANCE();
        if (plugin.isEnabled()) hidden.forEach(entity -> Bukkit.getOnlinePlayers().forEach(player -> player.showEntity(plugin, entity)));
        hidden.clear();
    }

    private static void back(ItemDisplay display, Look look) {
        if (!display.isValid()) return;
        display.setItemStack(look.item());
        display.setTransformation(look.transformation());
        if (!display.getLocation().equals(look.at())) display.teleport(look.at());
    }
}
