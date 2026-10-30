package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/***
 * Rope ladders and plank bridges: the map builder places them, in the round they are missing until a player
 * puts them up with parts. A ladder then takes the player up onto the edge it hangs on.
 */
public class Deployables implements BuildPart {
    // Bridges are pieces of 1 block (bruecke, bruecke_ende), touching pieces are laid out together
    private static final String LADDER_ID = "monsters_strickleiter", BRIDGE_ID = "monsters_bruecke", BRIDGE_END_ID = "monsters_bruecke_ende",
            STATION = "auslegen#";
    private static final int LADDER_PRICE = 3, BRIDGE_PRICE = 5;
    private static final int MAX_CLIMB = 16;
    private static final double CLIMB_SPEED = 0.3;
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;
    @Inject private UserManager userManager;

    private final List<Spot> spots = new ArrayList<>();
    private final Set<UUID> climbing = new HashSet<>();

    private static final class Spot {
        private final ItemDisplay display;
        private final String id;
        private final Block block;
        private final List<Block> barriers;
        private DecorationManager.Sign sign;
        private boolean built;
        private Spot group = this;

        private Spot(ItemDisplay display, String id, Block block, List<Block> barriers) {
            this.display = display;
            this.id = id;
            this.block = block;
            this.barriers = barriers;
        }

        private boolean ladder() {
            return LADDER_ID.equals(id);
        }

        private Spot group() {
            return group == this ? this : (group = group.group());
        }
    }

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (!LADDER_ID.equals(id) && !BRIDGE_ID.equals(id) && !BRIDGE_END_ID.equals(id)) return false;
        Spot spot = new Spot(display, id, block, decorationManager.barriers(block, id));
        // Not there yet: no model, the gap stays open
        display.setItemStack(null);
        spot.barriers.forEach(barrier -> barrier.setType(Material.AIR, false));
        boolean ladder = spot.ladder();
        int price = ladder ? LADDER_PRICE : BRIDGE_PRICE;
        if (!ladder && join(spot)) {
            decorationManager.hitbox(block, STATION + spots.size(), 1.6F, 1.0F);
            spots.add(spot);
            return true;
        }
        spot.sign = decorationManager.sign(block, ladder ? "Strickleiter" : "Bretterbrücke", ladder ? "Rope ladder" : "Plank bridge",
                Parts.price(price, false), Parts.price(price, true),
                ladder ? "Rechtsklick zum Anbringen" : "Rechtsklick zum Auslegen", ladder ? "Right-click to hang up" : "Right-click to lay out", 1.2);
        decorationManager.hitbox(block, STATION + spots.size(), ladder ? 1.0F : 1.6F, ladder ? 2.5F : 1.0F);
        spots.add(spot);
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
        if (index < 0 || index >= spots.size()) return true;
        Spot spot = spots.get(index).group();
        if (!spot.built) build(gamePlayer, spot);
        else if (spot.ladder()) climb(gamePlayer.getPlayer(), spot);
        return true;
    }

    private void build(GamePlayer gamePlayer, Spot spot) {
        int price = spot.ladder() ? LADDER_PRICE : BRIDGE_PRICE;
        if (!Parts.take(gamePlayer, price)) return;
        spot.built = true;
        for (Spot piece : spots) {
            if (piece.group() != spot) continue;
            decorationManager.setModel(piece.display, piece.id);
            piece.barriers.forEach(barrier -> barrier.setType(Material.BARRIER, false));
        }
        if (spot.sign != null) decorationManager.removeSign(spot.sign);
        Location center = spot.block.getLocation().add(0.5, 0.5, 0.5);
        center.getWorld().spawnParticle(Particle.BLOCK, center, 20, 0.6, 0.4, 0.6, Material.OAK_PLANKS.createBlockData());
        center.getWorld().playSound(center, spot.ladder() ? Sound.BLOCK_LADDER_PLACE : Sound.BLOCK_WOOD_PLACE, 1.2F, 0.9F);
        String de = spot.ladder() ? "Strickleiter angebracht" : "Brücke ausgelegt";
        String en = spot.ladder() ? "Rope ladder hung up" : "Bridge laid out";
        Overlay.small(gamePlayer.getPlayer(), Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>" + de + "</b> <color:" + Overlay.GREY + ">· -</color>" + Parts.price(price, false)),
                Overlay.icon(Overlay.PARTS, Parts.COLOR, "<b>" + en + "</b> <color:" + Overlay.GREY + ">· -</color>" + Parts.price(price, true)));
    }

    // A bridge piece next to another one belongs to its bridge; true when it found one (then it has no own sign)
    private boolean join(Spot spot) {
        Spot joined = null;
        for (Spot other : spots) {
            if (other.ladder() || other.group().built || !other.block.getWorld().equals(spot.block.getWorld())
                    || other.block.getY() != spot.block.getY()
                    || Math.abs(other.block.getX() - spot.block.getX()) + Math.abs(other.block.getZ() - spot.block.getZ()) != 1) continue;
            Spot group = other.group();
            if (joined == null) {
                joined = group;
            } else if (group != joined) {
                // The piece connects two bridges: one sign is enough
                if (group.sign != null) decorationManager.removeSign(group.sign);
                group.sign = null;
                group.group = joined;
            }
        }
        if (joined == null) return false;
        spot.group = joined;
        return true;
    }

    // Up the wall next to the ladder until there is room to stand on top
    private void climb(Player player, Spot spot) {
        Location top = top(spot.block);
        if (top == null || !climbing.add(player.getUniqueId())) return;
        Location bottom = spot.block.getLocation().add(0.5, 0, 0.5);
        player.teleport(bottom.clone().setDirection(top.toVector().subtract(bottom.toVector()).setY(0)));

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                boolean stop = !player.isOnline() || userManager.isPlayerDead(player.getUniqueId());
                // Stuck under something: after a while straight onto the edge
                if (!stop && player.getLocation().getY() < top.getY() && ticks <= MAX_CLIMB * 5) {
                    player.setVelocity(new Vector(0, CLIMB_SPEED, 0));
                    player.setFallDistance(0);
                    if (ticks % 4 == 0) player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LADDER_STEP, 0.8F, 1.0F);
                    return;
                }
                if (!stop) {
                    Location onTop = top.clone();
                    onTop.setYaw(player.getLocation().getYaw());
                    onTop.setPitch(player.getLocation().getPitch());
                    player.teleport(onTop);
                }
                climbing.remove(player.getUniqueId());
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Where the player stands after climbing: on the wall block beside the ladder, first spot with two free blocks */
    private static Location top(Block ladder) {
        for (BlockFace side : SIDES) {
            Block wall = ladder.getRelative(side).getRelative(BlockFace.UP);
            if (!wall.getType().isSolid()) continue;
            for (int up = 1; up <= MAX_CLIMB; up++) {
                Block feet = wall.getRelative(0, up, 0);
                if (feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable() && !feet.getRelative(BlockFace.DOWN).isPassable()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }
}
