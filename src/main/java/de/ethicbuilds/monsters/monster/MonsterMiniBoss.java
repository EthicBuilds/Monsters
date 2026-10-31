package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.lang.DualBar;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.manager.GrenadeManager;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/***
 * One stronger zombie per wave with a name and a boss bar, it leaves a grenade or coins for whoever kills it.
 * Every 10 to 14 seconds it uses the ability of its name, shown a second before so players can dodge.
 */
public class MonsterMiniBoss extends MonsterZombie {
    private static final String[] NAMES = {"Grabwächter", "Wüstenfürst", "Knochenbrecher", "Mumienkönig", "Sandschlund"};
    private static final String[] NAMES_EN = {"Tomb Warden", "Desert Lord", "Bonecrusher", "Mummy King", "Sand Maw"};
    private static final int LOOT_COINS = 300;
    private static final String BAR_COLOR = "#FF4838";
    // Abilities every 10 to 14 seconds (checked every half second), warned 1 second before
    private static final int SKILL_MIN = 20, SKILL_MAX = 28, SKILL_FIRST = 16, WARN = 20;
    private static final int CRAWLERS = 3;
    private static final double STORM_RADIUS = 6, STORM_DAMAGE = 1;
    private static final int STORM_TICKS = 80;
    private static final double CHARGE_LENGTH = 8, CHARGE_SPEED = 0.8, CHARGE_HIT = 1.6, CHARGE_DAMAGE = 6;
    private static final int CHARGE_TICKS = 12;
    private static final double PULL_RANGE = 14, PULL_MIN = 4;
    private static final int ROOT_TICKS = 40;
    private static final double EMERGE_RADIUS = 3, EMERGE_DAMAGE = 6;
    private static final int BURROW_TICKS = 40;
    private static final float BURY_DEPTH = 2.0F;
    private static final NamespacedKey ROOT = new NamespacedKey("monsters", "bandagen");
    private static final Color SAND = Color.fromRGB(0xE3C77F), BANDAGE = Color.fromRGB(0xE8DCC0), DANGER = Color.fromRGB(0xFF4838);

    private final String title, titleEn;
    private final int kind, round;
    private DualBar bar;
    private int nextSkill = SKILL_FIRST;
    private boolean burrowed;

    // Each kind once before one comes again, in a new order each time (the grave keeper came far less often, 31.10.)
    private static final Deque<Integer> nextKinds = new ArrayDeque<>();

    private static int nextKind() {
        if (nextKinds.isEmpty()) {
            List<Integer> kinds = new ArrayList<>();
            for (int i = 0; i < NAMES.length; i++) kinds.add(i);
            Collections.shuffle(kinds);
            nextKinds.addAll(kinds);
        }
        return nextKinds.poll();
    }

    public MonsterMiniBoss(int wave) {
        this(wave, nextKind());
    }

    private MonsterMiniBoss(int wave, int kind) {
        super(EntityType.ZOMBIE, "§c" + NAMES[kind]);
        this.kind = kind;
        this.round = wave;
        this.title = NAMES[kind];
        this.titleEn = NAMES_EN[kind];
        this.health = 30 + 15 * wave / 2;
        this.damage = 4;
        this.coin = 100;
    }

    // Models of the design session, same order as NAMES
    private static final String[] MODEL_IDS = {"grabwaechter", "wuestenfuerst", "knochenbrecher", "mumienkoenig", "sandschlund"};
    // The hitbox stays under 2 blocks for the spawn holes, only the model is bigger
    private static final float MODEL_SIZE = 1.4F;

    @Override
    protected String modelId() {
        return MODEL_IDS[kind];
    }

    @Override
    protected float modelSize() {
        return MODEL_SIZE;
    }

    @Override
    protected Voice voice() {
        return Voice.boss();
    }

    @Override
    protected boolean losesLimbs() {
        return false;
    }

    @Override
    protected void onSpawn() {
        super.onSpawn();
        // Normal size (under 2 blocks), so it fits through every spawn hole; the boss bar marks it
        var attack = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(damage);
        EntityEquipment equipment = model() == null ? monster.getEquipment() : null;
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            equipment.setHelmetDropChance(0);
            equipment.setChestplateDropChance(0);
        }

