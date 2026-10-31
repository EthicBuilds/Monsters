package de.ethicbuilds.monsters.bautest;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.building.Breakouts;
import de.ethicbuilds.monsters.building.BuildManager;
import de.ethicbuilds.monsters.gameplay.listener.GameListener;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.decoration.CrateManager;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.map.decoration.EscapeCar;
import de.ethicbuilds.monsters.map.decoration.Safes;
import de.ethicbuilds.monsters.map.decoration.Sirens;
import de.ethicbuilds.monsters.map.decoration.Wrecks;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.MonsterZombie;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
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
 * with test coins and parts and can use the game models like in a round, shoot and call test monsters.
 * The models only register while someone tests; afterwards the world is set back and everything is forgotten.
 */
public class BauTest implements Listener {
    public static final String META = "monsters-funktion";
    private static final int COINS = 100_000, PARTS = 50, MONSTER_SLOT = 6, MAX_MONSTERS = 10;
    private static final double BREAKOUT_REACH = 24, NEXO_REACH = 6;
    private static final double MONSTER_DISTANCE = 6;
    private static final NamespacedKey MONSTER_ITEM = new NamespacedKey("monsters", "test_gegner");
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
    @Inject private Wrecks wrecks;
    @Inject private BuildManager buildManager;
    @Inject private MonsterManager monsterManager;
    @Inject private WaveListener waveListener;
    @Inject private GameListener gameListener;
    @Inject private GameStates gameStates;

    private final Map<UUID, Saved> saved = new HashMap<>();
    private final Set<Monster> testMonsters = ConcurrentHashMap.newKeySet();

    private record Saved(ItemStack[] contents, GameMode mode, Collection<PotionEffect> effects, boolean flight, boolean flying,
                         int slot, boolean invulnerable) {}

    public static boolean on() {
        return on;
    }

    public static boolean active(Player player) {
        return active.contains(player.getUniqueId());
    }

