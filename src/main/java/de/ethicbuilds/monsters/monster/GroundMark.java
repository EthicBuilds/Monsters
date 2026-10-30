package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/***
 * Markers on the ground from the design session (font monsters:ziel): a circle shows where something hits,
 * a thin ring grows up to it, shortly before the circle changes to "soon". Each kind has circle, soon and ring.
 */
public final class GroundMark {
    public static final char CRYSTAL = '', WEB = '', DANGER = '';
    private static final Key FONT = Key.key("monsters", "ziel");
    private static final int SOON_TICKS = 6;
    private static final Color RED = Color.fromRGB(0xFF4838);

    private final TextDisplay circle, ring;

    private GroundMark(TextDisplay circle, TextDisplay ring) {
        this.circle = circle;
        this.ring = ring;
    }

    /** Bright edge of the marker image at 28.75 px, 0.025 blocks per px */
    public static float scaleFor(double radius) {
        return (float) (radius / 0.71875);
    }

    /** Warning circle at the spot, the ring is full after the given ticks, then everything goes */
    public static GroundMark warn(Location at, char kind, double radius, int ticks) {
        float full = scaleFor(radius);
        GroundMark mark = new GroundMark(decal(at, kind, full, 0.02), decal(at, (char) (kind + 2), 0.3F, 0.03));
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                if (!mark.circle.isValid() || tick >= ticks) {
                    mark.clear();
                    cancel();
                    return;
                }
                if (tick % 2 == 0) {
                    mark.ring.setInterpolationDelay(0);
                    mark.ring.setInterpolationDuration(2);
                    mark.ring.setTransformation(flat((float) (0.3 + (full - 0.3) * tick / ticks)));
                }
                if (tick == ticks - SOON_TICKS) mark.circle.text(mark((char) (kind + 1)));
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 1L);
        return mark;
    }

    /** Only the resting circle, no growing ring (cheaper when there are many), "soon" at the end */
    public static void still(Location at, char kind, double radius, int ticks) {
        TextDisplay circle = decal(at, kind, scaleFor(radius), 0.02);
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (circle.isValid()) circle.text(mark((char) (kind + 1)));
        }, Math.max(1, ticks - SOON_TICKS));
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), circle::remove, ticks);
    }

    public void clear() {
        circle.remove();
        ring.remove();
    }

    /** Red rings from 1 to 8 blocks within half a second, 3 ticks apart, with ground and red dust along the ring */
    public static void shockwave(Location at, int rings) {
        World world = at.getWorld();
        var blockData = at.clone().subtract(0, 0.5, 0).getBlock().getBlockData();
        Particle.DustOptions dust = new Particle.DustOptions(RED, 1.5F);
        for (int i = 0; i < rings; i++) {
            Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
                TextDisplay wave = decal(at, (char) (DANGER + 2), scaleFor(1), 0.05);
                new BukkitRunnable() {
                    int tick = 0;

                    @Override
                    public void run() {
                        if (tick == 1) {
                            wave.setInterpolationDelay(0);
                            wave.setInterpolationDuration(10);
                            wave.setTransformation(flat(scaleFor(8)));
                            wave.setTextOpacity((byte) 30);
                        }
                        if (tick % 2 == 0) {
                            double radius = 1 + 7 * tick / 10.0;
                            for (int p = 0; p < 24; p++) {
                                double angle = Math.PI * 2 * p / 24;
                                Location spot = at.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
                                world.spawnParticle(Particle.DUST, spot, 1, 0, 0, 0, 0, dust);
                                if (p % 3 == 0) world.spawnParticle(Particle.BLOCK, spot, 2, 0.1, 0.1, 0.1, blockData);
                            }
                        }
                        if (++tick > 11) {
                            wave.remove();
                            cancel();
                        }
                    }
                }.runTaskTimer(Main.getINSTANCE(), 0L, 1L);
            }, i * 3L);
        }
    }

    static TextDisplay decal(Location at, char glyph, float scale, double lift) {
        Location spot = at.clone().add(0, lift, 0);
        spot.setYaw(0);
        spot.setPitch(0);
        return at.getWorld().spawn(spot, TextDisplay.class, display -> {
            display.text(mark(glyph));
            display.setBillboard(Display.Billboard.FIXED);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setDefaultBackground(false);
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(flat(scale));
            display.setPersistent(false);
        });
    }

    static Component mark(char glyph) {
        return Component.text(glyph, NamedTextColor.WHITE).font(FONT);
    }

    // Lies flat facing up; the baseline sits 1.5 text pixels above the origin, moved back so the centre is on the spot
    static Transformation flat(float scale) {
        return new Transformation(new Vector3f(0, 0, 1.5F * 0.025F * scale), new AxisAngle4f((float) -Math.PI / 2, 1, 0, 0),
                new Vector3f(scale), new AxisAngle4f());
    }

    /** Top of the ground below the spot, so the circle lies on the floor */
    public static Location ground(Location spot) {
        Block block = spot.getBlock();
        for (int i = 0; i < 6 && !block.getRelative(0, -1, 0).isSolid(); i++) block = block.getRelative(0, -1, 0);
        if (!block.getRelative(0, -1, 0).isSolid()) return null;
        return block.getLocation().add(0.5, 0.02, 0.5);
    }
}
