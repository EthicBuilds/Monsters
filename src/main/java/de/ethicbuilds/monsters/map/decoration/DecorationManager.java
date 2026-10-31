package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.bautest.NexoLooks;
import de.ethicbuilds.monsters.bautest.NexoStandIns;
import de.ethicbuilds.monsters.bautest.Restore;
import de.ethicbuilds.monsters.building.BuildManager;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

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
    private static final String PARTS_CRATE = "monsters_bauteilkiste";
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
    @Inject private BuildManager buildManager;
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

    /** Our station the player looks at, for clicks that reach Nexo's own hitbox instead of ours */
    public String stationLookedAt(Player player, double reach) {
        Vector eye = player.getEyeLocation().toVector(), look = player.getEyeLocation().getDirection();
        // Exact first, then a little more generous for thin hitboxes
        for (double grow : new double[]{0, 0.4}) {
            String best = null;
            double bestDistance = reach;
            for (Map.Entry<UUID, String> entry : stations.entrySet()) {
                Entity hitbox = Bukkit.getEntity(entry.getKey());
                if (hitbox == null || !hitbox.getWorld().equals(player.getWorld())) continue;
                RayTraceResult hit = hitbox.getBoundingBox().expand(grow).rayTrace(eye, look, reach);
                if (hit == null) continue;
                double distance = hit.getHitPosition().distance(eye);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = entry.getValue();
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    public void registerLoaded(World world) {
        world.getEntities().forEach(this::register);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (BauTest.on() && !BauTest.session()) return;
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

    public void showSigns(Player player) {
        signs.removeIf(sign -> !sign.isValid());
        signs.forEach(sign -> show(player, sign));
    }

    public void hideSigns(Player player) {
        signs.forEach(sign -> {
            toggle(player, sign.de(), false);
            toggle(player, sign.en(), false);
        });
    }

    /** Unloaded furniture on the build server registers again when it comes back */
    public void forget(Entity entity) {
        registered.remove(entity.getUniqueId());
    }

    // German or English sign, workbench signs depending on whether it is unlocked
    private void show(Player player, Sign sign) {
        boolean shown = workbenchOpen.contains(sign) ? workbenchUnlocked : !(workbenchUnlocked && workbenchLocked.contains(sign));
        if (BauTest.on() && !BauTest.active(player)) shown = false;
        boolean english = Lang.english(player);
        toggle(player, sign.de(), shown && !english);
        toggle(player, sign.en(), shown && english);
    }

    private void toggle(Player player, TextDisplay holo, boolean visible) {
        if (visible) player.showEntity(plugin, holo);
        else player.hideEntity(plugin, holo);
    }

    public void register(Entity entity) {
        if (!entity.isValid() || !isFurniture(entity) || !registered.add(entity.getUniqueId())) return;

        String id = entity.getPersistentDataContainer().get(FURNITURE, PersistentDataType.STRING);
        Block block = entity.getLocation().getBlock();
        keepLoaded(block);

        // Nexo sends the item only via packets; on the build server Nexo is there itself
        if (!BauTest.on() && entity instanceof ItemDisplay display && (display.getItemStack() == null || display.getItemStack().getType().isAir())) {
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
            String station = wrecks.register(block, entity instanceof ItemDisplay display ? barriers(display, id) : List.of());
            Location center = wrecks.center(station);
            Interaction hitbox = block.getWorld().spawn(center, Interaction.class, interaction -> {
                interaction.setInteractionWidth(WRECK_WIDTH);
                interaction.setInteractionHeight(WRECK_HEIGHT);
                interaction.setPersistent(!BauTest.on());
            });
            BauTest.limit(hitbox);
            stations.put(hitbox.getUniqueId(), station);
        } else if (SOLID.contains(id) && entity instanceof ItemDisplay display) {
            crateManager.register(display, block, false);
        } else if (PARTS_CRATE.equals(id) && entity instanceof ItemDisplay display) {
            crateManager.register(display, block, true);
        } else if (entity instanceof ItemDisplay display && buildManager.register(display, id, block)) {
            return;
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
    /** The item that shows a model: the real Nexo item where Nexo runs, else ours from the Monsters pack */
    public ItemStack modelItem(String id) {
        if (NexoStandIns.active()) return NexoStandIns.item(id);
        ItemStack item = new ItemStack(Material.PAPER);
        GameItems.setModel(item, id);
        return item;
    }

    /** Nexo's scale and transform for an id, for displays we spawn ourselves */
    public void look(ItemDisplay display, String id) {
        look().apply(display, id);
    }

    public void setModel(ItemDisplay display, String id) {
        Restore.display(display);
        // Our stand-in for Nexo furniture carries the real Nexo item
        if (NexoStandIns.is(display)) {
            display.setItemStack(NexoStandIns.item(id));
            look().apply(display, id);
            return;
        }
        ItemStack item = new ItemStack(Material.PAPER);
        GameItems.setModel(item, id);
        display.setItemStack(item);
        look().apply(display, id);
        NexoLooks.show(display, id);
    }

    /** The barrier blocks Nexo placed for this furniture: the turn is found by trying which one hits barriers */
    public List<Block> barriers(Block base, String id) {
        List<int[]> offsets = look().barriers(id);
        if (offsets.isEmpty()) return List.of();
        // Nexo mirrors x, so also the mirrored turns are tried
        for (int turn = 0; turn < 8; turn++) {
            List<Block> blocks = new ArrayList<>();
            for (int[] offset : offsets) {
                int x = turn < 4 ? offset[0] : -offset[0], z = offset[2];
                for (int i = 0; i < turn % 4; i++) {
                    int old = x;
                    x = -z;
                    z = old;
                }
                blocks.add(base.getRelative(x, offset[1], z));
            }
            if (blocks.stream().allMatch(block -> block.getType() == Material.BARRIER)) return blocks;
        }
        return List.of();
    }

    /** Where Nexo puts the barriers of this id at this yaw (its own turn), Nexo itself only sends them to the players */
    public List<Block> barriers(Block base, String id, float yaw) {
        List<Block> blocks = new ArrayList<>();
        for (int[] offset : look().barriers(id)) blocks.add(relative(base, offset[0], offset[1], offset[2], yaw));
        return blocks;
    }

    /** A Nexo offset (x mirrored) of furniture with this yaw, turned like Nexo turns it */
    public static Block relative(Block base, int x, int y, int z, float yaw) {
        double angle = Math.toRadians(yaw < 0 ? yaw + 360 : yaw);
        return base.getRelative((int) Math.rint(x * Math.cos(angle) + z * Math.sin(angle)), y, (int) Math.rint(x * Math.sin(angle) - z * Math.cos(angle)));
    }

    /** Real barrier blocks if the world has them, else where Nexo has them */
    public List<Block> barriers(ItemDisplay display, String id) {
        Block base = display.getLocation().getBlock();
        List<Block> blocks = barriers(base, id);
        return blocks.isEmpty() ? barriers(base, id, display.getLocation().getYaw()) : blocks;
    }

    /** True when Nexo knows this furniture id */
    public boolean known(String id) {
        return look().has(id);
    }

    /** Clickable area without a sign, the station name goes to the click handling */
    public Interaction hitbox(Block block, String station, float width, float height) {
        Interaction hitbox = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), Interaction.class, interaction -> {
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setPersistent(!BauTest.on());
        });
        BauTest.limit(hitbox);
        stations.put(hitbox.getUniqueId(), station);
        return hitbox;
    }

    /** Sign without a hitbox, e.g. over a spot that is built later */
    public Sign sign(Block block, String name, String nameEn, String info, String infoEn, String hint, String hintEn, double lift) {
        return sign(block, null, name, nameEn, info, infoEn, hint, hintEn, lift);
    }

    /** Sign with a holo symbol in front of the title (HoloText) */
    public Sign sign(Block block, Character icon, String name, String nameEn, String info, String infoEn, String hint, String hintEn, double lift) {
        Sign sign = signAbove(block, icon, false, name, nameEn, info, infoEn, hint, hintEn, lift);
        Bukkit.getOnlinePlayers().forEach(player -> show(player, sign));
        return sign;
    }

    public void removeSign(Sign sign) {
        signs.remove(sign);
        sign.remove();
    }

    /** Function test on the build server: removes our hitboxes and signs, everything registers again in the next test */
    public void clearAll() {
        stations.keySet().forEach(uuid -> {
            Entity hitbox = Bukkit.getEntity(uuid);
            if (hitbox != null) hitbox.remove();
        });
        stations.clear();
        signs.forEach(Sign::remove);
        signs.clear();
        workbenchLocked.clear();
        workbenchOpen.clear();
        workbenchUnlocked = false;
        registered.clear();
    }

    // The parts of the round stay loaded while it runs: far away from the players they would lose their signs, hitboxes and state.
    // Not on the build server, its world stays as it is.
    private void keepLoaded(Block block) {
        if (BauTest.on()) return;
        block.getChunk().addPluginChunkTicket(plugin);
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
        keepLoaded(block);
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
        keepLoaded(block);
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

    public static String price(int coins, boolean english) {
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

    public Sign createStation(Block block, String station, String name, String nameEn, String prices, String pricesEn,
                              String hint, String hintEn, float width, float height) {
        return createStation(block, station, name, nameEn, prices, pricesEn, hint, hintEn, width, height, SIGN);
    }

    private Sign createStation(Block block, String station, String name, String nameEn, String prices, String pricesEn,
                               String hint, String hintEn, float width, float height, double lift) {
        Interaction hitbox = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), Interaction.class, interaction -> {
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setPersistent(!BauTest.on());
        });
        BauTest.limit(hitbox);
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
            // Kept with its chunk in the round world (a fresh copy every round), never in the build world
            holo.setPersistent(!BauTest.on());
            if (BauTest.on()) holo.setVisibleByDefault(false);
        });
    }
}
