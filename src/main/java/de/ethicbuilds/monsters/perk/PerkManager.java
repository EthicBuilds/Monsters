package de.ethicbuilds.monsters.perk;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.building.PowerGrid;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/***
 * Owned perks per player. Health and speed come as attribute modifiers on top of the base values,
 * so lying down, the sniper scope and the reset between rounds keep working with their base values.
 */
public class PerkManager implements Listener {
    @Inject private PowerGrid powerGrid;
    private static final double EXTRA_HEALTH = 10, SPEED_BOOST = 0.12, RELOAD_FACTOR = 0.6;
    private static final NamespacedKey HEALTH_KEY = new NamespacedKey("monsters", "perk_leben");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("monsters", "perk_tempo");

    private final Map<UUID, Set<Perk>> owned = new HashMap<>();

    /** Owned perks of the player, for the HUD */
    public Set<Perk> perks(Player player) {
        Set<Perk> perks = owned.get(player.getUniqueId());
        return perks == null ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(perks));
    }

    public boolean has(Player player, Perk perk) {
        Set<Perk> perks = owned.get(player.getUniqueId());
        return perks != null && perks.contains(perk);
    }

    /** Ticks until the weapon is ready again, shorter with Quick Reload */
    public int cooldown(Player player, int ticks) {
        return has(player, Perk.QUICK_RELOAD) ? Math.max(1, (int) Math.ceil(ticks * RELOAD_FACTOR)) : ticks;
    }

    public void buy(GamePlayer gamePlayer, Perk perk) {
        Player player = gamePlayer.getPlayer();
        if (has(player, perk)) {
            Lang.note(player, Lang.t(player, "§7Du hast §a" + perk.getNameDe() + " §7schon", "§7You already have §a" + perk.getNameEn()));
            Sounds.deny(player);
            return;
        }
        if (!powerGrid.isPowered()) {
            Lang.note(player, Lang.t(player, "§eKein Strom! §7Schaltet erst den Strom ein", "§eNo power! §7Turn the power on first"));
            Sounds.deny(player);
            return;
        }
        if (gamePlayer.getCoins() < perk.getPrice()) {
            Lang.note(player, Lang.f(player, "§cDu brauchst noch §6%d Coins", "§cNot enough coins, §6%d Coins §cmissing", perk.getPrice() - gamePlayer.getCoins()));
            Sounds.deny(player);
            return;
        }
        gamePlayer.setCoins(gamePlayer.getCoins() - perk.getPrice());
        owned.computeIfAbsent(player.getUniqueId(), uuid -> EnumSet.noneOf(Perk.class)).add(perk);
        apply(player, perk);

        String price = "<color:#FF8A80>-" + Lang.f(player, "%,d", "%,d", perk.getPrice()) + "</color> " + Overlay.coin();
        Overlay.small(player, Overlay.line((char) 0, perk.getColor(), perk.getNameDe().toUpperCase(), perk.getEffectDe()) + " " + price,
                Overlay.line((char) 0, perk.getColor(), perk.getNameEn().toUpperCase(), perk.getEffectEn()) + " " + price);
        Sounds.buy(player);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.6F, 1.2F);
    }

    private void apply(Player player, Perk perk) {
        switch (perk) {
            case VITALITY -> {
                modifier(player, Attribute.MAX_HEALTH, HEALTH_KEY, EXTRA_HEALTH, AttributeModifier.Operation.ADD_NUMBER);
                player.setHealth(Math.min(maxHealth(player), player.getHealth() + EXTRA_HEALTH));
            }
            case STAMINA -> modifier(player, Attribute.MOVEMENT_SPEED, SPEED_KEY, SPEED_BOOST, AttributeModifier.Operation.ADD_SCALAR);
            default -> {}
        }
    }

    /** Downed, left or round over: every perk is gone */
    public void lose(Player player) {
        owned.remove(player.getUniqueId());
        strip(player);
    }

    public void clear() {
        owned.clear();
        Bukkit.getOnlinePlayers().forEach(PerkManager::strip);
    }

    // Modifiers are saved with the player, so they go on quit and anything left over goes on join
    private static void strip(Player player) {
        remove(player, Attribute.MAX_HEALTH, HEALTH_KEY);
        remove(player, Attribute.MOVEMENT_SPEED, SPEED_KEY);
        if (player.getHealth() > maxHealth(player)) player.setHealth(maxHealth(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        strip(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lose(event.getPlayer());
    }

    private static void modifier(Player player, Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(key);
        instance.addModifier(new AttributeModifier(key, amount, operation, EquipmentSlotGroup.ANY));
    }

    private static void remove(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(key);
    }

    private static double maxHealth(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        return instance != null ? instance.getValue() : 20;
    }
}
