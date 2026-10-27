package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.weapons.Weapon;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.weapons.manager.WorkbenchManager;
import de.ethicbuilds.monsters.weapons.manager.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/***
 * Nexo furniture placed in the map (the game servers run without Nexo)
 */
public class DecorationManager implements Listener {
    public static final NamespacedKey FURNITURE = new NamespacedKey("nexo", "furniture");
    public static final String AMMO_STATION = "munition";
    public static final String WORKBENCH_STATION = "werkbank";

    private static final Map<String, String> WEAPON_CRATES = Map.of(
            "waffenkiste_schrot_1", "schrot",
            "waffenkiste_sniper_1", "sniper",
            "waffenkiste_sturm_1", "sturmgewehr"
    );
    private static final String AMMO_CRATE = "munitionskiste_v1";
    private static final Set<String> SOLID = Set.of(
            "fass_1", "fass_2", "fass_3", "kiste_leer_2", "kiste_leer_3", "kiste_voll_1", "kiste_voll_2"
    );

    @Inject private Main plugin;
    @Inject private WeaponManager weaponManager;
    @Inject private CrateManager crateManager;

    private final Set<UUID> registered = new HashSet<>();
    private final Map<UUID, String> stations = new HashMap<>();

    public static boolean isFurniture(Entity entity) {
        return entity.getPersistentDataContainer().has(FURNITURE, PersistentDataType.STRING);
    }

    public String getStation(Entity entity) {
        return stations.get(entity.getUniqueId());
    }

    public void registerLoaded(World world) {
        world.getEntities().forEach(this::register);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        var entities = event.getEntities();
        Bukkit.getScheduler().runTask(plugin, () -> entities.forEach(this::register));
    }

    private void register(Entity entity) {
        if (!entity.isValid() || !isFurniture(entity) || !registered.add(entity.getUniqueId())) return;

        String id = entity.getPersistentDataContainer().get(FURNITURE, PersistentDataType.STRING);
        Block block = entity.getLocation().getBlock();

        // Nexo sends the item only via packets
        if (entity instanceof ItemDisplay display && (display.getItemStack() == null || display.getItemStack().getType().isAir())) {
            ItemStack item = new ItemStack(Material.PAPER);
            GameItems.setModel(item, id);
            display.setItemStack(item);
        }

        if (SOLID.contains(id) && entity instanceof ItemDisplay display) {
            crateManager.register(display, block);
        } else if (WEAPON_CRATES.containsKey(id)) {
            createWeaponStation(block, WEAPON_CRATES.get(id));
        } else if (WORKBENCH_STATION.equals(id)) {
            createStation(block, WORKBENCH_STATION, "Werkbank", "<#9AA6B8>ab Welle " + WorkbenchManager.UNLOCK_WAVE + " · " + price(WorkbenchManager.UNLOCK_PRICE)
                    + "\n<#9AA6B8>Waffen verbessern", 2.6F, 1.4F);
        } else if (AMMO_CRATE.equals(id)) {
            createStation(block, AMMO_STATION, "Munition", price(WaveListener.AMMO_PRICE) + "\n<#9AA6B8>für die Waffe in der Hand");
        }
    }

    private void createWeaponStation(Block block, String weaponName) {
        Weapon weapon;
        try {
            weapon = weaponManager.getWeaponNames().get(weaponName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        createStation(block, weaponName, ChatColor.stripColor(weapon.getName()), price(weapon.getPrice()));
    }

    private static String price(int coins) {
        return "<white><font:monsters:hud>\uE017</font></white> <#F9A11E>" + coins + " <#9AA6B8>Coins";
    }

    private void createStation(Block block, String station, String name, String prices) {
        createStation(block, station, name, prices, 1.2F, 1.1F);
    }

    private void createStation(Block block, String station, String name, String prices, float width, float height) {
        Component text = MiniMessage.miniMessage().deserialize("<#F2F5F8><name>\n" + prices + "\n<#9AA6B8>Rechtsklick zum Kaufen",
                Placeholder.unparsed("name", name));
        Location base = block.getLocation().add(0.5, 0, 0.5);
        World world = block.getWorld();

        Interaction hitbox = world.spawn(base, Interaction.class, interaction -> {
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setPersistent(false);
        });
        stations.put(hitbox.getUniqueId(), station);

        world.spawn(base.clone().add(0, 1.6, 0), TextDisplay.class, holo -> {
            holo.text(text);
            holo.setBillboard(Display.Billboard.CENTER);
            holo.setBackgroundColor(Color.fromARGB(0xB3, 0x14, 0x10, 0x0C));
            holo.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9F), new AxisAngle4f()));
            holo.setPersistent(false);
        });
    }
}
