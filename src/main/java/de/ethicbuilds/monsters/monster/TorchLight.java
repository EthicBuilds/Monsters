package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

/***
 * Light that walks with a torch carrier: only a block change for the players nearby, the map itself stays as it is
 */
public final class TorchLight extends BukkitRunnable {
    private static final int LEVEL = 13;
    private static final double VIEW_DISTANCE = 40;

    private final LivingEntity carrier;
    private final Set<Player> viewers = new HashSet<>();
    private Location lit;

    private TorchLight(LivingEntity carrier) {
        this.carrier = carrier;
    }

    public static void follow(LivingEntity carrier) {
        new TorchLight(carrier).runTaskTimer(Main.getINSTANCE(), 5L, 5L);
    }

    @Override
    public void run() {
        if (!carrier.isValid() || carrier.isDead()) {
            restore();
            cancel();
            return;
        }
        Location at = carrier.getEyeLocation().toBlockLocation();
        if (at.equals(lit)) return;
        restore();
        if (!at.getBlock().getType().isAir()) return;
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(LEVEL);
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
            player.sendBlockChange(at, light);
            viewers.add(player);
        }
        lit = at;
    }

    private void restore() {
        if (lit != null) {
            for (Player player : viewers) if (player.isOnline()) player.sendBlockChange(lit, lit.getBlock().getBlockData());
        }
        viewers.clear();
        lit = null;
    }
}
