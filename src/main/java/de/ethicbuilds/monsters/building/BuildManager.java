package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.bautest.NexoBarriers;
import de.ethicbuilds.monsters.bautest.Restore;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.ArmorSet;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.map.decoration.HoloText;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import de.ethicbuilds.monsters.weapons.manager.WorkbenchManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/***
 * Parts of the map that players build with: rubble, barricades, base walls, break-outs, power (apomapregi).
 * Recognised by their Nexo id like the perk machines, maps without them stay as they are.
 */
public class BuildManager {
    public static final String RUBBLE = "schutt#", BARRICADE = "barrikade#";
    private static final String VEST_STAND = "weste#";
    // The dummy of each stand wears one set: westenstaender (bronze), _silber, _gold, _diamant, _netherit
    private static final List<String> VEST_SETS = List.of("", "_silber", "_gold", "_diamant", "_netherit");
    // The vest stand comes with the armor of every set (westenstaender, westenstaender_silber ...), break-outs in many shapes (ausbruch_<name>)
    private static final String RUBBLE_ID = "monsters_schutt", BARRICADE_ID = "monsters_barrikade_", BREAKOUT_ID = "monsters_ausbruch",
            VEST_STAND_ID = "monsters_westenstaender", WALL_ID = "monsters_mauer_";
    private static final int MAX_LEVEL = 3;
    private static final int REPAIR = 1;

    // Barricade and base wall work the same with their own values: health, upgrade and rebuild price in parts per level
    private record Kind(String id, String de, String en, int[] health, int[] upgrade, int[] rebuild, String[] names, String[] namesEn,
                        String menu, Material[] dust) {}

    private static final Kind BARRICADE_KIND = new Kind(BARRICADE_ID, "Barrikade", "barricade", new int[]{0, 120, 240, 400},
            new int[]{0, 0, 4, 8}, new int[]{0, 2, 4, 6}, new String[]{"", "Holz", "Wellblech", "Stahl"},
            new String[]{"", "Wood", "Corrugated", "Steel"}, "ausbau_barrikade_", new Material[]{null, Material.OAK_PLANKS, Material.IRON_BLOCK, Material.IRON_BLOCK});
    // User 31.10.: 3/6/10 parts, repair 1; 10.11.: stronger than the barricade
    private static final Kind WALL_KIND = new Kind(WALL_ID, "Mauer", "wall", new int[]{0, 200, 400, 700},
            new int[]{0, 0, 6, 10}, new int[]{0, 3, 6, 10}, new String[]{"", "Holz", "Stein", "Stahl"},
            new String[]{"", "Wood", "Stone", "Steel"}, "nexo:monsters_mauer_", new Material[]{null, Material.OAK_PLANKS, Material.STONE_BRICKS, Material.IRON_BLOCK});
    private static final double REACH = 1.6;
    private static final int HIT = 10;

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private MonsterManager monsterManager;
    @Inject private DecorationManager decorationManager;
    @Inject private WorkbenchManager workbenchManager;
    @Inject private PowerGrid powerGrid;
    @Inject private Nests nests;
    @Inject private Flares flares;
    @Inject private Ziplines ziplines;
    @Inject private Deployables deployables;
    @Inject private Elevators elevators;
    @Inject private UpgradeMenu upgradeMenu;
    @Inject private RollerDoors rollerDoors;
    @Inject private FieldBeds fieldBeds;

    private final List<Rubble> rubble = new ArrayList<>();
    private final List<Barricade> barricades = new ArrayList<>();
    private boolean used;

    public record Rubble(ItemDisplay display, Block block, List<Block> barriers, Interaction hitbox) {
        public Location center() {
            return block.getLocation().add(0.5, 0.5, 0.5);
        }
    }

    private static final class Barricade {
        private final ItemDisplay display;
        private final Block block;
        private final List<Block> barriers;
        private final Kind kind;
        private int level, health;

        private Barricade(ItemDisplay display, Block block, List<Block> barriers, Kind kind, int level) {
            this.display = display;
            this.block = block;
            this.barriers = barriers;
            this.kind = kind;
            this.level = level;
            this.health = kind.health()[level];
        }

        private int full() {
            return kind.health()[level];
        }

        private Location center() {
            return block.getLocation().add(0.5, 0.5, 0.5);
        }

        private boolean broken() {
            return health <= 0;
        }
    }

    private List<BuildPart> parts() {
        return List.of(powerGrid, nests, flares, ziplines, deployables, elevators, rollerDoors, fieldBeds);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(flares, plugin);
        plugin.getServer().getPluginManager().registerEvents(elevators, plugin);
        plugin.getServer().getPluginManager().registerEvents(upgradeMenu, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, powerGrid::animate, 2L, 2L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> parts().forEach(BuildPart::look), 2L, 2L);
    }

    /** Function test on the build server: forgets everything, it registers again in the next test */
    public void clear() {
        rubble.clear();
        barricades.clear();
        parts().forEach(BuildPart::clear);
        Breakouts.clear();
        used = false;
    }