        bar = new DualBar(Overlay.parse(Overlay.line(Overlay.BOSS, BAR_COLOR, title, null)),
                Overlay.parse(Overlay.line(Overlay.BOSS, BAR_COLOR, titleEn, null)), BarColor.RED, BarStyle.SEGMENTED_6);
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
                if (!burrowed && getTarget() != null && LivingPlayers.waveRunning() && --nextSkill <= 0) skill();
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 10L);
    }

    private void skill() {
        nextSkill = ThreadLocalRandom.current().nextInt(SKILL_MIN, SKILL_MAX + 1);
        switch (kind) {
            case 0 -> crawlers();
            case 1 -> sandstorm();
            case 2 -> charge(getTarget());
            case 3 -> bandages();
            default -> burrow(getTarget());
        }
    }

    // Only while it lives and the wave runs
    private void later(int ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (monster.isValid() && !monster.isDead() && LivingPlayers.waveRunning()) action.run();
        }, ticks);
    }

    private static Location ground(Location at) {
        Location ground = GroundMark.ground(at);
        return ground != null ? ground : at;
    }

    private static void dirt(Location at, int count) {
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.2, 0), count, 0.3, 0.1, 0.3,
                at.clone().subtract(0, 0.5, 0).getBlock().getBlockData());
    }

    // Grabwächter: three crawlers dig themselves out around it
    private void crawlers() {
        Location center = monster.getLocation();
        double start = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        List<Location> spots = new ArrayList<>();
        for (int i = 0; i < CRAWLERS; i++) {
            double angle = start + Math.PI * 2 * i / CRAWLERS;
            Location spot = GroundMark.ground(center.clone().add(Math.cos(angle) * 2, 0.5, Math.sin(angle) * 2));
            if (spot == null || !OpenGround.isFree(spot, 0, 2)) spot = ground(center);
            spots.add(spot);
            GroundMark.still(spot, GroundMark.DANGER, 0.8, WARN);
            dirt(spot, 10);
        }
        monster.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_AMBIENT, 1.2F, 0.5F);
        monster.getWorld().playSound(center, Sound.ENTITY_WARDEN_DIG, 0.8F, 1.3F);
        later(WARN, () -> {
            MonsterManager monsters = Main.getINSTANCE().getInjector().getInstance(MonsterManager.class);
            Player target = monsters.leastChasedPlayer();
            if (target == null) return;
            for (Location spot : spots) {
                MonsterCrawler crawler = new MonsterCrawler();
                crawler.setWave(round);
                monsters.summonAt(crawler, target, spot);
                dirt(spot, 20);
                spot.getWorld().playSound(spot, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0F, 0.7F);
            }
        });
    }

    // Wüstenfürst: 4 seconds of sand storm around it, slows and stings everyone close
    private void sandstorm() {
        GroundMark.warn(ground(monster.getLocation()), GroundMark.DANGER, STORM_RADIUS, WARN);
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_HUSK_AMBIENT, 1.2F, 0.5F);
        monster.getWorld().playSound(monster.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.6F, 0.6F);
        monster.getWorld().spawnParticle(Particle.FALLING_DUST, monster.getLocation().add(0, 1, 0), 30, 1.5, 0.8, 1.5,
                Material.SAND.createBlockData());
        later(WARN, () -> {
            Set<UUID> told = new HashSet<>();
            Particle.DustOptions dust = new Particle.DustOptions(SAND, 1.6F);
            new BukkitRunnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (tick >= STORM_TICKS || !monster.isValid() || monster.isDead()) {
                        cancel();
                        return;
                    }
                    if (!LivingPlayers.waveRunning()) return;
                    Location at = monster.getLocation();
                    for (int i = 0; i < 16; i++) {
                        double angle = tick * 0.25 + Math.PI * 2 * i / 16;
                        double radius = 1.5 + (i % 4) * 1.2;
                        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * radius, 0.3 + (i % 3) * 0.7,
                                Math.sin(angle) * radius), 1, 0.1, 0.1, 0.1, 0, dust);
                    }
                    at.getWorld().spawnParticle(Particle.FALLING_DUST, at.clone().add(0, 2.5, 0), 6, STORM_RADIUS / 2, 0.5,
                            STORM_RADIUS / 2, Material.SAND.createBlockData());
                    if (tick % 20 == 0) at.getWorld().playSound(at, Sound.BLOCK_SAND_BREAK, 1.0F, 0.6F);
                    for (Player player : LivingPlayers.near(at, STORM_RADIUS)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 1, false, false));
                        // No source: the stings do not push
                        if (tick % 20 == 0) player.damage(STORM_DAMAGE);
                        if (told.add(player.getUniqueId())) Lang.note(player, Lang.t(player, "§eSandsturm!", "§eSandstorm!"));
                    }
                    tick += 5;
                }
            }.runTaskTimer(Main.getINSTANCE(), 0L, 5L);
        });
    }

    // Knochenbrecher: stops, shows the line to its player and dashes 8 blocks along it
    private void charge(Player target) {
        if (!LivingPlayers.alive(target) || !target.getWorld().equals(monster.getWorld())) return;
        Vector way = target.getLocation().toVector().subtract(monster.getLocation().toVector()).setY(0);
        if (way.lengthSquared() < 1) return;
        Vector direction = way.normalize();
        Mob mob = (Mob) monster;
        mob.setAware(false);
        mob.setTarget(null);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        monster.setRotation(yaw, 0);
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0F, 1.3F);
        Location start = monster.getLocation().add(0, 0.2, 0);
        Particle.DustOptions dust = new Particle.DustOptions(DANGER, 1.3F);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= WARN || !monster.isValid() || monster.isDead()) {
                    cancel();
                    return;
                }
                for (double d = 1; d <= CHARGE_LENGTH; d += 0.5) {
                    monster.getWorld().spawnParticle(Particle.DUST, start.clone().add(direction.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
                }
                tick += 4;
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 4L);
        Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (!monster.isValid() || monster.isDead()) return;
            if (!LivingPlayers.waveRunning()) {
                if (!PauseManager.isPaused()) mob.setAware(true);
                return;
            }
            dash(mob, direction, yaw);
        }, WARN);
    }

    private void dash(Mob mob, Vector direction, float yaw) {
        Set<UUID> hit = new HashSet<>();
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_HOGLIN_ATTACK, 1.0F, 0.6F);
        new BukkitRunnable() {
            int tick = 0;
            Location last = monster.getLocation();

            @Override
            public void run() {
                tick++;
                Location at = monster.getLocation();
                // Into a wall it stops early
                boolean stuck = tick > 2 && at.toVector().setY(0).distanceSquared(last.toVector().setY(0)) < 0.01;
                if (tick > CHARGE_TICKS || stuck || !monster.isValid() || monster.isDead()) {
                    cancel();
                    if (monster.isValid() && !PauseManager.isPaused()) mob.setAware(true);
                    return;
                }
                last = at;
                monster.setRotation(yaw, 0);
                monster.setVelocity(direction.clone().multiply(CHARGE_SPEED).setY(Math.min(0, monster.getVelocity().getY())));
                dirt(at, 4);
                if (tick % 2 == 0) monster.getWorld().playSound(at, Sound.ENTITY_RAVAGER_STEP, 0.8F, 1.0F);
                for (Player player : LivingPlayers.near(at, CHARGE_HIT)) {
                    if (!hit.add(player.getUniqueId())) continue;
                    player.damage(CHARGE_DAMAGE, monster);
                    Vector side = player.getLocation().toVector().subtract(at.toVector()).setY(0);
                    Vector push = direction.clone().multiply(0.7);
                    if (side.lengthSquared() > 0.01) push.add(side.normalize().multiply(0.5));
                    player.setVelocity(push.setY(0.45));
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 1L);
    }

    // Mumienkönig: bandages reach for the farthest player close by, hold him 2 seconds and pull him a bit closer
    private void bandages() {
        Player far = null;
        double best = PULL_MIN;
        for (Player player : LivingPlayers.near(monster.getLocation(), PULL_RANGE)) {
            double distance = player.getLocation().distance(monster.getLocation());
            if (distance > best) {
                best = distance;
                far = player;
            }
        }
        if (far == null) {
            // Nobody in reach: try again soon
            nextSkill = 4;
            return;
        }
        Player target = far;
        monster.getWorld().playSound(monster.getLocation(), Sound.ENTITY_HUSK_AMBIENT, 1.2F, 0.6F);
        target.playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0F, 0.6F);
        Lang.note(target, Lang.t(target, "§eBandagen greifen nach dir!", "§eBandages reach for you!"));
        bandage(target, WARN, true);
        later(WARN, () -> {
            if (!LivingPlayers.alive(target) || !target.getWorld().equals(monster.getWorld())
                    || target.getLocation().distance(monster.getLocation()) > PULL_RANGE + 4) return;
            Vector way = monster.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
            if (way.lengthSquared() > 0.01) target.setVelocity(way.normalize().multiply(Math.min(1.2, way.length() * 0.12)).setY(0.3));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ROOT_TICKS, 255, false, false, true));
            AttributeInstance jump = target.getAttribute(Attribute.JUMP_STRENGTH);
            if (jump != null) {
                jump.removeModifier(ROOT);
                jump.addTransientModifier(new AttributeModifier(ROOT, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
            target.playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0F, 0.5F);
            bandage(target, ROOT_TICKS, false);
            // Always free again, also when the boss dies meanwhile
            Bukkit.getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
                AttributeInstance again = target.getAttribute(Attribute.JUMP_STRENGTH);
                if (again != null) again.removeModifier(ROOT);
            }, ROOT_TICKS);
        });
    }

    // Bandage strip from the boss to the player, growing while it reaches out
    private void bandage(Player target, int ticks, boolean grow) {
        Particle.DustOptions dust = new Particle.DustOptions(BANDAGE, 1.1F);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick += 2;
                if (tick > ticks || !monster.isValid() || monster.isDead() || !target.isOnline() || !target.getWorld().equals(monster.getWorld())) {
                    cancel();
                    return;
                }
                Location from = monster.getLocation().add(0, 1.2, 0);
                Vector way = target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
                double length = grow ? way.length() * tick / ticks : way.length();
                way.normalize();
                for (double d = 0; d < length; d += 0.4) {
                    monster.getWorld().spawnParticle(Particle.DUST, from.clone().add(way.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 2L);
    }

    // Sandschlund: digs in for 2 seconds and comes up where its player stood, with a shock wave
    private void burrow(Player target) {
        if (!LivingPlayers.alive(target) || !target.getWorld().equals(monster.getWorld())) return;
        burrowed = true;
        Mob mob = (Mob) monster;
        EntityEquipment equipment = monster.getEquipment();
        ItemStack[] armor = equipment != null ? equipment.getArmorContents() : null;
        if (equipment != null) equipment.setArmorContents(new ItemStack[4]);
        // Head and shoulders stay above the sand, so it can always be seen
        if (model() != null) model().bury(BURY_DEPTH);
        monster.setInvulnerable(true);
        mob.setAware(false);
        mob.setTarget(null);
        Location from = monster.getLocation();
        from.getWorld().spawnParticle(Particle.BLOCK, from.clone().add(0, 0.5, 0), 40, 0.5, 0.4, 0.5, Material.SAND.createBlockData());
        from.getWorld().playSound(from, Sound.BLOCK_SAND_BREAK, 1.2F, 0.6F);
        from.getWorld().playSound(from, Sound.ENTITY_WARDEN_DIG, 1.0F, 1.2F);
        new BukkitRunnable() {
            int tick = 0;
            Location exit;

            @Override
            public void run() {
                if (!monster.isValid()) {
                    cancel();
                    return;
                }
                // The last second the circle shows where it comes up
                if (tick == BURROW_TICKS - WARN) {
                    Player aim = LivingPlayers.alive(target) && target.getWorld().equals(monster.getWorld()) ? target : getTarget();
                    exit = aim != null && aim.getWorld().equals(monster.getWorld()) ? ground(aim.getLocation()) : ground(monster.getLocation());
                    if (!OpenGround.isFree(exit, 0, 2)) exit = ground(monster.getLocation());
                    GroundMark.warn(exit, GroundMark.DANGER, EMERGE_RADIUS, WARN);
                }
                // The last second it moves under the sand to the circle, the head stays visible
                if (exit != null) {
                    double k = Math.min(1, (tick - (BURROW_TICKS - WARN) + 5) / (double) WARN);
                    Location at = from.clone().add(exit.clone().subtract(from).toVector().multiply(k));
                    at.setYaw(monster.getLocation().getYaw());
                    monster.teleport(at, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                }
                Location dust = monster.getLocation();
                dust.getWorld().spawnParticle(Particle.BLOCK, dust.clone().add(0, 0.2, 0), 8, 0.6, 0.1, 0.6, Material.SAND.createBlockData());
                if (tick % 10 == 0) dust.getWorld().playSound(dust, Sound.BLOCK_GRAVEL_BREAK, 0.8F, 0.6F);
                tick += 5;
                if (tick <= BURROW_TICKS) return;
                cancel();
                emerge(mob, exit != null ? exit : monster.getLocation(), equipment, armor);
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 5L);
    }

    // Always visible and vulnerable again, the shock wave only while the wave runs
    private void emerge(Mob mob, Location exit, EntityEquipment equipment, ItemStack[] armor) {
        burrowed = false;
        Location to = exit.clone();
        to.setYaw(monster.getLocation().getYaw());
        monster.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        if (model() != null) model().bury(0);
        if (equipment != null && armor != null) equipment.setArmorContents(armor);
        if (!PauseManager.isPaused()) {
            monster.setInvulnerable(false);
            mob.setAware(true);
        }
        if (!LivingPlayers.waveRunning()) return;
        World world = to.getWorld();
        world.spawnParticle(Particle.BLOCK, to.clone().add(0, 0.5, 0), 60, 1.2, 0.5, 1.2, Material.SAND.createBlockData());
        world.playSound(to, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 0.8F);
        world.playSound(to, Sound.BLOCK_SAND_BREAK, 1.2F, 0.5F);
        GroundMark.shockwave(to, 2);
        for (Player player : LivingPlayers.near(to, EMERGE_RADIUS)) {
            double distance = player.getLocation().distance(to);
            player.damage(EMERGE_DAMAGE * (1 - 0.5 * distance / EMERGE_RADIUS), monster);
            Vector away = player.getLocation().toVector().subtract(to.toVector()).setY(0);
            if (away.lengthSquared() > 0.01) away.normalize().multiply(0.8);
            player.setVelocity(away.setY(0.6));
        }
    }

    @Override
    public void onDeath() {
        // Model falls away from the killer, with the boss voice
        super.onDeath();
        if (bar != null) bar.removeAll();
        Player killer = monster.getKiller();
        if (killer == null) return;

        var injector = Main.getINSTANCE().getInjector();
        GamePlayer gamePlayer = injector.getInstance(UserManager.class).getGamePlayer(killer.getUniqueId());
        if (gamePlayer == null) return;

        // Grenade if there is room for one, otherwise coins
        boolean grenade = ThreadLocalRandom.current().nextBoolean() && injector.getInstance(GrenadeManager.class).give(killer);
        if (!grenade) gamePlayer.addCoins(LOOT_COINS);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Lang.note(player, Lang.f(player, "%s§a%s §7hat §c%s §7besiegt und bekommt %s§7!", "%s§a%s §7defeated §c%s §7and gets %s§7!",
                    Main.getINSTANCE().getMonstersPrefix(), killer.getName(), Lang.t(player, title, titleEn),
                    grenade ? Lang.t(player, "§aeine Granate", "§aa grenade") : "§6" + LOOT_COINS + " Coins"));
        }
        Bukkit.getConsoleSender().sendMessage(String.format("%s§a%s §7hat §c%s §7besiegt und bekommt %s§7!", Main.getINSTANCE().getMonstersPrefix(),
                killer.getName(), title, grenade ? "§aeine Granate" : "§6" + LOOT_COINS + " Coins"));
        killer.playSound(killer.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.3F);
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.2F);
        monster.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, monster.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.15);
    }
}
