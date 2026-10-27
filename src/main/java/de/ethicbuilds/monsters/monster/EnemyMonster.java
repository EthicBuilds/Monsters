package de.ethicbuilds.monsters.monster;

import com.destroystokyo.paper.entity.Pathfinder;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Bukkit;
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
    @Getter
    private Player target;

    private final Main plugin = Main.getINSTANCE();

    private final UserManager userManager = plugin.getInjector().getInstance(UserManager.class);

    public void summon(Player player, Location spawnLocation) {
        monster = (Monster) plugin.getWorld().spawnEntity(spawnLocation, type);
        monster.setCustomNameVisible(false);
        if (monster instanceof org.bukkit.entity.Ageable ageable) ageable.setAdult();
        var movement = monster.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(Math.min(movement.getBaseValue(), 0.23));
        MonsterGuard.mark(monster);
        monster.setHealth(health);
        if (plugin.getConfig().getBoolean("health-bars", true)) HealthBars.attach(monster);

        target = player;
        Pathfinder pathfinder = monster.getPathfinder();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (monster.isDead()) {
                    this.cancel();
                    return;
                }

                // Every monster keeps its own player, a dead one hands it to the least chased living player
                if (target == null || !target.isOnline() || userManager.isPlayerDead(target.getUniqueId())) {
                    target = monsterManager().leastChasedPlayer();
                }
                if (target == null) return;
                monster.setTarget(target);
                pathfinder.moveTo(target, speed);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private de.ethicbuilds.monsters.monster.manager.MonsterManager monsterManager() {
        return plugin.getInjector().getInstance(de.ethicbuilds.monsters.monster.manager.MonsterManager.class);
    }
}
