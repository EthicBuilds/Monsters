package de.ethicbuilds.monsters.scoreboard;

/***
 * One line of the tab list as MiniMessage, drawn with a pen at pixel positions (design session):
 * backgrounds first, then back with space glyphs and the content on top
 */
final class TabLine {
    private static final String TAB = "monsters:tab", SMALL = "monsters:tab_klein";

    private final StringBuilder out = new StringBuilder();
    private int x;

    TabLine(int start) {
        x = start;
    }

    int x() {
        return x;
    }

    TabLine moveTo(int px) {
        int distance = px - x;
        if (distance == 0) return this;
        StringBuilder spaces = new StringBuilder();
        for (int i = 7; i >= 0; i--) {
            int step = 1 << i;
            while (Math.abs(distance) >= step) {
                spaces.append((char) ((distance > 0 ? 0xF011 : 0xF021) + i));
                distance += distance > 0 ? -step : step;
            }
        }
        out.append("<font:").append(TAB).append(">").append(spaces).append("</font>");
        x = px;
        return this;
    }

    /** Glyph of monsters:tab, advance = width + 1 */
    TabLine glyph(int px, char glyph, int advance, String color) {
        moveTo(px);
        out.append("<color:").append(color).append("><font:").append(TAB).append(">").append(glyph).append("</font></color>");
        x += advance;
        return this;
    }

    /** Glyph of monsters:hud (the coin etc.), drawn in white */
    TabLine hud(int px, char glyph, int advance) {
        moveTo(px);
        out.append("<white><font:monsters:hud>").append(glyph).append("</font></white>");
        x += advance;
        return this;
    }

    TabLine text(int px, String text, String color) {
        moveTo(px);
        out.append("<color:").append(color).append(">").append(escape(text)).append("</color>");
        x += width(text);
        return this;
    }

    TabLine bold(int px, String text, String color) {
        moveTo(px);
        out.append("<color:").append(color).append("><bold>").append(escape(text)).append("</bold></color>");
        x += width(text) + text.length();
        return this;
    }

    /** Small capitals of monsters:tab_klein */
    TabLine small(int px, String text, String color) {
        moveTo(px);
        out.append("<color:").append(color).append("><font:").append(SMALL).append(">").append(escape(text)).append("</font></color>");
        x += smallWidth(text);
        return this;
    }

    /** Text that ends with its last pixel at px */
    TabLine textRight(int px, String text, String color) {
        return text(px - width(text) + 2, text, color);
    }

    TabLine smallCentered(int middle, String text, String color) {
        return small(middle - (smallWidth(text) - 1) / 2, text, color);
    }

    String end(int px) {
        moveTo(px);
        return out.toString();
    }

    static int width(String text) {
        int total = 0;
        for (char c : text.toCharArray()) total += advance(c);
        return total;
    }

    // Advances of the Minecraft font including the 1 px gap
    private static int advance(char c) {
        if ("!',.:;i|".indexOf(c) >= 0) return 2;
        if ("`l".indexOf(c) >= 0) return 3;
        if ("\"()*I[]t{} ".indexOf(c) >= 0) return 4;
        if ("<>fk".indexOf(c) >= 0) return 5;
        if (c == '@' || c == '~') return 7;
        return 6;
    }

    static int smallWidth(String text) {
        int total = 0;
        for (char c : text.toCharArray()) {
            total += switch (c) {
                case ' ' -> 3;
                case 'M', 'W', '#' -> 6;
                case 'N' -> 5;
                case '.', ':', '·' -> 2;
                case '-' -> 3;
                default -> 4;
            };
        }
        return total;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }
}
