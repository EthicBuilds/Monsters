package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;

/***
 * Boss of every tenth wave: the big amethyst weaver with a lot of health, a boss bar for everyone and its crystal volley.
 * Every ten waves it comes back one level stronger: more health, more crystals, from level 2 a web shot,
 * from level 3 it calls its brood and from level 4 it leaps at the players.
 */
public class MonsterSpiderBoss extends MonsterSpider {
    // Crystal volley every 6 to 9 seconds (checked every half second)
    private static final int MIN_VOLLEY = 12, MAX_VOLLEY = 18;

    private static final int MAX_HEALTH = 4000;
    private static final int WEB_GAP = 18, BROOD_GAP = 40, LEAP_GAP = 26;
    private static final double LEAP_RANGE = 20, SHOCK_RADIUS = 4, SHOCK_DAMAGE = 8;

    private final int level;
    private final int players;
    private BossBar bar;
    private int nextVolley = MIN_VOLLEY;
    private int nextWeb = WEB_GAP, nextBrood = BROOD_GAP, nextLeap = LEAP_GAP;

    public MonsterSpiderBoss(int players, int wave) {
        super();
        this.players = players;
        this.level = Math.max(1, wave / MonsterManager.BOSS_WAVE);
        this.health = (int) Math.min(MAX_HEALTH, (300 + 200 * players) * (1 + 0.5 * (level - 1)));
        this.damage = 6;
        this.speed = 0.9;
        this.name = "§5Amethyst-Weberin";
        this.coin = 500;
        this.modelSize = 1.0F;
        this.hitboxScale = 3.0;
        this.room = 2;
        this.attackCooldown = 30;
    }

    // Finds the players across the whole map
    @Override
    protected double followRange() {
        return 128;
    }

    @Override
    protected double maxSpeed() {
        return 0.23;
    }

    @Override
    public boolean showsHealthBar() {
        return false;
    }

    @Override
    protected void onSpawn() {
        super.onSpawn();
        bar = Bukkit.createBossBar("§5§lAmethyst-Weberin §8· §dStufe " + level, BarColor.PURPLE, BarStyle.SEGMENTED_10);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!monster.isValid() || monster.isDead()) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
                bar.setProgress(Math.max(0, Math.min(1, monster.getHealth() / health)));

                attacks();
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 10L);
    }

    private void attacks() {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        if (--nextVolley <= 0) {
            // Each level a bit more often, down to every 3 seconds
            int faster = 2 * (level - 1);
            nextVolley = random.nextInt(Math.max(6, MIN_VOLLEY - faster), Math.max(7, MAX_VOLLEY - faster) + 1);
            // Rears up with the crystal animation, the crystals leave its back shortly after
            if (model != null) model.play("kristall");
            CrystalBarrage.fire(monster, (monster.getHealth() < health / 2 ? 3 : 1) + 2 * (level - 1));
        }
        Player target = nearest();
        if (level >= 2 && --nextWeb <= 0 && target != null) {
            nextWeb = WEB_GAP + random.nextInt(6);
            WebShot.fire(monster, target);
        }
        if (level >= 3 && --nextBrood <= 0) {
            nextBrood = BROOD_GAP;
            Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).addBrood(2 + players);
            monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_SPIDER_HURT, 1.5F, 0.5F);
        }
        if (level >= 4 && --nextLeap <= 0 && target != null && target.getLocation().distance(monster.getLocation()) < LEAP_RANGE) {
            nextLeap = LEAP_GAP + random.nextInt(8);
            leap(target);
        }
    }

    private Player nearest() {
        return Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).nearestPlayer(monster.getLocation());
    }

    // Jumps at the player and lands with a shock wave that throws everyone close away
    private void leap(Player target) {
        org.bukkit.util.Vector jump = target.getLocation().toVector().subtract(monster.getLocation().toVector());
        jump.setY(0);
        double distance = jump.length();
        if (distance < 0.1) return;
        jump.normalize().multiply(Math.min(1.6, distance / 8)).setY(0.9);
        monster.setVelocity(jump);
        monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_RAVAGER_ROAR, 1.0F, 1.4F);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!monster.isValid() || ticks > 60) {
                    cancel();
                    return;
                }
                if (ticks < 6 || !monster.isOnGround()) return;
                cancel();
                var at = monster.getLocation();
                at.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, at, 60, 2, 0.3, 2, at.clone().subtract(0, 1, 0).getBlock().getBlockData());
                at.getWorld().playSound(at, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 0.7F);
                var users = Main.getINSTANCE().getInjector().getInstance(de.ethicbuilds.monsters.player.manager.UserManager.class);
                for (Player player : at.getWorld().getPlayers()) {
                    if (users.isPlayerDead(player.getUniqueId())) continue;
                    double d = player.getLocation().distance(at);
                    if (d > SHOCK_RADIUS) continue;
                    player.damage(SHOCK_DAMAGE * (1 - 0.5 * d / SHOCK_RADIUS), monster);
                    org.bukkit.util.Vector away = player.getLocation().toVector().subtract(at.toVector()).setY(0);
                    if (away.lengthSquared() > 0.01) player.setVelocity(away.normalize().multiply(0.9).setY(0.4));
                    player.sendHurtAnimation(0);
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 1L, 1L);
    }

    @Override
    public void onDeath() {
        super.onDeath();
        if (bar != null) bar.removeAll();
    }
}
