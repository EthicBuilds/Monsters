package de.ethicbuilds.monsters.weapons;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/***
 * The rocket launcher only comes as boss loot (user 31.10.): who finishes a mini boss or the boss weaver gets it now
 * and then. Without a free weapon slot it stays on the ground, sneaking over it swaps it with the weapon in hand.
 */
public class RocketLoot implements Listener {
    private static final double CHANCE = 0.25;
    private static final long HINT_MILLIS = 3000;
    private final Map<UUID, Long> lastHint = new HashMap<>();
    private static final NamespacedKey KEY = new NamespacedKey("monsters", "raketenwerfer_beute");

    @Inject private Main plugin;
    @Inject private UserManager userManager;

    /** A boss died, with luck the killer gets the launcher */
    public void roll(Player killer, Location at) {
        if (killer == null || ThreadLocalRandom.current().nextDouble() >= CHANCE) return;
        GamePlayer gamePlayer = userManager.getGamePlayer(killer.getUniqueId());
        if (gamePlayer == null) return;
        Weapon owned = gamePlayer.getWeapons().get(Material.IRON_NUGGET);
        if (owned != null) {
            owned.refill();
            Lang.note(killer, Lang.t(killer, "§6Raketen §7aufgefüllt!", "§6Rockets §7refilled!"));
            Sounds.buy(killer);
            return;
        }
        Lang.broadcast("%s§6%s §7hat einen §6Raketenwerfer §7erbeutet!", "%s§6%s §7looted a §6Rocket Launcher§7!",
                plugin.getMonstersPrefix(), killer.getName());
        if (!give(gamePlayer, false)) drop(at);
    }

    /** Sure loot, e.g. out of the safe */
    public void grant(GamePlayer gamePlayer, Location at) {
        Weapon owned = gamePlayer.getWeapons().get(Material.IRON_NUGGET);
        if (owned != null) {
            owned.refill();
            return;
        }
        if (!give(gamePlayer, false)) drop(at);
    }

    private void drop(Location at) {
        RocketLauncher launcher = new RocketLauncher();
        ItemStack item = launcher.getItem();
        item.editMeta(meta -> meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1));
        Item dropped = at.getWorld().dropItem(at.clone().add(0, 0.5, 0), item, entity -> {
            entity.setUnlimitedLifetime(true);
            entity.setCanMobPickup(false);
            entity.setGlowing(true);
            entity.setPersistent(false);
        });
        dropped.getWorld().spawnParticle(Particle.FIREWORK, dropped.getLocation(), 20, 0.3, 0.3, 0.3, 0.05);
    }

    // Into a free weapon slot, or (only when picking it up) instead of the weapon in hand
    private boolean give(GamePlayer gamePlayer, boolean swap) {
        Player player = gamePlayer.getPlayer();
        RocketLauncher launcher = new RocketLauncher();
        launcher.localize(player);
        for (int slot : gamePlayer.getWeaponSlots()) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current != null && current.getType() == Material.LIGHT_GRAY_DYE) {
                player.getInventory().setItem(slot, launcher.getItem());
                gamePlayer.addWeapon(launcher);
                Sounds.buy(player);
                return true;
            }
        }
        if (!swap) {
            Lang.note(player, Lang.t(player, "§7Kein Waffen-Slot frei · der §6Raketenwerfer §7liegt am Boden",
                    "§7No free weapon slot · the §6Rocket Launcher §7is on the ground"));
            return false;
        }
        Weapon held = gamePlayer.getWeapon(player.getInventory().getItemInMainHand());
        if (held == null || !player.isSneaking()) {
            long now = System.currentTimeMillis();
            if (now - lastHint.getOrDefault(player.getUniqueId(), 0L) < HINT_MILLIS) return false;
            lastHint.put(player.getUniqueId(), now);
            Lang.note(player, Lang.t(player, "§7Waffe in die Hand und §6schleichen§7, um sie gegen den Raketenwerfer zu tauschen",
                    "§7Hold a weapon and §6sneak §7to swap it for the rocket launcher"));
            return false;
        }
        gamePlayer.removeWeapon(held);
        player.getInventory().setItemInMainHand(launcher.getItem());
        gamePlayer.addWeapon(launcher);
        Sounds.buy(player);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(KEY)) return;
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player)) return;
        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(player.getUniqueId()) || gamePlayer.getWeapons().containsKey(Material.IRON_NUGGET)) return;
        if (give(gamePlayer, true)) event.getItem().remove();
    }
}
