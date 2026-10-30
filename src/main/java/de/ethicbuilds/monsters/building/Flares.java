package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/***
 * Signal flare crate: flares in slot 8, thrown with right-click they burn a few seconds and draw the monsters nearby
 */
public class Flares implements BuildPart, Listener {
    private static final String ID = "monsters_fackelkiste", STATION = "fackeln";
    private static final int SLOT = 8, MAX = 2, PRICE = 250;
    private static final int FLIGHT_TICKS = 40, BURN_TICKS = 160;
    private static final double RADIUS = 25;
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0xFF3B2F), 1.4F);

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;
    @Inject private MonsterManager monsterManager;
    @Inject private DecorationManager decorationManager;

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (!ID.equals(id)) return false;
        decorationManager.createStation(block, STATION, "Signalfackeln", "Signal flares",
                "<white><font:monsters:hud></font></white> <#F9A11E>" + PRICE + " <#9AA6B8>Coins\n<#9AA6B8>lenken Monster ab",
                "<white><font:monsters:hud></font></white> <#F9A11E>" + PRICE + " <#9AA6B8>Coins\n<#9AA6B8>draw the monsters away",
                "Rechtsklick zum Kaufen", "Right-click to buy", 1.2F, 1.1F);
        return true;
    }

    @Override
    public boolean use(GamePlayer gamePlayer, String station) {
        if (!STATION.equals(station)) return false;
        Player player = gamePlayer.getPlayer();
        ItemStack held = player.getInventory().getItem(SLOT);
        int amount = GameItems.isFlare(held) ? held.getAmount() : 0;
        // The slot is taken by the pause item of the creator
        if (held != null && !held.getType().isAir() && amount == 0) {
            Lang.note(player, Lang.t(player, "§cKein Platz für Fackeln, Slot 9 ist belegt", "§cNo room for flares, slot 9 is taken"));
            Sounds.deny(player);
            return true;
        }
        if (amount >= MAX) {
            Lang.note(player, Lang.f(player, "§7Du hast schon §c%d Fackeln", "§7You already carry §c%d flares", MAX));
            Sounds.deny(player);
            return true;
        }
        if (gamePlayer.getCoins() < PRICE) {
            Lang.note(player, Lang.f(player, "§cDu brauchst noch §6%d Coins", "§cNot enough coins, §6%d Coins §cmissing", PRICE - gamePlayer.getCoins()));
            Sounds.deny(player);
            return true;
        }
        gamePlayer.setCoins(gamePlayer.getCoins() - PRICE);
        player.getInventory().setItem(SLOT, GameItems.flare(player, amount + 1));
        Lang.note(player, Lang.f(player, "§cSignalfackel §7in Slot 9 · Rechtsklick zum Werfen §c-%d Coins",
                "§cSignal flare §7in slot 9 · right-click to throw §c-%d Coins", PRICE));
        Sounds.buy(player);
        return true;
    }

    @EventHandler
    public void onThrow(PlayerInteractEvent event) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!GameItems.isFlare(held) || userManager.isPlayerDead(player.getUniqueId())) return;
        event.setCancelled(true);

        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItemInMainHand(held.getAmount() > 0 ? held : null);
        Location start = player.getEyeLocation();
        Item flare = player.getWorld().dropItem(start, GameItems.flare(player, 1), item -> {
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setCanMobPickup(false);
            item.setUnlimitedLifetime(true);
            item.setPersistent(false);
        });
        flare.setVelocity(start.getDirection().multiply(0.9).add(new Vector(0, 0.2, 0)));
        player.getWorld().playSound(start, Sound.ENTITY_WITCH_THROW, 1.0F, 0.8F);

        new BukkitRunnable() {
            int ticks;
            Block light;

            @Override
            public void run() {
                if (!flare.isValid()) {
                    unlight();
                    cancel();
                    return;
                }
                ticks++;
                Location at = flare.getLocation().add(0, 0.3, 0);
                flare.getWorld().spawnParticle(Particle.DUST, at, 3, 0.1, 0.1, 0.1, RED);
                if (light == null && (flare.isOnGround() || ticks >= FLIGHT_TICKS)) {
                    ignite(at);
                    light = at.getBlock();
                    if (light.getType().isAir()) {
                        Light data = (Light) Material.LIGHT.createBlockData();
                        data.setLevel(13);
                        light.setBlockData(data, false);
                    }
                }
                if (light != null) {
                    if (ticks % 2 == 0) flare.getWorld().spawnParticle(Particle.FLAME, at, 2, 0.05, 0.1, 0.05, 0.02);
                    if (ticks % 20 == 0) flare.getWorld().playSound(at, Sound.BLOCK_FIRE_AMBIENT, 1.0F, 1.4F);
                }
                if (ticks >= FLIGHT_TICKS + BURN_TICKS) {
                    unlight();
                    flare.remove();
                    cancel();
                }
            }

            private void unlight() {
                if (light != null && light.getType() == Material.LIGHT) light.setType(Material.AIR, false);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // All monsters around run to the flare while it burns
    private void ignite(Location at) {
        at.getWorld().playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.2F, 0.8F);
        long millis = BURN_TICKS * 50L;
        for (Monster monster : at.getWorld().getNearbyEntitiesByType(Monster.class, at, RADIUS)) {
            EnemyMonster enemy = monsterManager.getEnemyMonster(monster);
            if (enemy != null) enemy.lure(at, millis);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Monster monster) || !(event.getTarget() instanceof Player)) return;
        EnemyMonster enemy = monsterManager.getEnemyMonster(monster);
        if (enemy != null && enemy.isLured()) event.setCancelled(true);
    }
}
