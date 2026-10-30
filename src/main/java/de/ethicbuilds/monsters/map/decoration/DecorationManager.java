package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.perk.Perk;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/***
 * Nexo furniture placed in the map (the game servers run without Nexo).
 * Every sign exists in German and English, each player only sees the one in their language.
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

    // Sign height above the block, the perk machines are taller
    private static final double SIGN = 1.6, PERK_SIGN = 2.5;

    @Inject private Main plugin;
    @Inject private WeaponManager weaponManager;
    @Inject private CrateManager crateManager;

    private final Set<UUID> registered = new HashSet<>();
    private final Map<UUID, String> stations = new HashMap<>();
    private final List<Sign> signs = new ArrayList<>();
    private final List<Sign> workbenchLocked = new ArrayList<>();
    private final List<Sign> workbenchOpen = new ArrayList<>();
    private boolean workbenchUnlocked;

    private record Sign(TextDisplay de, TextDisplay en) {
        boolean isValid() {
            return de.isValid() && en.isValid();
        }
    }

    private FurnitureLook look;

    private FurnitureLook look() {
        if (look == null) {
            look = new FurnitureLook(new File(plugin.getConfig().getString("nexo-items", "../monsters-bau/plugins/Nexo/items")), plugin.getLogger());
        }
        return look;
    }

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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) showSigns(player);
        }, 5L);
    }

    private void showSigns(Player player) {
        signs.removeIf(sign -> !sign.isValid());
        signs.forEach(sign -> show(player, sign));
    }

    // German or English sign, workbench signs depending on whether it is unlocked
    private void show(Player player, Sign sign) {
        boolean shown = workbenchOpen.contains(sign) ? workbenchUnlocked : !(workbenchUnlocked && workbenchLocked.contains(sign));
        boolean english = Lang.english(player);
        toggle(player, sign.de(), shown && !english);
        toggle(player, sign.en(), shown && english);
    }

    private void toggle(Player player, TextDisplay holo, boolean visible) {
        if (visible) player.showEntity(plugin, holo);
        else player.hideEntity(plugin, holo);
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
            look().apply(display, id);
        }

        if (SOLID.contains(id) && entity instanceof ItemDisplay display) {
            crateManager.register(display, block);
        } else if (WEAPON_CRATES.containsKey(id)) {
            createWeaponStation(block, WEAPON_CRATES.get(id));
        } else if (WORKBENCH_STATION.equals(id)) {
            workbenchLocked.add(createStation(block, WORKBENCH_STATION, "Werkbank", "Workbench",
                    workbenchPrices(WorkbenchManager.UNLOCK_PRICE, false), workbenchPrices(WorkbenchManager.UNLOCK_PRICE, true),
                    "Rechtsklick zum Kaufen", "Right-click to buy", 2.6F, 1.4F));
            Sign open = signAbove(block, "Werkbank", "Workbench", "<#9AA6B8>Waffen verbessern", "<#9AA6B8>Upgrade weapons",
                    "Rechtsklick zum Öffnen", "Right-click to open", SIGN);
            open.de().setVisibleByDefault(false);
            workbenchOpen.add(open);
            Bukkit.getOnlinePlayers().forEach(player -> show(player, open));
        } else if (Perk.byFurniture(id) != null) {
            Perk perk = Perk.byFurniture(id);
            // The machines are 2 blocks high, the sign floats above them
            createStation(block, perk.getFurniture(), perk.getNameDe(), perk.getNameEn(),
                    price(perk.getPrice()) + "\n<#9AA6B8>" + perk.getEffectDe(), price(perk.getPrice()) + "\n<#9AA6B8>" + perk.getEffectEn(),
                    "Rechtsklick zum Kaufen", "Right-click to buy", 1.1F, 2.0F, PERK_SIGN);
        } else if (AMMO_CRATE.equals(id)) {
            createStation(block, AMMO_STATION, "Munition", "Ammo", price(WaveListener.AMMO_PRICE) + "\n<#9AA6B8>für die Waffe in der Hand",
                    price(WaveListener.AMMO_PRICE) + "\n<#9AA6B8>for the weapon in your hand");
        }
    }

    private void createWeaponStation(Block block, String weaponName) {
        Weapon weapon;
        try {
            weapon = weaponManager.getWeaponNames().get(weaponName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        String nameEn = weapon.getNameEn() != null ? weapon.getNameEn() : weapon.getName();
        createStation(block, weaponName, ChatColor.stripColor(weapon.getName()), ChatColor.stripColor(nameEn),
                price(weapon.getPrice()), price(weapon.getPrice()));
    }

    private static String price(int coins) {
        return "<white><font:monsters:hud>\uE017</font></white> <#F9A11E>" + coins + " <#9AA6B8>Coins";
    }

    /** Coins put together so far: the sign shows what is still missing */
    public void setWorkbenchPrice(int remaining) {
        Component text = MiniMessage.miniMessage().deserialize("<#F2F5F8>Werkbank\n" + workbenchPrices(remaining, false) + "\n<#9AA6B8>Rechtsklick zum Kaufen");
        Component textEn = MiniMessage.miniMessage().deserialize("<#F2F5F8>Workbench\n" + workbenchPrices(remaining, true) + "\n<#9AA6B8>Right-click to buy");
        workbenchLocked.removeIf(sign -> !sign.isValid());
        workbenchLocked.forEach(sign -> {
            sign.de().text(text);
            sign.en().text(textEn);
        });
    }

    private static String workbenchPrices(int coins, boolean english) {
        return english
                ? "<#9AA6B8>from wave " + WorkbenchManager.UNLOCK_WAVE + " · " + price(coins) + "\n<#9AA6B8>Upgrade weapons"
                : "<#9AA6B8>ab Welle " + WorkbenchManager.UNLOCK_WAVE + " · " + price(coins) + "\n<#9AA6B8>Waffen verbessern";
    }

    /** The workbench is unlocked: from now on the sign without the price */
    public void showWorkbenchOpen(Player player) {
        workbenchUnlocked = true;
        workbenchLocked.removeIf(sign -> !sign.isValid());
        workbenchOpen.removeIf(sign -> !sign.isValid());
        workbenchLocked.forEach(sign -> show(player, sign));
        workbenchOpen.forEach(sign -> show(player, sign));
    }

    private void createStation(Block block, String station, String name, String nameEn, String prices, String pricesEn) {
        createStation(block, station, name, nameEn, prices, pricesEn, "Rechtsklick zum Kaufen", "Right-click to buy", 1.2F, 1.1F);
    }

    private Sign createStation(Block block, String station, String name, String nameEn, String prices, String pricesEn,
                               String hint, String hintEn, float width, float height) {
        return createStation(block, station, name, nameEn, prices, pricesEn, hint, hintEn, width, height, SIGN);
    }

    private Sign createStation(Block block, String station, String name, String nameEn, String prices, String pricesEn,
                               String hint, String hintEn, float width, float height, double lift) {
        Interaction hitbox = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), Interaction.class, interaction -> {
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setPersistent(false);
        });
        stations.put(hitbox.getUniqueId(), station);
        Sign sign = signAbove(block, name, nameEn, prices, pricesEn, hint, hintEn, lift);
        Bukkit.getOnlinePlayers().forEach(player -> show(player, sign));
        return sign;
    }

    // German sign visible by default, the English one only for English players
    private Sign signAbove(Block block, String name, String nameEn, String prices, String pricesEn, String hint, String hintEn, double lift) {
        TextDisplay en = holo(block, nameEn, pricesEn, hintEn, lift);
        en.setVisibleByDefault(false);
        Sign sign = new Sign(holo(block, name, prices, hint, lift), en);
        signs.add(sign);
        return sign;
    }

    private TextDisplay holo(Block block, String name, String prices, String hint, double lift) {
        Component text = MiniMessage.miniMessage().deserialize("<#F2F5F8><name>\n" + prices + "\n<#9AA6B8><hint>",
                Placeholder.unparsed("name", name), Placeholder.unparsed("hint", hint));
        Location base = block.getLocation().add(0.5, 0, 0.5);
        return block.getWorld().spawn(base.clone().add(0, lift, 0), TextDisplay.class, holo -> {
            holo.text(text);
            holo.setBillboard(Display.Billboard.CENTER);
            holo.setBackgroundColor(Color.fromARGB(0xB3, 0x14, 0x10, 0x0C));
            holo.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9F), new AxisAngle4f()));
            holo.setPersistent(false);
        });
    }
}
