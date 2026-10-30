package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
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
 * Parts of the map that players build with: rubble, barricades, break-outs, power (apomapregi).
 * Recognised by their Nexo id like the perk machines, maps without them stay as they are.
 */
public class BuildManager {
    public static final String RUBBLE = "schutt#", BARRICADE = "barrikade#";
    private static final String VEST_STAND = "weste";
    private static final String RUBBLE_ID = "monsters_schutt", BARRICADE_ID = "monsters_barrikade_", BREAKOUT_ID = "monsters_ausbruch",
            VEST_STAND_ID = "monsters_westenstaender";
    private static final int MAX_LEVEL = 3;
    // Health per level (wood, corrugated iron, steel), upgrade price in parts for level 2 and 3
    private static final int[] HEALTH = {0, 120, 240, 400};
    private static final int[] UPGRADE = {0, 0, 4, 8};
    private static final int REPAIR = 1;
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
        private int level, health;

        private Barricade(ItemDisplay display, Block block, List<Block> barriers, int level) {
            this.display = display;
            this.block = block;
            this.barriers = barriers;
            this.level = level;
            this.health = HEALTH[level];
        }

        private Location center() {
            return block.getLocation().add(0.5, 0.5, 0.5);
        }

        private boolean broken() {
            return health <= 0;
        }
    }

    private List<BuildPart> parts() {
        return List.of(powerGrid, nests, flares, ziplines, deployables, elevators);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(flares, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** True when a map has building parts, only then monsters drop parts */
    public boolean isUsed() {
        return used;
    }

    public boolean register(ItemDisplay display, String id, Block block) {
        if (RUBBLE_ID.equals(id)) {
            List<Block> barriers = decorationManager.barriers(block, id);
            Interaction hitbox = decorationManager.hitbox(block, RUBBLE + rubble.size(), 1.6F, 2.0F);
            rubble.add(new Rubble(display, block, barriers, hitbox));
        } else if (id.startsWith(BARRICADE_ID) && level(id) > 0) {
            List<Block> barriers = decorationManager.barriers(block, id);
            decorationManager.hitbox(block, BARRICADE + barricades.size(), 1.4F, 2.0F);
            barricades.add(new Barricade(display, block, barriers, level(id)));
        } else if (BREAKOUT_ID.equals(id)) {
            Breakouts.add(display);
        } else if (VEST_STAND_ID.equals(id)) {
            decorationManager.createStation(block, VEST_STAND, "Westen", "Vests", "<#9AA6B8>Rüstung verbessern", "<#9AA6B8>Upgrade armor",
                    "Rechtsklick zum Kaufen", "Right-click to buy", 1.0F, 2.0F);
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
        piece.barriers().forEach(block -> block.setType(Material.AIR, false));
        piece.display().remove();
        decorationManager.removeHitbox(piece.hitbox());
    }

    /** Clicks on build parts, false when the station is not one of them */
    public boolean use(GamePlayer gamePlayer, String station) {
        int index = index(station, BARRICADE);
        if (index >= 0 && index < barricades.size()) {
            useBarricade(gamePlayer, barricades.get(index));
            return true;
        }
        if (VEST_STAND.equals(station)) {
            workbenchManager.upgradeArmor(gamePlayer);
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

    // Broken: build again, damaged: repair, whole: next level
    private void useBarricade(GamePlayer gamePlayer, Barricade barricade) {
        Player player = gamePlayer.getPlayer();
        if (barricade.broken()) {
            int price = REPAIR * barricade.level * 2;
            if (!Parts.take(gamePlayer, price)) return;
            barricade.health = HEALTH[barricade.level];
            decorationManager.setModel(barricade.display, BARRICADE_ID + barricade.level);
            barricade.barriers.forEach(block -> block.setType(Material.BARRIER, false));
            note(player, "Barrikade aufgebaut", "Barricade rebuilt", price);
            sound(barricade, Sound.BLOCK_WOOD_PLACE);
        } else if (barricade.health < HEALTH[barricade.level]) {
            if (!Parts.take(gamePlayer, REPAIR)) return;
            barricade.health = HEALTH[barricade.level];
            note(player, "Barrikade repariert", "Barricade repaired", REPAIR);
            sound(barricade, Sound.BLOCK_ANVIL_USE);
        } else if (barricade.level < MAX_LEVEL) {
            int price = UPGRADE[barricade.level + 1];
            if (!Parts.take(gamePlayer, price)) return;
            barricade.level++;
            barricade.health = HEALTH[barricade.level];
            decorationManager.setModel(barricade.display, BARRICADE_ID + barricade.level);
            note(player, barricade.level == 2 ? "Barrikade aus Wellblech" : "Barrikade aus Stahl",
                    barricade.level == 2 ? "Corrugated iron barricade" : "Steel barricade", price);
            sound(barricade, Sound.BLOCK_ANVIL_PLACE);
        } else {
            Overlay.small(player, Overlay.icon(Overlay.CRATE, Parts.COLOR, grey("Die Barrikade ist voll ausgebaut")),
                    Overlay.icon(Overlay.CRATE, Parts.COLOR, grey("The barricade is fully upgraded")));
            Sounds.deny(player);
        }
    }

    private static void note(Player player, String de, String en, int price) {
        Overlay.small(player, Overlay.icon(Overlay.CRATE, Parts.COLOR, "<b>" + de + "</b> " + grey("· -") + Parts.price(price, false)),
                Overlay.icon(Overlay.CRATE, Parts.COLOR, "<b>" + en + "</b> " + grey("· -") + Parts.price(price, true)));
        Sounds.buy(player);
    }

    private static void sound(Barricade barricade, Sound sound) {
        barricade.center().getWorld().playSound(barricade.center(), sound, 1.0F, 1.0F);
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
            center.getWorld().spawnParticle(Particle.BLOCK, center, 6 * attackers, 0.4, 0.5, 0.4,
                    (barricade.level == 1 ? Material.OAK_PLANKS : Material.IRON_BLOCK).createBlockData());
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
        barricade.barriers.forEach(block -> block.setType(Material.AIR, false));
        barricade.display.setItemStack(null);
        Overlay.smallAll(Overlay.icon(Overlay.CRATE, Parts.COLOR, "<b>Barrikade zerstört</b> " + grey("· mit Bauteilen neu aufbauen")),
                Overlay.icon(Overlay.CRATE, Parts.COLOR, "<b>Barricade destroyed</b> " + grey("· rebuild it with parts")));
    }
}
