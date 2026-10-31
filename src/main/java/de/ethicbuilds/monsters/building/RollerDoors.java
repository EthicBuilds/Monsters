package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.bautest.NexoBarriers;
import de.ethicbuilds.monsters.bautest.NexoStandIns;
import de.ethicbuilds.monsters.bautest.Restore;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.map.decoration.HoloText;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/***
 * Roller doors (user 31.10.): 2 wide and 3 high, closed at the start. The switch beside it buys it free once (the team can chip in),
 * then opens and closes it and shows how it stands. Monsters break closed doors, parts repair and upgrade them
 * with a right-click on the door (whoever looks at it sees the price), from level 2 they close by themselves when monsters come.
 */
public class RollerDoors implements BuildPart {
    private static final String DOOR_ID = "monsters_rolltor_", SWITCH_ID = "monsters_rolltor_schalter",
            DOOR = "rolltor#", SWITCH = "rolltorschalter#";
    private static final String COLOR = "#9AA6B8";
    private static final int MAX_LEVEL = 3, REPAIR = 1, HIT = 10, UNLOCK = 2500;
    private static final int[] UPGRADE = {0, 0, 10, 16};
    private static final int[] HEALTH = {0, 250, 500, 900};
    private static final double SWITCH_REACH = 12, ATTACK_REACH = 1.8, ALARM_REACH = 6, LOOK_REACH = 6, INFO_LIFT = 3.15;

    @Inject private DecorationManager decorationManager;
    @Inject private MonsterManager monsterManager;

    private final List<Door> doors = new ArrayList<>();
    private final List<Lever> switches = new ArrayList<>();
    // The door each player looks at, its price sign is shown to that player only
    private final Map<UUID, Door> looking = new HashMap<>();

    private static final class Lever {
        private final ItemDisplay display;
        private final Block block;
        private DecorationManager.Sign sign;

        private Lever(ItemDisplay display, Block block) {
            this.display = display;
            this.block = block;
        }
    }

    private static final class Door {
        private final ItemDisplay display;
        private final Block block;
        private final List<Block> barriers;
        private final Location center;
        private Interaction hitbox;
        private TextDisplay info, infoEn;
        private int level, health, paid;
        private RollerShutter shutter;
        private boolean bought, open;

        private Door(ItemDisplay display, Block block, List<Block> barriers, int level) {
            this.display = display;
            this.block = block;
            this.barriers = barriers;
            this.level = level;
            this.health = HEALTH[level];
            double x = 0, z = 0;
            for (Block barrier : barriers) {
                x += barrier.getX() - block.getX();
                z += barrier.getZ() - block.getZ();
            }
            int n = Math.max(1, barriers.size());
            this.center = block.getLocation().add(0.5 + x / n, 0, 0.5 + z / n);
        }

        private boolean whole() {
            return health > 0;
        }

