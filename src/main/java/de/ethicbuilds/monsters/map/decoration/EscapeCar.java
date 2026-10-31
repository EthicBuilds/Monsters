package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.bautest.Restore;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import de.ethicbuilds.monsters.weapons.ShotDamage;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Escape car (user 31.10.): a wreck that the team repairs with a tyre, a car battery and a fuel can out of crates
 * and wrecks. Then it drives 20 seconds (W/S, steering with the look) and runs monsters over. Each drive takes a fuel can.
 * The driver stays in until the end (user 01.11.), hears the engine and sees the car without its roof.
 */
public class EscapeCar implements Listener {
    public static final String STATION = "fluchtauto#";
    static final String BROKEN_ID = "monsters_fluchtauto_kaputt";
    private static final String FIXED_ID = "monsters_fluchtauto_repariert", ROOFLESS_ID = "monsters_fluchtauto_ohne_dach";
    private static final double PART_CHANCE = 0.12;
    private static final int DRIVE_TICKS = 20 * 20;
    private static final double MAX_SPEED = 0.6, ACCELERATION = 0.04, BRAKE = 0.06, HIT_RADIUS = 2.2, HIT_DAMAGE = 60;
    private static final String COLOR = "#50839D";
    // Seat height (DESIGN): eyes about 1.7 above the ground, free above the cabin edge of the roofless car
    private static final double SEAT = 0.1;

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;

    private final List<Car> cars = new ArrayList<>();
    // Seats of running drives: getting off is only possible at the end
    private final Set<Entity> seats = new HashSet<>();
    // Found by the whole team
    private boolean tyre, battery;
    private int fuel;

    private static final class Car {
        private final ItemDisplay display;
        private final List<Block> barriers;
        private final Interaction hitbox;
        // Middle of the barriers seen from the model origin, at the placed yaw
        private final Vector offset;
        private final float yaw;
        private boolean fixed, driving;

        private Car(ItemDisplay display, List<Block> barriers, Interaction hitbox, Vector offset) {
            this.display = display;
            this.barriers = barriers;
            this.hitbox = hitbox;
            this.offset = offset;
            this.yaw = display.getLocation().getYaw();
        }

        private Location middle() {
            Location at = display.getLocation();
            return at.clone().add(offset.clone().rotateAroundY(-Math.toRadians(at.getYaw() - yaw)));
        }
    }

