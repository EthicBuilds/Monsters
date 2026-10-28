package de.ethicbuilds.monsters.player.manager;

import de.ethicbuilds.monsters.sound.Sounds;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.GamePlayer;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/***
 * Downed players: a mannequin with their skin lies where they died, they themselves are hidden and
 * look up from the ground until the next wave or until a teammate revives them for coins.
 * With key 5 they watch the living teammates meanwhile, through their eyes in spectator mode.
 */
public class ReviveManager implements Listener {
    private static final int BASE_COST = 1000;
    private static final float HITBOX_WIDTH = 1.8F, HITBOX_HEIGHT = 0.7F;
    private static final long CLICK_COOLDOWN = 500;
    private static final double REACH = 4;
    private static final int EMPTY_SLOT = 8;
    public static final int MEDKIT_SLOT = 7;
    private static final long SELF_REVIVE_DELAY = 60;
    // A lying body has its feet at its position, the head this far along the body
    private static final double HEAD_DISTANCE = 1.3;
    // Key 5 while lying starts watching, the held slot stays empty
    private static final int WATCH_SLOT = 4;

    @Inject private Main plugin;
    @Inject private UserManager userManager;

    private final Map<UUID, Mannequin> bodies = new HashMap<>();
    private final Map<UUID, Location> spots = new HashMap<>();
    private final Map<UUID, Location> views = new HashMap<>();
    private final Map<UUID, Interaction> hitboxes = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Integer> revives = new HashMap<>();
    // Self revive with a stored medkit, once per round
    private final java.util.Set<UUID> selfRevived = new java.util.HashSet<>();
    // Own values of the player before going down (the attribute default of movement speed is 0.7, players walk with 0.1)
    private final Map<UUID, Map<Attribute, Double>> savedBases = new HashMap<>();
    private final Map<UUID, Player> watching = new HashMap<>();
    private final Map<UUID, ItemStack> savedWatchSlot = new HashMap<>();
    private BukkitRunnable watchCheck;

    /** Every revive of the same player costs 1000 more */
    public int cost(UUID uuid) {
        return BASE_COST * (revives.getOrDefault(uuid, 0) + 1);
    }

    public boolean isDown(UUID uuid) {
        return spots.containsKey(uuid);
    }

    public void down(Player player) {
        // The player keeps the normal view where they fell, only the body lies on the ground
        Location view = player.getLocation().clone();
        Location spot = view.clone();
        spot.setYaw(freeYaw(spot));
        spot.setPitch(0);
        int cost = cost(player.getUniqueId());

        Mannequin body = spot.getWorld().spawn(spot, Mannequin.class, mannequin -> {
            mannequin.setBodyYaw(spot.getYaw());
            mannequin.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            mannequin.setPose(Pose.SLEEPING, true);
            mannequin.setInvulnerable(true);
            mannequin.setSilent(true);
            mannequin.setAI(false);
            mannequin.setCollidable(false);
            mannequin.setPersistent(false);
            mannequin.setCustomName("§c" + player.getName());
            mannequin.setCustomNameVisible(true);
            mannequin.setDescription(Component.text(String.format("§7Wiederbeleben: §6%d Coins", cost)));
        });
        bodies.put(player.getUniqueId(), body);
        spots.put(player.getUniqueId(), spot);
        hitboxes.put(player.getUniqueId(), spawnHitbox(spot));

        // The player stays listed for the others (tab list, names), the invisibility hides the standing body

        // Not even the downed player sees their own player body (F5) or an item in hand
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        player.getInventory().setHeldItemSlot(EMPTY_SLOT);
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        Map<Attribute, Double> saved = new HashMap<>();
        for (Attribute attribute : List.of(Attribute.SCALE, Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) saved.put(attribute, instance.getBaseValue());
        }
        savedBases.putIfAbsent(player.getUniqueId(), saved);
        setBase(player, Attribute.MOVEMENT_SPEED, 0);
        setBase(player, Attribute.JUMP_STRENGTH, 0);

        // Monsters chasing the downed player look for someone alive
        for (org.bukkit.entity.Monster monster : spot.getWorld().getEntitiesByClass(org.bukkit.entity.Monster.class)) {
            if (player.equals(monster.getTarget())) monster.setTarget(null);
        }

        views.put(player.getUniqueId(), view);
        ItemStack watchSlot = player.getInventory().getItem(WATCH_SLOT);
        savedWatchSlot.put(player.getUniqueId(), watchSlot == null ? new ItemStack(Material.AIR) : watchSlot);
        player.getInventory().setItem(WATCH_SLOT, watchItem());
        player.sendActionBar("§7Taste 5: §fMitspielern zuschauen");
        trySelfRevive(player);
    }