    /** True when a map has building parts, only then monsters drop parts */
    public boolean isUsed() {
        return used;
    }

    public boolean register(ItemDisplay display, String id, Block block) {
        if (RUBBLE_ID.equals(id)) {
            List<Block> barriers = decorationManager.barriers(display, id);
            Interaction hitbox = decorationManager.hitbox(block, RUBBLE + rubble.size(), 1.6F, 2.0F);
            rubble.add(new Rubble(display, block, barriers, hitbox));
        } else if ((id.startsWith(BARRICADE_ID) || id.startsWith(WALL_ID)) && level(id) > 0) {
            // Wall decoration (mauer_grau_gerade ...) has no level, only mauer_1 to _3 are base walls
            Kind kind = id.startsWith(WALL_ID) ? WALL_KIND : BARRICADE_KIND;
            List<Block> barriers = decorationManager.barriers(display, id);
            decorationManager.hitbox(block, BARRICADE + barricades.size(), kind == WALL_KIND ? 1.2F : 1.4F, 2.0F);
            barricades.add(new Barricade(display, block, barriers, kind, level(id)));
        } else if (id.startsWith(BREAKOUT_ID)) {
            Breakouts.add(display, id);
        } else if (id.startsWith(VEST_STAND_ID)) {
            int set = Math.max(1, VEST_SETS.indexOf(id.substring(VEST_STAND_ID.length())) + 1);
            int price = ArmorSet.price(set - 1);
            decorationManager.createStation(block, VEST_STAND + set, ArmorSet.name(set, false) + "-Rüstung", ArmorSet.name(set, true) + " armor",
                    HoloText.price(price, false), HoloText.price(price, true), "Rechtsklick zum Anziehen", "Right-click to put on", 1.0F, 2.0F);
        } else if (parts().stream().noneMatch(part -> part.register(display, id, block))) {
            return false;
        }
        used = true;
        return true;
    }

