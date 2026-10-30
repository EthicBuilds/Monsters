package de.ethicbuilds.monsters.monster;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.GoalType;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.monster.model.AnimatedModel;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/***
 * Bounty zombie: golden, runs away instead of attacking and escapes after 45 seconds.
 * Whoever gets it receives a lot of coins.
 */
public class MonsterBounty extends EnemyMonster {
    private static final int ESCAPE_SECONDS = 45;
    private static final int BASE_REWARD = 500, REWARD_PER_WAVE = 50;
    private static final double FLEE_DISTANCE = 30;

    private static final String MODEL = "kopfgeld_zombie";
    private static final Color GLOW = Color.fromRGB(0xFFCC2E);

    private final int reward;
    private int seconds;
    private AnimatedModel model;

    public MonsterBounty(int wave) {
        this.health = 40;
        this.damage = 0;
        this.speed = 1.3;
        this.name = "§6Kopfgeld-Zombie";
        this.type = EntityType.ZOMBIE;
        this.reward = BASE_REWARD + REWARD_PER_WAVE * wave;
        this.coin = reward;
    }

    @Override
    protected void onSpawn() {
        Bukkit.getMobGoals().removeAllGoals(monster, GoalType.TARGET);
        // Model of the design session glowing gold, else the vanilla zombie in gold armor
        var bones = MonsterZombie.load(MODEL).orElse(null);
        var equipment = bones == null ? monster.getEquipment() : null;
        if (bones != null) {
            monster.setInvisible(true);
            monster.setCanPickupItems(false);
            if (monster.getEquipment() != null) monster.getEquipment().clear();
            model = new AnimatedModel(bones, monster, 1.0F);
            model.glow(GLOW);
            model.play("spawn");
        } else {
            monster.setGlowing(true);
        }
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            equipment.setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
            equipment.setHelmetDropChance(0);
            equipment.setChestplateDropChance(0);
        }
        String prize = Overlay.num("+" + reward) + " " + Overlay.coin();
        Overlay.mediumAll(Overlay.GOLD, Overlay.line(Overlay.BOUNTY, Overlay.GOLD, "KOPFGELD", "Goldener Zombie · " + prize),
                Overlay.line(Overlay.BOUNTY, Overlay.GOLD, "BOUNTY", "golden zombie · " + prize));
        for (Player player : Bukkit.getOnlinePlayers()) player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.8F, 1.6F);
    }

    // Runs to the trail spot farthest from the players, after 45 seconds it is gone
    @Override
    protected boolean flee(Pathfinder pathfinder) {
        monster.setTarget(null);
        monster.getWorld().spawnParticle(Particle.WAX_ON, monster.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0);
        if (++seconds >= ESCAPE_SECONDS) {
            escape();
            return true;
        }
        MonsterManager monsters = Main.getINSTANCE().getInjector().getInstance(MonsterManager.class);
        Player hunter = monsters.nearestPlayer(monster.getLocation());
        if (hunter == null) return true;
        Location best = null;
        double bestDistance = 0;
        for (GamePlayer gamePlayer : Main.getINSTANCE().getInjector().getInstance(UserManager.class).getGamePlayers()) {
            for (Location spot : PlayerTrail.of(gamePlayer.getPlayer())) {
                if (!spot.getWorld().equals(monster.getWorld()) || spot.distance(monster.getLocation()) > FLEE_DISTANCE) continue;
                double distance = spot.distance(hunter.getLocation());
                if (distance > bestDistance) {
                    bestDistance = distance;
                    best = spot;
                }
            }
        }
        if (best != null) pathfinder.moveTo(best, speed);
        return true;
    }

    private void escape() {
        Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).removeMonster(this);
        monster.getWorld().spawnParticle(Particle.CLOUD, monster.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
        if (model != null) model.remove();
        monster.remove();
        Lang.noteAll("%s§7Der Kopfgeld-Zombie ist entkommen!", "%s§7The Bounty Zombie got away!", Main.getINSTANCE().getMonstersPrefix());
    }

    @Override
    public void onDeath() {
        Player killer = monster.getKiller();
        if (model != null) model.die(killer != null ? killer.getLocation() : null);
        if (killer == null) return;
        GamePlayer hunter = Main.getINSTANCE().getInjector().getInstance(UserManager.class).getGamePlayer(killer.getUniqueId());
        if (hunter == null) return;
        hunter.addCoins(reward);
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.6F);
        Lang.noteAll("%s§6%s §7hat das Kopfgeld geholt: §6+%d Coins", "%s§6%s §7claimed the bounty: §6+%d Coins", Main.getINSTANCE().getMonstersPrefix(), killer.getName(), reward);
    }
}
