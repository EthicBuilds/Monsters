package de.ethicbuilds.monsters.monster;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.manager.UserManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;


public abstract class EnemyMonster {
    private static final double FOLLOW_RANGE = 64;
    private static final int TRAIL_TRIES = 4;
    private static final int TRAIL_PER_TRY = 2;
    // Within sight and this close the normal attack AI finds the way, further away a path is kept a few seconds
    private static final double NEAR = 16;
    private static final int REPATH_SECONDS = 3;
    private static final double SWITCH_MARGIN = 3;
    private static final double KNOCKBACK_RESISTANCE = 1.0;

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
    @Getter @lombok.Setter
    private int tier;
    private int repath;
    private int trailCursor;
    private Player chased;

    private final Main plugin = Main.getINSTANCE();

    private final UserManager userManager = plugin.getInjector().getInstance(UserManager.class);

    public void summon(Player player, Location spawnLocation) {
        // Elite tiers only for the zombie kinds, bosses keep their own values
        boolean elite = tier > 0 && this instanceof MonsterZombie && !(this instanceof MonsterMiniBoss);
        if (elite) health = (int) Math.round(health * EliteTier.healthFactor(tier));
        monster = (Monster) plugin.getWorld().spawnEntity(spawnLocation, type);
        if (elite) EliteTier.dress(monster, tier);
        monster.setCustomNameVisible(false);
        if (monster instanceof org.bukkit.entity.Ageable ageable) ageable.setAdult();
        var movement = monster.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(Math.min(movement.getBaseValue(), maxSpeed()));
        // Hits do not push them back, otherwise a player can keep them away just by firing
        var knockback = monster.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(KNOCKBACK_RESISTANCE);
        // Paths are searched this far, otherwise far away monsters just stand around
        var followRange = monster.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
        if (followRange != null) followRange.setBaseValue(followRange());
        MonsterGuard.mark(monster);
        // They always hunt a player, wandering and looking around only cost CPU
        var goals = Bukkit.getMobGoals();
        goals.removeGoal((org.bukkit.entity.Creature) monster, VanillaGoal.RANDOM_STROLL);
        goals.removeGoal((org.bukkit.entity.Creature) monster, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL);
        goals.removeGoal((org.bukkit.entity.Creature) monster, VanillaGoal.STROLL_THROUGH_VILLAGE);
        goals.removeGoal((org.bukkit.entity.Mob) monster, VanillaGoal.LOOK_AT_PLAYER);
        goals.removeGoal((org.bukkit.entity.Mob) monster, VanillaGoal.RANDOM_LOOK_AROUND);
        var maxHealth = monster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getBaseValue() < health) maxHealth.setBaseValue(health);
        // The server caps max health (spigot.yml), more would fail the whole spawn
        monster.setHealth(maxHealth != null ? Math.min(health, maxHealth.getValue()) : health);
        onSpawn();

        target = player;
        Pathfinder pathfinder = monster.getPathfinder();
        // Start spread over the second, otherwise all monsters search their way in the same tick
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
                // Spread over all players, but a clearly closer player always comes first
                Player nearest = monsterManager().nearestPlayer(monster.getLocation());
                if (nearest != null && !nearest.equals(target)
                        && distance(nearest) + SWITCH_MARGIN < distance(target)) {
                    target = nearest;
                }
                chase(pathfinder);
            }
        }.runTaskTimer(plugin, java.util.concurrent.ThreadLocalRandom.current().nextInt(20), 20L);
    }

    private double distance(Player player) {
        return player.getWorld().equals(monster.getWorld()) ? player.getLocation().distance(monster.getLocation()) : Double.MAX_VALUE;
    }

    // Full path to the player, else along the trail the player walked, else as close as it gets
    private void chase(Pathfinder pathfinder) {
        if (target != chased) {
            chased = target;
            repath = 0;
        }
        if (distance(target) < NEAR && monster.hasLineOfSight(target)) {
            if (!target.equals(monster.getTarget())) monster.setTarget(target);
            repath = 0;
            return;
        }
        if (--repath > 0 && pathfinder.hasPath()) return;
        repath = REPATH_SECONDS;

        Pathfinder.PathResult direct = pathfinder.findPath(target);
        if (direct != null && direct.canReachFinalPoint()) {
            monster.setTarget(target);
            pathfinder.moveTo(direct, speed);
            return;
        }
        List<Location> trail = PlayerTrail.of(target);
        // Failed searches over the whole range cost the most, so only two trail spots per try, the next ones next time
        int step = Math.max(1, trail.size() / TRAIL_TRIES);
        for (int n = 0; n < TRAIL_PER_TRY && !trail.isEmpty(); n++) {
            Location spot = trail.get((trailCursor++ % TRAIL_TRIES) * step % trail.size());
            if (!spot.getWorld().equals(monster.getWorld())) continue;
            Pathfinder.PathResult path = pathfinder.findPath(spot);
            if (path != null && path.canReachFinalPoint()) {
                // Without a target the attack AI does not steer it back into the dead end
                monster.setTarget(null);
                pathfinder.moveTo(path, speed);
                return;
            }
        }
        monster.setTarget(target);
        if (direct != null) pathfinder.moveTo(direct, speed);
    }

    /** How far paths are searched */
    protected double followRange() {
        return FOLLOW_RANGE;
    }

    /** After spawning, e.g. to put a model on the monster */
    protected void onSpawn() {}

    /** The monster died, monsters with a model play their death animation */
    public void onDeath() {}

    /** The monster hits a player */
    public void onAttack() {}

    /** Movement speed limit, always below the players' walking speed */
    protected double maxSpeed() {
        return 0.23;
    }

    public boolean showsHealthBar() {
        return true;
    }

    private de.ethicbuilds.monsters.monster.manager.MonsterManager monsterManager() {
        return plugin.getInjector().getInstance(de.ethicbuilds.monsters.monster.manager.MonsterManager.class);
    }
}
