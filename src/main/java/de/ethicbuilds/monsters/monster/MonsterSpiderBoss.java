package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.DualBar;
import de.ethicbuilds.monsters.lang.Lang;
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
 * session): level 1 the crystal volley, from level 2 web shot and stomp, from level 3 its brood, from level 4 the leap,
 * from level 5 the cocoon trap, from level 6 the silk pull, from level 7 the crystal rain. From level 5 it looks darker
 * with glowing eyes and bigger crystals, under a quarter of its health it rages. Never the same attack twice in a row. Defeated, it ends with a victory show and coins for everyone.
 */
public class MonsterSpiderBoss extends MonsterSpider {
    // An attack every 6 to 9 seconds (checked every half second), each level a bit more often
    private static final int MIN_GAP = 12, MAX_GAP = 18;
    private static final int MAX_HEALTH = 4000;
    private static final double LEAP_RANGE = 20, SHOCK_RADIUS = 4, SHOCK_DAMAGE = 8;
    private static final double STOMP_RADIUS = 7, STOMP_DAMAGE = 8;
    // Moments in the animations (ticks)
    private static final int WEB_SHOT = 17, STOMP_HIT = 17, LEAP_PUSH = 11, COCOON_SHOT = 20, SILK_TICKS = 6, RAIN_START = 24, RAGE_ROAR = 6;
    private static final double RAGE_AT = 0.25, RAGE_SPEED = 1.3, RAGE_DAMAGE = 1.25, RAGE_GAP = 0.6;
    private static final double PULL_RANGE = 40, PULL_MIN = 8, PULL_DAMAGE = 4;
    private static final Color RAGE = Color.fromRGB(0xFF5A4A), RAGE_DUST = Color.fromRGB(0xFF3B30), SILK = Color.fromRGB(0xECE8F0);
    // Darker the higher the level, from level 5 (design session)
    private static final int[] LEVEL_TINTS = {0xD8CCE8, 0xC8B8E0, 0xB8A4D8, 0xA890CC};
    private static final int[] BROOD_PULSES = {9, 17, 25};
    private static final int REWARD_PER_LEVEL = 500;
    public static final NamespacedKey SHOW_FIREWORK = new NamespacedKey("monsters", "boss_feuerwerk");
    private static final Color AMETHYST = Color.fromRGB(0xA970FF), GOLD = Color.fromRGB(0xFFCC2E), ORANGE = Color.fromRGB(0xF9A11E);

    private enum Attack { VOLLEY, WEB, STOMP, BROOD, LEAP, COCOON, PULL, RAIN }

    private final int level;
    private final int players;
    private DualBar bar;
    private boolean defeated;
    private boolean stompShown;
    private boolean rage;
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
        if (level >= 5) look(Color.fromRGB(LEVEL_TINTS[Math.min(level - 5, LEVEL_TINTS.length - 1)]));
        bar = new DualBar("§5§lAmethyst-Weberin §8· §dStufe " + level, "§5§lAmethyst Weaver §8· §dLevel " + level, BarColor.PURPLE, BarStyle.SEGMENTED_10);
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
                if (!rage && monster.getHealth() < health * RAGE_AT) rage();
                if (rage) monster.getWorld().spawnParticle(Particle.DUST, monster.getLocation().add(0, 1.2, 0), 6, 1.2, 0.6, 1.2,
                        new Particle.DustOptions(RAGE_DUST, 1.2F));

