package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.DualBar;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

/***
 * Pause wave: after every boss wave the creator of the round gets an item. Click, click again to confirm,
 * then everything stops for 5 minutes right away: the monsters freeze, no new ones come, time to upgrade.
 * At most one item at a time, between two pauses at least 30 minutes.
 */
public class PauseManager implements Listener {
    private static final long PAUSE_MILLIS = 5 * 60_000, COOLDOWN_MILLIS = 30 * 60_000, CONFIRM_MILLIS = 3000;
    private static final int ITEM_SLOT = 8;
    private static final NamespacedKey ITEM_KEY = new NamespacedKey("monsters", "pause");

    private static volatile boolean paused;

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;
    @Inject private MonsterManager monsterManager;

    private long lastPause;
    private long askedAt;

    /** While true the waves, monster AI and boss attacks wait */
    public static boolean isPaused() {
        return paused;
    }

    /** After a boss wave: the creator gets the item, if they do not have one yet */
    public void giveItem() {
        Player creator = creator();
        if (creator == null || has(creator)) return;
        creator.getInventory().setItem(ITEM_SLOT, item(creator));
        creator.sendMessage(Lang.t(creator, plugin.getMonstersPrefix() + "§eDu kannst jetzt eine §65-Minuten-Pause §eeinlösen (Slot 9).",
                plugin.getMonstersPrefix() + "§eYou can now use a §65 minute break §e(slot 9)."));
        creator.playSound(creator.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8F, 1.4F);
    }

    private Player creator() {
        if (gameManager.getGameConfig() == null || gameManager.getGameConfig().getErsteller() == null) return null;
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(gameManager.getGameConfig().getErsteller()));
            return player != null && userManager.isGamePlayer(player.getUniqueId()) ? player : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(ITEM_KEY);
    }

    private static boolean has(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isItem(item)) return true;
        return false;
    }

    private static ItemStack item(Player player) {
        ItemStack item = new ItemStack(Material.CLOCK);
        item.editMeta(meta -> {
            meta.setDisplayName(Lang.t(player, "§e§lPause §8· §75 Minuten", "§e§lBreak §8· §75 minutes"));
            meta.setLore(List.of(Lang.t(player, "§7Klicken, dann nochmal zum Bestätigen", "§7Click, then again to confirm"),
                    Lang.t(player, "§7Die Monster frieren sofort ein", "§7The monsters freeze right away")));
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL || !isItem(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (paused) return;
        long now = System.currentTimeMillis();
        long wait = lastPause + COOLDOWN_MILLIS - now;
        if (lastPause > 0 && wait > 0) {
            player.sendActionBar(Lang.f(player, "§7Nächste Pause in §f%d Min.", "§7Next break in §f%d min", (wait + 59_999) / 60_000));
            return;
        }
        if (now - askedAt > CONFIRM_MILLIS) {
            askedAt = now;
            player.sendActionBar(Lang.t(player, "§eNochmal klicken: §f5 Minuten Pause", "§eClick again: §f5 minute break"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.8F);
            return;
        }
        askedAt = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isItem(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        }
        start(player);
    }

    private void start(Player by) {
        paused = true;
        lastPause = System.currentTimeMillis();
        freeze(true);
        // Monsters of a spawn batch that was already on its way
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (paused) freeze(true); }, 5L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(Lang.t(player, "§e§lPAUSE", "§e§lBREAK"),
                    Lang.f(player, "§7%s gönnt euch 5 Minuten §8· §7Monster eingefroren", "§7%s gives you 5 minutes §8· §7monsters frozen", by.getName()), 10, 60, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
        }
        DualBar bar = new DualBar("", "", BarColor.YELLOW, BarStyle.SOLID);
        long end = lastPause + PAUSE_MILLIS;
        new BukkitRunnable() {
            @Override
            public void run() {
                long left = end - System.currentTimeMillis();
                if (left <= 0 || gameManager.getCurrentPhase() != de.ethicbuilds.monsters.gameplay.model.GamePhase.WAVE) {
                    bar.removeAll();
                    finish();
                    cancel();
                    return;
                }
                long seconds = (left + 999) / 1000;
                String time = String.format("%d:%02d", seconds / 60, seconds % 60);
                bar.setTitle("§e§lPAUSE §8· §f" + time, "§e§lBREAK §8· §f" + time);
                bar.setProgress(Math.max(0, Math.min(1, left / (double) PAUSE_MILLIS)));
                Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
                if (seconds <= 10) Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, 1.2F));
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void finish() {
        freeze(false);
        paused = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(Lang.t(player, "§c§lWEITER GEHT'S!", "§c§lBACK TO IT!"), "", 5, 40, 10);
            player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.5F, 1.2F);
        }
    }

    // Frozen monsters stand still, do nothing and cannot be hurt
    private void freeze(boolean frozen) {
        for (EnemyMonster enemyMonster : List.copyOf(monsterManager.getMonsters().values())) {
            if (!(enemyMonster.getMonster() instanceof Mob mob) || !mob.isValid()) continue;
            mob.setAware(!frozen);
            mob.setInvulnerable(frozen);
            if (frozen) mob.setTarget(null);
        }
    }
}
