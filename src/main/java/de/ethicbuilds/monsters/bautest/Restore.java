package de.ethicbuilds.monsters.bautest;

import io.papermc.paper.entity.TeleportFlag;
import de.ethicbuilds.monsters.Main;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/***
 * The build server is the source of the live maps: everything a function test changes in the world
 * is noted here first and set back exactly, at the latest before its chunk is saved.
 * The notes are also kept in a file, after a crash the next start sets the world back from it.
 */
public final class Restore {
    private record Look(ItemStack item, Location at, Transformation transformation) {}

    private static final String FILE = "bautest-restore.yml";

    private static final Map<ItemDisplay, Look> displays = new HashMap<>();
    private static final Map<Block, BlockData> blocks = new HashMap<>();
    private static final Set<Entity> hidden = new HashSet<>();
    // From the file after a crash, until their chunk is loaded
    private static final Map<UUID, Look> pending = new HashMap<>();

    private Restore() {}

    public static void display(ItemDisplay display) {
        if (!BauTest.on() || displays.containsKey(display)) return;
        ItemStack item = display.getItemStack();
        displays.put(display, new Look(item == null ? null : item.clone(), display.getLocation(), display.getTransformation()));
        save();
    }

    public static void block(Block block) {
        if (!BauTest.on() || blocks.containsKey(block)) return;
        blocks.put(block, block.getBlockData().clone());
        save();
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
        save();
        return restored;
    }

    /** Before a chunk is saved */
    public static void chunk(Chunk chunk) {
        boolean changed = false;
        for (Iterator<Map.Entry<ItemDisplay, Look>> it = displays.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ItemDisplay, Look> entry = it.next();
            // A driven car may stand in another chunk than its own
            ItemDisplay display = entry.getKey();
            boolean here = entry.getValue().at().getChunk().equals(chunk) || display.isValid() && display.getLocation().getChunk().equals(chunk);
            if (!here) continue;
            back(entry.getKey(), entry.getValue());
            it.remove();
            changed = true;
        }
        for (Iterator<Map.Entry<Block, BlockData>> it = blocks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Block, BlockData> entry = it.next();
            if (!entry.getKey().getChunk().equals(chunk)) continue;
            entry.getKey().setBlockData(entry.getValue(), false);
            it.remove();
            changed = true;
        }
        if (changed) save();
    }

    /** After a crash: blocks back right away, displays as soon as their chunk is loaded */
    static void recover() {
        File file = file();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int fixed = 0;
        for (String line : yaml.getStringList("blocks")) {
            String[] part = line.split(";", 5);
            World world = part.length == 5 ? Bukkit.getWorld(part[0]) : null;
            if (world == null) continue;
            world.getBlockAt(Integer.parseInt(part[1]), Integer.parseInt(part[2]), Integer.parseInt(part[3])).setBlockData(Bukkit.createBlockData(part[4]), false);
            fixed++;
        }
        ConfigurationSection section = yaml.getConfigurationSection("displays");
        if (section != null) {
            for (String uuid : section.getKeys(false)) {
                Look look = look(section.getConfigurationSection(uuid));
                if (look == null) continue;
                pending.put(UUID.fromString(uuid), look);
                look.at().getChunk().load();
            }
        }
        Main.getINSTANCE().getLogger().warning("Bau-Test: nach Absturz " + fixed + " Bloecke zurueckgesetzt, " + pending.size() + " Displays folgen");
        loaded(Bukkit.getWorlds().stream().flatMap(world -> world.getEntities().stream()).toList());
        save();
    }

    /** Entities came in: displays from the crash file get their look back */
    static void loaded(List<? extends Entity> entities) {
        if (pending.isEmpty()) return;
        boolean changed = false;
        for (Entity entity : entities) {
            Look look = pending.remove(entity.getUniqueId());
            if (look == null || !(entity instanceof ItemDisplay display)) continue;
            back(display, look);
            changed = true;
        }
        if (changed) save();
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
        if (!display.getLocation().equals(look.at())) display.teleport(look.at(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    // Everything still to set back; our own displays vanish anyway
    private static void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        blocks.forEach((block, data) -> lines.add(block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ() + ";" + data.getAsString()));
        yaml.set("blocks", lines);
        displays.forEach((display, look) -> {
            if (display.isPersistent()) write(yaml.createSection("displays." + display.getUniqueId()), look);
        });
        pending.forEach((uuid, look) -> write(yaml.createSection("displays." + uuid), look));
        File file = file();
        try {
            if (lines.isEmpty() && !yaml.contains("displays")) {
                if (file.exists() && !file.delete()) Main.getINSTANCE().getLogger().warning("Bau-Test: " + FILE + " nicht geloescht");
                return;
            }
            yaml.save(file);
        } catch (IOException e) {
            Main.getINSTANCE().getLogger().warning("Bau-Test: " + FILE + " nicht gespeichert: " + e.getMessage());
        }
    }

    private static void write(ConfigurationSection section, Look look) {
        Location at = look.at();
        section.set("world", at.getWorld().getName());
        section.set("at", List.of(at.getX(), at.getY(), at.getZ(), (double) at.getYaw(), (double) at.getPitch()));
        section.set("item", look.item() == null || look.item().isEmpty() ? "" : Base64.getEncoder().encodeToString(look.item().serializeAsBytes()));
        Transformation t = look.transformation();
        Quaternionf left = t.getLeftRotation(), right = t.getRightRotation();
        section.set("transformation", List.of(t.getTranslation().x, t.getTranslation().y, t.getTranslation().z,
                left.x, left.y, left.z, left.w, t.getScale().x, t.getScale().y, t.getScale().z, right.x, right.y, right.z, right.w));
    }

    private static Look look(ConfigurationSection section) {
        World world = section == null ? null : Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        List<Double> at = section.getDoubleList("at");
        List<Float> t = section.getFloatList("transformation");
        if (at.size() != 5 || t.size() != 14) return null;
        String item = section.getString("item", "");
        return new Look(item.isEmpty() ? null : ItemStack.deserializeBytes(Base64.getDecoder().decode(item)),
                new Location(world, at.get(0), at.get(1), at.get(2), at.get(3).floatValue(), at.get(4).floatValue()),
                new Transformation(new Vector3f(t.get(0), t.get(1), t.get(2)), new Quaternionf(t.get(3), t.get(4), t.get(5), t.get(6)),
                        new Vector3f(t.get(7), t.get(8), t.get(9)), new Quaternionf(t.get(10), t.get(11), t.get(12), t.get(13))));
    }

    private static File file() {
        return new File(Main.getINSTANCE().getDataFolder(), FILE);
    }
}
