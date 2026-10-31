package de.ethicbuilds.monsters.player.manager;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.MapManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/***
 * Watchers: whoever comes into a running round from the lobby without playing in it only watches.
 * They start right in the view of a living player (spectator mode). Key 1 or a click on a player switches,
 * sneaking flies freely. They do not count as players: no coins, no statistics, no rewards.
 */
public class WatcherManager implements Listener {

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private MapManager mapManager;

    // Watched player, null while the watcher flies freely
    private final Map<UUID, Player> watchers = new HashMap<>();
    private final Map<UUID, Player> lastWatched = new HashMap<>();
    private final Set<UUID> switching = new HashSet<>();
    private BukkitRunnable check;

    public boolean isWatcher(UUID uuid) {
        return watchers.containsKey(uuid);
    }

    public void join(Player player) {
        watchers.put(player.getUniqueId(), null);
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.setInvulnerable(true);
        player.setCollidable(false);
        List<Player> living = living();
        player.teleport(living.isEmpty() ? mapManager.getMapConfiguration().getSpawn() : living.getFirst().getLocation());
        ChatStatus.send(player, true);
        Overlay.medium(player, Overlay.GREY,
                Overlay.line(Overlay.NEXT, "#3CDC6E", "Zuschauen", "die Runde läuft schon"),
                Overlay.line(Overlay.NEXT, "#3CDC6E", "Spectating", "the round is already running"));
        // A moment later, right after joining the camera does not attach yet
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isWatcher(player.getUniqueId())) cycle(player, 1);
        }, 10L);
        startCheck();
    }

    private List<Player> living() {
        return userManager.getLivingPlayers();
    }

    private void cycle(Player player, int direction) {
        List<Player> living = living();
        if (living.isEmpty()) {
            fly(player);
            player.sendActionBar(Lang.t(player, "§7Gerade steht niemand, dem du zuschauen kannst", "§7Nobody is standing right now"));
            return;
        }
        int index = living.indexOf(lastWatched.get(player.getUniqueId()));
        index = index < 0 ? (direction > 0 ? 0 : living.size() - 1) : Math.floorMod(index + direction, living.size());
        watch(player, living.get(index));
    }

    private void watch(Player player, Player target) {
        watchers.put(player.getUniqueId(), target);
        lastWatched.put(player.getUniqueId(), target);
        player.setGameMode(GameMode.SPECTATOR);
        camera(player, target);
        player.sendActionBar(Lang.f(player, "§7Du schaust §a%s §7zu §8· §7Taste 1: wechseln §8· §7Schleichen: frei fliegen",
                "§7Watching §a%s §8· §7Key 1: switch §8· §7Sneak: fly freely", target.getName()));
    }

    private void fly(Player player) {
        // Cleared first, otherwise leaving the camera below is cancelled
        watchers.put(player.getUniqueId(), null);
        camera(player, null);
        player.sendActionBar(Lang.t(player, "§7Frei fliegen §8· §7Taste 1 oder Klick auf einen Spieler: zuschauen",
                "§7Flying freely §8· §7Key 1 or click a player: watch"));
    }

    // Goes on to the next player when the watched one falls or leaves
    private void startCheck() {
        if (check != null) return;
        check = new BukkitRunnable() {
            @Override
            public void run() {
                if (watchers.isEmpty()) {
                    cancel();
                    check = null;
                    return;
                }
                for (Map.Entry<UUID, Player> entry : List.copyOf(watchers.entrySet())) {
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    Player target = entry.getValue();
                    if (player == null || target == null) continue;
                    if (!target.isOnline() || userManager.isPlayerDead(target.getUniqueId()) || !target.equals(player.getSpectatorTarget())) {
                        cycle(player, 1);
                    }
                }
            }
        };
        check.runTaskTimer(plugin, 10L, 10L);
    }

    // Sneaking leaves the view and flies freely
    @EventHandler
    public void onStopWatching(PlayerStopSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (watchers.get(player.getUniqueId()) == null || !event.getSpectatorTarget().isValid()) return;
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (watchers.get(player.getUniqueId()) != null) fly(player);
        });
    }

    @EventHandler
    public void onStartWatching(PlayerStartSpectatingEntityEvent event) {
        if (!isWatcher(event.getPlayer().getUniqueId())) return;
        if (!(event.getNewSpectatorTarget() instanceof Player target) || !living().contains(target)) event.setCancelled(true);
    }

    // The heads in the spectator menu (key 1) switch the player
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE || !isWatcher(player.getUniqueId())) return;
        event.setCancelled(true);
        if (switching.contains(player.getUniqueId())) return;
        living().stream().filter(p -> p.getWorld().equals(event.getTo().getWorld()) && p.getLocation().distanceSquared(event.getTo()) < 1)
                .findFirst().ifPresentOrElse(target -> watch(player, target),
                        () -> player.sendActionBar(Lang.t(player, "§7Du kannst nur lebenden Spielern zuschauen", "§7You can only watch living players")));
    }


    // Own camera switches: cancelled first above, so UtilityCommands stays quiet, allowed again here
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOwnSwitch(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && switching.contains(event.getPlayer().getUniqueId())) event.setCancelled(false);
    }

    // Changes the camera; the teleport this causes belongs to us
    private void camera(Player player, Player target) {
        switching.add(player.getUniqueId());
        try {
            player.setSpectatorTarget(target);
        } finally {
            switching.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isWatcher(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isWatcher(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isWatcher(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isWatcher(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (isWatcher(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        watchers.remove(event.getPlayer().getUniqueId());
        lastWatched.remove(event.getPlayer().getUniqueId());
    }
}
