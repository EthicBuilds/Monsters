package de.ethicbuilds.monsters.gameplay;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.monster.MonsterGuard;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/***
 * "+ Coins" above the hit monster, only for the player who earned them. Hits in quick succession add up in one
 * popup as a combo instead of stacking many texts, and it fades out quickly.
 */
public final class CoinPopups {
    private static final long COMBO_MILLIS = 1500;
    private static final int LIFETIME_TICKS = 30;
    private static final double RISE_PER_TICK = 0.03;

    private static final class Popup {
        private ArmorStand stand;
        private int coins;
        private int hits;
        private int age;
        private long last;
    }

    private static final Map<UUID, Popup> active = new HashMap<>();

    private CoinPopups() {}

    public static void show(Player viewer, Location monster, int coins, boolean headshot) {
        Main plugin = Main.getINSTANCE();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            long now = System.currentTimeMillis();
            Location at = monster.clone().add(0, 2.1, 0);
            Popup popup = active.get(viewer.getUniqueId());
            if (popup != null && popup.stand.isValid() && now - popup.last < COMBO_MILLIS) {
                popup.coins += coins;
                popup.hits++;
                popup.age = 0;
                popup.last = now;
                popup.stand.teleport(at);
                popup.stand.setCustomName(text(viewer, popup, headshot));
                return;
            }

            Popup created = new Popup();
            created.coins = coins;
            created.hits = 1;
            created.last = now;
            created.stand = at.getWorld().spawn(at, ArmorStand.class, stand -> {
                stand.setVisibleByDefault(false);
                stand.setCustomName(text(viewer, created, headshot));
                stand.setCustomNameVisible(true);
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setPersistent(false);
                stand.setCollidable(false);
            });
            MonsterGuard.markHolo(created.stand);
            if (viewer.isOnline()) viewer.showEntity(plugin, created.stand);
            active.put(viewer.getUniqueId(), created);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!created.stand.isValid() || ++created.age >= LIFETIME_TICKS) {
                        created.stand.remove();
                        active.remove(viewer.getUniqueId(), created);
                        cancel();
                        return;
                    }
                    created.stand.teleport(created.stand.getLocation().add(0, RISE_PER_TICK, 0));
                }
            }.runTaskTimer(plugin, 1L, 1L);
        });
    }

    private static String text(Player viewer, Popup popup, boolean headshot) {
        String combo = popup.hits > 1 ? String.format("§6+ %d Coins §e×%d", popup.coins, popup.hits) : String.format("§6+ %d Coins", popup.coins);
        return headshot ? combo + Lang.t(viewer, " §c§lKOPFSCHUSS", " §c§lHEADSHOT") : combo;
    }
}
