package de.ethicbuilds.monsters.powerup;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.DualBar;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.ReviveManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/***
 * Powerups on the ground: medkits spawn now and then on spots where players have been, monsters drop
 * medkits, speed, instakill and ammo with luck. Speed and instakill count for all players for 10 seconds,
 * picking up the same one again adds 10 seconds to the running bar. Ammo fills the weapons of everyone at once.
 */
public class PowerupManager {
    private static final int MAX_MEDKITS = 2;
    private static final String MEDKIT_COLOR = "#3CDC6E";
    private static final int MIN_DELAY = 60 * 20;
    private static final int MAX_DELAY = 90 * 20;
    private static final int LIFETIME = 60 * 20;
    private static final int MAX_SPOTS = 60;
    private static final double MIN_PLAYER_DISTANCE = 8.0;
    private static final double PICKUP_DISTANCE = 1.3;
    private static final int MAX_DROPPED = 3;
    private static final int EFFECT_TICKS = 10 * 20;
    private static final int MAX_AMMO_PER_WAVE = 1;
    // Emergency crate once per round when all living players together are below a quarter of their health (design session)
    private static final double EMERGENCY_HEALTH = 0.25, FREE_DISTANCE = 3;
    private static final String EMERGENCY_COLOR = "#FF4848";
    private static final int EMERGENCY_FALL = 12;

    public enum Type {
        MEDKIT(0.04, 0x3CC860, () -> GameItems.medkit(null)),
        SPEED(0.02, 0x4FB3FF, () -> model("powerup/tempo")),
        INSTAKILL(0.015, 0xFF3B3B, () -> model("powerup/instakill")),
        AMMO(0.01, 0xF2C14E, () -> model("powerup/munition")),
        // Never dropped by chance, see checkEmergency
        EMERGENCY(0, 0xFF4848, () -> model("powerup/munition"));

        private final double dropChance;
        private final int color;
        private final Supplier<ItemStack> item;

        Type(double dropChance, int color, Supplier<ItemStack> item) {
            this.dropChance = dropChance;
            this.color = color;
            this.item = item;
        }

        // Models from the Monsters pack (design session)
        private static ItemStack model(String id) {
            ItemStack item = new ItemStack(Material.PAPER);
            item.editMeta(meta -> meta.setItemModel(new NamespacedKey("monsters", id)));
            return item;
        }
    }

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private GameManager gameManager;
    @Inject private de.ethicbuilds.monsters.gameplay.manager.WaveManager waveManager;

    private final LinkedList<Location> spots = new LinkedList<>();
    private final Map<ItemDisplay, Type> powerups = new HashMap<>();
    private final Map<Type, Countdown> countdowns = new EnumMap<>(Type.class);
    private int nextSpawn = MIN_DELAY;
    private long instakillUntil;
    private int ammoWave = -1, ammoDrops;
    private boolean emergencyUsed;

    public void start() {
        emergencyUsed = false;
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
                if (ticks % 20 == 0) checkEmergency();
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
        // Gold rush: twice the chance
        double roll = ThreadLocalRandom.current().nextDouble() / de.ethicbuilds.monsters.event.WaveEvents.powerupFactor();
        for (Type type : Type.values()) {
            if ((roll -= type.dropChance) < 0) {
                if (type == Type.AMMO && !ammoAllowed()) return;
                spawn(type, location.toBlockLocation().add(0.5, 0.8, 0.5));
                return;
            }
        }
    }

