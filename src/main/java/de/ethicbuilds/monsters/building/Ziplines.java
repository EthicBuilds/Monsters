package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import io.papermc.paper.entity.TeleportFlag;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/***
 * Zipline: the top and the bottom holder are paired (nearest bottom below a top), the rope is one rope model
 * stretched and turned between them. Right-click at the top slides the player down.
 */
public class Ziplines implements BuildPart {
    private static final String TOP_ID = "monsters_seilrutsche_oben", BOTTOM_ID = "monsters_seilrutsche_unten",
            ROPE_ID = "monsters_seil", STATION = "seilrutsche#";
    // Where the rope sits on the holders, above the furniture block
    private static final double ROPE_HEIGHT = 2.2;
    private static final double MAX_LENGTH = 80, SPEED = 0.7;
    // The player hangs this far below the rope
    private static final double HANG = 2.1;

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;
    @Inject private UserManager userManager;

    private final List<Block> tops = new ArrayList<>();
    private final List<Block> bottoms = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final Set<UUID> riding = new HashSet<>();

    private record Line(Location from, Location to) {}

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (TOP_ID.equals(id)) tops.add(block);
        else if (BOTTOM_ID.equals(id)) bottoms.add(block);
        else return false;
        // Both holders may come in any order, so pairing waits a tick for the rest of the chunk
        plugin.getServer().getScheduler().runTask(plugin, this::pair);
        return true;
    }

    private void pair() {
        for (Block top : List.copyOf(tops)) {
            Block best = null;
            for (Block bottom : bottoms) {
                if (!bottom.getWorld().equals(top.getWorld()) || bottom.getY() >= top.getY()) continue;
                double distance = bottom.getLocation().distance(top.getLocation());
                if (distance <= MAX_LENGTH && (best == null || distance < best.getLocation().distance(top.getLocation()))) best = bottom;
            }
            if (best == null) continue;
            tops.remove(top);
            bottoms.remove(best);
            Location from = top.getLocation().add(0.5, ROPE_HEIGHT, 0.5);
            Location to = best.getLocation().add(0.5, ROPE_HEIGHT, 0.5);
            rope(from, to);
            decorationManager.hitbox(top, STATION + lines.size(), 1.4F, 2.4F);
            lines.add(new Line(from, to));
        }
    }

    // One rope model (1 block along +Z, centred) stretched to the full length and turned towards the bottom
    private void rope(Location from, Location to) {
        Vector along = to.toVector().subtract(from.toVector());
        float length = (float) along.length();
        Location middle = from.clone().add(along.clone().multiply(0.5));
        middle.setYaw(0);
        middle.setPitch(0);
        float yaw = (float) Math.atan2(along.getX(), along.getZ());
        float pitch = (float) -Math.atan2(along.getY(), Math.hypot(along.getX(), along.getZ()));
        ItemStack item = new ItemStack(Material.PAPER);
        GameItems.setModel(item, ROPE_ID);
        from.getWorld().spawn(middle, ItemDisplay.class, rope -> {
            rope.setItemStack(item);
            rope.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            rope.setPersistent(false);
            rope.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(yaw).rotateX(pitch),
                    new Vector3f(1, 1, length), new Quaternionf()));
            rope.setViewRange(4.0F);
        });
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
        if (index >= 0 && index < lines.size()) ride(gamePlayer.getPlayer(), lines.get(index));
        return true;
    }

    // The player sits on an invisible display that moves along the rope, smooth by teleport interpolation
    private void ride(Player player, Line line) {
        if (!riding.add(player.getUniqueId())) return;
        Vector along = line.to().toVector().subtract(line.from().toVector());
        double length = along.length();
        Vector step = along.clone().normalize().multiply(SPEED);
        Location start = line.from().clone().subtract(0, HANG, 0);
        start.setYaw(player.getLocation().getYaw());
        ItemDisplay seat = player.getWorld().spawn(start, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setTeleportDuration(1);
            display.setBillboard(Display.Billboard.FIXED);
        });
        seat.addPassenger(player);
        player.getWorld().playSound(start, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0F, 0.6F);

        new BukkitRunnable() {
            double travelled;

            @Override
            public void run() {
                travelled += SPEED;
                boolean end = travelled >= length || !player.isOnline() || !seat.getPassengers().contains(player)
                        || userManager.isPlayerDead(player.getUniqueId());
                if (end) {
                    seat.eject();
                    seat.remove();
                    riding.remove(player.getUniqueId());
                    if (player.isOnline()) player.setFallDistance(0);
                    cancel();
                    return;
                }
                Location next = seat.getLocation().add(step);
                seat.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                if (((int) (travelled / SPEED)) % 4 == 0) {
                    player.getWorld().spawnParticle(Particle.CRIT, next.clone().add(0, HANG, 0), 1, 0, 0, 0, 0);
                    player.getWorld().playSound(next, Sound.BLOCK_CHAIN_STEP, 0.4F, 1.6F);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        Lang.note(player, Lang.t(player, "§7Seilrutsche · §eSchleichen §7zum Abspringen", "§7Zipline · §esneak §7to jump off"));
    }
}
