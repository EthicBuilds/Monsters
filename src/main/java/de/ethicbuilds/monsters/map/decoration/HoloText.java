package de.ethicbuilds.monsters.map.decoration;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Locale;

/***
 * One look for all signs of the game (DESIGN 31.10.): symbol and bold title, a divider, the price, a short info and a small
 * hint, no backdrop (it washed out the models behind it), shadow, always the same brightness
 */
public final class HoloText {
    public static final char COIN = '', PARTS = '', MOUSE = '', LOCK = '', WAVE = '',
            DIVIDER = '', GAP = '';
    private static final String HOLO = "<font:monsters:holo>", SMALL = "<font:monsters:holo_klein>";
    private static final String ACCENT = "<#F9A11E>", TITLE = "<#F2F5F8>", PRICE = "<#FFB938>", INFO = "<#C3CCD8>", MUTED = "<#9AA6B8>";

    private HoloText() {}

    /** Symbol (or none) and the bold name; perk symbols bring their own colours */
    public static String title(Character symbol, boolean colored, String name) {
        String icon = symbol == null ? "" : HOLO + (colored ? "<white>" : ACCENT) + symbol + "</font>";
        return icon + "<bold>" + TITLE + (symbol == null ? "" : " ") + escape(name) + "</bold>";
    }

    public static String divider() {
        return HOLO + ACCENT + DIVIDER + "</font>";
    }

    public static String price(int coins, boolean english) {
        return HOLO + ACCENT + COIN + GAP + "</font><bold>" + PRICE + number(coins, english) + "</bold>" + MUTED + " Coins";
    }

    /** Price in building parts (apomapregi) */
    public static String parts(int parts, boolean english) {
        String word = english ? (parts == 1 ? " part" : " parts") : (parts == 1 ? " Bauteil" : " Bauteile");
        return HOLO + ACCENT + PARTS + GAP + "</font><bold>" + PRICE + number(parts, english) + "</bold>" + MUTED + word;
    }

    public static String info(String text) {
        return INFO + escape(text);
    }

    public static String wave(int wave, boolean english) {
        return HOLO + MUTED + WAVE + "</font>" + INFO + (english ? " from wave " : " ab Welle ") + wave;
    }

    /** "Rechtsklick zum Kaufen" becomes the small line "Rechtsklick · Kaufen" */
    public static String hint(String hint) {
        String click = hint, action = "";
        for (String split : new String[]{" zum ", " zur ", " to "}) {
            int at = hint.indexOf(split);
            if (at < 0) continue;
            click = hint.substring(0, at);
            action = hint.substring(at + split.length());
            action = action.isEmpty() ? action : Character.toUpperCase(action.charAt(0)) + action.substring(1);
            break;
        }
        return SMALL + MUTED + MOUSE + " <#D5DCE6>" + escape(click) + (action.isEmpty() ? "" : "<#A3AEBF> · " + escape(action)) + "</font>";
    }

    public static String number(int value, boolean english) {
        return String.format(english ? Locale.US : Locale.GERMANY, "%,d", value);
    }

    /** The display settings every sign shares */
    public static void style(TextDisplay holo) {
        holo.setBillboard(Display.Billboard.VERTICAL);
        holo.setAlignment(TextDisplay.TextAlignment.CENTER);
        holo.setLineWidth(200);
        holo.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        holo.setDefaultBackground(false);
        holo.setSeeThrough(false);
        holo.setShadowed(true);
        holo.setBrightness(new Display.Brightness(15, 15));
        holo.setViewRange(0.3F);
        holo.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8F), new AxisAngle4f()));
    }

    // Names come from code and configs, but never as MiniMessage
    private static String escape(String text) {
        return text.replace("<", "\\<");
    }
}
