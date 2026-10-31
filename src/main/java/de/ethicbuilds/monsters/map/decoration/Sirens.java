package de.ethicbuilds.monsters.map.decoration;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/***
 * Warning sirens of the map (Nexo deco monsters_sirene, monsters_sirene_mast): before a horde they howl and a red
 * light turns at the top
 */
public final class Sirens {
    private static final String ID = "monsters_sirene";
    // Height of the horn above the furniture block, the mast is scaled up by Nexo
    private static final double MAST_TOP = 3.4, WALL_TOP = 0.5;
    private static final int HOWL_TICKS = 6 * 20;
    private static final double LIGHT_RADIUS = 0.6;
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0xFF3020), 1.3F);
    private static final List<Location> horns = new ArrayList<>();

    private Sirens() {}

    static boolean register(ItemDisplay display, String id) {
        if (!id.startsWith(ID)) return false;
        horns.add(display.getLocation().add(0, id.endsWith("_mast") ? MAST_TOP : WALL_TOP, 0));
        return true;
    }

    /** A horde is coming: every siren howls for a few seconds */
    public static void howl() {
        if (horns.isEmpty()) return;
        new BukkitRunnable() {
            int ticks;
            final List<Block> lit = new ArrayList<>();

            @Override
            public void run() {
                if (ticks >= HOWL_TICKS) {
                    lit.forEach(block -> { if (block.getType() == Material.LIGHT) block.setType(Material.AIR, false); });
                    cancel();
                    return;
                }
                double angle = ticks * 0.35;
                for (Location horn : horns) {
                    if (!horn.isChunkLoaded()) continue;
                    if (ticks == 0) light(horn.getBlock(), lit);
                    // Rising and falling howl, the goat horn sounds like a siren
                    if (ticks % 30 == 0) horn.getWorld().playSound(horn, Sound.ITEM_GOAT_HORN_SOUND_7, SoundCategory.RECORDS, 4.0F, 0.7F);
                    if (ticks % 2 == 0) {
                        horn.getWorld().spawnParticle(Particle.DUST, horn.clone().add(Math.cos(angle) * LIGHT_RADIUS, 0, Math.sin(angle) * LIGHT_RADIUS), 2, 0.05, 0.05, 0.05, RED);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 1L);
    }

    private static void light(Block block, List<Block> lit) {
        if (!block.getType().isAir()) return;
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(12);
        block.setBlockData(light, false);
        lit.add(block);
    }
}
