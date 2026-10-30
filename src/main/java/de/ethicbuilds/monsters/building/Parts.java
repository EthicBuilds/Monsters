package de.ethicbuilds.monsters.building;

import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/***
 * Building parts: from part crates, monsters and nests, each player has their own
 */
public final class Parts {
    // Until the design session sends its own symbol the crate stands in
    public static final String COLOR = "#C9A26B";

    private Parts() {}

    public static void give(GamePlayer gamePlayer, int amount) {
        gamePlayer.setParts(gamePlayer.getParts() + amount);
        Player player = gamePlayer.getPlayer();
        String found = "<b>" + Overlay.num("+" + amount) + "</b> ";
        Overlay.small(player, Overlay.icon(Overlay.CRATE, COLOR, found + word(amount, false) + total(gamePlayer)),
                Overlay.icon(Overlay.CRATE, COLOR, found + word(amount, true) + total(gamePlayer)));
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.8F, 1.4F);
    }

    /** Takes the parts if the player has enough, otherwise says how many are missing */
    public static boolean take(GamePlayer gamePlayer, int amount) {
        Player player = gamePlayer.getPlayer();
        if (gamePlayer.getParts() < amount) {
            int missing = amount - gamePlayer.getParts();
            Overlay.small(player, Overlay.icon(Overlay.CRATE, COLOR, grey("Dir fehlen ") + Overlay.num(missing) + " " + word(missing, false)),
                    Overlay.icon(Overlay.CRATE, COLOR, grey("You need ") + Overlay.num(missing) + " " + grey("more ") + word(missing, true)));
            Sounds.deny(player);
            return false;
        }
        gamePlayer.setParts(gamePlayer.getParts() - amount);
        return true;
    }

    /** Price for signs and notes, e.g. "4 Bauteile" */
    public static String price(int amount, boolean english) {
        return Overlay.num(amount) + " " + word(amount, english);
    }

    private static String word(int amount, boolean english) {
        String word = english ? (amount == 1 ? "part" : "parts") : (amount == 1 ? "Bauteil" : "Bauteile");
        return "<color:" + COLOR + ">" + word + "</color>";
    }

    private static String total(GamePlayer gamePlayer) {
        return grey(" (" + gamePlayer.getParts() + ")");
    }

    private static String grey(String text) {
        return "<color:" + Overlay.GREY + ">" + text + "</color>";
    }
}
