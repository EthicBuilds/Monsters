package de.ethicbuilds.monsters.monster;

import com.destroystokyo.paper.entity.Pathfinder;
import de.ethicbuilds.monsters.Main;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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

    public void summon(Player player, Location spawnLocation) {
        monster = (Monster) plugin.getWorld().spawnEntity(spawnLocation, type);
        monster.setCustomNameVisible(true);
        monster.setCustomName(monster.getCustomName());
        monster.setHealth(health);

        Pathfinder pathfinder = monster.getPathfinder();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || monster.isDead()) {
                    this.cancel();
                    return;
                }
                pathfinder.moveTo(player, speed);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
