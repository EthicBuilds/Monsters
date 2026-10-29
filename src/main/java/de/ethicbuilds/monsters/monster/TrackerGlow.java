package de.ethicbuilds.monsters.monster;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/***
 * While a player holds the monster tracker, every monster and every living teammate glows for this player only
 * (metadata packets via ProtocolLib)
 */
@Singleton
public class TrackerGlow {
    private static final byte ON_FIRE = 0x01, SNEAKING = 0x02, SPRINTING = 0x08, SWIMMING = 0x10, INVISIBLE = 0x20, GLOWING = 0x40;
    private static final byte GLIDING = (byte) 0x80;

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private MonsterManager monsterManager;
    @Inject private UserManager userManager;

    private final ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
    private final Map<UUID, Set<Entity>> glowing = new HashMap<>();

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    glowing.clear();
                    cancel();
                    return;
                }
                for (GamePlayer gamePlayer : userManager.getGamePlayers()) update(gamePlayer.getPlayer());
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void update(Player player) {
        Set<Entity> shown = glowing.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
        boolean holding = player.isOnline() && player.getInventory().getItemInMainHand().getType() == Material.COMPASS;

        Set<Entity> now = new HashSet<>();
        if (holding) {
            for (EnemyMonster enemyMonster : monsterManager.getMonsters().values()) {
                if (enemyMonster.getMonster() == null || !enemyMonster.getMonster().isValid()) continue;
                now.addAll(glowParts(enemyMonster));
            }
            // Teammates glow as well, so the tracker also shows where the others are
            for (GamePlayer mate : userManager.getGamePlayers()) {
                Player other = mate.getPlayer();
                if (!other.equals(player) && other.isOnline() && other.getWorld().equals(player.getWorld())
                        && !userManager.isPlayerDead(other.getUniqueId())) now.add(other);
            }
            // Sent again every time, the server overwrites the flags when something changes
            for (Entity entity : now) send(player, entity, true);
        }
        for (Entity entity : shown) {
            if (!now.contains(entity) && entity.isValid() && player.isOnline()) send(player, entity, false);
        }
        shown.clear();
        shown.addAll(now);
    }

    // Spiders are invisible, their model parts glow instead
    private static List<Entity> glowParts(EnemyMonster enemyMonster) {
        LivingEntity monster = enemyMonster.getMonster();
        if (!monster.isInvisible()) return List.of(monster);
        List<Entity> parts = new ArrayList<>();
        for (Entity passenger : monster.getPassengers()) {
            if (passenger instanceof ItemDisplay) parts.add(passenger);
        }
        return parts;
    }

    private void send(Player player, Entity entity, boolean glow) {
        if (!entity.getWorld().equals(player.getWorld())) return;
        byte flags = 0;
        if (entity.getFireTicks() > 0) flags |= ON_FIRE;
        if (entity instanceof LivingEntity living && living.isInvisible()) flags |= INVISIBLE;
        // Players keep their pose flags, otherwise they would stand up while sneaking
        if (entity instanceof Player other) {
            if (other.isSneaking()) flags |= SNEAKING;
            if (other.isSprinting()) flags |= SPRINTING;
            if (other.isSwimming()) flags |= SWIMMING;
            if (other.isGliding()) flags |= GLIDING;
        }
        boolean realGlow = entity.isGlowing() || entity instanceof LivingEntity living && living.hasPotionEffect(PotionEffectType.GLOWING);
        if (glow || realGlow) flags |= GLOWING;

        PacketContainer packet = protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, entity.getEntityId());
        packet.getDataValueCollectionModifier().write(0, List.of(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), flags)));
        protocol.sendServerPacket(player, packet);
    }
}
