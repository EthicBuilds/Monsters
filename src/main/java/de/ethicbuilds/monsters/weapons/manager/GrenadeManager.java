package de.ethicbuilds.monsters.weapons.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GrenadeManager {
    public static final int MAX_GRENADES = 4;

    private static final int FUSE_TICKS = 40;
    private static final double RADIUS = 5.0;
    private static final double DAMAGE = 30.0;
    private static final java.util.Set<Integer> TICKS = java.util.Set.of(8, 20, 28, 34, 37, 39);

    @Inject private Main plugin;

    /** One more grenade in the grenade slot, false when the player already carries the maximum */
    public boolean give(Player player) {
        ItemStack current = player.getInventory().getItem(GameItems.GRENADE_SLOT);
        int amount = GameItems.isGrenade(current) ? current.getAmount() : 0;
        if (amount >= MAX_GRENADES) return false;

        player.getInventory().setItem(GameItems.GRENADE_SLOT, GameItems.grenade(amount + 1));
        return true;
    }

    public void throwGrenade(Player player) {
        ItemStack current = player.getInventory().getItem(GameItems.GRENADE_SLOT);
        if (!GameItems.isGrenade(current)) return;

        current.setAmount(current.getAmount() - 1);
        player.getInventory().setItem(GameItems.GRENADE_SLOT, current.getAmount() > 0 ? current : null);

        Location start = player.getEyeLocation();
        Item grenade = player.getWorld().dropItem(start, GameItems.grenade(1), item -> {
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setCanMobPickup(false);
            item.setUnlimitedLifetime(true);
            item.setPersistent(false);
        });
        grenade.setVelocity(start.getDirection().multiply(0.9).add(new Vector(0, 0.2, 0)));
        player.getWorld().playSound(start, Sound.ENTITY_WITCH_THROW, 1.0F, 1.0F);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!grenade.isValid()) {
                    cancel();
                    return;
                }
                ticks++;
                Location at = grenade.getLocation().add(0, 0.2, 0);
                grenade.getWorld().spawnParticle(Particle.SMOKE, at, 1, 0, 0, 0, 0);
                grenade.getWorld().spawnParticle(Particle.SMALL_FLAME, at, 1, 0, 0, 0, 0);
                // Ticking gets faster and higher, with a red blink
                if (TICKS.contains(ticks)) {
                    grenade.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.0F + ticks / (float) FUSE_TICKS);
                    grenade.getWorld().spawnParticle(Particle.DUST, at, 4, 0.05, 0.05, 0.05, new Particle.DustOptions(Color.RED, 1.0F));
                }
                if (ticks >= FUSE_TICKS) {
                    explode(player, grenade.getLocation());
                    grenade.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void explode(Player thrower, Location location) {
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.6F, 1.1F);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.0F, 1.0F);
        world.spawnParticle(Particle.EXPLOSION, location, 3, 0.8, 0.4, 0.8, 0);
        world.spawnParticle(Particle.FLAME, location, 25, 1.5, 0.5, 1.5, 0.05);
        world.spawnParticle(Particle.LARGE_SMOKE, location, 15, 1.2, 0.6, 1.2, 0.02);
        world.spawnParticle(Particle.LAVA, location, 6, 0.8, 0.3, 0.8, 0);

        for (Monster monster : world.getNearbyEntitiesByType(Monster.class, location, RADIUS)) {
            double distance = monster.getLocation().distance(location);
            if (distance > RADIUS) continue;
            monster.damage(DAMAGE * (1 - distance / RADIUS) + 5, thrower);
        }
    }
}
