package de.ethicbuilds.monsters.powerup;

import de.ethicbuilds.monsters.weapons.AmmoHud;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/***
 * Powerups on the ground: medkits spawn now and then on spots where players have been, monsters drop
 * medkits, speed and instakill with luck. Speed and instakill count for all players for 10 seconds.
 */
public class PowerupManager {
    private static final int MAX_MEDKITS = 2;
    private static final int MIN_DELAY = 60 * 20;
    private static final int MAX_DELAY = 90 * 20;
    private static final int LIFETIME = 60 * 20;
    private static final int MAX_SPOTS = 60;
    private static final double MIN_PLAYER_DISTANCE = 8.0;
    private static final double PICKUP_DISTANCE = 1.3;
    private static final int MAX_DROPPED = 3;
    private static final int EFFECT_TICKS = 10 * 20;

    public enum Type {
        MEDKIT(0.04, 0x3CC860, GameItems::medkit),
        SPEED(0.02, 0x4FB3FF, () -> new ItemStack(Material.SUGAR)),
        INSTAKILL(0.015, 0xE84848, () -> new ItemStack(Material.WITHER_SKELETON_SKULL));

        private final double dropChance;
        private final int color;
        private final Supplier<ItemStack> item;

        Type(double dropChance, int color, Supplier<ItemStack> item) {
            this.dropChance = dropChance;
            this.color = color;
            this.item = item;
        }
    }

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private GameManager gameManager;

    private final LinkedList<Location> spots = new LinkedList<>();
    private final Map<ItemDisplay, Type> powerups = new HashMap<>();
    private int nextSpawn = MIN_DELAY;
    private long instakillUntil;

    public void start() {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    powerups.keySet().forEach(ItemDisplay::remove);
                    powerups.clear();
                    instakillUntil = 0;
                    cancel();
                    return;
                }
                ticks++;

                if (ticks % 100 == 0) rememberSpots();
                if (ticks >= nextSpawn) {
                    ticks = 0;
                    nextSpawn = ThreadLocalRandom.current().nextInt(MIN_DELAY, MAX_DELAY);
                    spawnMedkit();
                }
                checkPickups();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Every hit kills while it lasts */
    public boolean isInstakill() {
        return System.currentTimeMillis() < instakillUntil;
    }