    private static int level(String id) {
        try {
            int level = Integer.parseInt(id.substring(id.lastIndexOf('_') + 1));
            return level >= 1 && level <= MAX_LEVEL ? level : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Rubble rubble(String station) {
        int index = index(station, RUBBLE);
        return index >= 0 && index < rubble.size() ? rubble.get(index) : null;
    }

    /** Rubble at an opened door goes with it */
    public void clearRubbleNear(Location location, double distance) {
        for (Rubble piece : List.copyOf(rubble)) {
            if (piece.display().isValid() && piece.center().getWorld().equals(location.getWorld())
                    && piece.center().distanceSquared(location) <= distance * distance) clear(piece);
        }
    }

    public void clear(Rubble piece) {
        Location center = piece.center();
        center.getWorld().spawnParticle(Particle.BLOCK, center, 40, 0.8, 0.8, 0.8, Material.GRAVEL.createBlockData());
        center.getWorld().spawnParticle(Particle.POOF, center, 10, 0.6, 0.6, 0.6, 0.02);
        center.getWorld().playSound(center, Sound.BLOCK_GRAVEL_BREAK, 1.2F, 0.8F);
        piece.barriers().forEach(block -> {
            Restore.block(block);
            block.setType(Material.AIR, false);
        });
        NexoBarriers.open(piece.display());
        // On the build server the furniture only disappears for the test
        if (BauTest.on()) Restore.hide(piece.display());
        else piece.display().remove();
        decorationManager.removeHitbox(piece.hitbox());
    }

    /** Clicks on build parts, false when the station is not one of them */
    public boolean use(GamePlayer gamePlayer, String station) {
        int index = index(station, BARRICADE);
        if (index >= 0 && index < barricades.size()) {
            Barricade barricade = barricades.get(index);
            upgradeMenu.open(gamePlayer, () -> view(barricade), player -> useBarricade(player, barricade));
            return true;
        }
        int set = index(station, VEST_STAND);
        if (set > 0) {
            workbenchManager.buyArmor(gamePlayer, set);
            return true;
        }
        return parts().stream().anyMatch(part -> part.use(gamePlayer, station));
    }

    private static int index(String station, String prefix) {
        if (!station.startsWith(prefix)) return -1;
        try {
            return Integer.parseInt(station.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // What the upgrade menu shows for the barricade or wall
    private static UpgradeMenu.View view(Barricade barricade) {
        Kind type = barricade.kind;
        UpgradeMenu.Kind kind = barricade.broken() ? UpgradeMenu.Kind.REBUILD : barricade.health < barricade.full() ? UpgradeMenu.Kind.REPAIR
                : barricade.level < MAX_LEVEL ? UpgradeMenu.Kind.UPGRADE : UpgradeMenu.Kind.MAX;
        int cost = switch (kind) {
            case REBUILD -> type.rebuild()[barricade.level];
            case REPAIR -> REPAIR;
            case UPGRADE -> type.upgrade()[barricade.level + 1];
            case MAX -> 0;
        };
        int health = (int) Math.round(100.0 * Math.max(0, barricade.health) / barricade.full());
        return new UpgradeMenu.View(type.de(), type.en(), type.menu() + barricade.level, MAX_LEVEL, barricade.level, kind, health, cost,
                type.names(), type.namesEn());
    }

    // The button of the upgrade menu. Broken: build again, damaged: repair, whole: next level
    private void useBarricade(GamePlayer gamePlayer, Barricade barricade) {
        Player player = gamePlayer.getPlayer();
        Kind type = barricade.kind;
        if (barricade.broken()) {
            int price = type.rebuild()[barricade.level];
            if (!Parts.take(gamePlayer, price)) return;
            barricade.health = barricade.full();
            decorationManager.setModel(barricade.display, type.id() + barricade.level);
            barricade.barriers.forEach(block -> {
                Restore.block(block);
                block.setType(Material.BARRIER, false);
            });
            NexoBarriers.close(barricade.display);
            note(player, type.de() + " aufgebaut", capital(type.en()) + " rebuilt", price);
            sound(barricade, Sound.BLOCK_WOOD_PLACE);
        } else if (barricade.health < barricade.full()) {
            if (!Parts.take(gamePlayer, REPAIR)) return;
            barricade.health = barricade.full();
            note(player, type.de() + " repariert", capital(type.en()) + " repaired", REPAIR);
            sound(barricade, Sound.BLOCK_ANVIL_USE);
        } else if (barricade.level < MAX_LEVEL) {
            int price = type.upgrade()[barricade.level + 1];
            if (!Parts.take(gamePlayer, price)) return;
            barricade.level++;
            barricade.health = barricade.full();
            decorationManager.setModel(barricade.display, type.id() + barricade.level);
            note(player, type.de() + " aus " + type.names()[barricade.level], type.namesEn()[barricade.level] + " " + type.en(), price);
            sound(barricade, Sound.BLOCK_ANVIL_PLACE);
        } else {
            Overlay.small(player, Overlay.icon(Overlay.PARTS, Parts.COLOR, grey(type.de() + " ist voll ausgebaut")),
                    Overlay.icon(Overlay.PARTS, Parts.COLOR, grey(capital(type.en()) + " is fully upgraded")));
            Sounds.deny(player);
        }
    }

    private static void note(Player player, String de, String en, int price) {
        Overlay.small(player, Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>" + de + "</b> " + grey("· -") + Parts.price(price, false)),
                Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>" + en + "</b> " + grey("· -") + Parts.price(price, true)));
        Sounds.buy(player);
    }

    private static void sound(Barricade barricade, Sound sound) {
        barricade.center().getWorld().playSound(barricade.center(), sound, 1.0F, 1.0F);
    }

    private static String capital(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String grey(String text) {
        return "<color:" + Overlay.GREY + ">" + text + "</color>";
    }

    private void tick() {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE || PauseManager.isPaused()) return;
        for (Barricade barricade : barricades) {
            if (barricade.broken() || !barricade.display.isValid()) continue;
            int attackers = attackers(barricade.center());
            if (attackers == 0) continue;
            barricade.health -= attackers * HIT;
            Location center = barricade.center();
            center.getWorld().spawnParticle(Particle.BLOCK, center, 6 * attackers, 0.4, 0.5, 0.4, barricade.kind.dust()[barricade.level].createBlockData());
            center.getWorld().playSound(center, barricade.level == 1 ? Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR : Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8F, 1.0F);
            if (barricade.broken()) breakDown(barricade);
        }
        parts().forEach(BuildPart::tick);
    }

    private int attackers(Location center) {
        int count = 0;
        for (Monster monster : monsterManager.getMonsters().keySet()) {
            if (!monster.isValid() || !monster.getWorld().equals(center.getWorld())) continue;
            Location at = monster.getLocation();
            double dx = at.getX() - center.getX(), dz = at.getZ() - center.getZ();
            if (dx * dx + dz * dz <= REACH * REACH && Math.abs(at.getY() - center.getY()) < 2) count++;
        }
        return count;
    }

    private void breakDown(Barricade barricade) {
        Location center = barricade.center();
        center.getWorld().playSound(center, barricade.level == 1 ? Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR : Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.2F);
        center.getWorld().spawnParticle(Particle.POOF, center, 12, 0.5, 0.6, 0.5, 0.02);
        barricade.barriers.forEach(block -> {
            Restore.block(block);
            block.setType(Material.AIR, false);
        });
        NexoBarriers.open(barricade.display);
        Restore.display(barricade.display);
        barricade.display.setItemStack(null);
        Overlay.smallAll(Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>" + barricade.kind.de() + " zerstört</b> " + grey("· mit Bauteilen neu aufbauen")),
                Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>" + capital(barricade.kind.en()) + " destroyed</b> " + grey("· rebuild it with parts")));
    }
}