    /** Someone tests right now, only then the models are registered */
    public static boolean session() {
        return !active.isEmpty();
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
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20L, 10L);
        plugin.getLogger().info("Bau-Test: Funktionen nur im Funktionsmodus (/monster)");
    }

    // While disabling nothing can be hidden any more, our entities are not saved anyway
    public void stop() {
        testMonsters.forEach(Entity::remove);
        NexoBarriers.all(Restore.all());
        for (UUID uuid : List.copyOf(active)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) giveBack(player);
        }
        active.clear();
    }

    private void check() {
        limited.removeIf(entity -> !entity.isValid());
        testMonsters.removeIf(monster -> !monster.isValid());
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean wanted = player.hasMetadata(META);
            if (wanted && !active(player)) enter(player);
            else if (!wanted && active(player)) leave(player);
        }
        NexoBarriers.resend();
    }

    private void enter(Player player) {
        saved.put(player.getUniqueId(), new Saved(player.getInventory().getContents().clone(), player.getGameMode(), player.getActivePotionEffects(),
                player.getAllowFlight(), player.isFlying(), player.getInventory().getHeldItemSlot(), player.isInvulnerable()));
        boolean first = !session();
        active.add(player.getUniqueId());
        if (first) Bukkit.getWorlds().forEach(decorationManager::registerLoaded);
        GamePlayer gamePlayer = userManager.addTestPlayer(player);
        gamePlayer.setCoins(COINS);
        gamePlayer.setParts(PARTS);
        // Monsters may chase the tester, but do not hurt
        player.setInvulnerable(true);
        player.getInventory().setItem(MONSTER_SLOT, monsterItem(player));
        limited.forEach(entity -> player.showEntity(plugin, entity));
        Restore.hideFor(player, true);
        decorationManager.showSigns(player);
        Lang.note(player, Lang.f(player, "§aFunktionsmodus §8· §6%,d Test-Coins §8· §e%d Bauteile", "§aFunction mode §8· §6%,d test coins §8· §e%d parts",
                COINS, PARTS));
    }

    private void leave(Player player) {
        active.remove(player.getUniqueId());
        NexoBarriers.forget(player);
        Restore.hideFor(player, false);
        limited.forEach(entity -> player.hideEntity(plugin, entity));
        decorationManager.hideSigns(player);
        giveBack(player);
        if (!session()) end();
    }

    // The last tester left: monsters away, world back, everything forgotten until the next test
    private void end() {
        testMonsters.forEach(monster -> monster.setHealth(0));
        testMonsters.clear();
        NexoBarriers.all(Restore.all());
        decorationManager.clearAll();
        crateManager.clear();
        safes.clear();
        escapeCar.clear();
        wrecks.clear();
        Sirens.clear();
        buildManager.clear();
        limited.forEach(Entity::remove);
        limited.clear();
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
            player.setInvulnerable(before.invulnerable());
        }
    }

    private static ItemStack monsterItem(Player player) {
        ItemStack item = new ItemStack(Material.ZOMBIE_HEAD);
        item.editMeta(meta -> {
            meta.setDisplayName(Lang.t(player, "§cTest-Gegner", "§cTest monster"));
            meta.setLore(List.of(Lang.t(player, "§7Rechtsklick: Zombie vor dir", "§7Right-click: zombie in front of you"),
                    Lang.t(player, "§7Auf Ausbruchstelle schauen: kommt dort raus", "§7Look at a break-out: comes out of it")));
            meta.getPersistentDataContainer().set(MONSTER_ITEM, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    private void spawnMonster(Player player) {
        if (testMonsters.size() >= MAX_MONSTERS) {
            Lang.note(player, Lang.f(player, "§7Schon §c%d §7Test-Gegner unterwegs", "§7Already §c%d §7test monsters around", MAX_MONSTERS));
            return;
        }
        Location ahead = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(MONSTER_DISTANCE));
        Block ground = ahead.getWorld().getHighestBlockAt(ahead.getBlockX(), ahead.getBlockZ());
        Block feet = ahead.getBlock();
        // Under a roof the spot in front stays, otherwise on top of the ground
        Location at = feet.getType().isAir() ? feet.getLocation().add(0.5, 0, 0.5) : ground.getLocation().add(0.5, 1, 0.5);
        ItemDisplay breakout = Breakouts.lookedAt(player, BREAKOUT_REACH);
        if (breakout != null) at = Breakouts.out(breakout);
        EnemyMonster enemy = new MonsterZombie();
        monsterManager.summonAt(enemy, player, at);
        enemy.getMonster().setPersistent(false);
        testMonsters.add(enemy.getMonster());
    }

    // Furniture a builder places during a test
    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (session() && DecorationManager.isFurniture(entity)) Bukkit.getScheduler().runTask(plugin, () -> decorationManager.register(entity));
    }

    // Shooting, reloading and the test monster item, only for testers
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!active(player) || event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(MONSTER_ITEM)) {
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) spawnMonster(player);
            return;
        }
        waveListener.onPlayerThrowGrenade(event);
        gameListener.onPlayerInteract(event);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        if (active(event.getPlayer())) gameListener.onPlayerChangeWeapon(event);
    }

    // Without a round nobody else plays the death of a monster
    @EventHandler
    public void onMonsterDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster monster) || !testMonsters.remove(monster)) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        EnemyMonster enemy = monsterManager.getEnemyMonster(monster);
        if (enemy == null) return;
        enemy.onDeath();
        monsterManager.removeMonster(enemy);
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
        event.getEntities().forEach(entity -> {
            if (entity instanceof Monster monster && testMonsters.remove(monster)) monster.remove();
        });
    }

    @EventHandler
    public void onStation(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !active(event.getPlayer()) || !(event.getRightClicked() instanceof Interaction interaction)) return;
        String station = decorationManager.getStation(interaction);
        if (station == null) return;
        event.setCancelled(true);
        waveListener.useStation(event.getPlayer(), station);
    }

    // Nexo's own invisible hitboxes are unknown to the server: such a click goes to the station the tester looks at
    @EventHandler
    public void onNexoHitbox(PlayerUseUnknownEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !active(event.getPlayer())) return;
        String station = decorationManager.stationLookedAt(event.getPlayer(), NEXO_REACH);
        if (station != null) waveListener.useStation(event.getPlayer(), station);
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        if (session()) NexoLooks.tracked(event.getPlayer(), event.getEntity());
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