        private boolean passable() {
            return !whole() || open;
        }
    }

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (id.startsWith(SWITCH_ID)) {
            decorationManager.hitbox(block, SWITCH + switches.size(), 0.8F, 1.4F);
            // On the build server our own display instead of the Nexo furniture, Nexo would overwrite every change
            Lever lever = new Lever(NexoStandIns.of(display, id), block);
            switches.add(lever);
            sign(lever, UNLOCK);
            Door door = nearest(block);
            if (door != null) lever(lever, door);
            return true;
        }
        if (!id.startsWith(DOOR_ID) || !id.endsWith("_zu") && !id.endsWith("_auf")) return false;
        String closed = id.endsWith("_zu") ? id : id.substring(0, id.length() - "_auf".length()) + "_zu";
        Door door = new Door(NexoStandIns.of(display, closed), block, decorationManager.barriers(display, closed), level(closed));
        door.hitbox = decorationManager.hitbox(block, DOOR + doors.size(), 2.0F, 3.0F);
        door.hitbox.teleport(door.center);
        doors.add(door);
        // The furniture only marks the place, frame and slats are our own displays
        Restore.display(door.display);
        door.display.setItemStack(null);
        door.shutter = new RollerShutter(decorationManager, door.display.getLocation(), door.level);
        door.info = info(door);
        door.infoEn = info(door);
        text(door);
        // At the start it is closed, whatever model the map builder placed
        show(door);
        return true;
    }

    private static int level(String closed) {
        try {
            int level = Integer.parseInt(closed.substring(DOOR_ID.length(), closed.length() - "_zu".length()));
            return Math.max(1, Math.min(MAX_LEVEL, level));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public void clear() {
        for (Door door : doors) {
            door.info.remove();
            door.infoEn.remove();
        }
        looking.clear();
        doors.clear();
        switches.clear();
    }

    @Override
    public boolean use(GamePlayer gamePlayer, String station) {
        int index = index(station, SWITCH);
        if (index >= 0) {
            if (index < switches.size()) toggle(gamePlayer, switches.get(index));
            return true;
        }
        index = index(station, DOOR);
        if (index < 0) return false;
        if (index < doors.size()) work(gamePlayer, doors.get(index));
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

    // At the door only parts work, bought free or not: broken rebuild, damaged repair, whole next level
    private void work(GamePlayer gamePlayer, Door door) {
        Player player = gamePlayer.getPlayer();
        if (!door.whole()) {
            int price = REPAIR * door.level * 2;
            if (!Parts.take(gamePlayer, price)) return;
            door.health = HEALTH[door.level];
            door.open = false;
            show(door);
            note(player, "Rolltor aufgebaut", "Roller door rebuilt", price);
            door.center.getWorld().playSound(door.center, Sound.BLOCK_ANVIL_PLACE, 1.0F, 1.0F);
        } else if (door.health < HEALTH[door.level]) {
            if (!Parts.take(gamePlayer, REPAIR)) return;
            door.health = HEALTH[door.level];
            text(door);
            note(player, "Rolltor repariert", "Roller door repaired", REPAIR);
            door.center.getWorld().playSound(door.center, Sound.BLOCK_ANVIL_USE, 1.0F, 1.0F);
        } else if (door.level < MAX_LEVEL) {
            int price = UPGRADE[door.level + 1];
            if (!Parts.take(gamePlayer, price)) return;
            door.level++;
            door.shutter.level(door.level, door.open);
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

    // The switch works the nearest roller door: first buy it free, then open and close
    private void toggle(GamePlayer gamePlayer, Lever lever) {
        Player player = gamePlayer.getPlayer();
        Door door = nearest(lever.block);
        if (door == null || !door.whole()) {
            String de = door == null ? "Kein Rolltor in der Nähe" : "Kaputt · am Tor mit Bauteilen aufbauen";
            String en = door == null ? "No roller door nearby" : "Broken · rebuild it at the door with parts";
            Overlay.small(player, Overlay.line((char) 0, COLOR, "ROLLTOR", de), Overlay.line((char) 0, COLOR, "ROLLER DOOR", en));
            Sounds.deny(player);
            return;
        }
        if (!door.bought) {
            int remaining = UNLOCK - door.paid;
            if (gamePlayer.getCoins() < remaining) {
                contribute(gamePlayer, door, remaining);
                return;
            }
            gamePlayer.setCoins(gamePlayer.getCoins() - remaining);
            door.bought = true;
            for (Lever other : switches) if (nearest(other.block) == door) sign(other, 0);
            Lang.note(player, Lang.f(player, "§7Rolltor freigekauft §c-%d Coins", "§7Roller door bought free §c-%d Coins", remaining));
            Sounds.buy(player);
        } else if (door.open && blocked(door)) {
            Overlay.small(player, Overlay.line((char) 0, COLOR, "ROLLTOR", "Jemand steht im Tor"),
                    Overlay.line((char) 0, COLOR, "ROLLER DOOR", "Someone is in the way"));
            Sounds.deny(player);
            return;
        }
        door.open = !door.open;
        show(door, true);
        roll(door);
        Location at = lever.block.getLocation().add(0.5, 1.0, 0.5);
        at.getWorld().playSound(at, Sound.BLOCK_LEVER_CLICK, 1.0F, door.open ? 0.9F : 0.6F);
        Color color = door.open ? Color.fromRGB(0x5BD15B) : Color.fromRGB(0xFF4848);
        at.getWorld().spawnParticle(Particle.DUST, at, 8, 0.15, 0.15, 0.15, new Particle.DustOptions(color, 1.0F));
        Overlay.small(player, Overlay.line((char) 0, COLOR, "ROLLTOR", door.open ? "offen" : "zu"),
                Overlay.line((char) 0, COLOR, "ROLLER DOOR", door.open ? "open" : "closed"));
    }

    // Not enough coins: everything the player has goes in, like at the doors of the map
    private void contribute(GamePlayer gamePlayer, Door door, int remaining) {
        Player player = gamePlayer.getPlayer();
        int coins = gamePlayer.getCoins();
        if (coins <= 0) {
            Lang.note(player, Lang.f(player, "§cNoch §6%d Coins §cfür das Rolltor, legt zusammen!", "§cThe roller door still needs §6%d Coins§c, chip in!", remaining));
            Sounds.deny(player);
            return;
        }
        gamePlayer.setCoins(0);
        door.paid += coins;
        for (Lever lever : switches) if (nearest(lever.block) == door) sign(lever, UNLOCK - door.paid);
        Lang.noteAll("%s§a%s §7legt §6%d Coins §7fürs §aRolltor §7ein (§6%d§7/§6%d§7). Noch §6%d Coins§7, legt zusammen!",
                "%s§a%s §7puts §6%d Coins §7into the §aroller door §7(§6%d§7/§6%d§7). §6%d Coins §7to go, chip in!",
                Main.getINSTANCE().getMonstersPrefix(), player.getName(), coins, door.paid, UNLOCK, UNLOCK - door.paid);
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 1.2F);
    }

    // Price sign over the switch with what is still missing, gone once the door is free
    private void sign(Lever lever, int remaining) {
        if (lever.sign != null) decorationManager.removeSign(lever.sign);
        lever.sign = remaining <= 0 ? null : decorationManager.sign(lever.block, HoloText.LOCK, "Rolltor", "Roller door",
                HoloText.price(remaining, false), HoloText.price(remaining, true), "Rechtsklick zum Freikaufen", "Right-click to buy free", 1.0);
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

    // Model, barriers, click area and switches for the current state
    private void show(Door door) {
        show(door, false);
    }

    // Model, barriers, click area and switches for the current state; while it rolls the shutter moves itself
    private void show(Door door, boolean rolling) {
        text(door);
        if (!door.whole()) door.shutter.broken();
        else if (!rolling) door.shutter.place(door.open);
        setBarriers(door, !door.passable());
        // Open, the door is rolled up under the top
        boolean up = door.whole() && door.open;
        door.hitbox.teleport(door.center.clone().add(0, up ? 2 : 0, 0));
        door.hitbox.setInteractionHeight(up ? 1.0F : 3.0F);
        for (Lever lever : switches) if (nearest(lever.block) == door) lever(lever, door);
    }

    // User 31.10.: frame and box stay, the slats roll up into the box one after the other
    private void roll(Door door) {
        door.center.getWorld().playSound(door.center, door.open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 0.6F);
        door.center.getWorld().playSound(door.center, Sound.BLOCK_CHAIN_PLACE, 0.8F, 0.6F);
        door.shutter.roll(door.open);
    }

    // The switch shows how the door stands, if Nexo knows both lever models
    private void lever(Lever lever, Door door) {
        String id = SWITCH_ID + (door.whole() && door.open ? "_auf" : "_zu");
        if (lever.display.isValid() && decorationManager.known(id)) decorationManager.setModel(lever.display, id);
    }

    private static void setBarriers(Door door, boolean closed) {
        for (Block block : door.barriers) {
            if (closed ? !block.getType().isAir() : block.getType() != Material.BARRIER) continue;
            Restore.block(block);
            block.setType(closed ? Material.BARRIER : Material.AIR, false);
        }
        if (closed) NexoBarriers.close(door.display);
        else NexoBarriers.open(door.display);
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
            if (!door.whole() || !door.display.isValid()) continue;
            if (door.open) {
                // From level 2 the door closes by itself when monsters come
                if (door.level >= 2 && monsters(ALARM_REACH, door) > 0 && !blocked(door)) {
                    door.open = false;
                    show(door, true);
                    roll(door);
                }
                continue;
            }
            int attackers = monsters(ATTACK_REACH, door);
            if (attackers == 0) continue;
            door.health -= attackers * HIT;
            text(door);
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

    // Small price sign over the box, nobody sees it until they look at the door
    private static TextDisplay info(Door door) {
        return door.center.getWorld().spawn(door.center.clone().add(0, INFO_LIFT, 0), TextDisplay.class, holo -> {
            HoloText.style(holo);
            holo.setPersistent(false);
            holo.setVisibleByDefault(false);
        });
    }

    // What a right-click on the door does next and what it costs
    private static void text(Door door) {
        if (door.info == null) return;
        for (boolean english : new boolean[]{false, true}) {
            String name, lines, hint;
            if (!door.whole()) {
                name = english ? "Rebuild" : "Wiederaufbau";
                lines = HoloText.parts(REPAIR * door.level * 2, english);
                hint = english ? "Right-click to rebuild" : "Rechtsklick zum Aufbauen";
            } else if (door.health < HEALTH[door.level]) {
                name = english ? "Repair" : "Reparatur";
                lines = HoloText.parts(REPAIR, english);
                hint = english ? "Right-click to repair" : "Rechtsklick zum Reparieren";
            } else if (door.level < MAX_LEVEL) {
                name = (english ? "Upgrade level " : "Ausbau Stufe ") + (door.level + 1);
                lines = HoloText.parts(UPGRADE[door.level + 1], english);
                hint = english ? "Right-click to upgrade" : "Rechtsklick zum Ausbauen";
            } else {
                name = (english ? "Level " : "Stufe ") + MAX_LEVEL;
                lines = HoloText.info("max.");
                hint = "";
            }
            (english ? door.infoEn : door.info).text(MiniMessage.miniMessage().deserialize(DecorationManager.compose(HoloText.PARTS, false, name, lines, hint)));
        }
    }

    @Override
    public void look() {
        if (doors.isEmpty()) return;
        Main plugin = Main.getINSTANCE();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Door door = BauTest.on() && !BauTest.active(player) ? null : lookedAt(player);
            Door before = door == null ? looking.remove(player.getUniqueId()) : looking.put(player.getUniqueId(), door);
            if (before == door) continue;
            if (before != null) {
                player.hideEntity(plugin, before.info);
                player.hideEntity(plugin, before.infoEn);
            }
            if (door != null) player.showEntity(plugin, Lang.english(player) ? door.infoEn : door.info);
        }
    }

    private Door lookedAt(Player player) {
        Vector eye = player.getEyeLocation().toVector(), direction = player.getEyeLocation().getDirection();
        Door best = null;
        double bestDistance = LOOK_REACH;
        for (Door door : doors) {
            if (!door.hitbox.isValid() || !door.hitbox.getWorld().equals(player.getWorld())) continue;
            RayTraceResult hit = door.hitbox.getBoundingBox().rayTrace(eye, direction, LOOK_REACH);
            if (hit == null) continue;
            double distance = hit.getHitPosition().distance(eye);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = door;
            }
        }
        return best;
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
