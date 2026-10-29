package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Boss of every tenth wave: the big amethyst weaver with a lot of health and a boss bar for everyone.
 * Every ten waves it comes back one level stronger with more attacks (animations and ground markers from the design
 * session): level 1 the crystal volley, from level 2 web shot and stomp, from level 3 its brood, from level 4 the leap.
 * Never the same attack twice in a row. Defeated, it ends with a victory show and coins for everyone.
 */
public class MonsterSpiderBoss extends MonsterSpider {
    // An attack every 6 to 9 seconds (checked every half second), each level a bit more often
    private static final int MIN_GAP = 12, MAX_GAP = 18;
    private static final int MAX_HEALTH = 4000;
    private static final double LEAP_RANGE = 20, SHOCK_RADIUS = 4, SHOCK_DAMAGE = 8;
    private static final double STOMP_RADIUS = 7, STOMP_DAMAGE = 8;
    // Moments in the animations (ticks)
    private static final int WEB_SHOT = 17, STOMP_HIT = 17, LEAP_PUSH = 11;
    private static final int[] BROOD_PULSES = {9, 17, 25};
    private static final int REWARD_PER_LEVEL = 500;
    public static final NamespacedKey SHOW_FIREWORK = new NamespacedKey("monsters", "boss_feuerwerk");
    private static final Color AMETHYST = Color.fromRGB(0xA970FF), GOLD = Color.fromRGB(0xFFCC2E), ORANGE = Color.fromRGB(0xF9A11E);

    private enum Attack { VOLLEY, WEB, STOMP, BROOD, LEAP }

