package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Escape car (user 31.10.): a wreck that the team repairs with a tyre, a car battery and a fuel can out of crates
 * and wrecks. Then it drives 20 seconds (W/S, steering with the look) and runs monsters over. Each drive takes a fuel can.
 */
public class EscapeCar {
    public static final String STATION = "fluchtauto#";
    static final String BROKEN_ID = "monsters_fluchtauto_kaputt";
    private static final String FIXED_ID = "monsters_fluchtauto_repariert";
    private static final double PART_CHANCE = 0.12;
    private static final int DRIVE_TICKS = 20 * 20;
    private static final double MAX_SPEED = 0.6, ACCELERATION = 0.04, BRAKE = 0.06, HIT_RADIUS = 2.2, HIT_DAMAGE = 60;
    private static final String COLOR = "#50839D";

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;

    private final List<Car> cars = new ArrayList<>();
    // Found by the whole team
    private boolean tyre, battery;
    private int fuel;

    private static final class Car {
        private final ItemDisplay display;
        private final List<Block> barriers;
        private final Interaction hitbox;
        private boolean fixed, driving;

        private Car(ItemDisplay display, List<Block> barriers, Interaction hitbox) {
            this.display = display;
            this.barriers = barriers;
            this.hitbox = hitbox;
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
        Block middle = block.getRelative((int) Math.round(x / n), 0, (int) Math.round(z / n));
        cars.add(new Car(display, barriers, decorationManager.hitbox(middle, STATION + cars.size(), 3.2F, 1.8F)));
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

    private static String mark(boolean have) {
        return have ? "§a✔" : "§c✘";
    }

    // The player sits on an invisible seat, the car follows; steering with the look, W/S for gas and brake
    private void drive(Car car, Player driver) {
        car.driving = true;
        car.barriers.forEach(block -> block.setType(Material.AIR, false));
        Location start = car.display.getLocation();
        Location seatAt = start.clone().add(0, 0.4, 0);
        seatAt.setYaw(0);
        ItemDisplay seat = start.getWorld().spawn(seatAt, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setTeleportDuration(1);
        });
        seat.addPassenger(driver);
        car.display.setTeleportDuration(1);
        start.getWorld().playSound(start, Sound.ENTITY_MINECART_RIDING, 1.0F, 0.6F);
        Overlay.small(driver, Overlay.line((char) 0, COLOR, "FLUCHTAUTO", "W/S fahren · Blick lenkt · 20 s"),
                Overlay.line((char) 0, COLOR, "ESCAPE CAR", "W/S drive · look to steer · 20 s"));

        new BukkitRunnable() {
            int ticks;
            double speed;

            @Override
            public void run() {
                boolean end = ++ticks > DRIVE_TICKS || !driver.isOnline() || !seat.getPassengers().contains(driver);
                if (end) {
                    seat.eject();
                    seat.remove();
                    car.hitbox.teleport(car.display.getLocation());
                    car.driving = false;
                    cancel();
                    return;
                }
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
                    next = now.clone();
                    speed = 0;
                }
                // The model's front is -z, so it turns half round from the look
                next.setYaw(yaw + 180);
                next.setPitch(0);
                car.display.teleport(next);
                // A turned seat would turn the driver too, so it keeps its yaw
                Location seatAt = next.clone().add(0, 0.4, 0);
                seatAt.setYaw(0);
                seat.teleport(seatAt, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                if (Math.abs(speed) > 0.25) runOver(next.clone().add(forward.clone().multiply(Math.signum(speed) * 1.5)), driver);
                if (ticks % 8 == 0) next.getWorld().spawnParticle(Particle.SMOKE, next.clone().add(forward.clone().multiply(-2)).add(0, 0.4, 0), 2, 0.1, 0.1, 0.1, 0.01);
                if (ticks % 20 == 0) next.getWorld().playSound(next, Sound.ENTITY_MINECART_RIDING, 0.6F, 0.5F + (float) Math.abs(speed));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void runOver(Location front, Player driver) {
        for (Monster monster : front.getWorld().getNearbyEntitiesByType(Monster.class, front, HIT_RADIUS)) {
            if (!monster.isValid() || monster.isDead() || monster.getNoDamageTicks() > 0) continue;
            ShotDamage.deal(monster, HIT_DAMAGE, driver);
            Vector away = monster.getLocation().toVector().subtract(front.toVector()).setY(0.4);
            if (away.lengthSquared() > 0.01) monster.setVelocity(away.normalize().multiply(0.8));
            front.getWorld().playSound(front, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.7F);
        }
    }
}
