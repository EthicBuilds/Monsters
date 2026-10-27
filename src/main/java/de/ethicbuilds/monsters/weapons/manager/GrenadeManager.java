package de.ethicbuilds.monsters.weapons.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.player.GamePlayer;
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
    public static final int PRICE = 750;
    public static final int MAX_GRENADES = 4;

    private static final int FUSE_TICKS = 50;
    private static final double RADIUS = 5.0;
    private static final double DAMAGE = 30.0;

    @Inject private Main plugin;

    public void buy(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        ItemStack current = player.getInventory().getItem(GameItems.GRENADE_SLOT);
        int amount = GameItems.isGrenade(current) ? current.getAmount() : 0;

        if (amount >= MAX_GRENADES) {
            player.sendMessage(String.format("§cDu kannst höchstens %d Granaten tragen!", MAX_GRENADES));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }
        if (gamePlayer.getCoins() < PRICE) {
            player.sendMessage(String.format("§cDu brauchst noch §6%d Coins §cfür eine Granate", PRICE - gamePlayer.getCoins()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }

        gamePlayer.setCoins(gamePlayer.getCoins() - PRICE);
        player.getInventory().setItem(GameItems.GRENADE_SLOT, GameItems.grenade(amount + 1));
        player.sendMessage(String.format("%s§7Du hast eine §aGranate §7gekauft! §c-%d Coins!", plugin.getMonstersPrefix(), PRICE));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0F, 1.0F);
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
        player.playSound(start, Sound.ENTITY_SNOWBALL_THROW, 1.0F, 0.6F);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!grenade.isValid()) {
                    cancel();
                    return;
                }
                ticks++;
                grenade.getWorld().spawnParticle(Particle.SMOKE, grenade.getLocation().add(0, 0.2, 0), 1, 0, 0, 0, 0);
                if (ticks % 10 == 0) {
                    grenade.getWorld().playSound(grenade.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.5F + ticks / 100F);
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
        world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 1);
        world.spawnParticle(Particle.FLAME, location, 40, 1.5, 0.5, 1.5, 0.05);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 1.0F);

        for (Monster monster : world.getNearbyEntitiesByType(Monster.class, location, RADIUS)) {
            double distance = monster.getLocation().distance(location);
            if (distance > RADIUS) continue;
            monster.damage(DAMAGE * (1 - distance / RADIUS) + 5, thrower);
        }
    }
}
