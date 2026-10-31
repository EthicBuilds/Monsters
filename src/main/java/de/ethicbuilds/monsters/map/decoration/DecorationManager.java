package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.elements.Door;
import de.ethicbuilds.monsters.perk.Perk;
import de.ethicbuilds.monsters.weapons.Weapon;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.weapons.manager.WorkbenchManager;
import de.ethicbuilds.monsters.weapons.manager.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    // Clickable area over the whole car body
    private static final float WRECK_WIDTH = 3.2F, WRECK_HEIGHT = 1.8F;
    // Sign height above the block, the perk machines are taller
    private static final double SIGN = 1.6, PERK_SIGN = 2.5, DOOR_SIGN = 1.7;

    @Inject private Main plugin;
    @Inject private WeaponManager weaponManager;
    @Inject private CrateManager crateManager;
    @Inject private Wrecks wrecks;
    @Inject private Safes safes;
    @Inject private EscapeCar escapeCar;

    private final Set<UUID> registered = new HashSet<>();
    private final Map<UUID, String> stations = new HashMap<>();
    private final Map<Door, Sign> doorSigns = new HashMap<>();
    private final List<Sign> signs = new ArrayList<>();
    private final List<Sign> workbenchLocked = new ArrayList<>();
    private final List<Sign> workbenchOpen = new ArrayList<>();
    private boolean workbenchUnlocked;

    /** German and English sign; it keeps its text, so a sign whose chunk was away gets the latest one back */
    public static final class Sign {
        private TextDisplay de, en;
        private final UUID deId, enId;
        private Component deText, enText;
        private boolean removed;

        Sign(TextDisplay de, TextDisplay en) {
            this.de = de;
            this.en = en;
            this.deId = de.getUniqueId();
            this.enId = en.getUniqueId();
        }

        public TextDisplay de() {
            return de = fresh(de, deId);
        }

        public TextDisplay en() {
            return en = fresh(en, enId);
        }

        void text(Component german, Component english) {
            deText = german;
            enText = english;
            de().text(german);
            en().text(english);
        }

        // Its chunk came back: the new entity with the latest text
        void loaded(TextDisplay display) {
            if (display.getUniqueId().equals(deId)) {
                de = display;
                if (deText != null) de.text(deText);
            } else if (display.getUniqueId().equals(enId)) {
                en = display;
                if (enText != null) en.text(enText);
            }
        }

        void remove() {
            removed = true;
            de().remove();
            en().remove();
        }

        boolean isValid() {
            return !removed;
        }

        private static TextDisplay fresh(TextDisplay display, UUID id) {
            if (display.isValid()) return display;
            return Bukkit.getEntity(id) instanceof TextDisplay loaded ? loaded : display;
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
        for (Entity entity : entities) {
            if (entity instanceof TextDisplay display) signs.forEach(sign -> sign.loaded(display));
        }
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

        if (entity instanceof ItemDisplay display) Sirens.register(display, id);
        if (Safes.SAFE_ID.equals(id) && entity instanceof ItemDisplay display) {
            safes.register(display, block);
        } else if (EscapeCar.BROKEN_ID.equals(id) && entity instanceof ItemDisplay display) {
            escapeCar.register(display, block);
        } else if (Wrecks.isWreck(id)) {
            String station = wrecks.register(block);
            Location center = wrecks.center(station);
            Interaction hitbox = block.getWorld().spawn(center, Interaction.class, interaction -> {
                interaction.setInteractionWidth(WRECK_WIDTH);
                interaction.setInteractionHeight(WRECK_HEIGHT);
                interaction.setPersistent(true);
            });
            stations.put(hitbox.getUniqueId(), station);
        } else if (SOLID.contains(id) && entity instanceof ItemDisplay display) {
            crateManager.register(display, block);
        } else if (WEAPON_CRATES.containsKey(id)) {
            createWeaponStation(block, WEAPON_CRATES.get(id));
        } else if (WORKBENCH_STATION.equals(id)) {
            workbenchLocked.add(createStation(block, WORKBENCH_STATION, "Werkbank", "Workbench",
                    workbenchPrices(WorkbenchManager.UNLOCK_PRICE, false), workbenchPrices(WorkbenchManager.UNLOCK_PRICE, true),
                    "Rechtsklick zum Kaufen", "Right-click to buy", 2.6F, 1.4F));
            Sign open = signAbove(block, HoloText.PARTS, false, "Werkbank", "Workbench", HoloText.info("Waffen verbessern"),
                    HoloText.info("Upgrade weapons"), "Rechtsklick zum Öffnen", "Right-click to open", SIGN);
            open.de().setVisibleByDefault(false);
            workbenchOpen.add(open);
            Bukkit.getOnlinePlayers().forEach(player -> show(player, open));
        } else if (Perk.byFurniture(id) != null) {
            Perk perk = Perk.byFurniture(id);
            // The machines are 2 blocks high, the sign floats above them
            createStation(block, perk.getFurniture(), perk.getNameDe(), perk.getNameEn(),
                    price(perk.getPrice(), false) + "\n" + HoloText.info(perk.getEffectDe()), price(perk.getPrice(), true) + "\n" + HoloText.info(perk.getEffectEn()),
                    "Rechtsklick zum Kaufen", "Right-click to buy", 1.1F, 2.0F, PERK_SIGN);
        } else if (AMMO_CRATE.equals(id)) {
            createStation(block, AMMO_STATION, "Munition", "Ammo", price(WaveListener.AMMO_PRICE, false) + "\n" + HoloText.info("für die Waffe in der Hand"),
                    price(WaveListener.AMMO_PRICE, true) + "\n" + HoloText.info("for the weapon in your hand"));
        }
    }

    /** Shows another Nexo model on the furniture, with the look Nexo gives it */
    public void setModel(ItemDisplay display, String id) {
        ItemStack item = new ItemStack(Material.PAPER);
        GameItems.setModel(item, id);
        display.setItemStack(item);
        look().apply(display, id);
    }

    /** Clickable area without a sign, the station name goes to the click handling */
    public Interaction hitbox(Block block, String station, float width, float height) {
        Interaction hitbox = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), Interaction.class, interaction -> {
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setPersistent(true);
        });
        stations.put(hitbox.getUniqueId(), station);
        return hitbox;
    }

    public void removeHitbox(Interaction hitbox) {
        stations.remove(hitbox.getUniqueId());
        // The object may be from before its chunk was away
        Entity current = hitbox.isValid() ? hitbox : Bukkit.getEntity(hitbox.getUniqueId());
        if (current != null) current.remove();
    }

    /** Sign of a door of the older maps: what is still missing, it is broken open with the crowbar */
    public void doorSign(Door door, int remaining) {
        Sign old = doorSigns.get(door);
        if (old != null) {
            old.text(MiniMessage.miniMessage().deserialize(doorText(door, remaining, false)), MiniMessage.miniMessage().deserialize(doorText(door, remaining, true)));
            return;
        }
        Block block = door.getHoloLocation().getBlock();
        TextDisplay en = holo(block, doorText(door, remaining, true), DOOR_SIGN);
        en.setVisibleByDefault(false);
        Sign sign = new Sign(holo(block, doorText(door, remaining, false), DOOR_SIGN), en);
        signs.add(sign);
        doorSigns.put(door, sign);
        Bukkit.getOnlinePlayers().forEach(player -> show(player, sign));
    }

    private static String doorText(Door door, int remaining, boolean english) {
        return compose(HoloText.LOCK, false, door.getName(), price(remaining, english) + "\n" + HoloText.info(english ? "with the crowbar" : "mit der Brechstange"),
                english ? "Right-click to break open" : "Rechtsklick zum Aufbrechen");
    }

    public void removeDoorSign(Door door) {
        Sign sign = doorSigns.remove(door);
        if (sign == null) return;
        signs.remove(sign);
        sign.remove();
    }

    /** Weapon point of the older maps: the first spot gets the sign, every spot can be clicked */
    public void weaponPoint(Block block, String weaponName, boolean withSign) {
        if (withSign) createWeaponStation(block, weaponName);
        else hitbox(block, weaponName, 1.2F, 1.1F);
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
                price(weapon.getPrice(), false), price(weapon.getPrice(), true));
    }

    private static String price(int coins, boolean english) {
        return HoloText.price(coins, english);
    }

    /** Coins put together so far: the sign shows what is still missing */
    public void setWorkbenchPrice(int remaining) {
        Component text = MiniMessage.miniMessage().deserialize(compose(HoloText.PARTS, false, "Werkbank", workbenchPrices(remaining, false), "Rechtsklick zum Kaufen"));
        Component textEn = MiniMessage.miniMessage().deserialize(compose(HoloText.PARTS, false, "Workbench", workbenchPrices(remaining, true), "Right-click to buy"));
        workbenchLocked.removeIf(sign -> !sign.isValid());
        workbenchLocked.forEach(sign -> sign.text(text, textEn));
    }

    private static String workbenchPrices(int coins, boolean english) {
        return price(coins, english) + "\n" + HoloText.wave(WorkbenchManager.UNLOCK_WAVE, english)
                + "\n" + HoloText.info(english ? "Upgrade weapons" : "Waffen verbessern");
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
            interaction.setPersistent(true);
        });
        stations.put(hitbox.getUniqueId(), station);
        Perk perk = Perk.byFurniture(station);
        Character icon = WORKBENCH_STATION.equals(station) ? Character.valueOf(HoloText.PARTS) : perk != null ? perkIcon(perk) : null;
        Sign sign = signAbove(block, icon, perk != null, name, nameEn, prices, pricesEn, hint, hintEn, lift);
        Bukkit.getOnlinePlayers().forEach(player -> show(player, sign));
        return sign;
    }

    // The perk symbols of the holo font: health, reload, speed, revive
    private static Character perkIcon(Perk perk) {
        String id = perk.getFurniture();
        if (id.endsWith("leben")) return '\uE20B';
        if (id.endsWith("nachladen")) return '\uE20C';
        if (id.endsWith("tempo")) return '\uE20D';
        if (id.endsWith("wiederbeleben")) return '\uE20E';
        return null;
    }

    // German sign visible by default, the English one only for English players
    private Sign signAbove(Block block, Character icon, boolean colored, String name, String nameEn, String prices, String pricesEn,
                           String hint, String hintEn, double lift) {
        TextDisplay en = holo(block, compose(icon, colored, nameEn, pricesEn, hintEn), lift);
        en.setVisibleByDefault(false);
        Sign sign = new Sign(holo(block, compose(icon, colored, name, prices, hint), lift), en);
        signs.add(sign);
        return sign;
    }

    // Title, divider, the lines of the sign and the small hint (DESIGN 31.10.)
    private static String compose(Character icon, boolean colored, String name, String lines, String hint) {
        return HoloText.title(icon, colored, name) + "\n" + HoloText.divider() + (lines.isEmpty() ? "" : "\n" + lines) + "\n" + HoloText.hint(hint);
    }

    private TextDisplay holo(Block block, String text, double lift) {
        Component component = MiniMessage.miniMessage().deserialize(text);
        Location base = block.getLocation().add(0.5, 0, 0.5);
        return block.getWorld().spawn(base.clone().add(0, lift, 0), TextDisplay.class, holo -> {
            holo.text(component);
            HoloText.style(holo);
            holo.setPersistent(true);
        });
    }
}