                if (!de.ethicbuilds.monsters.gameplay.manager.PauseManager.isPaused() && --nextAttack <= 0) attack();
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 10L);
    }

    private void attack() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int faster = 2 * (level - 1);
        nextAttack = random.nextInt(Math.max(6, MIN_GAP - faster), Math.max(7, MAX_GAP - faster) + 1);
        if (rage) nextAttack = Math.max(3, (int) Math.round(nextAttack * RAGE_GAP));

        Player target = nearest();
        List<Attack> choices = new ArrayList<>(List.of(Attack.VOLLEY));
        if (level >= 2 && target != null) choices.add(Attack.WEB);
        if (level >= 2) choices.add(Attack.STOMP);
        if (level >= 3) choices.add(Attack.BROOD);
        if (level >= 4 && target != null && target.getLocation().distance(monster.getLocation()) < LEAP_RANGE) choices.add(Attack.LEAP);
        if (level >= 5 && !living().isEmpty()) choices.add(Attack.COCOON);
        Player far = farthest();
        if (level >= 6 && far != null) choices.add(Attack.PULL);
        if (level >= 7) choices.add(Attack.RAIN);
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
            case COCOON -> cocoon(living().get(random.nextInt(living().size())));
            case PULL -> pull(far);
            case RAIN -> {
                if (model != null) model.play("regen");
                later(RAIN_START, () -> CrystalRain.fall(monster));
            }
        }
    }

    // Same models in another colour, from level 5 with glowing eyes and bigger crystals
    private void look(Color tint) {
        if (model == null) return;
        model.restyle("weberin_getoent", tint, level >= 5 ? java.util.Map.of("head", "weberin_getoent/head_augen") : java.util.Map.of());
        if (level > 4) model.scaleBones("crystal_", (float) Math.min(1.4, 1 + 0.08 * (level - 4)));
    }

    // Under a quarter of its health: red, quicker and attacks more often
    private void rage() {
        rage = true;
        if (model != null) model.play("wut");
        look(RAGE);
        if (model != null) model.glow(RAGE);
        var movement = monster.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(movement.getBaseValue() * RAGE_SPEED);
        var attack = monster.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(attack.getBaseValue() * RAGE_DAMAGE);
        bar.setColor(BarColor.RED);
        bar.setTitle("§5§lAmethyst-Weberin §8· §dStufe " + level + " §8· §c§lWUT", "§5§lAmethyst Weaver §8· §dLevel " + level + " §8· §c§lRAGE");
        Location at = monster.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(RAGE_DUST, 1.5F);
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32;
            at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 3, 0.3, Math.sin(a) * 3), 1, dust);
        }
        at.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, at.clone().add(0, monster.getHeight() + 0.5, 0), 5, 0.6, 0.3, 0.6);
        later(RAGE_ROAR, () -> {
            monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0F, 0.7F);
            monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5F, 0.6F);
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(Lang.mm(player, "<bold><#FF4838>DIE WEBERIN TOBT!</#FF4838></bold>", "<bold><#FF4838>THE WEAVER RAGES!</#FF4838></bold>"),
                    Lang.mm(player, "<#8A93A3>schneller · stärker · häufiger</#8A93A3>", "<#8A93A3>faster · stronger · more often</#8A93A3>"),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
    }

    private Player farthest() {
        Player far = null;
        double best = PULL_MIN;
        for (Player player : living()) {
            double distance = player.getLocation().distance(monster.getLocation());
            if (distance <= PULL_RANGE && distance > best) {
                best = distance;
                far = player;
            }
        }
        return far;
    }

    // Silk thread from the abdomen to the player, drawn a bit further every tick
    private void silk(Player target, int ticks) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (++tick > ticks || !monster.isValid() || !target.isOnline()) {
                    cancel();
                    return;
                }
                Location from = monster.getLocation().add(0, 1, 0);
                Vector way = target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
                double length = way.length() * tick / ticks;
                way.normalize();
                Particle.DustOptions dust = new Particle.DustOptions(SILK, 1.0F);
                for (double d = 0; d < length; d += 0.5) {
                    monster.getWorld().spawnParticle(Particle.DUST, from.clone().add(way.clone().multiply(d)), 1, dust);
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 1L);
    }

    // Spins the player in where they stand, the circle warns from the start of the animation
    private void cocoon(Player target) {
        Location spot = GroundMark.ground(target.getLocation());
        if (spot == null) spot = target.getLocation();
        if (model != null) model.play("kokon");
        GroundMark.warn(spot, GroundMark.WEB, 1.5, COCOON_SHOT + SILK_TICKS);
        Location mark = spot;
        later(COCOON_SHOT, () -> {
            monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.0F, 0.6F);
            silk(target, SILK_TICKS);
        });
        later(COCOON_SHOT + SILK_TICKS, () -> {
            UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
            if (!target.isOnline() || users.isPlayerDead(target.getUniqueId()) || !target.getWorld().equals(mark.getWorld())
                    || target.getLocation().distanceSquared(mark) > 2.5 * 2.5) return;
            Cocoon.trap(target, monster);
        });
    }

    // Silk pull: the farthest player is pulled close and bitten right after
    private void pull(Player target) {
        if (model != null) model.play("netz");
        later(WEB_SHOT, () -> {
            if (!target.isOnline()) return;
            monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.0F, 0.6F);
            target.playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0F, 0.6F);
            target.sendActionBar(Lang.mm(target, "<#ECE8F0>Seidenzug!</#ECE8F0>", "<#ECE8F0>Silk pull!</#ECE8F0>"));
            silk(target, 4);
            new BukkitRunnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (++tick > 20 || !monster.isValid() || !target.isOnline()) {
                        cancel();
                        return;
                    }
                    Vector way = monster.getLocation().toVector().subtract(target.getLocation().toVector());
                    if (way.length() < 3) {
                        cancel();
                        if (model != null) model.play("beissen");
                        target.damage(PULL_DAMAGE, monster);
                        return;
                    }
                    target.setVelocity(way.normalize().multiply(1.1).setY(0.25));
                }
            }.runTaskTimer(Main.getINSTANCE(), 4L, 1L);
        });
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
                if (player.getLocation().distance(at) < STOMP_RADIUS * 2) player.sendActionBar(Lang.mm(player, "<#FF4838>Spring!", "<#FF4838>Jump!"));
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
            bar.setTitle("§x§F§F§C§C§2§E✔ Amethyst-Weberin besiegt", "§x§F§F§C§C§2§E✔ Amethyst Weaver defeated");
            bar.setColor(BarColor.YELLOW);
            bar.setProgress(1);
            Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), bar::removeAll, 60L);
        }

        MiniMessage mm = MiniMessage.miniMessage();
        Title.Times times = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(750));
        String coin = " <white><font:monsters:hud></font></white>";
        Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            for (GamePlayer gamePlayer : team) {
                Player player = gamePlayer.getPlayer();
                if (!player.isOnline()) continue;
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                player.showTitle(Title.title(Lang.mm(player, "<bold><gradient:#FFCC2E:#F9A11E>BOSS BESIEGT</gradient></bold>",
                                "<bold><gradient:#FFCC2E:#F9A11E>BOSS DEFEATED</gradient></bold>"),
                        Lang.mm(player, "<#A970FF>Amethyst-Weberin</#A970FF> <#8A93A3>· Stufe " + level + " · +" + reward + "</#8A93A3>" + coin,
                                "<#A970FF>Amethyst Weaver</#A970FF> <#8A93A3>· Level " + level + " · +" + reward + "</#8A93A3>" + coin),
                        times));
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
            Lang.broadcast("%s§5Amethyst-Weberin §7(Stufe %d) besiegt! Alle bekommen §6%d Coins§7!",
                    "%s§5Amethyst Weaver §7(Level %d) defeated! Everyone gets §6%d Coins§7!", Main.getINSTANCE().getMonstersPrefix(), level, reward);
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
