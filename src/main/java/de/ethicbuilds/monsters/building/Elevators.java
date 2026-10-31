package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.bautest.NexoBarriers;
import de.ethicbuilds.monsters.bautest.NexoStandIns;
import de.ethicbuilds.monsters.bautest.Restore;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/***
 * Freight elevator: one platform, a crank on every floor it stops at (within a few blocks beside it).
 * A crank calls the platform to its floor, at that floor it sends it to the next one. Who stands on it rides along,
 * held in place until it stops (user 31.10.).
 * With a shaft (aufzug_unten below, aufzug_oben above) the crank sits on the platform and rides along,
 * the plugin hangs two ropes from the top and fills the guide posts between both pieces.
 */
public class Elevators implements BuildPart, Listener {
    private static final String PLATFORM_ID = "monsters_aufzug", CAR_ID = "monsters_aufzug_plattform", CRANK_ID = "monsters_aufzug_kurbel",
            ARM_ID = "monsters_aufzug_kurbel_arm", CAR_PLAIN_ID = "monsters_aufzug_plattform_ohne_arm",
            BOTTOM_ID = "monsters_aufzug_unten", TOP_ID = "monsters_aufzug_oben", ROPE_ID = "monsters_aufzug_seil", POST_ID = "monsters_aufzug_pfosten",
            STATION = "kurbel#", CAR_STATION = "fahrkorb#";
    private static final double CRANK_REACH = 8, SPEED = 0.12;
    // Riders: this far off the deck they are put back, farther away they left the ride
    private static final double LOST = 0.5, GONE = 3;
    // At the stop riders this close to the deck are put exactly onto it
    private static final double SNAP = 0.6, SNAP_BELOW = 1.5;
    // Share of the remaining gap corrected per tick, divided by the ping in ticks (a higher gain swings with latency)
    private static final double GAIN = 0.6;
    private static final int MAX_LAG = 20;
    // Not saved with the player, a disconnect during the ride takes it away
    private static final NamespacedKey RIDE = new NamespacedKey("monsters", "aufzug_fahrt"), STEP = new NamespacedKey("monsters", "aufzug_stufe");
    // User 01.11.: the platform stops this many blocks below the upper floor, the step up is walkable
    private static final int TOP_BLOCKS = 1;
    // Soft start and stop over this part of the way (blocks), never slower than MIN_STEP per tick
    private static final double EASE = 0.6, EASE_START = 0.15, MIN_STEP = 0.02;
    private static final double TOP_DROP = 0;
    private static final long FLY_BACK_TICKS = 10;
    // From DESIGN: deck 1 block above the platform block, posts beside it at model x ±1.583, bottom posts 2 high
    private static final double DECK = 1.0, POST_SIDE = 1.583, BOTTOM_POSTS = 2.0;
    // From DESIGN: two ropes at model x ±1.25, from the bottom of the top piece to the eyes of the platform
    private static final double ROPE_SIDE = 1.25, ROPE_EYE = 0.765625, ROPE_TOP = 0.5;
    private static final int SHAFT_HEIGHT = 96;
    // Crank arm (DESIGN): its model is centred on the drum, which sits here in the platform model; one turn per block
    private static final Vector3f ARM_PIVOT = new Vector3f(0, (20F - 8) / 16, (25F - 8) / 16);
    private static final double ARM_TURN = Math.PI * 2;

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;

    private final List<Platform> platforms = new ArrayList<>();
    // Players on a moving platform: they may not start flying (shift would let them sink)
    private final Set<UUID> riding = new HashSet<>();
    // Players on a platform waiting below the upper floor: they can walk up the step
    private Set<UUID> stepping = new HashSet<>();
    private final List<Block> cranks = new ArrayList<>();
    private final List<Shaft> shafts = new ArrayList<>();
    private final List<ItemDisplay> tops = new ArrayList<>();
    // Platforms and shaft parts we spawned ourselves, gone after a function test
    private final List<ItemDisplay> spawned = new ArrayList<>();

    private static final class Platform {
        private final ItemDisplay display;
        private final List<int[]> footprint = new ArrayList<>();
        private final Block origin;
        // Height of the model above its block, Nexo puts furniture half a block up
        private final double offset;
        private Block base;
        private boolean moving;
        // Crank on the platform (shaft platforms only)
        private Interaction crank;
        private Shaft shaft;
        // Separate crank arm, if Nexo knows its model
        private ItemDisplay arm;
        private double armAngle;

