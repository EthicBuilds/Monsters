package de.ethicbuilds.monsters.weapons;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/***
 * Sniper scope: right click looks through the scope (zoom like the spyglass, the scope overlay, walking slowly),
 * the mouse wheel sets the zoom in 5 steps, right click again or a number key ends it
 */
public class SniperScope implements Listener {
    private static final double AIM_SPEED = 0.03;
    // The client narrows the view by (speed / walk speed + 1) / 2, a negative walk speed zooms in up to 0.1 like the spyglass
    private static final double[] ZOOM = {0.6, 0.45, 0.32, 0.2, 0.1};
    private static final int START_ZOOM = 2;
    // Held down the right click arrives every 4 ticks, that still counts as one click
    private static final long TOGGLE_GAP = 300;
    private static final Key OVERLAY = Key.key("monsters", "misc/zielfernrohr");
    private static final NamespacedKey SCOPE_KEY = new NamespacedKey("monsters", "zielfernrohr");

    private static final Map<UUID, Double> savedSpeed = new HashMap<>();
    private static final Map<UUID, ItemStack> savedHelmet = new HashMap<>();
    private static final Map<UUID, Long> lastRightClick = new HashMap<>();
    private static final Map<UUID, Float> savedWalkSpeed = new HashMap<>();
    private static final Map<UUID, Integer> zoomLevel = new HashMap<>();

    public static boolean isAiming(Player player) {
        return savedSpeed.containsKey(player.getUniqueId());
    }

    public static void rightClick(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastRightClick.put(player.getUniqueId(), now);
        if (last != null && now - last < TOGGLE_GAP) return;
        if (isAiming(player)) stop(player);
        else start(player);
    }

    private static void start(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        savedSpeed.put(player.getUniqueId(), speed.getBaseValue());
        savedWalkSpeed.put(player.getUniqueId(), player.getWalkSpeed());
        applyZoom(player);
        ItemStack helmet = player.getInventory().getHelmet();
        savedHelmet.put(player.getUniqueId(), helmet == null ? new ItemStack(Material.AIR) : helmet);
        player.getInventory().setHelmet(scopeHelmet(helmet));
        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.8F, 1.0F);
    }

    public static void stop(Player player) {
        Double speed = savedSpeed.remove(player.getUniqueId());
        if (speed == null) return;
        // Setting the walk speed also sets the speed attribute, so the attribute comes after
        Float walkSpeed = savedWalkSpeed.remove(player.getUniqueId());
        player.setWalkSpeed(walkSpeed != null ? walkSpeed : 0.2F);
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null) attribute.setBaseValue(speed);
        ItemStack helmet = savedHelmet.remove(player.getUniqueId());
        ItemStack worn = player.getInventory().getHelmet();
        // Only if the scope is still on, armor bought meanwhile stays
        if (worn != null && worn.getPersistentDataContainer().has(SCOPE_KEY)) player.getInventory().setHelmet(helmet);
        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 0.8F, 1.0F);
    }

    // The worn helmet keeps its look and protection and only gets the scope overlay
    private static ItemStack scopeHelmet(ItemStack helmet) {
        boolean bare = helmet == null || helmet.getType().isAir();
        ItemStack scope = bare ? new ItemStack(Material.PAPER) : helmet.clone();
        Equippable worn = bare ? null : scope.getData(DataComponentTypes.EQUIPPABLE);
        Equippable.Builder equippable = Equippable.equippable(EquipmentSlot.HEAD).cameraOverlay(OVERLAY);
        if (worn != null) equippable.assetId(worn.assetId()).equipSound(worn.equipSound());
        scope.setData(DataComponentTypes.EQUIPPABLE, equippable.build());
        scope.editMeta(meta -> meta.getPersistentDataContainer().set(SCOPE_KEY, PersistentDataType.BYTE, (byte) 1));
        return scope;
    }

    private static void applyZoom(Player player) {
        double fov = ZOOM[zoomLevel.getOrDefault(player.getUniqueId(), START_ZOOM)];
        // Bukkit walk speed is twice the client value
        player.setWalkSpeed((float) (2 * AIM_SPEED / (2 * fov - 1)));
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(AIM_SPEED);
    }

    // Mouse wheel up zooms in, down zooms out. Other keys leave the scope and change the slot
    @EventHandler(priority = EventPriority.LOW)
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!isAiming(player)) return;
        int step = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        if (step != 1 && step != 8) {
            stop(player);
            return;
        }
        event.setCancelled(true);
        int level = Math.clamp(zoomLevel.getOrDefault(player.getUniqueId(), START_ZOOM) + (step == 8 ? 1 : -1), 0, ZOOM.length - 1);
        zoomLevel.put(player.getUniqueId(), level);
        applyZoom(player);
        AmmoHud.pause(player);
        player.sendActionBar(String.format("§7Zoom §f%d§7/%d", level + 1, ZOOM.length));
        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.4F, 0.8F + 0.1F * level);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
        lastRightClick.remove(event.getPlayer().getUniqueId());
        zoomLevel.remove(event.getPlayer().getUniqueId());
    }

    // A monster right in front gets hit as an attack, with the sniper that is a shot as well
    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Monster)) return;
        Player player = event.getPlayer();
        Weapon weapon = sniperInHand(player);
        if (weapon == null) return;
        event.setCancelled(true);
        weapon.shoot(player);
    }

    private static Weapon sniperInHand(Player player) {
        var gamePlayer = de.ethicbuilds.monsters.Main.getINSTANCE().getInjector()
                .getInstance(de.ethicbuilds.monsters.player.manager.UserManager.class).getGamePlayer(player.getUniqueId());
        if (gamePlayer == null) return null;
        Weapon weapon = gamePlayer.getWeapon(player.getInventory().getItemInMainHand());
        return weapon instanceof Sniper ? weapon : null;
    }
}
