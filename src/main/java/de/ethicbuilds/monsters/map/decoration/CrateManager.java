package de.ethicbuilds.monsters.map.decoration;

import de.ethicbuilds.monsters.weapons.AmmoHud;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
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
    private static final int COINS = 100;

    @Inject private UserManager userManager;
    @Inject private GameManager gameManager;
    @Inject private GrenadeManager grenadeManager;

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
        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(player.getUniqueId())) return;

        if (!GameItems.isCrowbar(player.getInventory().getItemInMainHand())) {
            AmmoHud.pause(player);
            player.sendActionBar("§7Zerschlage die Kiste mit der §6Brechstange");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHits.getOrDefault(player.getUniqueId(), 0L) < HIT_COOLDOWN) return;
        lastHits.put(player.getUniqueId(), now);

        hit(gamePlayer, crate);
    }

    private void hit(GamePlayer gamePlayer, Crate crate) {
        Player player = gamePlayer.getPlayer();
        Location center = crate.block.getLocation().add(0.5, 0.5, 0.5);
        crate.damage++;

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
    }

    private void loot(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < GRENADE_CHANCE && grenadeManager.give(player)) {
            player.sendActionBar("§aGlück gehabt! §7Du hast eine §aGranate §7gefunden");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0F, 1.2F);
        } else if (roll < GRENADE_CHANCE + COINS_CHANCE) {
            gamePlayer.addCoins(COINS);
            player.sendActionBar(String.format("§6+%d Coins §7gefunden", COINS));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.4F);
        } else {
            player.sendActionBar("§7Die Kiste war leer");
        }
    }
}
