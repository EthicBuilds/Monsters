package de.ethicbuilds.monsters.player.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.GamePlayer;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/***
 * Downed players: a mannequin with their skin lies where they died, they themselves are hidden and
 * look up from the ground until the next wave or until a teammate revives them for coins
 */
public class ReviveManager implements Listener {
    private static final int BASE_COST = 1000;
    private static final double DOWN_SCALE = 0.15;

    @Inject private Main plugin;
    @Inject private UserManager userManager;

    private final Map<UUID, Mannequin> bodies = new HashMap<>();
    private final Map<UUID, Location> spots = new HashMap<>();
    private final Map<UUID, Integer> revives = new HashMap<>();

    /** Every revive of the same player costs 1000 more */
    public int cost(UUID uuid) {
        return BASE_COST * (revives.getOrDefault(uuid, 0) + 1);
    }

    public boolean isDown(UUID uuid) {
        return spots.containsKey(uuid);
    }

    public void down(Player player) {
        Location spot = player.getLocation().clone();
        int cost = cost(player.getUniqueId());

        Mannequin body = spot.getWorld().spawn(spot, Mannequin.class, mannequin -> {
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

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) other.hidePlayer(plugin, player);
        }

        setBase(player, Attribute.SCALE, DOWN_SCALE);
        setBase(player, Attribute.MOVEMENT_SPEED, 0);
        setBase(player, Attribute.JUMP_STRENGTH, 0);

        // Monsters chasing the downed player look for someone alive
        for (org.bukkit.entity.Monster monster : spot.getWorld().getEntitiesByClass(org.bukkit.entity.Monster.class)) {
            if (player.equals(monster.getTarget())) monster.setTarget(null);
        }

        Location view = spot.clone();
        view.setPitch(-90);
        player.teleport(view);
    }

    /** Ends the downed state: mannequin away, player visible and normal again */
    public void clear(Player player) {
        Mannequin body = bodies.remove(player.getUniqueId());
        if (body != null) body.remove();
        spots.remove(player.getUniqueId());

        resetBase(player, Attribute.SCALE);
        resetBase(player, Attribute.MOVEMENT_SPEED);
        resetBase(player, Attribute.JUMP_STRENGTH);

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    @EventHandler
    public void onReviveClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Mannequin body)) return;

        UUID downed = bodies.entrySet().stream()
                .filter(entry -> entry.getValue().equals(body))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (downed == null) return;
        event.setCancelled(true);

        Player helper = event.getPlayer();
        GamePlayer helperPlayer = userManager.getGamePlayer(helper.getUniqueId());
        GamePlayer downedPlayer = userManager.getGamePlayer(downed);
        if (helperPlayer == null || downedPlayer == null || userManager.isPlayerDead(helper.getUniqueId())) return;

        int cost = cost(downed);
        if (helperPlayer.getCoins() < cost) {
            helper.sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum %s wiederzubeleben", cost - helperPlayer.getCoins(), downedPlayer.getPlayer().getName()));
            helper.playSound(helper.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }

        helperPlayer.setCoins(helperPlayer.getCoins() - cost);
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
        Location spot = spots.get(event.getPlayer().getUniqueId()).clone();
        spot.setYaw(event.getTo().getYaw());
        spot.setPitch(event.getTo().getPitch());
        event.setTo(spot);
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

    private static void resetBase(Player player, Attribute attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(instance.getDefaultValue());
    }
}
