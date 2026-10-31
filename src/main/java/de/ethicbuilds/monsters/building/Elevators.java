package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.bautest.NexoBarriers;
import de.ethicbuilds.monsters.bautest.Restore;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/***
 * Freight elevator: one platform, a crank on every floor it stops at (within a few blocks beside it).
 * A crank calls the platform to its floor, at that floor it sends it to the next one. Who stands on it rides along,
 * held in place until it stops (user 31.10.).
 * With a shaft (aufzug_unten below, aufzug_oben above) the crank sits on the platform and rides along,
 * the plugin hangs the rope from the top and fills the guide posts between both pieces.
 */
public class Elevators implements BuildPart {
    private static final String PLATFORM_ID = "monsters_aufzug", CAR_ID = "monsters_aufzug_plattform", CRANK_ID = "monsters_aufzug_kurbel",
            BOTTOM_ID = "monsters_aufzug_unten", TOP_ID = "monsters_aufzug_oben", ROPE_ID = "monsters_aufzug_seil", POST_ID = "monsters_aufzug_pfosten",
            STATION = "kurbel#", CAR_STATION = "fahrkorb#";
    private static final double CRANK_REACH = 8, SPEED = 0.12;
    // Riders: a little lift against sinking, this far off the deck they are put back, farther away they left the ride
    private static final double HOLD = 0.0, LOST = 0.5, SUNK = 0.25, GONE = 3;
    // Not saved with the player, a disconnect during the ride takes it away
    private static final NamespacedKey RIDE = new NamespacedKey("monsters", "aufzug_fahrt");
    // Soft start and stop over this part of the way (blocks), never slower than MIN_STEP per tick
    private static final double EASE = 0.6, EASE_START = 0.15, MIN_STEP = 0.02;
    private static final double TOP_DROP = 0.15;
    private static final long FLY_BACK_TICKS = 10;
    // From DESIGN: deck 1 block above the platform block, posts beside it at model x ±1.583, bottom posts 2 high
    private static final double DECK = 1.0, POST_SIDE = 1.583, BOTTOM_POSTS = 2.0;
    private static final int SHAFT_HEIGHT = 96;

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;

    private final List<Platform> platforms = new ArrayList<>();
    private final List<Block> cranks = new ArrayList<>();
    private final List<Shaft> shafts = new ArrayList<>();
    private final List<ItemDisplay> tops = new ArrayList<>();
    // Platforms and shaft parts we spawned ourselves, gone after a function test
    private final List<ItemDisplay> spawned = new ArrayList<>();

    private static final class Platform {
        private final ItemDisplay display;
        private final List<int[]> footprint = new ArrayList<>();
        private final Block origin;
        private Block base;
        private boolean moving;
        // Crank on the platform (shaft platforms only)
        private Interaction crank;
        private Shaft shaft;

        private Platform(ItemDisplay display, Block base, List<Block> barriers) {
            this.display = display;
            this.base = base;
            this.origin = base;
            for (Block barrier : barriers) {
                footprint.add(new int[]{barrier.getX() - base.getX(), barrier.getY() - base.getY(), barrier.getZ() - base.getZ()});
            }
        }

        private void barriers(Material material) {
            for (int[] offset : footprint) {
                Block block = base.getRelative(offset[0], offset[1], offset[2]);
                if (material == Material.BARRIER ? block.getType().isAir() : block.getType() == Material.BARRIER) {
                    Restore.block(block);
                    block.setType(material, false);
                }
            }
        }

        // Players standing on the platform area
        private boolean carries(Player player) {
            Location at = player.getLocation();
            if (at.getY() < base.getY() - 0.5 || at.getY() > base.getY() + 2.5) return false;
            for (int[] offset : footprint) {
                if (at.getBlockX() == base.getX() + offset[0] && at.getBlockZ() == base.getZ() + offset[2]) return true;
            }
            return at.getBlockX() == base.getX() && at.getBlockZ() == base.getZ();
        }
    }

    private static final class Shaft {
        private final ItemDisplay bottom;
        private ItemDisplay top, rope;
        private Platform car;

        private Shaft(ItemDisplay bottom) {
            this.bottom = bottom;
        }

        private Block floor() {
            return bottom.getLocation().getBlock();
        }

