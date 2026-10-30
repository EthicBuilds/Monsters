package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Crystal rain of the boss weaver from level 7 (design session): 3 waves of crystals over the area of the team,
 * split into 3x3 fields. Every wave hits other fields, so the players have to move to the free ones.
 */
public final class CrystalRain {
    private static final int WAVES = 3, WAVE_GAP = 16, WARN_TICKS = 30;
    private static final int FIELDS_PER_WAVE = 5, PER_FIELD = 2;
    private static final double RADIUS = 1.5, DAMAGE = 6, MARGIN = 4, MIN_SIZE = 12;
    private static final Particle.DustOptions AMETHYST = new Particle.DustOptions(Color.fromRGB(0xA970FF), 1.4F);

    private CrystalRain() {}

    public static void fall(LivingEntity boss) {
        UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
        List<Player> living = new ArrayList<>();
        for (GamePlayer gamePlayer : users.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (player.isOnline() && !users.isPlayerDead(player.getUniqueId()) && player.getWorld().equals(boss.getWorld())) living.add(player);
        }
        if (living.isEmpty()) return;

        // The area of the team, at least 12 blocks wide
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE, y = 0;
        for (Player player : living) {
            minX = Math.min(minX, player.getLocation().getX());
            maxX = Math.max(maxX, player.getLocation().getX());
            minZ = Math.min(minZ, player.getLocation().getZ());
            maxZ = Math.max(maxZ, player.getLocation().getZ());
            y += player.getLocation().getY();
        }
        y /= living.size();
        double width = Math.max(MIN_SIZE, maxX - minX + 2 * MARGIN), depth = Math.max(MIN_SIZE, maxZ - minZ + 2 * MARGIN);
        double startX = (minX + maxX - width) / 2, startZ = (minZ + maxZ - depth) / 2;
        World world = boss.getWorld();
        world.playSound(boss.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int wave = 0; wave < WAVES; wave++) {
            List<Integer> fields = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
            Collections.shuffle(fields);
            for (int field : fields.subList(0, FIELDS_PER_WAVE)) {
                for (int i = 0; i < PER_FIELD; i++) {
                    double x = startX + (field % 3 + random.nextDouble()) * width / 3;
                    double z = startZ + (field / 3 + random.nextDouble()) * depth / 3;
                    Location spot = GroundMark.ground(new Location(world, x, y + 1, z));
                    if (spot == null) continue;
                    Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
                        GroundMark.still(spot, GroundMark.CRYSTAL, RADIUS, WARN_TICKS);
                        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> impact(boss, spot), WARN_TICKS);
                    }, (long) wave * WAVE_GAP);
                }
            }
        }
    }

    private static void impact(LivingEntity boss, Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), 6, 0.4, 0.2, 0.4, Material.AMETHYST_BLOCK.createBlockData());
        world.spawnParticle(Particle.DUST, at.clone().add(0, 0.4, 0), 10, 0.6, 0.3, 0.6, AMETHYST);
        world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.6F, 1.0F);
        UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
        for (Player player : world.getPlayers()) {
            if (!users.isGamePlayer(player.getUniqueId()) || users.isPlayerDead(player.getUniqueId())) continue;
            if (player.getLocation().distanceSquared(at) <= RADIUS * RADIUS) player.damage(DAMAGE, boss.isValid() ? boss : null);
        }
    }
}
