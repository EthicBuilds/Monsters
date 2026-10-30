package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/***
 * Freight elevator: one platform, a crank on every floor it stops at (within a few blocks beside it).
 * A crank calls the platform to its floor, at that floor it sends it to the next one. Who stands on it rides along.
 */
public class Elevators implements BuildPart {
    private static final String PLATFORM_ID = "monsters_aufzug", CRANK_ID = "monsters_aufzug_kurbel", STATION = "kurbel#";
    private static final double CRANK_REACH = 8, SPEED = 0.15;

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;

    private final List<Platform> platforms = new ArrayList<>();
    private final List<Block> cranks = new ArrayList<>();

    private static final class Platform {
        private final ItemDisplay display;
        private final List<int[]> footprint = new ArrayList<>();
        private Block base;
        private boolean moving;

        private Platform(ItemDisplay display, Block base, List<Block> barriers) {
            this.display = display;
            this.base = base;
            for (Block barrier : barriers) {
                footprint.add(new int[]{barrier.getX() - base.getX(), barrier.getY() - base.getY(), barrier.getZ() - base.getZ()});
            }
        }

        private void barriers(Material material) {
            for (int[] offset : footprint) {
                Block block = base.getRelative(offset[0], offset[1], offset[2]);
                if (material == Material.BARRIER ? block.getType().isAir() : block.getType() == Material.BARRIER) block.setType(material, false);
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

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (PLATFORM_ID.equals(id)) {
            Platform platform = new Platform(display, block, decorationManager.barriers(block, id));
            display.setTeleportDuration(1);
            platforms.add(platform);
        } else if (CRANK_ID.equals(id)) {
            decorationManager.hitbox(block, STATION + cranks.size(), 1.0F, 1.4F);
            cranks.add(block);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean use(GamePlayer gamePlayer, String station) {
        if (!station.startsWith(STATION)) return false;
        int index;
        try {
            index = Integer.parseInt(station.substring(STATION.length()));
        } catch (NumberFormatException e) {
            return true;
        }
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

    private Platform nearest(Block crank) {
        Platform best = null;
        double bestDistance = CRANK_REACH * CRANK_REACH;
        for (Platform platform : platforms) {
            if (!platform.base.getWorld().equals(crank.getWorld())) continue;
            double dx = platform.base.getX() - crank.getX(), dz = platform.base.getZ() - crank.getZ();
            if (dx * dx + dz * dz <= bestDistance) {
                bestDistance = dx * dx + dz * dz;
                best = platform;
            }
        }
        return best;
    }

    // Floor away while moving, the riders are carried by velocity, at the end the floor comes back at the new height
    private void move(Platform platform, int targetY) {
        platform.moving = true;
        platform.barriers(Material.AIR);
        double direction = Math.signum(targetY - platform.base.getY());
        Location start = platform.display.getLocation();
        double distance = Math.abs(targetY - platform.base.getY());
        List<Player> riders = new ArrayList<>();
        for (Player player : start.getWorld().getPlayers()) if (platform.carries(player)) riders.add(player);
        start.getWorld().playSound(start, Sound.BLOCK_PISTON_EXTEND, 1.0F, 0.6F);

        new BukkitRunnable() {
            double moved;

            @Override
            public void run() {
                moved = Math.min(distance, moved + SPEED);
                Location at = start.clone().add(0, direction * moved, 0);
                platform.display.teleport(at);
                for (Player rider : riders) {
                    if (!rider.isOnline()) continue;
                    rider.setVelocity(new Vector(0, direction > 0 ? SPEED + 0.08 : -SPEED, 0));
                    rider.setFallDistance(0);
                }
                if ((int) (moved / SPEED) % 10 == 0) at.getWorld().playSound(at, Sound.BLOCK_CHAIN_STEP, 0.8F, 0.6F);
                if (moved < distance) return;

                platform.base = platform.base.getRelative(0, (int) (direction * distance), 0);
                platform.barriers(Material.BARRIER);
                // Riders that sank into the floor stand on it again
                for (Player rider : riders) {
                    if (!rider.isOnline()) continue;
                    double floor = platform.base.getY() + 1;
                    if (rider.getLocation().getY() < floor && platform.carries(rider)) {
                        Location up = rider.getLocation();
                        up.setY(floor);
                        rider.teleport(up);
                    }
                }
                at.getWorld().playSound(at, Sound.BLOCK_PISTON_CONTRACT, 1.0F, 0.6F);
                platform.moving = false;
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
