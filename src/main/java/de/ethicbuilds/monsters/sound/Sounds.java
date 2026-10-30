package de.ethicbuilds.monsters.sound;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/***
 * Short, soft feedback sounds used everywhere, so buying, refusing and hitting sound the same in the whole game
 */
public final class Sounds {
    private Sounds() {}

    /** Not possible: too few coins, already full, locked */
    public static void deny(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.7F, 0.6F);
    }

    /** Something bought or done */
    public static void buy(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.5F, 1.1F);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.35F, 1.6F);
    }

    /** Workbench upgrade, sounds like craft; the last level gets a little fanfare */
    public static void upgrade(Player player, boolean maxLevel) {
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, SoundCategory.MASTER, 0.5F, 1.1F);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.4F, 1.3F);
        if (maxLevel) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.3F, 1.6F);
    }

    private static final java.util.Map<java.util.UUID, Long> lastArmorHit = new java.util.HashMap<>();

    /** Dull metal on an armored monster, at most every 3 ticks (shotgun pellets) */
    public static void armorHit(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastArmorHit.put(player.getUniqueId(), now);
        if (last != null && now - last < 150) return;
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.5F, 1.4F);
    }

    /** A plate breaks off */
    public static void splinter(org.bukkit.Location at) {
        at.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 0.25F, 1.8F);
    }

    /** Crisp "ding" for a headshot, only for the shooter */
    public static void headshot(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.6F, 1.5F);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.8F, 1.7F);
    }
}
