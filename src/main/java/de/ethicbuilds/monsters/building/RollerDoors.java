package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.bautest.NexoBarriers;
import de.ethicbuilds.monsters.bautest.Restore;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Color;
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
 * Roller doors (user 31.10.): 2 wide and 3 high, built with parts in 3 levels (corrugated iron, reinforced, steel).
 * Only a switch nearby opens and closes it and shows how it stands, monsters break closed doors,
 * from level 2 they close by themselves when monsters come.
 */
public class RollerDoors implements BuildPart {
    private static final String DOOR_ID = "monsters_rolltor_", SWITCH_ID = "monsters_rolltor_schalter",
            DOOR = "rolltor#", SWITCH = "rolltorschalter#";
    private static final String COLOR = "#9AA6B8";
    private static final int MAX_LEVEL = 3, REPAIR = 1, HIT = 10;
    private static final int[] PRICE = {0, 6, 10, 16};
    private static final int[] HEALTH = {0, 250, 500, 900};
    private static final double SWITCH_REACH = 12, ATTACK_REACH = 1.8, ALARM_REACH = 6;

    @Inject private DecorationManager decorationManager;
    @Inject private MonsterManager monsterManager;

    private final List<Door> doors = new ArrayList<>();
    private final List<Lever> switches = new ArrayList<>();

    private record Lever(ItemDisplay display, Block block) {}

    private static final class Door {
        private final ItemDisplay display;
        private final Block block;
        private final List<Block> barriers;
        private final Location center;
        // Placed closed: Nexo sends its barriers to the players
        private final boolean nexo;
        private Interaction hitbox;
        private DecorationManager.Sign sign;
        private int level, health;
        private boolean open;

        private Door(ItemDisplay display, Block block, List<Block> barriers, boolean nexo) {
            this.display = display;
            this.block = block;
            this.barriers = barriers;
            this.nexo = nexo;
            double x = 0, z = 0;
            for (Block barrier : barriers) {
                x += barrier.getX() - block.getX();
                z += barrier.getZ() - block.getZ();
            }
            int n = Math.max(1, barriers.size());
            this.center = block.getLocation().add(0.5 + x / n, 0, 0.5 + z / n);
        }

        private boolean built() {
            return level > 0 && health > 0;
        }

