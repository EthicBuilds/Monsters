package de.ethicbuilds.monsters.powerup;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Medkits that spawn now and then on spots where players have been
 */
public class PowerupManager {
    private static final int MAX_MEDKITS = 2;
    private static final int MIN_DELAY = 60 * 20;
    private static final int MAX_DELAY = 90 * 20;
    private static final int LIFETIME = 60 * 20;
    private static final int MAX_SPOTS = 60;
    private static final double MIN_PLAYER_DISTANCE = 8.0;
    private static final double PICKUP_DISTANCE = 1.3;

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private GameManager gameManager;

    private final LinkedList<Location> spots = new LinkedList<>();
    private final List<ItemDisplay> medkits = new ArrayList<>();
    private int nextSpawn = MIN_DELAY;

    public void start() {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    medkits.forEach(ItemDisplay::remove);
                    medkits.clear();
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

    private void rememberSpots() {
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (userManager.isPlayerDead(player.getUniqueId()) || !player.isOnGround() || player.isInWater()) continue;

            spots.add(player.getLocation().toBlockLocation().add(0.5, 0, 0.5));
            if (spots.size() > MAX_SPOTS) spots.removeFirst();
        }
    }

    private void spawnMedkit() {
        if (medkits.size() >= MAX_MEDKITS || spots.isEmpty()) return;

        List<Location> away = spots.stream().filter(this::isAwayFromPlayers).toList();
        List<Location> candidates = away.isEmpty() ? spots : away;
        Location spot = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).clone().add(0, 0.8, 0);

        ItemDisplay medkit = spot.getWorld().spawn(spot, ItemDisplay.class, display -> {
            display.setItemStack(GameItems.medkit());
            display.setBillboard(Display.Billboard.FIXED);
            display.setGlowing(true);
            display.setPersistent(false);
            display.setTeleportDuration(2);
        });
        medkits.add(medkit);
        spot.getWorld().playSound(spot, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5F, 1.2F);
        spot.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spot, 10, 0.4, 0.4, 0.4, 0);

        new BukkitRunnable() {
            int age = 0;

            @Override
            public void run() {
                if (!medkit.isValid()) {
                    cancel();
                    return;
                }
                age += 2;
                float angle = (float) Math.toRadians(age * 3 % 360);
                float bob = (float) Math.sin(age / 10.0) * 0.1F;
                medkit.setTransformation(new Transformation(new Vector3f(0, bob, 0), new AxisAngle4f(angle, 0, 1, 0),
                        new Vector3f(0.8F, 0.8F, 0.8F), new AxisAngle4f()));
                medkit.setInterpolationDuration(2);
                medkit.setInterpolationDelay(0);

                if (age >= LIFETIME) {
                    medkit.remove();
                    medkits.remove(medkit);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void checkPickups() {
        medkits.removeIf(medkit -> {
            if (!medkit.isValid()) return true;
            for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                Player player = gamePlayer.getPlayer();
                if (userManager.isPlayerDead(player.getUniqueId()) || !player.getWorld().equals(medkit.getWorld())) continue;
                if (player.getLocation().add(0, 0.8, 0).distance(medkit.getLocation()) > PICKUP_DISTANCE) continue;

                pickUp(player, medkit.getLocation());
                medkit.remove();
                return true;
            }
            return false;
        });
    }

    private void pickUp(Player player, Location location) {
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.sendActionBar("§aErste Hilfe! §7Du bist wieder voll geheilt");
        player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.6F);
        location.getWorld().spawnParticle(Particle.HEART, location, 6, 0.4, 0.3, 0.4, 0);
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
