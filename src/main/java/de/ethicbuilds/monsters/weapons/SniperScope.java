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
 * Sniper scope: right click looks through the scope (a little zoom, the scope overlay, walking slowly),
 * right click again or another item in hand ends it
 */
public class SniperScope implements Listener {
    // Slower walking narrows the view a bit, that is the zoom
    private static final double AIM_SPEED = 0.03;
    // Held down the right click arrives every 4 ticks, that still counts as one click
    private static final long TOGGLE_GAP = 300;
    private static final Key OVERLAY = Key.key("monsters", "misc/zielfernrohr");
    private static final NamespacedKey SCOPE_KEY = new NamespacedKey("monsters", "zielfernrohr");

    private static final Map<UUID, Double> savedSpeed = new HashMap<>();
    private static final Map<UUID, ItemStack> savedHelmet = new HashMap<>();
    private static final Map<UUID, Long> lastRightClick = new HashMap<>();

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
        speed.setBaseValue(AIM_SPEED);
        ItemStack helmet = player.getInventory().getHelmet();
        savedHelmet.put(player.getUniqueId(), helmet == null ? new ItemStack(Material.AIR) : helmet);
        player.getInventory().setHelmet(scopeHelmet(helmet));
        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.8F, 1.0F);
    }

    public static void stop(Player player) {
        Double speed = savedSpeed.remove(player.getUniqueId());
        if (speed == null) return;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlotChange(PlayerItemHeldEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
        lastRightClick.remove(event.getPlayer().getUniqueId());
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