        private String model() {
            return DOOR_ID + level + (open ? "_auf" : "_zu");
        }
    }

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (id.startsWith(SWITCH_ID)) {
            decorationManager.hitbox(block, SWITCH + switches.size(), 0.8F, 1.4F);
            Lever lever = new Lever(display, block);
            switches.add(lever);
            Door door = nearest(block);
            if (door != null) lever(lever, door);
            return true;
        }
        if (!id.startsWith(DOOR_ID) || !id.endsWith("_zu") && !id.endsWith("_auf")) return false;
        String closed = id.endsWith("_zu") ? id : id.substring(0, id.length() - "_auf".length()) + "_zu";
        Door door = new Door(display, block, decorationManager.barriers(display, closed), id.endsWith("_zu"));
        // In the round the door is missing until a player builds it
        Restore.display(display);
        display.setItemStack(null);
        setBarriers(door, false);
        door.hitbox = decorationManager.hitbox(block, DOOR + doors.size(), 2.0F, 3.0F);
        door.hitbox.teleport(door.center);
        door.sign = decorationManager.sign(block, "Rolltor", "Roller door", Parts.price(PRICE[1], false), Parts.price(PRICE[1], true),
                "Rechtsklick zum Bauen", "Right-click to build", 3.2);
        doors.add(door);
        levers(door);
        return true;
    }

    @Override
    public void clear() {
        doors.clear();
        switches.clear();
    }

    @Override
    public boolean use(GamePlayer gamePlayer, String station) {
        int index = index(station, SWITCH);
        if (index >= 0) {
            if (index < switches.size()) toggle(gamePlayer.getPlayer(), switches.get(index).block());
            return true;
        }
        index = index(station, DOOR);
        if (index < 0) return false;
        if (index < doors.size()) build(gamePlayer, doors.get(index));
        return true;
    }

    private static int index(String station, String prefix) {
        if (!station.startsWith(prefix)) return -1;
        try {
            return Integer.parseInt(station.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Missing or broken: build, damaged: repair, whole: next level
    private void build(GamePlayer gamePlayer, Door door) {
        Player player = gamePlayer.getPlayer();
        if (door.level == 0) {
            if (!Parts.take(gamePlayer, PRICE[1])) return;
            door.level = 1;
            door.health = HEALTH[1];
            door.open = true;
            if (door.sign != null) decorationManager.removeSign(door.sign);
            door.sign = null;
            show(door);
            note(player, "Rolltor gebaut", "Roller door built", PRICE[1]);
            door.center.getWorld().playSound(door.center, Sound.BLOCK_ANVIL_PLACE, 1.0F, 1.0F);
        } else if (door.health <= 0) {
            int price = REPAIR * door.level * 2;
            if (!Parts.take(gamePlayer, price)) return;
            door.health = HEALTH[door.level];
            door.open = true;
            show(door);
            note(player, "Rolltor aufgebaut", "Roller door rebuilt", price);
            door.center.getWorld().playSound(door.center, Sound.BLOCK_ANVIL_PLACE, 1.0F, 1.0F);
        } else if (door.health < HEALTH[door.level]) {
            if (!Parts.take(gamePlayer, REPAIR)) return;
            door.health = HEALTH[door.level];
            note(player, "Rolltor repariert", "Roller door repaired", REPAIR);
            door.center.getWorld().playSound(door.center, Sound.BLOCK_ANVIL_USE, 1.0F, 1.0F);
        } else if (door.level < MAX_LEVEL) {
            int price = PRICE[door.level + 1];
            if (!Parts.take(gamePlayer, price)) return;
            door.level++;
            door.health = HEALTH[door.level];
            show(door);
            note(player, door.level == 2 ? "Rolltor verstärkt" : "Rolltor aus Stahl", door.level == 2 ? "Reinforced roller door" : "Steel roller door", price);
            door.center.getWorld().playSound(door.center, Sound.BLOCK_ANVIL_PLACE, 1.0F, 0.8F);
        } else {
            Overlay.small(player, Overlay.icon(Overlay.PARTS, Parts.COLOR, grey("Das Rolltor ist voll ausgebaut")),
                    Overlay.icon(Overlay.PARTS, Parts.COLOR, grey("The roller door is fully upgraded")));
            Sounds.deny(player);
        }
    }

    // The switch works the nearest roller door
    private void toggle(Player player, Block lever) {
        Door door = nearest(lever);
        Location at = lever.getLocation().add(0.5, 1.0, 0.5);
        if (door == null || !door.built()) {
            String de = door == null ? "Kein Rolltor in der Nähe" : door.level == 0 ? "Erst das Rolltor bauen" : "Das Rolltor ist kaputt";
            String en = door == null ? "No roller door nearby" : door.level == 0 ? "Build the roller door first" : "The roller door is broken";
            Overlay.small(player, Overlay.line((char) 0, COLOR, "ROLLTOR", de), Overlay.line((char) 0, COLOR, "ROLLER DOOR", en));
            Sounds.deny(player);
            return;
        }
        if (door.open && blocked(door)) {
            Overlay.small(player, Overlay.line((char) 0, COLOR, "ROLLTOR", "Jemand steht im Tor"),
                    Overlay.line((char) 0, COLOR, "ROLLER DOOR", "Someone is in the way"));
            Sounds.deny(player);
            return;
        }
        door.open = !door.open;
        show(door);
        at.getWorld().playSound(at, Sound.BLOCK_LEVER_CLICK, 1.0F, door.open ? 0.9F : 0.6F);
        Color color = door.open ? Color.fromRGB(0x5BD15B) : Color.fromRGB(0xFF4848);
        at.getWorld().spawnParticle(Particle.DUST, at, 8, 0.15, 0.15, 0.15, new Particle.DustOptions(color, 1.0F));
        Overlay.small(player, Overlay.line((char) 0, COLOR, "ROLLTOR", door.open ? "offen" : "zu"),
                Overlay.line((char) 0, COLOR, "ROLLER DOOR", door.open ? "open" : "closed"));
    }

    private Door nearest(Block lever) {
        Door best = null;
        double bestDistance = SWITCH_REACH * SWITCH_REACH;
        for (Door door : doors) {
            if (!door.center.getWorld().equals(lever.getWorld())) continue;
            double distance = door.center.distanceSquared(lever.getLocation().add(0.5, 0, 0.5));
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = door;
            }
        }
        return best;
    }

    // Model, barriers and click area for the current state
    private void show(Door door) {
        boolean closed = door.built() && !door.open;
        if (door.built()) {
            decorationManager.setModel(door.display, door.model());
        } else {
            Restore.display(door.display);
            door.display.setItemStack(null);
        }
        setBarriers(door, closed);
        // Open, the door is rolled up under the top
        door.hitbox.teleport(door.center.clone().add(0, door.built() && door.open ? 2 : 0, 0));
        door.hitbox.setInteractionHeight(door.built() && door.open ? 1.0F : 3.0F);
        door.center.getWorld().playSound(door.center, door.open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 0.6F);
        levers(door);
    }

    // Every switch of this door shows its state, if DESIGN made both lever models
    private void levers(Door door) {
        for (Lever lever : switches) if (nearest(lever.block()) == door) lever(lever, door);
    }

    private void lever(Lever lever, Door door) {
        String id = SWITCH_ID + (door.built() && door.open ? "_auf" : "_zu");
        if (lever.display().isValid() && decorationManager.known(id)) decorationManager.setModel(lever.display(), id);
    }

    private static void setBarriers(Door door, boolean closed) {
        for (Block block : door.barriers) {
            if (closed ? !block.getType().isAir() : block.getType() != Material.BARRIER) continue;
            Restore.block(block);
            block.setType(closed ? Material.BARRIER : Material.AIR, false);
        }
        if (!door.nexo) return;
        if (closed) NexoBarriers.close(door.barriers);
        else NexoBarriers.open(door.barriers);
    }

    private static boolean blocked(Door door) {
        for (Player player : door.center.getWorld().getPlayers()) {
            Location at = player.getLocation();
            for (Block block : door.barriers) {
                if (at.getBlockX() == block.getX() && at.getBlockZ() == block.getZ() && at.getY() > block.getY() - 1.8 && at.getY() < block.getY() + 1) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void tick() {
        for (Door door : doors) {
            if (!door.built() || !door.display.isValid()) continue;
            if (door.open) {
                // From level 2 the door closes by itself when monsters come
                if (door.level >= 2 && monsters(ALARM_REACH, door) > 0 && !blocked(door)) {
                    door.open = false;
                    show(door);
                }
                continue;
            }
            int attackers = monsters(ATTACK_REACH, door);
            if (attackers == 0) continue;
            door.health -= attackers * HIT;
            Location at = door.center.clone().add(0, 1, 0);
            at.getWorld().spawnParticle(Particle.BLOCK, at, 6 * attackers, 0.6, 0.8, 0.6, Material.IRON_BLOCK.createBlockData());
            at.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8F, 1.0F);
            if (door.health <= 0) breakDown(door);
        }
    }

    private int monsters(double reach, Door door) {
        int count = 0;
        for (Monster monster : monsterManager.getMonsters().keySet()) {
            if (!monster.isValid() || !monster.getWorld().equals(door.center.getWorld())) continue;
            Location at = monster.getLocation();
            double dx = at.getX() - door.center.getX(), dz = at.getZ() - door.center.getZ();
            if (dx * dx + dz * dz <= reach * reach && Math.abs(at.getY() - door.center.getY()) < 3) count++;
        }
        return count;
    }

    private void breakDown(Door door) {
        Location at = door.center.clone().add(0, 1, 0);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.2F);
        at.getWorld().spawnParticle(Particle.POOF, at, 16, 0.6, 1.0, 0.6, 0.02);
        show(door);
        Overlay.smallAll(Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>Rolltor zerstört</b> " + grey("· mit Bauteilen neu aufbauen")),
                Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>Roller door destroyed</b> " + grey("· rebuild it with parts")));
    }

    private static void note(Player player, String de, String en, int price) {
        Overlay.small(player, Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>" + de + "</b> " + grey("· -") + Parts.price(price, false)),
                Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>" + en + "</b> " + grey("· -") + Parts.price(price, true)));
        Sounds.buy(player);
    }

    private static String grey(String text) {
        return "<color:" + Overlay.GREY + ">" + text + "</color>";
    }
}
