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
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.5F, 0.7F);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.25F, 0.6F);
    }

    /** Something bought or done */
    public static void buy(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.5F, 1.1F);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.35F, 1.6F);
    }

    /** Crisp "ding" for a headshot, only for the shooter */
    public static void headshot(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.MASTER, 0.9F, 1.7F);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.45F, 2.0F);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.3F, 1.8F);
    }
}
