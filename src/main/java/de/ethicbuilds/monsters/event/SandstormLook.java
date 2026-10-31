package de.ethicbuilds.monsters.event;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Look of the sandstorm from the design session: dust ring as warning, then two turning sand walls, growing clouds,
 * blowing bands and a frame on the screen of whoever stands inside. Only displays, moved every few ticks.
 */
final class SandstormLook {
    private static final double RADIUS = 8, OUTER_RADIUS = 8.7, BAND_EDGE = 7;
    private static final int RING = 16, CLOUDS = 8, BANDS = 3;
    private static final int CLOUD_CYCLE = 40, BAND_CYCLE = 30;
    private static final double[] BAND_HEIGHTS = {0.6, 1.4, 2.2};
    private static final Key OVERLAY = Key.key("monsters", "overlay");
    private static final Title.Times FRAME = Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ZERO);

    private final Location center;
    private final World world;
    // Direction the wind blows to, fixed for one storm
    private final double wind = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
    private final List<ItemDisplay> warning = new ArrayList<>(), inner = new ArrayList<>(), outer = new ArrayList<>(),
            clouds = new ArrayList<>(), bands = new ArrayList<>();
    private final Set<UUID> framed = new HashSet<>();
    private final double[] bandSide = new double[BANDS];
    private double warnTurn, innerTurn, outerTurn;
    private int nextGust = 60;

    SandstormLook(Location center) {
        this.center = center.clone();
        this.world = center.getWorld();
        for (int i = 0; i < RING; i++) warning.add(display("warnung", ring(i, warnTurn, RADIUS, 0.5), new Vector3f(1.6F, 1, 1)));
    }

    /** ticks since the start; active once the warning is over */
    void tick(int ticks, boolean active, Collection<? extends Player> players) {
        if (!active) {
            if (ticks % 5 == 0) {
                warnTurn += 3;
                move(warning, warnTurn, RADIUS, 0.5, 5);
            }
            return;
        }
        if (inner.isEmpty()) start();
        if (ticks % 5 == 0) {
            innerTurn += 5;
            outerTurn -= 3;
            move(inner, innerTurn, RADIUS, 1.5, 5);
            move(outer, outerTurn, OUTER_RADIUS, 1.5, 5);
        }
        for (int i = 0; i < CLOUDS; i++) {
            int phase = (ticks - i * 5) % CLOUD_CYCLE;
            if (phase == 0) cloudReset(clouds.get(i), i);
            else if (phase == 1) cloudGrow(clouds.get(i), i);
        }
        for (int i = 0; i < BANDS; i++) {
            int phase = (ticks - i * 10) % BAND_CYCLE;
            if (phase == 0) bandReset(bands.get(i), i);
            else if (phase == 1) bandBlow(bands.get(i), i);
        }
        if (ticks % 10 == 0) frames(players, ticks / 10 % 2 == 0);
        sounds(ticks, players);
        // A little fine grain, the displays carry the look
        if (ticks % 8 == 0) {
            world.spawnParticle(Particle.FALLING_DUST, randomInside(2.5), 1, 0.2, 0.2, 0.2, Material.SAND.createBlockData());
        }
    }

    void end(Collection<? extends Player> players) {
        for (List<ItemDisplay> list : List.of(warning, inner, outer, clouds, bands)) {
            list.forEach(ItemDisplay::remove);
            list.clear();
        }
        for (Player player : players) {
            if (framed.contains(player.getUniqueId())) fade(player, 20);
        }
        framed.clear();
    }

    private void start() {
        warning.forEach(ItemDisplay::remove);
        warning.clear();
        for (int i = 0; i < RING; i++) {
            inner.add(display("schleier", ring(i, innerTurn, RADIUS, 1.5), new Vector3f(1.6F, 1.6F, 1)));
            outer.add(display("schleier", ring(i, outerTurn, OUTER_RADIUS, 1.5), new Vector3f(1.7F, 1.3F, 1)));
        }
        for (int i = 0; i < CLOUDS; i++) {
            ItemDisplay cloud = display("wolke", cloudSpot(i, 0.5), new Vector3f(1));
            cloud.setBillboard(Display.Billboard.CENTER);
            clouds.add(cloud);
        }
        for (int i = 0; i < BANDS; i++) {
            ItemDisplay band = display("bahn", bandSpot(-BAND_EDGE, 0, BAND_HEIGHTS[i]), new Vector3f(3, 0.8F, 1));
            bands.add(band);
        }
    }

    // Spot i of a ring, the surface facing the middle
    private Location ring(int i, double turn, double radius, double height) {
        double angle = Math.PI * 2 * i / RING + Math.toRadians(turn);
        Location at = center.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
        at.setYaw((float) Math.toDegrees(Math.atan2(Math.cos(angle), -Math.sin(angle))));
        at.setPitch(0);
        return at;
    }

    private void move(List<ItemDisplay> ring, double turn, double radius, double height, int ticks) {
        for (int i = 0; i < ring.size(); i++) {
            ItemDisplay display = ring.get(i);
            display.setTeleportDuration(ticks);
            display.teleport(ring(i, turn, radius, height));
        }
    }

    private Location cloudSpot(int i, double height) {
        double angle = Math.PI * 2 * i / CLOUDS;
        return center.clone().add(Math.cos(angle) * RADIUS, height, Math.sin(angle) * RADIUS);
    }

    // Back small and low without sliding there, then up and bigger over the whole cycle
    private void cloudReset(ItemDisplay cloud, int i) {
        cloud.setTeleportDuration(0);
        cloud.teleport(cloudSpot(i, 0.5));
        cloud.setInterpolationDelay(0);
        cloud.setInterpolationDuration(0);
        cloud.setTransformation(scaled(new Vector3f(1)));
    }

    private void cloudGrow(ItemDisplay cloud, int i) {
        cloud.setTeleportDuration(CLOUD_CYCLE - 1);
        cloud.teleport(cloudSpot(i, 2.0));
        cloud.setInterpolationDelay(0);
        cloud.setInterpolationDuration(CLOUD_CYCLE - 1);
        cloud.setTransformation(scaled(new Vector3f(2.2F)));
    }

    // Along the wind: along = -edge is the windward side, side shifts sideways
    private Location bandSpot(double along, double side, double height) {
        double dx = Math.cos(wind), dz = Math.sin(wind);
        Location at = center.clone().add(dx * along - dz * side, height, dz * along + dx * side);
        // The width of the band lies in the wind, its surface across it
        at.setYaw((float) Math.toDegrees(Math.atan2(-dz, -dx)));
        at.setPitch(0);
        return at;
    }

    private void bandReset(ItemDisplay band, int i) {
        bandSide[i] = ThreadLocalRandom.current().nextDouble(-3, 3);
        band.setTeleportDuration(0);
        band.teleport(bandSpot(-BAND_EDGE, bandSide[i], BAND_HEIGHTS[i]));
    }

    private void bandBlow(ItemDisplay band, int i) {
        band.setTeleportDuration(BAND_CYCLE - 1);
        band.teleport(bandSpot(BAND_EDGE, bandSide[i], BAND_HEIGHTS[i]));
    }

    // Frame on the screen while standing in the storm, it fades once when leaving
    private void frames(Collection<? extends Player> players, boolean first) {
        for (Player player : players) {
            boolean inside = player.getWorld().equals(world) && player.getLocation().distanceSquared(center) <= RADIUS * RADIUS;
            if (inside) {
                framed.add(player.getUniqueId());
                player.showTitle(Title.title(frame(first ? '' : ''), Component.empty(), FRAME));
            } else if (framed.remove(player.getUniqueId())) {
                fade(player, 10);
            }
        }
    }

    private static void fade(Player player, int ticks) {
        player.showTitle(Title.title(frame(''), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ZERO, Duration.ofMillis(ticks * 50L))));
    }

    private static Component frame(char glyph) {
        return Component.text(glyph).font(OVERLAY).color(NamedTextColor.WHITE);
    }

    private void sounds(int ticks, Collection<? extends Player> players) {
        if (ticks % 40 == 0) {
            for (Player player : players) {
                if (player.getWorld().equals(world) && player.getLocation().distanceSquared(center) <= (RADIUS + 4) * (RADIUS + 4)) {
                    player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.35F, 0.5F);
                }
            }
        }
        if (--nextGust <= 0) {
            nextGust = ThreadLocalRandom.current().nextInt(60, 101);
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            world.playSound(center.clone().add(Math.cos(angle) * RADIUS, 1, Math.sin(angle) * RADIUS), Sound.ENTITY_BREEZE_IDLE_AIR, 0.5F,
                    (float) ThreadLocalRandom.current().nextDouble(0.6, 0.8));
        }
        if (ticks % 10 == 0) world.playSound(randomInside(0.2), Sound.BLOCK_SAND_FALL, 0.25F, 0.7F);
    }

    private Location randomInside(double height) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2), distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * RADIUS;
        return center.clone().add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
    }

    private ItemDisplay display(String name, Location at, Vector3f scale) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("monsters", "sturm/" + name));
        item.setItemMeta(meta);
        return world.spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(0.75F);
            display.setPersistent(false);
            display.setTransformation(scaled(scale));
        });
    }

    private static Transformation scaled(Vector3f scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), scale, new Quaternionf());
    }
}