    // A stored medkit gets the player back up after a few seconds, once per round
    private void trySelfRevive(Player player) {
        ItemStack stored = player.getInventory().getItem(MEDKIT_SLOT);
        if (stored == null || stored.getType().isAir() || selfRevived.contains(player.getUniqueId())) return;
        player.sendTitle("§aMedkit!", "§7Du stehst gleich wieder auf", 5, 40, 10);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isDown(player.getUniqueId())) return;
            selfRevived.add(player.getUniqueId());
            player.getInventory().setItem(MEDKIT_SLOT, null);
            Location spot = spots.get(player.getUniqueId()).clone();
            spot.setPitch(0);
            userManager.revivePlayer(player.getUniqueId(), spot);
            spot.getWorld().spawnParticle(Particle.HEART, spot.clone().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0);
            Bukkit.broadcastMessage(String.format("%s§a%s §7steht mit einem §aMedkit §7wieder auf!", plugin.getMonstersPrefix(), player.getName()));
        }, SELF_REVIVE_DELAY);
    }

    public boolean hasStoredMedkit(Player player) {
        ItemStack stored = player.getInventory().getItem(MEDKIT_SLOT);
        return stored != null && !stored.getType().isAir();
    }

    // Key 5 while lying: watch the living teammates in spectator mode, right through their eyes
    @EventHandler
    public void onDownedSlotChange(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!isDown(player.getUniqueId())) return;
        event.setCancelled(true);
        if (event.getNewSlot() == WATCH_SLOT && !watching.containsKey(player.getUniqueId())) watch(player, null);
    }

    // The heads in the spectator hotbar (key 1) switch the teammate, nobody flies around freely
    @EventHandler
    public void onSpectatorTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE || !watching.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        Player target = mates(player).stream()
                .filter(mate -> mate.getWorld().equals(event.getTo().getWorld()) && mate.getLocation().distanceSquared(event.getTo()) < 1)
                .findFirst().orElse(null);
        if (target == null) {
            player.sendActionBar("§7Du kannst nur lebenden Mitspielern zuschauen");
            return;
        }
        watch(player, target);
    }

    @EventHandler
    public void onStartWatching(com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (!isDown(player.getUniqueId())) return;
        if (!(event.getNewSpectatorTarget() instanceof Player mate) || !mates(player).contains(mate)) event.setCancelled(true);
    }

    // Sneaking would leave the camera, the view stays with the teammate
    @EventHandler
    public void onStopWatching(com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (watching.containsKey(player.getUniqueId()) && event.getSpectatorTarget().isValid()) event.setCancelled(true);
    }

    private List<Player> mates(Player player) {
        return userManager.getGamePlayers().stream().map(GamePlayer::getPlayer)
                .filter(mate -> mate.isOnline() && !mate.equals(player) && !userManager.isPlayerDead(mate.getUniqueId())).toList();
    }

    // Without a target the first living teammate
    private void watch(Player player, Player mate) {
        List<Player> mates = mates(player);
        if (mate == null) mate = mates.isEmpty() ? null : mates.get(0);
        if (mate == null) {
            backToBody(player);
            player.sendActionBar("§7Gerade steht niemand, dem du zuschauen kannst");
            return;
        }
        watching.put(player.getUniqueId(), mate);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.setSpectatorTarget(mate);
        player.sendActionBar("§7Du schaust §a" + mate.getName() + " §7zu §8· §7Taste 1: Mitspieler wechseln");
        startWatchCheck();
    }

    // Goes on to the next teammate when the watched one falls or leaves
    private void startWatchCheck() {
        if (watchCheck != null) return;
        watchCheck = new BukkitRunnable() {
            @Override
            public void run() {
                if (watching.isEmpty()) {
                    cancel();
                    watchCheck = null;
                    return;
                }
                for (UUID uuid : List.copyOf(watching.keySet())) {
                    Player player = Bukkit.getPlayer(uuid);
                    Player mate = watching.get(uuid);
                    if (player == null) continue;
                    if (!mate.isOnline() || userManager.isPlayerDead(mate.getUniqueId()) || !mate.equals(player.getSpectatorTarget())) {
                        watch(player, null);
                    }
                }
            }
        };
        watchCheck.runTaskTimer(plugin, 10L, 10L);
    }

    private void backToBody(Player player) {
        stopWatching(player);
        Location view = views.get(player.getUniqueId());
        if (view != null) player.teleport(view);
        player.sendActionBar("§7Taste 5: §fMitspielern zuschauen");
    }

    private void stopWatching(Player player) {
        // Removed first, otherwise leaving the camera below is cancelled
        if (watching.remove(player.getUniqueId()) == null) return;
        player.setSpectatorTarget(null);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
    }

    private static ItemStack watchItem() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        item.editMeta(meta -> {
            meta.setDisplayName("§aMitspielern zuschauen");
            meta.setLore(List.of("§7Taste 5"));
        });
        return item;
    }

    // The lying body is flat and hard to hit: a click box over the whole body, left and right click count
    private static Interaction spawnHitbox(Location spot) {
        Location head = headOf(spot);
        Location middle = spot.clone().add(head.clone().subtract(spot).toVector().multiply(0.5));
        return spot.getWorld().spawn(middle, Interaction.class, box -> {
            box.setInteractionWidth(HITBOX_WIDTH);
            box.setInteractionHeight(HITBOX_HEIGHT);
            box.setResponsive(true);
            box.setPersistent(false);
        });
    }

    // Lying direction where the head is not inside a wall
    private float freeYaw(Location spot) {
        for (int turn = 0; turn < 4; turn++) {
            Location at = spot.clone();
            at.setYaw(spot.getYaw() + turn * 90);
            if (headOf(at).getBlock().isPassable()) return at.getYaw();
        }
        return spot.getYaw();
    }

    // Matches how the client lays a sleeping body without bed along its body yaw
    private static Location headOf(Location spot) {
        double yaw = Math.toRadians(spot.getYaw());
        return spot.clone().add(-Math.cos(yaw) * HEAD_DISTANCE, 0, Math.sin(yaw) * HEAD_DISTANCE);
    }

    /** Ends the downed state: mannequin away, player visible and normal again */
    public void clear(Player player) {
        stopWatching(player);
        ItemStack watchSlot = savedWatchSlot.remove(player.getUniqueId());
        if (watchSlot != null) player.getInventory().setItem(WATCH_SLOT, watchSlot);
        Mannequin body = bodies.remove(player.getUniqueId());
        if (body != null) body.remove();
        Interaction hitbox = hitboxes.remove(player.getUniqueId());
        if (hitbox != null) hitbox.remove();
        spots.remove(player.getUniqueId());
        views.remove(player.getUniqueId());

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.getInventory().setHeldItemSlot(1);
        Map<Attribute, Double> saved = savedBases.remove(player.getUniqueId());
        setBase(player, Attribute.SCALE, saved != null ? saved.getOrDefault(Attribute.SCALE, 1.0) : 1.0);
        setBase(player, Attribute.MOVEMENT_SPEED, saved != null ? saved.getOrDefault(Attribute.MOVEMENT_SPEED, 0.1) : 0.1);
        setBase(player, Attribute.JUMP_STRENGTH, saved != null ? saved.getOrDefault(Attribute.JUMP_STRENGTH, 0.42) : 0.42);

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    @EventHandler
    public void onReviveClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        UUID downed = downedBy(event.getRightClicked());
        if (downed == null) return;
        event.setCancelled(true);
        revive(event.getPlayer(), downed);
    }

    @EventHandler
    public void onReviveHit(PrePlayerAttackEntityEvent event) {
        UUID downed = downedBy(event.getAttacked());
        if (downed == null) return;
        event.setCancelled(true);
        revive(event.getPlayer(), downed);
    }

    // Body or click box of a downed player
    private UUID downedBy(Entity entity) {
        // The invisible downed player stands at the body, a click on them counts as well, but not while they watch someone
        if (entity instanceof Player player && isDown(player.getUniqueId())
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) return player.getUniqueId();
        for (Map.Entry<UUID, Mannequin> entry : bodies.entrySet()) {
            if (entry.getValue().equals(entity)) return entry.getKey();
        }
        for (Map.Entry<UUID, Interaction> entry : hitboxes.entrySet()) {
            if (entry.getValue().equals(entity)) return entry.getKey();
        }
        return null;
    }

    private void revive(Player helper, UUID downed) {
        // Left and right click can come in the same moment, only one counts
        long now = System.currentTimeMillis();
        if (now - lastClick.getOrDefault(helper.getUniqueId(), 0L) < CLICK_COOLDOWN) return;
        lastClick.put(helper.getUniqueId(), now);

        GamePlayer helperPlayer = userManager.getGamePlayer(helper.getUniqueId());
        GamePlayer downedPlayer = userManager.getGamePlayer(downed);
        if (helperPlayer == null || downedPlayer == null || userManager.isPlayerDead(helper.getUniqueId())) return;
        if (helper.getGameMode() == org.bukkit.GameMode.SPECTATOR || !nearBody(helper, downed)) return;

        int cost = cost(downed);
        if (helperPlayer.getCoins() < cost) {
            helper.sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum %s wiederzubeleben", cost - helperPlayer.getCoins(), downedPlayer.getPlayer().getName()));
            Sounds.deny(helper);
            return;
        }

        helperPlayer.setCoins(helperPlayer.getCoins() - cost);
        helperPlayer.setRevives(helperPlayer.getRevives() + 1);
        revives.merge(downed, 1, Integer::sum);
        Location spot = spots.get(downed).clone();
        spot.setPitch(0);
        userManager.revivePlayer(downed, spot);

        spot.getWorld().spawnParticle(Particle.HEART, spot.clone().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0);
        Bukkit.broadcastMessage(String.format("%s§a%s §7hat §a%s §7wiederbelebt! §c-%d Coins", plugin.getMonstersPrefix(),
                helper.getName(), downedPlayer.getPlayer().getName(), cost));
    }

    // Only who stands at the body can revive
    private boolean nearBody(Player helper, UUID downed) {
        Interaction hitbox = hitboxes.get(downed);
        return hitbox != null && hitbox.getWorld().equals(helper.getWorld())
                && hitbox.getLocation().distance(helper.getLocation()) <= REACH;
    }

    // Downed players can look around but not move
    @EventHandler
    public void onDownedMove(PlayerMoveEvent event) {
        if (!isDown(event.getPlayer().getUniqueId()) || !event.hasChangedPosition()) return;
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR || watching.containsKey(event.getPlayer().getUniqueId())) return;
        Location view = views.getOrDefault(event.getPlayer().getUniqueId(), spots.get(event.getPlayer().getUniqueId())).clone();
        view.setYaw(event.getTo().getYaw());
        view.setPitch(event.getTo().getPitch());
        event.setTo(view);
    }

    @EventHandler
    public void onMonsterTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isDown(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isDown(event.getPlayer().getUniqueId())) clear(event.getPlayer());
    }

    private static void setBase(Player player, Attribute attribute, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

}
