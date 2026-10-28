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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/***
 * Downed players: a mannequin with their skin lies where they died, they themselves are hidden and
 * look up from the ground until the next wave or until a teammate revives them for coins
 */
public class ReviveManager implements Listener {
    private static final int BASE_COST = 1000;
    private static final float HITBOX_WIDTH = 1.8F, HITBOX_HEIGHT = 0.7F;
    private static final long CLICK_COOLDOWN = 500;
    private static final int EMPTY_SLOT = 8;
    // A lying body has its feet at its position, the head this far along the body
    private static final double HEAD_DISTANCE = 1.3;

    @Inject private Main plugin;
    @Inject private UserManager userManager;

    private final Map<UUID, Mannequin> bodies = new HashMap<>();
    private final Map<UUID, Location> spots = new HashMap<>();
    private final Map<UUID, Location> views = new HashMap<>();
    private final Map<UUID, Interaction> hitboxes = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Integer> revives = new HashMap<>();
    // Own values of the player before going down (the attribute default of movement speed is 0.7, players walk with 0.1)
    private final Map<UUID, Map<Attribute, Double>> savedBases = new HashMap<>();

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

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) other.hidePlayer(plugin, player);
        }

        // Not even the downed player sees their own player body (F5) or an item in hand
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        player.getInventory().setHeldItemSlot(EMPTY_SLOT);
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

    // Downed players can look around but not move
    @EventHandler
    public void onDownedMove(PlayerMoveEvent event) {
        if (!isDown(event.getPlayer().getUniqueId()) || !event.hasChangedPosition()) return;
        Location view = views.getOrDefault(event.getPlayer().getUniqueId(), spots.get(event.getPlayer().getUniqueId())).clone();
        view.setYaw(event.getTo().getYaw());
        view.setPitch(event.getTo().getPitch());
        event.setTo(view);
    }

    @EventHandler
    public void onDownedSlotChange(org.bukkit.event.player.PlayerItemHeldEvent event) {
        if (isDown(event.getPlayer().getUniqueId())) event.setCancelled(true);
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