    void register(ItemDisplay display, Block block) {
        List<Block> barriers = new ArrayList<>();
        double x = 0, z = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Block part = block.getRelative(dx, dy, dz);
                    if (part.getType() != Material.BARRIER) continue;
                    barriers.add(part);
                    x += dx;
                    z += dz;
                }
            }
        }
        int n = Math.max(1, barriers.size());
        Location middle = block.getLocation().add(0.5 + x / n, 0, 0.5 + z / n);
        Interaction hitbox = decorationManager.hitbox(block, STATION + cars.size(), 3.2F, 1.8F);
        hitbox.teleport(middle);
        cars.add(new Car(display, barriers, hitbox, middle.toVector().subtract(display.getLocation().toVector()).setY(0)));
    }

    /** Function test on the build server: forgets the cars, they register again in the next test */
    public void clear() {
        cars.clear();
        tyre = battery = false;
        fuel = 0;
    }

    /** Out of a crate or a wreck: with luck a part the car still needs */
    public void maybePart(Player finder) {
        if (cars.isEmpty() || ThreadLocalRandom.current().nextDouble() >= PART_CHANCE) return;
        String de, en;
        if (!tyre) {
            tyre = true;
            de = "Reifen";
            en = "Tyre";
        } else if (!battery) {
            battery = true;
            de = "Autobatterie";
            en = "Car battery";
        } else {
            fuel++;
            de = "Benzinkanister";
            en = "Fuel can";
        }
        Lang.broadcast("%s§b%s §7hat ein Teil fürs §bFluchtauto §7gefunden: §b%s", "%s§b%s §7found a part for the §bescape car§7: §b%s",
                plugin.getMonstersPrefix(), finder.getName(), Lang.t(finder, de, en));
        finder.playSound(finder.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0F, 1.2F);
    }

    public void use(GamePlayer gamePlayer, String station) {
        int index = Integer.parseInt(station.substring(STATION.length()));
        if (index < 0 || index >= cars.size()) return;
        Car car = cars.get(index);
        Player player = gamePlayer.getPlayer();
        if (car.driving) return;
        // Function test on the build server: the parts are there
        if (BauTest.on()) {
            tyre = battery = true;
            fuel = Math.max(fuel, 1);
        }
        if (!car.fixed) {
            if (!tyre || !battery || fuel <= 0) {
                Lang.note(player, Lang.f(player, "§7Fluchtauto braucht: %s §7Reifen §8· %s §7Batterie §8· %s §7Benzin",
                        "§7Escape car needs: %s §7tyre §8· %s §7battery §8· %s §7fuel", mark(tyre), mark(battery), mark(fuel > 0)));
                Sounds.deny(player);
                return;
            }
            car.fixed = true;
            decorationManager.setModel(car.display, FIXED_ID);
            Location at = car.display.getLocation().add(0, 1, 0);
            at.getWorld().playSound(at, Sound.BLOCK_ANVIL_USE, 1.0F, 0.8F);
            at.getWorld().spawnParticle(Particle.CRIT, at, 20, 1.2, 0.5, 1.2, 0.1);
            Lang.broadcast("%s§b%s §7hat das §bFluchtauto §7repariert!", "%s§b%s §7repaired the §bescape car§7!", plugin.getMonstersPrefix(), player.getName());
            return;
        }
        if (fuel <= 0) {
            Lang.note(player, Lang.t(player, "§7Der Tank ist leer · finde einen §bBenzinkanister", "§7The tank is empty · find a §bfuel can"));
            Sounds.deny(player);
            return;
        }
        fuel--;
        drive(car, player);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player && seats.contains(event.getDismounted()) && event.isCancellable()) event.setCancelled(true);
    }

    private static String mark(boolean have) {
        return have ? "§a✔" : "§c✘";
    }

    // The player sits on an invisible seat, the car follows; steering with the look, W/S for gas and brake
    private void drive(Car car, Player driver) {
        car.driving = true;
        car.barriers.forEach(block -> {
            Restore.block(block);
            block.setType(Material.AIR, false);
        });
        Restore.display(car.display);
        Location start = car.display.getLocation();
        Location seatAt = start.clone().add(0, SEAT, 0);
        seatAt.setYaw(0);
        ItemDisplay seat = start.getWorld().spawn(seatAt, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setTeleportDuration(1);
        });
        seat.addPassenger(driver);
        seats.add(seat);
        car.display.setTeleportDuration(1);
        ItemDisplay view = roofless(car, driver);
        engine(start, driver, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.6F, 0.5F);
        Overlay.small(driver, Overlay.line((char) 0, COLOR, "FLUCHTAUTO", "W/S fahren · Blick lenkt · 20 s"),
                Overlay.line((char) 0, COLOR, "ESCAPE CAR", "W/S drive · look to steer · 20 s"));

        new BukkitRunnable() {
            int ticks, nextBeat;
            double speed;

            @Override
            public void run() {
                boolean end = ++ticks > DRIVE_TICKS || !driver.isOnline() || !seat.getPassengers().contains(driver);
                if (end) {
                    seats.remove(seat);
                    seat.eject();
                    seat.remove();
                    if (view != null) view.remove();
                    if (driver.isOnline()) driver.showEntity(plugin, car.display);
                    car.hitbox.teleport(car.middle());
                    car.driving = false;
                    Location at = car.display.getLocation();
                    engine(at, driver, Sound.BLOCK_PISTON_CONTRACT, 0.8F, 0.6F);
                    engine(at, driver, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5F, 0.5F);
                    cancel();
                    return;
                }
                // Starting: the engine turns over a few times
                if (ticks <= 10 && ticks % 3 == 1) engine(car.display.getLocation(), driver, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7F, 0.45F + ticks * 0.03F);
                var input = driver.getCurrentInput();
                if (input.isForward()) speed = Math.min(MAX_SPEED, speed + ACCELERATION);
                else if (input.isBackward()) speed = Math.max(-MAX_SPEED / 2, speed - BRAKE);
                else speed *= 0.92;
                float yaw = driver.getLocation().getYaw();
                Vector forward = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
                Location now = car.display.getLocation();
                Location next = now.clone().add(forward.clone().multiply(speed));
                Block ahead = next.getBlock();
                // One block up or down is fine, a wall or a deeper drop stops it
                Block below = ahead.getRelative(0, -1, 0);
                if (ahead.getType().isSolid()) {
                    if (!ahead.getRelative(0, 1, 0).getType().isSolid()) next.add(0, 1, 0);
                    else next = null;
                } else if (!below.getType().isSolid()) {
                    if (below.getRelative(0, -1, 0).getType().isSolid()) next.subtract(0, 1, 0);
                    else next = null;
                }
                if (next == null) {
                    if (Math.abs(speed) > 0.2) {
                        engine(now, driver, Sound.BLOCK_ANVIL_LAND, 0.5F, 1.4F);
                        engine(now, driver, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.6F, 1.2F);
                    }
                    next = now.clone();
                    speed = 0;
                }
                // The model's front is -x, so it turns a quarter from the look
                next.setYaw(yaw + 90);
                next.setPitch(0);
                car.display.teleport(next);
                if (view != null) view.teleport(next);
                // A turned seat would turn the driver too, so it keeps its yaw
                Location seatAt = next.clone().add(0, SEAT, 0);
                seatAt.setYaw(0);
                seat.teleport(seatAt, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                if (Math.abs(speed) > 0.25) runOver(next.clone().add(forward.clone().multiply(Math.signum(speed) * 1.5)), driver);
                if (ticks % 8 == 0) next.getWorld().spawnParticle(Particle.SMOKE, next.clone().add(forward.clone().multiply(-2)).add(0, 0.4, 0), 2, 0.1, 0.1, 0.1, 0.01);
                sounds(next, input.isBackward());
            }

            // The engine beats faster and higher with the speed, the tyres roll, braking squeaks
            private void sounds(Location at, boolean braking) {
                float share = (float) (Math.abs(speed) / MAX_SPEED);
                if (ticks > 10 && --nextBeat <= 0) {
                    engine(at, driver, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5F + 0.2F * share, 0.55F + 0.9F * share);
                    nextBeat = Math.max(2, Math.round(6 - 4 * share));
                }
                if (ticks % 10 == 0 && share > 0.15F) engine(at, driver, Sound.ENTITY_MINECART_RIDING, 0.2F + 0.3F * share, 0.6F + 0.6F * share);
                if (braking && speed > 0.15 && ticks % 4 == 0) engine(at, driver, Sound.BLOCK_GRINDSTONE_USE, 0.35F, 1.8F);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Heard around the car, the driver a little louder
    private static void engine(Location at, Player driver, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, volume, pitch);
        if (driver.isOnline()) driver.playSound(driver.getLocation(), sound, volume * 0.5F, pitch);
    }

    // Inside the roof covers half the view: the driver sees the car without it, everybody else the normal one
    private ItemDisplay roofless(Car car, Player driver) {
        if (!decorationManager.knownItem(ROOFLESS_ID)) return null;
        ItemDisplay view = car.display.getWorld().spawn(car.display.getLocation(), ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setItemStack(decorationManager.modelItem(ROOFLESS_ID));
            display.setItemDisplayTransform(car.display.getItemDisplayTransform());
            display.setTransformation(car.display.getTransformation());
            display.setTeleportDuration(1);
        });
        // Nexo's scale of the car, the roofless model is the same car
        decorationManager.look(view, FIXED_ID);
        driver.showEntity(plugin, view);
        driver.hideEntity(plugin, car.display);
        return view;
    }

    private static void runOver(Location front, Player driver) {
        for (Monster monster : front.getWorld().getNearbyEntitiesByType(Monster.class, front, HIT_RADIUS)) {
            if (!monster.isValid() || monster.isDead() || monster.getNoDamageTicks() > 0) continue;
            ShotDamage.deal(monster, HIT_DAMAGE, driver);
            Vector away = monster.getLocation().toVector().subtract(front.toVector()).setY(0.4);
            if (away.lengthSquared() > 0.01) monster.setVelocity(away.normalize().multiply(0.8));
            front.getWorld().playSound(front, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F);
            engine(front, driver, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0F, 0.8F);
        }
    }
}
