package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.map.elements.Door;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/***
 * Breaking doors open with the crowbar
 */
public class DoorManager {
    private static final int BREAK_TICKS = 40;
    private static final double MAX_DISTANCE = 4.5;

    @Inject private Main plugin;
    @Inject private UserManager userManager;

    private final Set<Door> breaking = new HashSet<>();

    public void breakOpen(GamePlayer gamePlayer, Door door, Runnable onOpen) {
        if (!breaking.add(door)) return;

        Player player = gamePlayer.getPlayer();
        List<Location> blocks = List.copyOf(door.getClickableLocations());
        Location center = center(blocks);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!canContinue(player, center)) {
                    stop(door, blocks, player);
                    player.sendActionBar("§cAufbrechen abgebrochen");
                    cancel();
                    return;
                }

                ticks++;
                float progress = (float) ticks / BREAK_TICKS;

                if (ticks % 8 == 0) {
                    player.swingMainHand();
                    center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8F, 1.3F);
                    center.getWorld().playSound(center, Sound.BLOCK_CHAIN_HIT, 0.6F, 0.7F);
                    for (Location block : blocks) {
                        center.getWorld().spawnParticle(Particle.BLOCK, block.clone().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3, block.getBlock().getBlockData());
                    }
                }
                for (Location block : blocks) {
                    Bukkit.getOnlinePlayers().forEach(p -> p.sendBlockDamage(block, Math.min(progress, 0.99F), player.getEntityId()));
                }
                player.sendActionBar(progressBar(progress));

                if (ticks >= BREAK_TICKS) {
                    stop(door, blocks, player);
                    center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.2F, 1.0F);
                    center.getWorld().spawnParticle(Particle.CLOUD, center, 15, 0.6, 0.8, 0.6, 0.02);
                    onOpen.run();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean canContinue(Player player, Location center) {
        return player.isOnline()
                && !userManager.isPlayerDead(player.getUniqueId())
                && GameItems.isCrowbar(player.getInventory().getItemInMainHand())
                && player.getWorld().equals(center.getWorld())
                && player.getLocation().distance(center) <= MAX_DISTANCE;
    }

    private void stop(Door door, List<Location> blocks, Player player) {
        for (Location block : blocks) {
            Bukkit.getOnlinePlayers().forEach(p -> p.sendBlockDamage(block, 0F, player.getEntityId()));
        }
        breaking.remove(door);
    }

    private Location center(List<Location> blocks) {
        Location center = new Location(blocks.getFirst().getWorld(), 0, 0, 0);
        for (Location block : blocks) center.add(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        return center.multiply(1.0 / blocks.size());
    }

    private String progressBar(float progress) {
        int filled = Math.round(progress * 20);
        return "§7Aufbrechen §a" + "|".repeat(filled) + "§8" + "|".repeat(20 - filled);
    }
}