        // The deck stands level with the floor the top piece stands on
        private Block upper() {
            return top.getLocation().getBlock().getRelative(0, -1, 0);
        }
    }

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (PLATFORM_ID.equals(id) || CAR_ID.equals(id)) {
            Platform platform = new Platform(display, block, decorationManager.barriers(display, id));
            display.setTeleportDuration(1);
            platforms.add(platform);
        } else if (CRANK_ID.equals(id)) {
            decorationManager.hitbox(block, STATION + cranks.size(), 1.0F, 1.4F);
            cranks.add(block);
        } else if (BOTTOM_ID.equals(id)) {
            shafts.add(new Shaft(display));
        } else if (TOP_ID.equals(id)) {
            tops.add(display);
        } else {
            return false;
        }
        // Pieces of a shaft come in any order
        plugin.getServer().getScheduler().runTask(plugin, this::link);
        return true;
    }

    @Override
    public void clear() {
        platforms.clear();
        cranks.clear();
        shafts.clear();
        tops.clear();
        spawned.forEach(ItemDisplay::remove);
        spawned.clear();
    }

    // Every shaft gets its top piece, posts, a platform (placed or our own), its crank and the rope
    private void link() {
        for (Shaft shaft : shafts) {
            if (shaft.top != null || !shaft.bottom.isValid()) continue;
            Block floor = shaft.floor();
            for (ItemDisplay top : tops) {
                Block at = top.getLocation().getBlock();
                if (!top.isValid() || !at.getWorld().equals(floor.getWorld()) || at.getX() != floor.getX() || at.getZ() != floor.getZ()
                        || at.getY() <= floor.getY() + 1 || at.getY() > floor.getY() + SHAFT_HEIGHT) continue;
                if (shaft.top == null || at.getY() < shaft.top.getLocation().getBlockY()) shaft.top = top;
            }
            if (shaft.top == null) continue;
            posts(shaft);
            shaft.car = car(shaft);
            shaft.car.shaft = shaft;
            crank(shaft.car);
            rope(shaft);
        }
    }

    private Platform car(Shaft shaft) {
        Block floor = shaft.floor();
        for (Platform platform : platforms) {
            Block at = platform.base;
            if (platform.shaft == null && at.getWorld().equals(floor.getWorld()) && at.getX() == floor.getX() && at.getZ() == floor.getZ()
                    && at.getY() >= floor.getY() && at.getY() <= shaft.upper().getY()) return platform;
        }
        // None placed: our own one on the bottom piece
        float yaw = shaft.bottom.getLocation().getYaw();
        ItemDisplay display = spawn(shaft.bottom.getLocation(), CAR_ID, yaw);
        display.setTeleportDuration(1);
        Platform platform = new Platform(display, floor, decorationManager.barriers(floor, CAR_ID, yaw));
        platform.barriers(Material.BARRIER);
        platforms.add(platform);
        return platform;
    }

    // The crank stands at the back left of the platform, its click area rides along
    private void crank(Platform platform) {
        Block cell = DecorationManager.relative(platform.base, -1, 1, 1, platform.display.getLocation().getYaw());
        platform.crank = decorationManager.hitbox(cell, CAR_STATION + platforms.indexOf(platform), 0.7F, 0.7F);
    }

    private void posts(Shaft shaft) {
        Location bottom = shaft.bottom.getLocation();
        double gap = shaft.top.getLocation().getBlockY() - bottom.getBlockY() - BOTTOM_POSTS;
        if (gap <= 0.01) return;
        double yaw = Math.toRadians(bottom.getYaw());
        Vector side = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw)).multiply(POST_SIDE);
        for (int sign : new int[]{1, -1}) {
            Location at = new Location(bottom.getWorld(), bottom.getBlockX() + 0.5, bottom.getBlockY() + BOTTOM_POSTS, bottom.getBlockZ() + 0.5)
                    .add(side.clone().multiply(sign));
            spawn(at, POST_ID, bottom.getYaw()).setTransformation(stretch(gap, false));
        }
    }

    private void rope(Shaft shaft) {
        Location top = shaft.top.getLocation();
        shaft.rope = spawn(new Location(top.getWorld(), top.getBlockX() + 0.5, top.getBlockY(), top.getBlockZ() + 0.5), ROPE_ID, top.getYaw());
        shaft.rope.setInterpolationDuration(1);
        hang(shaft);
    }

    // From the top piece down to the deck
    private static void hang(Shaft shaft) {
        if (shaft.rope == null || !shaft.rope.isValid()) return;
        double deck = shaft.car.display.getLocation().getY() + DECK;
        shaft.rope.setInterpolationDelay(0);
        shaft.rope.setTransformation(stretch(Math.max(0.01, shaft.rope.getLocation().getY() - deck), true));
    }

    // A 1 block piece (y 0..1 of the model, centred on the display) stretched up or down from the display
    private static Transformation stretch(double length, boolean down) {
        float middle = (float) (down ? -length : length) / 2;
        return new Transformation(new Vector3f(0, middle, 0), new Quaternionf(), new Vector3f(1, (float) length, 1), new Quaternionf());
    }

    private ItemDisplay spawn(Location at, String id, float yaw) {
        Location place = at.clone();
        place.setYaw(yaw);
        place.setPitch(0);
        ItemStack item = new ItemStack(Material.PAPER);
        GameItems.setModel(item, id);
        ItemDisplay display = at.getWorld().spawn(place, ItemDisplay.class, spawn -> {
            spawn.setItemStack(item);
            spawn.setPersistent(false);
            spawn.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        });
        decorationManager.look(display, id);
        BauTest.limit(display);
        spawned.add(display);
        return display;
    }

    @Override
    public boolean use(GamePlayer gamePlayer, String station) {
        if (station.startsWith(CAR_STATION)) {
            int index = index(station, CAR_STATION);
            if (index < 0 || index >= platforms.size()) return true;
            Platform platform = platforms.get(index);
            Shaft shaft = platform.shaft;
            if (shaft == null || platform.moving) return true;
            // Up from the bottom, otherwise down
            int target = platform.base.getY() == shaft.floor().getY() ? shaft.upper().getY() : shaft.floor().getY();
            move(platform, target);
            platform.base.getWorld().playSound(platform.crank.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 0.7F);
            return true;
        }
        if (!station.startsWith(STATION)) return false;
        int index = index(station, STATION);
        if (index < 0 || index >= cranks.size()) return true;
        Block crank = cranks.get(index);
        Platform platform = nearest(crank);
        if (platform == null || platform.moving) return true;

        // All floors of this platform, sorted
        TreeSet<Integer> floors = new TreeSet<>();
        for (Block other : cranks) if (nearest(other) == platform) floors.add(other.getY());
        int target = crank.getY();
        if (target == platform.base.getY()) {
            Integer next = floors.higher(target);
            target = next != null ? next : floors.first();
        }
        if (target != platform.base.getY()) move(platform, target);
        crank.getWorld().playSound(crank.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 0.7F);
        return true;
    }

    private static int index(String station, String prefix) {
        try {
            return Integer.parseInt(station.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Platform nearest(Block crank) {
        Platform best = null;
        double bestDistance = CRANK_REACH * CRANK_REACH;
        for (Platform platform : platforms) {
            if (platform.shaft != null || !platform.base.getWorld().equals(crank.getWorld())) continue;
            double dx = platform.base.getX() - crank.getX(), dz = platform.base.getZ() - crank.getZ();
            if (dx * dx + dz * dz <= bestDistance) {
                bestDistance = dx * dx + dz * dz;
                best = platform;
            }
        }
        return best;
    }

    // Floor away while moving, the riders are carried by velocity, at the end the floor comes back at the new height.
    // It starts and stops softly; riders can not walk or jump during the ride, they stay where they stood (user 31.10.)
    private void move(Platform platform, int targetY) {
        platform.moving = true;
        platform.barriers(Material.AIR);
        // Nexo's barriers stay where the platform was placed: away while it is somewhere else
        NexoBarriers.open(platform.display);
        Restore.display(platform.display);
        double direction = Math.signum(targetY - platform.base.getY());
        Location start = platform.display.getLocation();
        Location crankStart = platform.crank == null ? null : platform.crank.getLocation();
        double distance = Math.abs(targetY - platform.base.getY());
        // At the top of a shaft the model may sit a little lower than the floor it stops at
        boolean top = platform.shaft != null && targetY == platform.shaft.upper().getY();
        double displayTravel = targetY - (top ? plugin.getConfig().getDouble("aufzug-oben-tiefer", TOP_DROP) : 0) - start.getY();
        double crankOffset = crankStart == null ? 0 : crankStart.getY() - start.getY();
        int baseY = platform.base.getY();
        Map<Player, Location> riders = new HashMap<>();
        Map<Player, Boolean> couldFly = new HashMap<>();
        for (Player player : start.getWorld().getPlayers()) {
            if (!platform.carries(player)) continue;
            riders.put(player, player.getLocation());
            // Standing on air while it moves: no kick for flying, also on servers without allow-flight
            couldFly.put(player, player.getAllowFlight());
            player.setAllowFlight(true);
            hold(player, true);
        }
        start.getWorld().playSound(start, Sound.BLOCK_PISTON_EXTEND, 1.0F, 0.6F);

        new BukkitRunnable() {
            double moved;
            int ticks;

            @Override
            public void run() {
                moved = Math.min(distance, moved + step(moved, distance));
                double share = distance == 0 ? 1 : moved / distance;
                Location at = start.clone().add(0, displayTravel * share, 0);
                platform.display.teleport(at);
                if (crankStart != null && platform.crank.isValid()) {
                    Location crank = crankStart.clone();
                    crank.setY(at.getY() + crankOffset);
                    platform.crank.teleport(crank);
                }
                if (platform.shaft != null) hang(platform.shaft);
                carry(Math.min(distance, moved + step(moved, distance)));
                if (++ticks % 10 == 0) at.getWorld().playSound(at, Sound.BLOCK_CHAIN_STEP, 0.8F, 0.6F);
                if (moved < distance) return;

                platform.base = platform.base.getRelative(0, (int) (direction * distance), 0);
                platform.barriers(Material.BARRIER);
                riders.keySet().forEach(rider -> hold(rider, false));
                if (platform.base.equals(platform.origin)) NexoBarriers.close(platform.display);
                // Only who really sank in goes up, no snap for the others
                double floor = platform.base.getY() + 1;
                for (Player rider : riders.keySet()) {
                    if (!rider.isOnline() || !over(platform, rider.getLocation())) continue;
                    Location feet = rider.getLocation();
                    if (feet.getY() < floor - SUNK) {
                        feet.setY(floor);
                        rider.teleport(feet, TeleportFlag.Relative.VELOCITY_X, TeleportFlag.Relative.VELOCITY_Y, TeleportFlag.Relative.VELOCITY_Z);
                    }
                }
                // Flying goes back once everyone stands on the floor again
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> couldFly.forEach((rider, could) -> {
                    if (!rider.isOnline() || could || rider.getGameMode() == GameMode.CREATIVE || rider.getGameMode() == GameMode.SPECTATOR) return;
                    rider.setFlying(false);
                    rider.setAllowFlight(false);
                }), FLY_BACK_TICKS);
                at.getWorld().playSound(at, Sound.BLOCK_PISTON_CONTRACT, 1.0F, 0.6F);
                platform.moving = false;
                cancel();
            }

            // Height follows the deck exactly, x and z stay where the rider stood when it started
            private void carry(double next) {
                double deckNext = baseY + DECK + direction * next;
                for (Iterator<Map.Entry<Player, Location>> it = riders.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Player, Location> entry = it.next();
                    Player rider = entry.getKey();
                    Location stood = entry.getValue();
                    if (!rider.isOnline()) continue;
                    Location feet = rider.getLocation();
                    // Teleported away or dead: no longer on the ride
                    if (rider.isDead() || !feet.getWorld().equals(stood.getWorld())
                            || Math.hypot(feet.getX() - stood.getX(), feet.getZ() - stood.getZ()) > GONE) {
                        hold(rider, false);
                        it.remove();
                        continue;
                    }
                    rider.setFallDistance(0);
                    if (rider.isFlying()) rider.setFlying(false);
                    if (Math.abs(feet.getY() - deckNext) > LOST) {
                        feet.set(stood.getX(), deckNext, stood.getZ());
                        rider.teleport(feet, TeleportFlag.Relative.VELOCITY_X, TeleportFlag.Relative.VELOCITY_Z);
                        continue;
                    }
                    rider.setVelocity(new Vector(stood.getX() - feet.getX(), deckNext - feet.getY() + HOLD, stood.getZ() - feet.getZ()));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // No walking and no jumping while it moves
    private static void hold(Player player, boolean held) {
        for (Attribute attribute : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            instance.removeModifier(RIDE);
            if (held) instance.addTransientModifier(new AttributeModifier(RIDE, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    // Soft start and stop: slower in the first and the last part of the way
    private static double step(double moved, double distance) {
        double ease = Math.min(1, Math.min((moved + EASE_START) / EASE, (distance - moved) / EASE));
        return Math.max(MIN_STEP, SPEED * ease);
    }

    // Above the platform area, at any height
    private static boolean over(Platform platform, Location at) {
        for (int[] offset : platform.footprint) {
            if (at.getBlockX() == platform.base.getX() + offset[0] && at.getBlockZ() == platform.base.getZ() + offset[2]) return true;
        }
        return at.getBlockX() == platform.base.getX() && at.getBlockZ() == platform.base.getZ();
    }
}