        private Platform(ItemDisplay display, Block base, List<Block> barriers) {
            this.display = display;
            this.base = base;
            this.origin = base;
            this.offset = display.getLocation().getY() - base.getY();
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
        private ItemDisplay top;
        private final List<ItemDisplay> ropes = new ArrayList<>();
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
            // On the build server our own display rides instead of the Nexo furniture, Nexo sends its model itself
            ItemDisplay shown = NexoStandIns.of(display, id);
            Platform platform = new Platform(shown, block, decorationManager.barriers(display, id));
            shown.setTeleportDuration(1);
            // Hidden for the testers, Nexo's barriers went with it: real ones to stand on
            if (shown != display) platform.barriers(Material.BARRIER);
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
        riding.clear();
        stepping.forEach(uuid -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) step(player, 0);
        });
        stepping.clear();
        spawned.forEach(ItemDisplay::remove);
        spawned.clear();
    }

    // Every shaft gets its top piece, posts, a platform (placed or our own), its crank and the ropes
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
            arm(shaft.car);
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

    // The arm sits where the platform model has it and turns while the platform moves
    private void arm(Platform platform) {
        if (platform.arm != null || !decorationManager.knownItem(ARM_ID) || !decorationManager.knownItem(CAR_PLAIN_ID)) return;
        // The platform without its fixed arm, else there would be two
        decorationManager.setModel(platform.display, CAR_PLAIN_ID);
        Location at = platform.display.getLocation();
        platform.arm = spawn(at, ARM_ID, at.getYaw());
        // As passenger it rides exactly with the platform, only the turn is its own
        platform.display.addPassenger(platform.arm);
        turn(platform, 0, 0);
    }

    // Turn about the drum: the translation never changes, so the client only interpolates the rotation
    private static void turn(Platform platform, double angle, int ticks) {
        if (platform.arm == null || !platform.arm.isValid()) return;
        Transformation base = platform.display.getTransformation();
        Quaternionf spin = new Quaternionf().rotateX((float) angle);
        Vector3f translation = new Vector3f(base.getTranslation())
                .add(new Quaternionf(base.getLeftRotation()).transform(new Vector3f(ARM_PIVOT).mul(base.getScale())));
        platform.arm.setInterpolationDelay(0);
        platform.arm.setInterpolationDuration(ticks);
        platform.arm.setTransformation(new Transformation(translation, new Quaternionf(base.getLeftRotation()).mul(spin), base.getScale(),
                base.getRightRotation()));
    }

    @EventHandler
    public void onFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying() && riding.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
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
        double yaw = Math.toRadians(top.getYaw());
        Vector side = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw)).multiply(ROPE_SIDE);
        for (int sign : new int[]{1, -1}) {
            Location at = top.clone().add(side.clone().multiply(sign)).subtract(0, ROPE_TOP, 0);
            ItemDisplay rope = spawn(at, ROPE_ID, top.getYaw());
            rope.setInterpolationDuration(1);
            shaft.ropes.add(rope);
        }
        hang(shaft);
    }

    // From the top piece down to the eyes of the platform
    private static void hang(Shaft shaft) {
        double eye = shaft.car.display.getLocation().getY() + ROPE_EYE;
        for (ItemDisplay rope : shaft.ropes) {
            if (!rope.isValid()) continue;
            rope.setInterpolationDelay(0);
            rope.setTransformation(stretch(Math.max(0.01, rope.getLocation().getY() - eye), true));
        }
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
        ItemStack item = decorationManager.modelItem(id);
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
            // Up from where it was placed, otherwise back down there
            int bottom = platform.origin.getY();
            int target = platform.base.getY() == bottom ? topStop(shaft) : bottom;
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
        if (!NexoStandIns.is(platform.display)) Restore.display(platform.display);
        double direction = Math.signum(targetY - platform.base.getY());
        Location start = platform.display.getLocation();
        Location crankStart = platform.crank == null ? null : platform.crank.getLocation();
        double distance = Math.abs(targetY - platform.base.getY());
        // At the top of a shaft the model may sit a little lower than the floor it stops at
        boolean top = platform.shaft != null && targetY == topStop(platform.shaft);
        double displayTravel = targetY + platform.offset - (top ? plugin.getConfig().getDouble("aufzug-oben-tiefer", TOP_DROP) : 0) - start.getY();
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
            riding.add(player.getUniqueId());
        }
        start.getWorld().playSound(start, Sound.BLOCK_PISTON_EXTEND, 1.0F, 0.6F);

        new BukkitRunnable() {
            // Way done after each tick, the riders' reported heights belong to older entries
            final List<Double> path = new ArrayList<>(List.of(0.0));
            double moved;
            int ticks;

            @Override
            public void run() {
                double before = moved;
                moved = Math.min(distance, moved + step(moved, distance));
                path.add(moved);
                double share = distance == 0 ? 1 : moved / distance;
                Location at = start.clone().add(0, displayTravel * share, 0);
                platform.display.teleport(at, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                if (platform.arm != null && platform.arm.isValid()) {
                    platform.armAngle += direction * (moved - before) * ARM_TURN;
                    turn(platform, platform.armAngle, 1);
                }
                if (crankStart != null && platform.crank.isValid()) {
                    Location crank = crankStart.clone();
                    crank.setY(at.getY() + crankOffset);
                    platform.crank.teleport(crank);
                }
                if (platform.shaft != null) hang(platform.shaft);
                carry(before, moved);
                if (++ticks % 10 == 0) at.getWorld().playSound(at, Sound.BLOCK_CHAIN_STEP, 0.8F, 0.6F);
                if (moved < distance) return;

                platform.base = platform.base.getRelative(0, (int) (direction * distance), 0);
                platform.barriers(Material.BARRIER);
                riders.keySet().forEach(rider -> {
                    hold(rider, false);
                    riding.remove(rider.getUniqueId());
                });
                if (platform.base.equals(platform.origin)) NexoBarriers.close(platform.display);
                // Exactly onto the deck: the last steps leave the client a hair below the new barriers, and a player
                // already inside a block falls through it (user 01.11.: pushed down at the top, with shift too)
                double floor = platform.base.getY() + 1;
                for (Player rider : riders.keySet()) {
                    if (!rider.isOnline() || !over(platform, rider.getLocation())) continue;
                    Location feet = rider.getLocation();
                    rider.setVelocity(new Vector());
                    if (feet.getY() < floor + SNAP && feet.getY() > floor - SNAP_BELOW) {
                        feet.setY(floor);
                        rider.teleport(feet);
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

            // The rider moves with the deck's own step, sent in the same tick as the display (both arrive one client tick later).
            // Drift is corrected against where the deck was one ping ago, the time the reported position is from, never
            // against the deck now: with latency that swings up. x and z stay where the rider stood when it started.
            private void carry(double before, double now) {
                double deckNow = baseY + DECK + direction * now;
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
                        riding.remove(rider.getUniqueId());
                        it.remove();
                        continue;
                    }
                    rider.setFallDistance(0);
                    if (rider.isFlying()) rider.setFlying(false);
                    int lag = Math.min(MAX_LAG, (int) Math.round(rider.getPing() / 50.0));
                    double expected = baseY + DECK + direction * path.get(Math.max(0, path.size() - 2 - lag));
                    if (Math.abs(feet.getY() - expected) > LOST) {
                        feet.set(stood.getX(), deckNow, stood.getZ());
                        rider.teleport(feet, TeleportFlag.Relative.VELOCITY_X, TeleportFlag.Relative.VELOCITY_Z);
                        continue;
                    }
                    double gain = GAIN / (lag + 1);
                    rider.setVelocity(new Vector(gain * (stood.getX() - feet.getX()), direction * (now - before) + gain * (expected - feet.getY()),
                            gain * (stood.getZ() - feet.getZ())));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Upper stop of the platform: some blocks below the upper floor (config), never at or below where it was placed
    private int topStop(Shaft shaft) {
        int lower = Math.max(0, plugin.getConfig().getInt("aufzug-oben-bloecke-tiefer", TOP_BLOCKS));
        return Math.max(shaft.car.origin.getY() + 1, shaft.upper().getY() - lower);
    }

    @Override
    public void look() {
        if (shafts.isEmpty() && stepping.isEmpty()) return;
        Set<UUID> now = new HashSet<>();
        Map<UUID, Integer> gaps = new HashMap<>();
        for (Shaft shaft : shafts) {
            Platform car = shaft.car;
            if (car == null || car.moving || car.base.getY() != topStop(shaft)) continue;
            int gap = shaft.upper().getY() - car.base.getY();
            if (gap <= 0) continue;
            double deck = car.base.getY() + DECK;
            for (Player player : car.base.getWorld().getPlayers()) {
                Location at = player.getLocation();
                if (!over(car, at) || at.getY() < deck - 0.1 || at.getY() > deck + 0.6) continue;
                now.add(player.getUniqueId());
                gaps.put(player.getUniqueId(), gap);
            }
        }
        for (UUID uuid : stepping) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && !now.contains(uuid)) step(player, 0);
        }
        for (UUID uuid : now) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && !stepping.contains(uuid)) step(player, gaps.get(uuid));
        }
        stepping = now;
    }

    // A higher step while standing on the waiting platform, like walking up a slab; 0 takes it away
    private static void step(Player player, int blocks) {
        AttributeInstance instance = player.getAttribute(Attribute.STEP_HEIGHT);
        if (instance == null) return;
        instance.removeModifier(STEP);
        if (blocks > 0) instance.addTransientModifier(new AttributeModifier(STEP, blocks, AttributeModifier.Operation.ADD_NUMBER));
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