    /** A monster died there, with luck it leaves a powerup */
    public void dropFrom(Location location) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE || powerups.size() >= MAX_DROPPED) return;
        double roll = ThreadLocalRandom.current().nextDouble();
        for (Type type : Type.values()) {
            if ((roll -= type.dropChance) < 0) {
                spawn(type, location.toBlockLocation().add(0.5, 0.8, 0.5));
                return;
            }
        }
    }

    private void rememberSpots() {
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (userManager.isPlayerDead(player.getUniqueId()) || !player.isOnGround() || player.isInWater()) continue;

            spots.add(player.getLocation().toBlockLocation().add(0.5, 0, 0.5));
            if (spots.size() > MAX_SPOTS) spots.removeFirst();
        }
    }

    private void spawnMedkit() {
        long medkits = powerups.values().stream().filter(type -> type == Type.MEDKIT).count();
        if (medkits >= MAX_MEDKITS || spots.isEmpty()) return;

        List<Location> away = spots.stream().filter(this::isAwayFromPlayers).toList();
        List<Location> candidates = away.isEmpty() ? spots : away;
        spawn(Type.MEDKIT, candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).clone().add(0, 0.8, 0));
    }

    private void spawn(Type type, Location spot) {
        Color color = Color.fromRGB(type.color);
        ItemDisplay powerup = spot.getWorld().spawn(spot, ItemDisplay.class, display -> {
            display.setItemStack(type.item.get());
            display.setBillboard(Display.Billboard.FIXED);
            display.setGlowing(true);
            display.setGlowColorOverride(color);
            display.setPersistent(false);
            display.setTeleportDuration(2);
        });
        powerups.put(powerup, type);
        spot.getWorld().playSound(spot, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5F, 1.2F);
        spot.getWorld().playSound(spot, Sound.BLOCK_BEACON_ACTIVATE, 0.6F, 1.6F);
        spot.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spot, 10, 0.4, 0.4, 0.4, 0);

        new BukkitRunnable() {
            int age = 0;

            @Override
            public void run() {
                if (!powerup.isValid()) {
                    cancel();
                    return;
                }
                age += 2;
                // 4 degrees per tick, bobbing 0.12 up and down every 40 ticks
                float angle = (float) Math.toRadians(age * 4 % 360);
                float bob = (float) Math.sin(age * Math.PI / 20.0) * 0.12F;
                if (age % 10 == 0) {
                    Location ring = powerup.getLocation().subtract(0, 0.5, 0);
                    for (int i = 0; i < 12; i++) {
                        double a = Math.PI * 2 * i / 12;
                        ring.getWorld().spawnParticle(Particle.DUST, ring.clone().add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5), 1,
                                new Particle.DustOptions(color, 0.8F));
                    }
                    if (type == Type.MEDKIT) ring.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, ring.clone().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0);
                }
                powerup.setTransformation(new Transformation(new Vector3f(0, bob, 0), new AxisAngle4f(angle, 0, 1, 0),
                        new Vector3f(0.8F, 0.8F, 0.8F), new AxisAngle4f()));
                powerup.setInterpolationDuration(2);
                powerup.setInterpolationDelay(0);

                if (age >= LIFETIME) {
                    powerup.remove();
                    powerups.remove(powerup);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void checkPickups() {
        powerups.entrySet().removeIf(entry -> {
            ItemDisplay powerup = entry.getKey();
            if (!powerup.isValid()) return true;
            for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                Player player = gamePlayer.getPlayer();
                if (userManager.isPlayerDead(player.getUniqueId()) || !player.getWorld().equals(powerup.getWorld())) continue;
                if (player.getLocation().add(0, 0.8, 0).distance(powerup.getLocation()) > PICKUP_DISTANCE) continue;

                pickUp(player, entry.getValue(), powerup.getLocation());
                powerup.remove();
                return true;
            }
            return false;
        });
    }

    private void pickUp(Player player, Type type, Location location) {
        switch (type) {
            case MEDKIT -> {
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                AmmoHud.pause(player);
                player.sendActionBar("§aErste Hilfe! §7Du bist wieder voll geheilt");
                player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
                player.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0F, 1.5F);
                location.getWorld().spawnParticle(Particle.HEART, location, 6, 0.4, 0.3, 0.4, 0);
            }
            case SPEED -> {
                for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                    Player other = gamePlayer.getPlayer();
                    if (other.isOnline() && !userManager.isPlayerDead(other.getUniqueId())) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, EFFECT_TICKS, 1, false, false, true));
                    }
                }
                announce(player, "§b§lTEMPO!", "§7Alle sind 10 Sekunden schneller", BarColor.BLUE, Sound.ENTITY_BREEZE_WIND_BURST);
            }
            case INSTAKILL -> {
                instakillUntil = System.currentTimeMillis() + EFFECT_TICKS * 50L;
                announce(player, "§c§lINSTA-KILL!", "§7Jeder Treffer tötet, 10 Sekunden lang", BarColor.RED, Sound.ENTITY_WITHER_SPAWN);
            }
        }
    }

    // Title for everyone and a boss bar counting down the 10 seconds
    private void announce(Player finder, String title, String subtitle, BarColor color, Sound sound) {
        BossBar bar = Bukkit.createBossBar(title + " §7von " + finder.getName(), color, BarStyle.SOLID);
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline()) continue;
            player.sendTitle(title, subtitle, 5, 30, 10);
            player.playSound(player.getLocation(), sound, 0.6F, 1.4F);
            bar.addPlayer(player);
        }
        new BukkitRunnable() {
            int left = EFFECT_TICKS;

            @Override
            public void run() {
                left -= 5;
                if (left <= 0 || gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                bar.setProgress(left / (double) EFFECT_TICKS);
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private boolean isAwayFromPlayers(Location location) {
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (player.getWorld().equals(location.getWorld()) && player.getLocation().distance(location) < MIN_PLAYER_DISTANCE) {
                return false;
            }
        }
        return true;
    }
}
