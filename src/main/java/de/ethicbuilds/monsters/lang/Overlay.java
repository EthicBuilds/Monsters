package de.ethicbuilds.monsters.lang;

import de.ethicbuilds.monsters.weapons.AmmoHud;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;

/***
 * One look for every popup text (design session, font monsters:overlay): symbol, 3 px, bold main word in the event
 * colour, extra text grey, numbers gold. Subtitles sit between two dividers. Three strengths:
 * big (title + subtitle, rare events), medium (subtitle only) and small (action bar).
 * Every glyph and space run has its own font tag, normal text none (one tag around a whole line came out invisible).
 */
public final class Overlay {
    public static final char BOSS = '', ELITE = '', HORDE = '', TROPHY = '', BOUNTY = '',
            COINS = '', MOON = '', STORM = '', COCOON = '', SILK = '', MEDKIT = '',
            CRATE = '', PING = '', HOURGLASS = '', NEXT = '', WAVE = '', REVIVE = '';
    private static final char DIVIDER = '';
    public static final String GREY = "#8A93A3", GOLD = "#FFCC2E";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Title.Times BIG = times(10, 50, 15), MEDIUM = times(5, 40, 10), HIT_TIMES = times(0, 4, 3);
    // Hit markers: white, grey on armor, light red for headshots, red for kills
    private static final char HIT = '\uE212', HEAD_HIT = '\uE213';
    private static final String HIT_WHITE = "#FFFFFF", HIT_HEAD = "#FF8A8A", HIT_KILL = "#FF3B3B", HIT_ARMOR = "#9A9A9A";
    // Until then a big or medium text is shown, a hit marker would replace it
    private static final java.util.Map<java.util.UUID, Long> titleUntil = new java.util.HashMap<>();

    private Overlay() {}

    private static Title.Times times(int in, int stay, int out) {
        return Title.Times.times(Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L));
    }

    /** Space glyphs of monsters:tab, negative moves left */
    public static String space(int px) {
        if (px == 0) return "";
        StringBuilder out = new StringBuilder("<font:monsters:tab>");
        int distance = px;
        for (int i = 7; i >= 0; i--) {
            int step = 1 << i;
            while (Math.abs(distance) >= step) {
                out.append((char) ((distance > 0 ? 0xF011 : 0xF021) + i));
                distance += distance > 0 ? -step : step;
            }
        }
        return out.append("</font>").toString();
    }

    public static String glyph(char glyph, String color) {
        return "<color:" + color + "><font:monsters:overlay>" + glyph + "</font></color>";
    }

    /** Coin of the HUD font, always white */
    public static String coin() {
        return "<white><font:monsters:hud></font></white>";
    }

    public static String num(Object number) {
        return "<color:" + GOLD + ">" + number + "</color>";
    }

    /** Symbol, 3 px, bold word in the colour, then " · extra" in grey (extra may be null) */
    public static String line(char symbol, String color, String word, String extra) {
        StringBuilder out = new StringBuilder();
        if (symbol != 0) out.append(glyph(symbol, color)).append(space(3));
        out.append("<color:").append(color).append("><b>").append(word).append("</b></color>");
        if (extra != null && !extra.isEmpty()) out.append(" <color:").append(GREY).append(">· ").append(extra).append("</color>");
        return out.toString();
    }

    /** Symbol, 3 px, then the text as it is */
    public static String icon(char symbol, String color, String text) {
        return glyph(symbol, color) + space(3) + text;
    }

    /** Content between two dividers in the event colour, 6 px apart */
    public static String framed(String color, String content) {
        return glyph(DIVIDER, color) + space(6) + content + space(6) + glyph(DIVIDER, color);
    }

    public static Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static Component parse(Player player, String de, String en) {
        return MM.deserialize(Lang.t(player, de, en));
    }

    /** Hit marker in the middle, only while no other text is shown */
    public static void hit(Player player, boolean headshot, boolean armor, boolean kill) {
        Long until = titleUntil.get(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) return;
        String color = kill ? HIT_KILL : headshot ? HIT_HEAD : armor ? HIT_ARMOR : HIT_WHITE;
        player.showTitle(Title.title(Component.empty(), parse(glyph(headshot ? HEAD_HIT : HIT, color)), HIT_TIMES));
    }

    private static void shown(Player player, Title.Times times) {
        titleUntil.put(player.getUniqueId(), System.currentTimeMillis() + times.fadeIn().plus(times.stay()).toMillis());
    }

    /** Rare events: title and framed subtitle */
    public static void big(Player player, String color, String titleDe, String titleEn, String subDe, String subEn) {
        shown(player, BIG);
        player.showTitle(Title.title(parse(player, titleDe, titleEn), parse(framed(color, Lang.t(player, subDe, subEn))), BIG));
    }

    /** Framed subtitle, empty title */
    public static void medium(Player player, String color, String de, String en) {
        shown(player, MEDIUM);
        player.showTitle(Title.title(Component.empty(), parse(framed(color, Lang.t(player, de, en))), MEDIUM));
    }

    /** Action bar, the ammo panel waits a moment */
    public static void small(Player player, String de, String en) {
        AmmoHud.pause(player);
        player.sendActionBar(parse(player, de, en));
    }

    public static void bigAll(String color, String titleDe, String titleEn, String subDe, String subEn) {
        for (Player player : Bukkit.getOnlinePlayers()) big(player, color, titleDe, titleEn, subDe, subEn);
    }

    public static void mediumAll(String color, String de, String en) {
        for (Player player : Bukkit.getOnlinePlayers()) medium(player, color, de, en);
    }

    public static void smallAll(String de, String en) {
        for (Player player : Bukkit.getOnlinePlayers()) small(player, de, en);
    }
}
