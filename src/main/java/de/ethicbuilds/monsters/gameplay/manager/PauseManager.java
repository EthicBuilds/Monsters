package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.DualBar;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
 * After 1 minute the creator may go on early with a second item. At most one pause item at a time,
 * between two pauses at least 30 minutes. Design from the design session.
 */
public class PauseManager implements Listener {
    private static final long PAUSE_MILLIS = 5 * 60_000, MIN_PAUSE_MILLIS = 60_000, COOLDOWN_MILLIS = 30 * 60_000, CONFIRM_MILLIS = 3000;
    private static final byte PAUSE = 1, RESUME = 2;
    private static final int ITEM_SLOT = 8;
    private static final NamespacedKey ITEM_KEY = new NamespacedKey("monsters", "pause");
    private static final Key COOLDOWN_GROUP = Key.key("monsters", "pause"), RESUME_GROUP = Key.key("monsters", "weiter");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String GO_COLOR = "#3CDC6E";

    private static volatile boolean paused;

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;
    @Inject private MonsterManager monsterManager;

    private long lastPause, pauseEnd;
    private long askedAt;

    /** While true the waves, monster AI and boss attacks wait */
    public static boolean isPaused() {
        return paused;
    }

    /** After a boss wave: the creator gets the item, if they do not have one yet */
    public void giveItem() {
        Player creator = creator();
        if (creator == null || kind(creator) != 0) return;
        creator.getInventory().setItem(ITEM_SLOT, pauseItem(creator, false));
        showCooldown(creator);
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

    /** PAUSE or RESUME, 0 for any other item */
    private static byte kind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Byte kind = item.getItemMeta().getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.BYTE);
        return kind == null ? 0 : kind;
    }

    private static byte kind(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (kind(item) != 0) return kind(item);
        return 0;
    }

    private static void removeItems(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (kind(player.getInventory().getItem(slot)) != 0) player.getInventory().setItem(slot, null);
        }
    }

    private static void replaceItem(Player player, ItemStack item) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (kind(player.getInventory().getItem(slot)) == kind(item)) player.getInventory().setItem(slot, item);
        }
    }

    // While the 30 minutes run, the vanilla cooldown bar over the item and the time in the lore show it
    private ItemStack pauseItem(Player player, boolean asking) {
        long wait = cooldownLeft();
        String name = asking
                ? Lang.t(player, "<bold><#FF4838>Sicher?</#FF4838></bold> <#8A93A3>· nochmal klicken</#8A93A3>", "<bold><#FF4838>Sure?</#FF4838></bold> <#8A93A3>· click again</#8A93A3>")
                : Lang.t(player, "<bold><#FFCC2E>Pause</#FFCC2E></bold> <#8A93A3>· 5 Minuten</#8A93A3>", "<bold><#FFCC2E>Break</#FFCC2E></bold> <#8A93A3>· 5 minutes</#8A93A3>");
        String lore = wait > 0
                ? Lang.t(player, "<#8A93A3>Wieder bereit in</#8A93A3> <white>", "<#8A93A3>Ready again in</#8A93A3> <white>") + clock(wait) + "</white>"
                : Lang.t(player, "<#8A93A3>Klick, dann bestätigen</#8A93A3>", "<#8A93A3>Click, then confirm</#8A93A3>");
        ItemStack item = item(PAUSE, "pause", COOLDOWN_GROUP, MM.deserialize(name), MM.deserialize(lore));
        if (asking) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return item;
    }

    // Before 1 minute of break the cooldown bar over the item counts down to when it works
    private ItemStack resumeItem(Player player, boolean asking) {
        long wait = lastPause + MIN_PAUSE_MILLIS - System.currentTimeMillis();
        String name = asking
                ? Lang.t(player, "<bold><#FF4838>Sicher?</#FF4838></bold> <#8A93A3>· nochmal klicken</#8A93A3>", "<bold><#FF4838>Sure?</#FF4838></bold> <#8A93A3>· click again</#8A93A3>")
                : Lang.t(player, "<bold><#3CDC6E>Weiter</#3CDC6E></bold> <#8A93A3>· Pause beenden</#8A93A3>", "<bold><#3CDC6E>Go on</#3CDC6E></bold> <#8A93A3>· end the break</#8A93A3>");
        String lore = wait > 0
                ? Lang.t(player, "<#8A93A3>Möglich in</#8A93A3> <white>", "<#8A93A3>Possible in</#8A93A3> <white>") + clock(wait) + "</white>"
                : Lang.t(player, "<#8A93A3>Klick, dann bestätigen</#8A93A3>", "<#8A93A3>Click, then confirm</#8A93A3>");
        ItemStack item = item(RESUME, "weiter", RESUME_GROUP, MM.deserialize(name), MM.deserialize(lore));
        if (asking) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return item;
    }

    private static ItemStack item(byte kind, String model, Key group, Component name, Component lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(1).cooldownGroup(group).build());
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("monsters", model));
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(lore.decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.BYTE, kind);
        });
        return item;
    }

    private long cooldownLeft() {
        return lastPause == 0 ? 0 : lastPause + COOLDOWN_MILLIS - System.currentTimeMillis();
    }

    private void showCooldown(Player player) {
        long wait = cooldownLeft();
        if (wait > 0) player.setCooldown(COOLDOWN_GROUP, (int) (wait / 50));
    }

    private static String clock(long millis) {
        long seconds = (millis + 999) / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        byte kind = kind(event.getItem());
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL || kind == 0) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (kind == RESUME) {
            resume(player, now);
            return;
        }
        if (paused) return;
        if (cooldownLeft() > 0) {
            replaceItem(player, pauseItem(player, false));
            showCooldown(player);
            hint(player, "Nächste Pause in", "Next break in", cooldownLeft());
            return;
        }
        if (now - askedAt > CONFIRM_MILLIS) {
            askedAt = now;
            replaceItem(player, pauseItem(player, true));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.8F);
            // Back to normal when not confirmed in time
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (askedAt == now && !paused) replaceItem(player, pauseItem(player, false));
            }, CONFIRM_MILLIS / 50);
            return;
        }
        askedAt = 0;
        removeItems(player);
        start(player);
    }

    private static void hint(Player player, String de, String en, long millis) {
        String time = " " + Overlay.num(clock(millis));
        Overlay.small(player, Overlay.icon(Overlay.HOURGLASS, Overlay.GOLD, "<color:" + Overlay.GREY + ">" + de + "</color>" + time),
                Overlay.icon(Overlay.HOURGLASS, Overlay.GOLD, "<color:" + Overlay.GREY + ">" + en + "</color>" + time));
    }

    // The creator ends the break early, at the earliest after 1 minute
    private void resume(Player player, long now) {
        if (!paused) {
            removeItems(player);
            return;
        }
        long wait = lastPause + MIN_PAUSE_MILLIS - now;
        if (wait > 0) {
            replaceItem(player, resumeItem(player, false));
            player.setCooldown(RESUME_GROUP, (int) (wait / 50));
            hint(player, "Fortsetzen ab", "Go on from", wait);
            return;
        }
        if (now - askedAt > CONFIRM_MILLIS) {
            askedAt = now;
            replaceItem(player, resumeItem(player, true));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.8F);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (askedAt == now && paused) replaceItem(player, resumeItem(player, false));
            }, CONFIRM_MILLIS / 50);
            return;
        }
        askedAt = 0;
        pauseEnd = now;
    }

    private void start(Player by) {
        paused = true;
        lastPause = System.currentTimeMillis();
        freeze(true);
        // Monsters of a spawn batch that was already on its way
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (paused) freeze(true); }, 5L);
        String minutes = Overlay.num(PAUSE_MILLIS / 60_000);
        Overlay.mediumAll(Overlay.GOLD, Overlay.line(Overlay.HOURGLASS, Overlay.GOLD, "PAUSE", by.getName() + " gönnt euch " + minutes + " Minuten"),
                Overlay.line(Overlay.HOURGLASS, Overlay.GOLD, "BREAK", by.getName() + " gives you " + minutes + " minutes"));
        for (Player player : Bukkit.getOnlinePlayers()) player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
        by.getInventory().setItem(ITEM_SLOT, resumeItem(by, false));
        by.setCooldown(RESUME_GROUP, (int) (MIN_PAUSE_MILLIS / 50));
        DualBar bar = new DualBar("", "", BarColor.YELLOW, BarStyle.SOLID);
        pauseEnd = lastPause + PAUSE_MILLIS;
        new BukkitRunnable() {
            @Override
            public void run() {
                long left = pauseEnd - System.currentTimeMillis();
                if (left <= 0 || gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    bar.removeAll();
                    finish();
                    cancel();
                    return;
                }
                boolean last = left <= 10_000;
                String time = last ? "<#FF4838>" + clock(left) + "</#FF4838>" : Overlay.num(clock(left));
                bar.setTitle(Overlay.parse(Overlay.line(Overlay.HOURGLASS, Overlay.GOLD, "Pause", time)),
                        Overlay.parse(Overlay.line(Overlay.HOURGLASS, Overlay.GOLD, "Break", time)));
                bar.setProgress(Math.max(0, Math.min(1, left / (double) PAUSE_MILLIS)));
                Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
                if (last) Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, 1.2F));
                // Snowflakes show the frozen monsters
                for (EnemyMonster enemyMonster : List.copyOf(monsterManager.getMonsters().values())) {
                    var monster = enemyMonster.getMonster();
                    if (monster != null && monster.isValid()) {
                        monster.getWorld().spawnParticle(Particle.SNOWFLAKE, monster.getLocation().add(0, monster.getHeight() * 0.6, 0), 4, 0.3, 0.4, 0.3, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void finish() {
        freeze(false);
        paused = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (kind(player) == RESUME) removeItems(player);
            Overlay.medium(player, GO_COLOR, Overlay.line(Overlay.NEXT, GO_COLOR, "WEITER GEHT'S", null),
                    Overlay.line(Overlay.NEXT, GO_COLOR, "BACK TO IT", null));
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
