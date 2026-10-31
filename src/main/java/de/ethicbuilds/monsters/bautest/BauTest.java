package de.ethicbuilds.monsters.bautest;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.decoration.CrateManager;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.map.decoration.EscapeCar;
import de.ethicbuilds.monsters.map.decoration.Safes;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Function test on the build server (config bau-test): no rounds, building goes on as usual.
 * Whoever MonstersBau switches into function mode (/monster, metadata monsters-funktion) gets a test player
 * with test coins and parts and can use the game models like in a round. Everything else stays untouched.
 */
public class BauTest implements Listener {
    public static final String META = "monsters-funktion";
    private static final int COINS = 50_000, PARTS = 50;
    private static boolean on;
    private static final Set<UUID> active = ConcurrentHashMap.newKeySet();
    // Our hitboxes and models, only seen in function mode
    private static final Set<Entity> limited = ConcurrentHashMap.newKeySet();

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private DecorationManager decorationManager;
    @Inject private CrateManager crateManager;
    @Inject private Safes safes;
    @Inject private EscapeCar escapeCar;
    @Inject private WaveListener waveListener;
    @Inject private GameStates gameStates;

    private final Map<UUID, Saved> saved = new HashMap<>();

    private record Saved(ItemStack[] contents, GameMode mode, Collection<PotionEffect> effects, boolean flight, boolean flying, int slot) {}

    public static boolean on() {
        return on;
    }

    public static boolean active(Player player) {
        return active.contains(player.getUniqueId());
    }

    /** Who gets messages for everyone: on the build server only players in function mode */
    public static Collection<? extends Player> audience() {
        if (!on) return Bukkit.getOnlinePlayers();
        return Bukkit.getOnlinePlayers().stream().filter(BauTest::active).toList();
    }

    /** Spawned by Monsters: hidden for builders, shown in function mode */
    public static void limit(Entity entity) {
        if (!on) return;
        entity.setVisibleByDefault(false);
        limited.add(entity);
        Main plugin = Main.getINSTANCE();
        for (UUID uuid : active) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.showEntity(plugin, entity);
        }
    }

    public void start() {
        on = true;
        // Stations and crates only answer during a wave
        gameStates.setCurrentPhase(GamePhase.WAVE);
        Bukkit.getWorlds().forEach(decorationManager::registerLoaded);
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20L, 10L);
        plugin.getLogger().info("Bau-Test: Funktionen nur im Funktionsmodus (/monster)");
    }

    // While disabling nothing can be hidden any more, our entities are not saved anyway
    public void stop() {
        Restore.all();
        for (UUID uuid : List.copyOf(active)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) giveBack(player);
        }
        active.clear();
    }

    private void check() {
        limited.removeIf(entity -> !entity.isValid());
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean wanted = player.hasMetadata(META);
            if (wanted && !active(player)) enter(player);
            else if (!wanted && active(player)) leave(player);
        }
    }

    private void enter(Player player) {
        saved.put(player.getUniqueId(), new Saved(player.getInventory().getContents().clone(), player.getGameMode(),
                player.getActivePotionEffects(), player.getAllowFlight(), player.isFlying(), player.getInventory().getHeldItemSlot()));
        active.add(player.getUniqueId());
        Bukkit.getWorlds().forEach(decorationManager::registerLoaded);
        GamePlayer gamePlayer = userManager.addTestPlayer(player);
        gamePlayer.setCoins(COINS);
        gamePlayer.setParts(PARTS);
        limited.forEach(entity -> player.showEntity(plugin, entity));
        decorationManager.showSigns(player);
        Lang.note(player, Lang.f(player, "§aFunktionsmodus §8· §6%,d Test-Coins §8· §e%d Bauteile", "§aFunction mode §8· §6%,d test coins §8· §e%d parts",
                COINS, PARTS));
    }

    private void leave(Player player) {
        active.remove(player.getUniqueId());
        limited.forEach(entity -> player.hideEntity(plugin, entity));
        decorationManager.hideSigns(player);
        giveBack(player);
        if (active.isEmpty()) {
            crateManager.resetAll();
            Restore.all();
            safes.reset();
            escapeCar.reset();
        }
    }

    // Inventory, game mode and effects from before the function mode
    private void giveBack(Player player) {
        // Riding the car seat, the seat would be saved with the player
        if (player.isInsideVehicle()) player.leaveVehicle();
        userManager.removePlayer(player.getUniqueId());
        Saved before = saved.remove(player.getUniqueId());
        if (before != null) {
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.addPotionEffects(before.effects());
            player.getInventory().setContents(before.contents());
            player.getInventory().setHeldItemSlot(before.slot());
            player.setGameMode(before.mode());
            player.setAllowFlight(before.flight());
            player.setFlying(before.flying());
        }
    }

    // Furniture a builder places now
    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (DecorationManager.isFurniture(entity)) Bukkit.getScheduler().runTask(plugin, () -> decorationManager.register(entity));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (active(event.getPlayer())) leave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Restore.chunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        Restore.chunk(event.getChunk());
        // Our hitboxes go with the chunk, the furniture gets new ones when it comes back
        event.getEntities().forEach(decorationManager::forget);
    }

    @EventHandler
    public void onStation(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !active(event.getPlayer()) || !(event.getRightClicked() instanceof Interaction interaction)) return;
        String station = decorationManager.getStation(interaction);
        if (station == null) return;
        event.setCancelled(true);
        waveListener.useStation(event.getPlayer(), station);
    }

    @EventHandler
    public void onStationHit(PrePlayerAttackEntityEvent event) {
        if (!active(event.getPlayer()) || !(event.getAttacked() instanceof Interaction interaction)) return;
        String station = decorationManager.getStation(interaction);
        if (station == null) return;
        event.setCancelled(true);
        waveListener.useStation(event.getPlayer(), station);
    }
}