    private final int level;
    private final int players;
    private BossBar bar;
    private boolean defeated;
    private boolean stompShown;
    private Attack last;
    private int nextAttack = MIN_GAP;

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
                    // After the victory the bar stays a moment longer
                    if (!defeated) bar.removeAll();
                    cancel();
                    return;
                }
                Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
                bar.setProgress(Math.max(0, Math.min(1, monster.getHealth() / health)));

                if (--nextAttack <= 0) attack();
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 10L);
    }

    private void attack() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int faster = 2 * (level - 1);
        nextAttack = random.nextInt(Math.max(6, MIN_GAP - faster), Math.max(7, MAX_GAP - faster) + 1);

        Player target = nearest();
        List<Attack> choices = new ArrayList<>(List.of(Attack.VOLLEY));
        if (level >= 2 && target != null) choices.add(Attack.WEB);
        if (level >= 2) choices.add(Attack.STOMP);
        if (level >= 3) choices.add(Attack.BROOD);
        if (level >= 4 && target != null && target.getLocation().distance(monster.getLocation()) < LEAP_RANGE) choices.add(Attack.LEAP);
        if (choices.size() > 1) choices.remove(last);
        last = choices.get(random.nextInt(choices.size()));

        switch (last) {
            case VOLLEY -> {
                // Rears up with the crystal animation, the crystals leave its back shortly after
                if (model != null) model.play("kristall");
                CrystalBarrage.fire(monster, (monster.getHealth() < health / 2 ? 3 : 1) + 2 * (level - 1));
            }
            case WEB -> web(target);
            case STOMP -> stomp();
            case BROOD -> brood();
            case LEAP -> leap(target);
        }
    }

    private Player nearest() {
        return Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).nearestPlayer(monster.getLocation());
    }

    private void later(int ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (monster.isValid() && !monster.isDead()) action.run();
        }, ticks);
    }

    // Rears up and spits a web at the spot where the player stands now, the circle shows it from the start
    private void web(Player target) {
        Location to = GroundMark.ground(target.getLocation());
        if (to == null) to = target.getLocation();
        if (model != null) model.play("netz");
        GroundMark.warn(to, GroundMark.WEB, WebShot.RADIUS, WEB_SHOT + WebShot.FLIGHT);
        Location spot = to;
        later(WEB_SHOT, () -> WebShot.fire(monster, spot));
    }

    // Up on the hind legs, then down: a shock wave around the boss, jumping dodges it
    private void stomp() {
        Location at = GroundMark.ground(monster.getLocation());
        if (at == null) at = monster.getLocation();
        if (model != null) model.play("stampfen");
        GroundMark.warn(at, GroundMark.DANGER, STOMP_RADIUS, STOMP_HIT);
        if (!stompShown) {
            stompShown = true;
            for (Player player : living()) {
                if (player.getLocation().distance(at) < STOMP_RADIUS * 2) player.sendActionBar(MiniMessage.miniMessage().deserialize("<#FF4838>Spring!"));
            }
        }
        Location center = at;
        later(STOMP_HIT, () -> {
            World world = center.getWorld();
            world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0F, 0.8F);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 0.7F);
            GroundMark.shockwave(center, 3);
            for (Player player : living()) {
                double distance = player.getLocation().distance(center);
                if (distance > STOMP_RADIUS || !player.isOnGround()) continue;
                player.damage(STOMP_DAMAGE * (1 - 0.5 * distance / STOMP_RADIUS), monster);
                player.setVelocity(new Vector(0, 0.9, 0));
                player.sendHurtAnimation(0);
            }
        });
    }

    // The abdomen pumps three times, one part of the brood each time
    private void brood() {
        if (model != null) model.play("brut");
        int each = (int) Math.ceil((2 + players) / 3.0);
        for (int pulse : BROOD_PULSES) {
            later(pulse, () -> {
                Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).addBrood(each);
                monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_SPIDER_HURT, 1.5F, 0.5F);
            });
        }
    }

    // Ducks, jumps at the player and lands with a shock wave that throws everyone close away
    private void leap(Player target) {
        if (model != null) model.play("sprung");
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0F, 1.4F);
        later(LEAP_PUSH, () -> {
            Vector jump = target.getLocation().toVector().subtract(monster.getLocation().toVector());
            jump.setY(0);
            double distance = jump.length();
            if (distance < 0.1) return;
            Location landing = GroundMark.ground(target.getLocation());
            GroundMark mark = landing != null ? GroundMark.warn(landing, GroundMark.DANGER, SHOCK_RADIUS, 40) : null;
            jump.normalize().multiply(Math.min(1.6, distance / 8)).setY(0.9);
            monster.setVelocity(jump);
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    ticks++;
                    if (!monster.isValid() || ticks > 60) {
                        if (mark != null) mark.clear();
                        cancel();
                        return;
                    }
                    if (ticks < 6 || !monster.isOnGround()) return;
                    cancel();
                    if (mark != null) mark.clear();
                    land();
                }
            }.runTaskTimer(Main.getINSTANCE(), 1L, 1L);
        });
    }

    private void land() {
        if (model != null) model.play("landung");
        Location at = monster.getLocation();
        World world = at.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 60, 2, 0.3, 2, at.clone().subtract(0, 1, 0).getBlock().getBlockData());
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 0.7F);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
        GroundMark.shockwave(at, 1);
        // Purple ring like the crystal impact marks
        Particle.DustOptions ring = new Particle.DustOptions(AMETHYST, 1.4F);
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32;
            world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * SHOCK_RADIUS, 0.2, Math.sin(a) * SHOCK_RADIUS), 1, ring);
        }
        for (Player player : living()) {
            double d = player.getLocation().distance(at);
            if (d > SHOCK_RADIUS) continue;
            player.damage(SHOCK_DAMAGE * (1 - 0.5 * d / SHOCK_RADIUS), monster);
            Vector away = player.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (away.lengthSquared() > 0.01) player.setVelocity(away.normalize().multiply(0.9).setY(0.4));
            player.sendHurtAnimation(0);
        }
    }

    private List<Player> living() {
        UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
        return users.getGamePlayers().stream().map(GamePlayer::getPlayer)
                .filter(p -> p.isOnline() && !users.isPlayerDead(p.getUniqueId()) && p.getWorld().equals(monster.getWorld())).toList();
    }

    @Override
    public void onDeath() {
        super.onDeath();
        defeated = true;
        victory(monster.getLocation());
    }

    // About 3.5 seconds: crystals burst, title, fireworks, a gold spiral and the coins for everyone
    private void victory(Location at) {
        World world = at.getWorld();
        int reward = REWARD_PER_LEVEL * level;
        UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
        List<GamePlayer> team = users.getGamePlayers().stream().filter(p -> p.getPlayer().isOnline()).toList();
        team.forEach(p -> p.addCoins(reward));

        Location body = at.clone().add(0, 1, 0);
        world.spawnParticle(Particle.BLOCK, body, 80, 1.2, 1.0, 1.2, Material.AMETHYST_BLOCK.createBlockData());
        world.spawnParticle(Particle.DUST, body, 60, 1.5, 1.5, 1.5, new Particle.DustOptions(AMETHYST, 2.0F));
        world.spawnParticle(Particle.EXPLOSION_EMITTER, body, 1);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0F, 0.6F);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 0.8F);
        world.playSound(at, Sound.ENTITY_WITHER_DEATH, 0.3F, 1.6F);

        if (bar != null) {
            bar.setTitle("§x§F§F§C§C§2§E✔ Amethyst-Weberin besiegt");
            bar.setColor(BarColor.YELLOW);
            bar.setProgress(1);
            Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), bar::removeAll, 60L);
        }

        MiniMessage mm = MiniMessage.miniMessage();
        Title title = Title.title(mm.deserialize("<bold><gradient:#FFCC2E:#F9A11E>BOSS BESIEGT</gradient></bold>"),
                mm.deserialize("<#A970FF>Amethyst-Weberin</#A970FF> <#8A93A3>· Stufe " + level + " · +" + reward + "</#8A93A3> <white><font:monsters:hud></font></white>"),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(750)));
        Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            for (GamePlayer gamePlayer : team) {
                Player player = gamePlayer.getPlayer();
                if (!player.isOnline()) continue;
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                player.showTitle(title);
            }
        }, 8L);

        for (int i = 0; i < 4; i++) {
            int index = i;
            Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> firework(at, index), 20L + i * 8L);
        }

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick += 2;
                if (tick > 40) {
                    cancel();
                    return;
                }
                double height = tick / 40.0 * 3;
                for (int arm = 0; arm < 2; arm++) {
                    double angle = tick * 0.5 + Math.PI * arm;
                    world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * 0.8, height, Math.sin(angle) * 0.8), 1,
                            new Particle.DustOptions(GOLD, 1.2F));
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 20L, 2L);

        Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            for (GamePlayer gamePlayer : team) {
                Player player = gamePlayer.getPlayer();
                if (!player.isOnline()) continue;
                player.sendActionBar(mm.deserialize("<#FFCC2E>+" + reward + " <white><font:monsters:hud></font></white>"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
            }
            Bukkit.broadcastMessage(String.format("%s§5Amethyst-Weberin §7(Stufe %d) besiegt! Alle bekommen §6%d Coins§7!",
                    Main.getINSTANCE().getMonstersPrefix(), level, reward));
        }, 60L);
    }

    // Detonates right away around the body, it does not hurt anyone
    private static void firework(Location at, int index) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        Location spot = at.clone().add(Math.cos(angle) * 3, random.nextDouble(2, 4), Math.sin(angle) * 3);
        Color[] colors = {AMETHYST, GOLD, ORANGE};
        Firework firework = at.getWorld().spawn(spot, Firework.class, fw -> {
            var meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(index % 2 == 0 ? FireworkEffect.Type.BURST : FireworkEffect.Type.STAR)
                    .withColor(colors[index % 3], colors[(index + 1) % 3]).withFade(Color.WHITE).withFlicker().build());
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(SHOW_FIREWORK, PersistentDataType.BYTE, (byte) 1);
        });
        firework.detonate();
    }
}
