package de.ethicbuilds.monsters.lang;

/***
 * Pixel layout of texts: space glyphs that move by whole pixels (in monsters:tab, the menu fonts and the overlay)
 * and the advances of the Minecraft font
 */
public final class Pixels {
    private Pixels() {}

    /** Space glyphs for the distance, negative moves left; empty for 0 */
    public static String spaces(int px) {
        StringBuilder out = new StringBuilder();
        int distance = px;
        for (int i = 7; i >= 0; i--) {
            int step = 1 << i;
            while (Math.abs(distance) >= step) {
                out.append((char) ((distance > 0 ? 0xF011 : 0xF021) + i));
                distance += distance > 0 ? -step : step;
            }
        }
        return out.toString();
    }

    /** Advance of a character in the Minecraft font including the 1 px gap */
    public static int advance(char c) {
        if ("!',.:;i|".indexOf(c) >= 0) return 2;
        if ("`l".indexOf(c) >= 0) return 3;
        if ("\"()*I[]t{} ".indexOf(c) >= 0) return 4;
        if ("<>fk".indexOf(c) >= 0) return 5;
        if (c == '@' || c == '~') return 7;
        return 6;
    }
}
