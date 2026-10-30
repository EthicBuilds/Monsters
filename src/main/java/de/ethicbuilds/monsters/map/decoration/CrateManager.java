package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.CrowbarLevel;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.powerup.PowerupManager;
import de.ethicbuilds.monsters.weapons.manager.GrenadeManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Barrels and crates that break open with the crowbar and come back with the next wave
 */
public class CrateManager implements Listener {
    private static final int STAGES = 3;
    private static final long HIT_COOLDOWN = 400;
    private static final double GRENADE_CHANCE = 0.3;
    private static final double COINS_CHANCE = 0.2;
    private static final double AMMO_CHANCE = 0.15;
    private static final int COINS = 100;
    private static final double REACH = 5;
    private static final String COLOR = "#F9A11E";

    @Inject private UserManager userManager;
    @Inject private GameManager gameManager;
    @Inject private GrenadeManager grenadeManager;
    @Inject private PowerupManager powerupManager;

    private final Map<Location, Crate> crates = new HashMap<>();
    private final Map<UUID, Long> lastHits = new HashMap<>();

    private static class Crate {
        private final ItemDisplay display;
        private final ItemStack item;
        private final Transformation transformation;
        private final Block block;
        private int damage;

        private Crate(ItemDisplay display, Block block) {
            this.display = display;
            this.item = display.getItemStack().clone();
            this.transformation = display.getTransformation();
            this.block = block;
        }
    }

    public void register(ItemDisplay display, Block block) {
        if (block.getType().isAir()) block.setType(Material.BARRIER, false);
        crates.put(block.getLocation(), new Crate(display, block));
    }

    /** Called at the start of every wave */
    public void resetAll() {
        for (Crate crate : crates.values()) {
            if (crate.damage == 0 || !crate.display.isValid()) continue;
            crate.damage = 0;
            crate.display.setItemStack(crate.item);
            crate.display.setTransformation(crate.transformation);
            if (crate.block.getType().isAir()) crate.block.setType(Material.BARRIER, false);
        }
    }

    @EventHandler
    public void onCrateHit(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || gameManager.getCurrentPhase() != GamePhase.WAVE) return;

        Crate crate = crates.get(event.getClickedBlock().getLocation());
        if (crate == null || crate.damage >= STAGES) return;

        Player player = event.getPlayer();
        if (player.getEyeLocation().distance(crate.block.getLocation().add(0.5, 0.5, 0.5)) > REACH) return;
        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(player.getUniqueId())) return;

        if (!GameItems.isCrowbar(player.getInventory().getItemInMainHand())) {
            crate(player, grey("Zerschlage die Kiste mit der ") + bold("Brechstange"), grey("Smash the crate with the ") + bold("Crowbar"));
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHits.getOrDefault(player.getUniqueId(), 0L) < HIT_COOLDOWN / CrowbarLevel.speed(gamePlayer.getCrowbarLevel())) return;
        lastHits.put(player.getUniqueId(), now);

        hit(gamePlayer, crate);
    }

    private void hit(GamePlayer gamePlayer, Crate crate) {
        Player player = gamePlayer.getPlayer();
        Location center = crate.block.getLocation().add(0.5, 0.5, 0.5);
        crate.damage++;
        // A better crowbar needs fewer hits
        int hits = Math.max(1, (int) Math.round(STAGES / CrowbarLevel.speed(gamePlayer.getCrowbarLevel())));
        if (crate.damage >= hits) crate.damage = STAGES;

        player.swingMainHand();
        center.getWorld().spawnParticle(Particle.BLOCK, center, 12 * crate.damage, 0.3, 0.3, 0.3, Material.OAK_PLANKS.createBlockData());

        if (crate.damage < STAGES) {
            center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.7F, 1.2F + crate.damage * 0.15F);
            float tilt = (float) Math.toRadians(crate.damage == 1 ? 6 : 14);
            float scale = crate.damage == 1 ? 1.0F : 0.94F;
            Vector3f base = crate.transformation.getScale();
            crate.display.setInterpolationDelay(0);
            crate.display.setInterpolationDuration(3);
            crate.display.setTransformation(new Transformation(crate.transformation.getTranslation(),
                    new Quaternionf().rotateZ(tilt), new Vector3f(base).mul(scale), crate.transformation.getRightRotation()));
            return;
        }

        center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.9F, 1.1F);
        center.getWorld().spawnParticle(Particle.CLOUD, center, 8, 0.3, 0.3, 0.3, 0.02);
        crate.display.setItemStack(null);
        crate.block.setType(Material.AIR, false);
        loot(gamePlayer);
        if (ThreadLocalRandom.current().nextDouble() < CrowbarLevel.powerupChance(gamePlayer.getCrowbarLevel())) powerupManager.dropAt(center);
    }

    /** Chance in percent that a crate is not empty with this crowbar level, shown at the workbench */
    public static int lootPercent(int level) {
        return (int) Math.min(100, Math.round((GRENADE_CHANCE + COINS_CHANCE + AMMO_CHANCE + 3 * CrowbarLevel.extraLoot(level)) * 100));
    }

    private void loot(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        double roll = ThreadLocalRandom.current().nextDouble();
        double extra = CrowbarLevel.extraLoot(gamePlayer.getCrowbarLevel());
        int coins = COINS + CrowbarLevel.extraCoins(gamePlayer.getCrowbarLevel());

        if (roll < GRENADE_CHANCE + extra && grenadeManager.give(player)) {
            crate(player, bold("Granate") + grey(" gefunden"), bold("Grenade") + grey(" found"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0F, 1.2F);
        } else if (roll < GRENADE_CHANCE + COINS_CHANCE + 2 * extra) {
            gamePlayer.addCoins(coins);
            String found = "<b>" + Overlay.num("+" + coins) + "</b> " + Overlay.coin();
            crate(player, found + grey(" gefunden"), found + grey(" found"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.4F);
        } else if (roll < GRENADE_CHANCE + COINS_CHANCE + AMMO_CHANCE + 3 * extra && gamePlayer.refillWeapons()) {
            crate(player, bold("Munition") + grey(" aufgefüllt"), bold("Ammo") + grey(" refilled"));
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8F, 1.2F);
        } else {
            crate(player, grey("Die Kiste war leer"), grey("The crate was empty"));
        }
    }

    private static void crate(Player player, String de, String en) {
        Overlay.small(player, Overlay.icon(Overlay.CRATE, COLOR, de), Overlay.icon(Overlay.CRATE, COLOR, en));
    }

    private static String bold(String text) {
        return "<color:" + COLOR + "><b>" + text + "</b></color>";
    }

    private static String grey(String text) {
        return "<color:" + Overlay.GREY + ">" + text + "</color>";
    }
}
