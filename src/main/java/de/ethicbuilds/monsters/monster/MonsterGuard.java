package de.ethicbuilds.monsters.monster;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/***
 * Only monsters of the current round: no natural spawns or reinforcements,
 * and monsters, items and old holograms saved in the world (old rounds, map template) are removed when they load
 */
public class MonsterGuard implements Listener {
    private static final NamespacedKey ROUND_MONSTER = new NamespacedKey("monsters", "round_monster");
    private static final NamespacedKey HOLO = new NamespacedKey("monsters", "holo");

    public static void mark(Monster monster) {
        monster.getPersistentDataContainer().set(ROUND_MONSTER, PersistentDataType.BYTE, (byte) 1);
    }

    public static void markHolo(ArmorStand holo) {
        holo.getPersistentDataContainer().set(HOLO, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isOld(Entity entity) {
        if (entity instanceof Item) return true;
        if (entity instanceof ArmorStand) return !entity.getPersistentDataContainer().has(HOLO, PersistentDataType.BYTE);
        return entity instanceof Monster && !entity.getPersistentDataContainer().has(ROUND_MONSTER, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    // Monsters only die from players: no cramming, suffocation or falling
    @EventHandler
    public void onMonsterDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        switch (event.getCause()) {
            case CRAMMING, SUFFOCATION, FALL, FLY_INTO_WALL, CONTACT -> event.setCancelled(true);
            default -> {
            }
        }
    }

    // Monsters do not hurt each other, e.g. arrows of the parched
    @EventHandler
    public void onMonsterHitsMonster(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        org.bukkit.entity.Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Monster) event.setCancelled(true);
        if (damager instanceof Monster) event.setCancelled(true);
    }

    @EventHandler
    public void onMonsterTargetsMonster(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Monster && event.getTarget() instanceof Monster) event.setCancelled(true);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isOld(entity)) entity.remove();
        }
    }
}
