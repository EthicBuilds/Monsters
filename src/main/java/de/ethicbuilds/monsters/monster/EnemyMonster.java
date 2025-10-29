package de.ethicbuilds.monsters.monster;

import com.destroystokyo.paper.entity.Pathfinder;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public abstract class EnemyMonster {
    protected int health;
    protected int damage;
    protected double speed;
    protected String name;
    @Getter
    protected Monster monster;
    protected EntityType type;
    @Getter
    protected int coin;

    private final Main plugin = Main.getINSTANCE();

    private final UserManager userManager = plugin.getInjector().getInstance(UserManager.class);

    public void summon(Player player, Location spawnLocation) {
        monster = (Monster) plugin.getWorld().spawnEntity(spawnLocation, type);
        monster.setCustomNameVisible(true);
        monster.setCustomName(monster.getCustomName());
        monster.setHealth(health);

        Pathfinder pathfinder = monster.getPathfinder();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (monster.isDead()) {
                    this.cancel();
                    return;
                }

                if (!player.isOnline() || userManager.isPlayerDead(player.getUniqueId())) {
                    pathfinder.moveTo(getNearestPlayer(monster.getLocation()), speed);
                } else {
                    pathfinder.moveTo(player, speed);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private Player getNearestPlayer(Location origin) {
        Collection<Player> nearbyPlayers = origin.getWorld().getNearbyPlayers(origin, 1000);
        Player closest = null;
        double minDist = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            double dist = player.getLocation().distanceSquared(origin);
            if (dist < minDist) {
                minDist = dist;
                closest = player;
            }
        }
        return closest;
    }
}