    /** A crate opened with a good crowbar: medkit, speed or insta-kill right there */
    public void dropAt(Location location) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE) return;
        Type[] types = {Type.MEDKIT, Type.SPEED, Type.INSTAKILL};
        spawn(types[ThreadLocalRandom.current().nextInt(types.length)], location.toBlockLocation().add(0.5, 0.8, 0.5));
    }

    // Ammo for everyone only once per wave
    private boolean ammoAllowed() {
        int wave = waveManager.getCurrentWave();
        if (wave != ammoWave) {
            ammoWave = wave;
            ammoDrops = 0;
        }
        if (ammoDrops >= MAX_AMMO_PER_WAVE) return false;
        ammoDrops++;
        return true;
    }

    private void checkEmergency() {
        if (emergencyUsed) return;
        double health = 0, max = 0;
        List<Player> living = new java.util.ArrayList<>();
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline() || userManager.isPlayerDead(player.getUniqueId())) continue;
            living.add(player);
            health += player.getHealth();
            max += player.getAttribute(Attribute.MAX_HEALTH).getValue();
        }
        if (living.isEmpty() || health >= max * EMERGENCY_HEALTH) return;
        emergencyUsed = true;
        dropEmergency(emergencySpot(living));
    }

    // Remembered spot nearest to the middle of the players, a few blocks away from everyone
    private Location emergencySpot(List<Player> living) {
        Location first = living.getFirst().getLocation();
        org.bukkit.util.Vector center = new org.bukkit.util.Vector();
        int count = 0;
        for (Player player : living) {
            if (!player.getWorld().equals(first.getWorld())) continue;
            center.add(player.getLocation().toVector());
            count++;
        }
        center.multiply(1.0 / count);
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Location spot : spots) {
            if (!spot.getWorld().equals(first.getWorld()) || !isFree(spot)) continue;
            double distance = spot.toVector().distanceSquared(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = spot;
            }
        }
        return best != null ? best.clone().add(0, 0.8, 0) : first.toBlockLocation().add(0.5, 0.8, 0.5);
    }

    private boolean isFree(Location spot) {
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (player.getWorld().equals(spot.getWorld()) && player.getLocation().distanceSquared(spot) < FREE_DISTANCE * FREE_DISTANCE) return false;
        }
        return true;
    }

    // Falls from up to 12 blocks (less under a roof), then lies there like every powerup
    private void dropEmergency(Location spot) {
        int height = 0;
        while (height < EMERGENCY_FALL && spot.clone().add(0, height + 1, 0).getBlock().isPassable()) height++;
        Location top = spot.clone().add(0, height, 0);
        ItemDisplay falling = spot.getWorld().spawn(top, ItemDisplay.class, display -> {
            display.setItemStack(Type.EMERGENCY.item.get());
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(Type.EMERGENCY.color));
            display.setPersistent(false);
            display.setTeleportDuration(1);
        });
        String ping = Overlay.glyph(Overlay.PING, EMERGENCY_COLOR) + Overlay.space(3);
        Overlay.mediumAll(EMERGENCY_COLOR, ping + Overlay.line(Overlay.CRATE, EMERGENCY_COLOR, "NOTFALL-KISTE", "Medkit + Munition"),
                ping + Overlay.line(Overlay.CRATE, EMERGENCY_COLOR, "EMERGENCY CRATE", "medkit + ammo"));
        for (Player player : Bukkit.getOnlinePlayers()) player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.4F, 1.6F);
        new BukkitRunnable() {
            final Location at = top.clone();

            @Override
            public void run() {
                if (!falling.isValid() || gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    falling.remove();
                    cancel();
                    return;
                }
                at.subtract(0, 0.6, 0);
                if (at.getY() <= spot.getY()) {
                    falling.remove();
                    spot.getWorld().playSound(spot, Sound.BLOCK_ANVIL_LAND, 0.6F, 0.8F);
                    spot.getWorld().spawnParticle(Particle.CLOUD, spot, 12, 0.4, 0.1, 0.4, 0.02);
                    spawn(Type.EMERGENCY, spot);
                    cancel();
                    return;
                }
                falling.teleport(at);
                at.getWorld().spawnParticle(Particle.SMOKE, at, 2, 0.1, 0.1, 0.1, 0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
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
                // At full health a medkit goes into the inventory (one at most), otherwise it stays for someone who is hurt
                if (entry.getValue() == Type.MEDKIT && player.getHealth() >= player.getAttribute(Attribute.MAX_HEALTH).getValue()) {
                    if (player.getInventory().getItem(ReviveManager.MEDKIT_SLOT) != null) continue;
                    ItemStack stored = GameItems.medkit(player);
                    stored.editMeta(meta -> meta.setLore(Lang.english(player)
                            ? java.util.List.of("§7Gets you back up once", "§7per round when you are down")
                            : java.util.List.of("§7Holt dich einmal pro Runde", "§7wieder hoch, wenn du am Boden liegst")));
                    player.getInventory().setItem(ReviveManager.MEDKIT_SLOT, stored);
                    Overlay.small(player, Overlay.line(Overlay.MEDKIT, MEDKIT_COLOR, "Medkit eingesteckt", "holt dich hoch, wenn du am Boden liegst"),
                            Overlay.line(Overlay.MEDKIT, MEDKIT_COLOR, "Medkit stored", "gets you back up when you are down"));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.2F);
                    powerup.remove();
                    return true;
                }

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
                double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                String healed = "+" + (int) Math.ceil(max - player.getHealth());
                player.setHealth(max);
                Overlay.small(player, Overlay.icon(Overlay.MEDKIT, MEDKIT_COLOR, "<color:" + MEDKIT_COLOR + "><b>" + healed + "</b></color> <color:" + Overlay.GREY + ">Leben</color>"),
                        Overlay.icon(Overlay.MEDKIT, MEDKIT_COLOR, "<color:" + MEDKIT_COLOR + "><b>" + healed + "</b></color> <color:" + Overlay.GREY + ">health</color>"));
                player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
                player.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0F, 1.5F);
                location.getWorld().spawnParticle(Particle.HEART, location, 6, 0.4, 0.3, 0.4, 0);
            }
            case SPEED -> {
                int left = announce(player, type, "§x§4§F§B§3§F§F§lTEMPO!", "§x§4§F§B§3§F§F§lSPEED!", "§7Alle sind 10 Sekunden schneller",
                        "§7Everyone is faster for 10 seconds", BarColor.BLUE, Sound.ENTITY_BREEZE_WIND_BURST, 1.3F, null);
                for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                    Player other = gamePlayer.getPlayer();
                    if (other.isOnline() && !userManager.isPlayerDead(other.getUniqueId())) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, left, 1, false, false, true));
                    }
                }
            }
            case INSTAKILL -> {
                int left = announce(player, type, "§x§F§F§3§B§3§B§lINSTA-KILL!", "§x§F§F§3§B§3§B§lINSTA-KILL!", "§7Jeder Treffer tötet, 10 Sekunden lang",
                        "§7Every hit kills for 10 seconds", BarColor.RED, Sound.ENTITY_EVOKER_CAST_SPELL, 1.4F, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE);
                instakillUntil = System.currentTimeMillis() + left * 50L;
            }
            case EMERGENCY -> {
                // Ammo for everyone, the medkit for the finder: into the pocket if free, else healed
                for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                    Player other = gamePlayer.getPlayer();
                    if (other.isOnline() && !userManager.isPlayerDead(other.getUniqueId())) gamePlayer.refillWeapons();
                }
                if (player.getInventory().getItem(ReviveManager.MEDKIT_SLOT) == null) {
                    player.getInventory().setItem(ReviveManager.MEDKIT_SLOT, GameItems.medkit(player));
                } else {
                    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                }
                Overlay.mediumAll(EMERGENCY_COLOR, Overlay.line(Overlay.CRATE, EMERGENCY_COLOR, "NOTFALL-KISTE", "geöffnet von " + player.getName()),
                        Overlay.line(Overlay.CRATE, EMERGENCY_COLOR, "EMERGENCY CRATE", "opened by " + player.getName()));
                location.getWorld().playSound(location, Sound.BLOCK_CHEST_OPEN, 1.0F, 1.1F);
                location.getWorld().playSound(location, Sound.ITEM_ARMOR_EQUIP_IRON, 0.8F, 1.2F);
            }
            case AMMO -> {
                for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                    Player other = gamePlayer.getPlayer();
                    if (!other.isOnline()) continue;
                    if (!userManager.isPlayerDead(other.getUniqueId())) gamePlayer.refillWeapons();
                    other.sendTitle(Lang.t(other, "§x§F§2§C§1§4§E§lMUNITION!", "§x§F§2§C§1§4§E§lAMMO!"),
                            Lang.t(other, "§7Alle Waffen aufgefüllt §8· §7von ", "§7All weapons refilled §8· §7by ") + player.getName(), 5, 30, 10);
                    other.playSound(other.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8F, 1.2F);
                    other.playSound(other.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.5F);
                }
            }
        }
    }

    private static final class Countdown {
        private final DualBar bar;
        private int left = EFFECT_TICKS, total = EFFECT_TICKS;

        private Countdown(DualBar bar) {
            this.bar = bar;
        }
    }

    // Title for everyone and one boss bar per powerup, picking it up again adds 10 seconds. Returns the ticks left
    private int announce(Player finder, Type type, String title, String titleEn, String subtitle, String subtitleEn,
                         BarColor color, Sound sound, float pitch, Sound extra) {
        Countdown running = countdowns.get(type);
        Countdown countdown = running != null ? running : new Countdown(new DualBar("", "", color, BarStyle.SEGMENTED_10));
        if (running != null) {
            countdown.left += EFFECT_TICKS;
            countdown.total = countdown.left;
        }
        countdown.bar.setTitle(title + " §7von " + finder.getName(), titleEn + " §7by " + finder.getName());
        countdown.bar.setProgress(1);
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline()) continue;
            String sub = running != null
                    ? Lang.f(player, "§7+10 Sekunden §8· §7noch %d Sekunden", "§7+10 seconds §8· §7%d seconds left", countdown.left / 20)
                    : Lang.t(player, subtitle, subtitleEn);
            player.sendTitle(Lang.t(player, title, titleEn), sub, 5, 30, 10);
            player.playSound(player.getLocation(), sound, 0.6F, pitch);
            if (extra != null) player.playSound(player.getLocation(), extra, 0.6F, 0.7F);
            countdown.bar.addPlayer(player);
        }
        if (running != null) return countdown.left;

        countdowns.put(type, countdown);
        DualBar bar = countdown.bar;
        new BukkitRunnable() {
            @Override
            public void run() {
                countdown.left -= 5;
                int left = countdown.left;
                if (left <= 0 || gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    // Running out is heard without looking at the bar: a tick in the last 3 seconds, then a soft off
                    if (left <= 0) bar.getPlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4F, 1.4F));
                    bar.removeAll();
                    countdowns.remove(type);
                    cancel();
                    return;
                }
                if (left <= 60 && left % 20 == 0) bar.getPlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4F, 1.2F));
                bar.setProgress(left / (double) countdown.total);
            }
        }.runTaskTimer(plugin, 5L, 5L);
        return countdown.left;
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
