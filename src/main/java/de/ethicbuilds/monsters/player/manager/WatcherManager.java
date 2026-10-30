package de.ethicbuilds.monsters.player.manager;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/***
 * Watchers: whoever comes into a running round from the lobby without playing in it only watches.
 * They look through the eyes of the living players (spectator mode, no free flight) and switch with two items
 * or the mouse wheel. Sneaking goes back to the items, the door item back to the lobby.
 * They do not count as players: no coins, no statistics, no rewards.
 */
public class WatcherManager implements Listener {
    private static final int PREV_SLOT = 3, NEXT_SLOT = 4, LOBBY_SLOT = 8, HELD_SLOT = 6;
    private static final String LOBBY = "lobby";

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private MapManager mapManager;

    // Watched player, null while the watcher is at the items
    private final Map<UUID, Player> watchers = new HashMap<>();
    private final Map<UUID, Player> lastWatched = new HashMap<>();
    private final java.util.Set<UUID> switching = new java.util.HashSet<>();
    private BukkitRunnable check;

    public boolean isWatcher(UUID uuid) {
        return watchers.containsKey(uuid);
    }

    public void join(Player player) {
        watchers.put(player.getUniqueId(), null);
        player.getInventory().clear();
        player.getInventory().setItem(PREV_SLOT, item(Material.ENDER_EYE, null, Lang.t(player, "§a« Voriger Spieler", "§a« Previous player"),
                Lang.t(player, "§7Taste 4 oder Mausrad hoch", "§7Key 4 or mouse wheel up")));
        player.getInventory().setItem(NEXT_SLOT, item(Material.ENDER_EYE, null, Lang.t(player, "§aNächster Spieler »", "§aNext player »"),
                Lang.t(player, "§7Taste 5 oder Mausrad runter", "§7Key 5 or mouse wheel down")));
        player.getInventory().setItem(LOBBY_SLOT, item(Material.BARRIER, new NamespacedKey("ethicbuilds", "gui/icon_zurueck"),
                Lang.t(player, "§cZurück zur Lobby", "§cBack to the lobby"), Lang.t(player, "§7Taste 9", "§7Key 9")));
        player.getInventory().setHeldItemSlot(HELD_SLOT);
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        // Standing still at the spawn while at the items, the camera does not fall
        setBase(player, Attribute.MOVEMENT_SPEED, 0);
        setBase(player, Attribute.JUMP_STRENGTH, 0);
        setBase(player, Attribute.GRAVITY, 0);
        player.teleport(mapManager.getMapConfiguration().getSpawn());
        ChatStatus.send(player, true);
        de.ethicbuilds.monsters.lang.Overlay.medium(player, de.ethicbuilds.monsters.lang.Overlay.GREY,
                de.ethicbuilds.monsters.lang.Overlay.line(de.ethicbuilds.monsters.lang.Overlay.NEXT, "#3CDC6E", "Zuschauen", "die Runde läuft schon"),
                de.ethicbuilds.monsters.lang.Overlay.line(de.ethicbuilds.monsters.lang.Overlay.NEXT, "#3CDC6E", "Spectating", "the round is already running"));
        // A moment later, right after joining the camera does not attach yet
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isWatcher(player.getUniqueId())) cycle(player, 1);
        }, 10L);
        startCheck();
    }

    private List<Player> living() {
        return userManager.getGamePlayers().stream().map(GamePlayer::getPlayer)
                .filter(p -> p.isOnline() && !userManager.isPlayerDead(p.getUniqueId())).toList();
    }

    private void cycle(Player player, int direction) {
        List<Player> living = living();
        if (living.isEmpty()) {
            toItems(player);
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
        player.sendActionBar(Lang.f(player, "§7Du schaust §a%s §7zu §8· §7Taste 1: wechseln §8· §7Schleichen: zurück",
                "§7Watching §a%s §8· §7Key 1: switch §8· §7Sneak: back", target.getName()));
    }

    private void toItems(Player player) {
        // Cleared first, otherwise leaving the camera below is cancelled
        watchers.put(player.getUniqueId(), null);
        camera(player, null);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(mapManager.getMapConfiguration().getSpawn());
        player.getInventory().setHeldItemSlot(HELD_SLOT);
        player.sendActionBar(Lang.t(player, "§7Mausrad oder Taste 4/5: §fSpielern zuschauen", "§7Mouse wheel or key 4/5: §fwatch players"));
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

    private void toLobby(Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(LOBBY);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    // Keys 4/5 or the mouse wheel switch the player, key 9 goes to the lobby, the held slot stays empty
    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!isWatcher(player.getUniqueId())) return;
        event.setCancelled(true);
        if (event.getNewSlot() == LOBBY_SLOT) {
            toLobby(player);
            return;
        }
        int step = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        int direction = event.getNewSlot() == PREV_SLOT ? -1 : event.getNewSlot() == NEXT_SLOT ? 1 : step == 8 ? -1 : step == 1 ? 1 : 0;
        if (direction != 0) cycle(player, direction);
    }

    // Sneaking leaves the view and goes back to the items
    @EventHandler
    public void onStopWatching(com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (watchers.get(player.getUniqueId()) == null || !event.getSpectatorTarget().isValid()) return;
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (watchers.get(player.getUniqueId()) != null) toItems(player);
        });
    }

    @EventHandler
    public void onStartWatching(com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent event) {
        if (!isWatcher(event.getPlayer().getUniqueId())) return;
        if (!(event.getNewSpectatorTarget() instanceof Player target) || !living().contains(target)) event.setCancelled(true);
    }

    // The heads in the spectator menu (key 1) switch the player, nobody flies around freely
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

    private static ItemStack item(Material material, NamespacedKey model, String name, String lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            if (model != null) meta.setItemModel(model);
        });
        return item;
    }

    private static void setBase(Player player, Attribute attribute, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }
}
