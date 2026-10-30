package de.ethicbuilds.monsters.scoreboard;

/***
 * One line of the tab list as MiniMessage, drawn with a pen at pixel positions (design session).
 * Symbols, spaces and small capitals (from U+E100) each in their own font tag of monsters:tab, normal text stays
 * in the Minecraft font (one font tag around the whole line came out invisible through TAB).
 */
final class TabLine {
    private static final String SMALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜ0123456789.:-/#·";
    private static final char SMALL_SPACE = '';

    private static final String FONT = "<font:monsters:tab>", FONT_END = "</font>";

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
        out.append(FONT);
        for (int i = 7; i >= 0; i--) {
            int step = 1 << i;
            while (Math.abs(distance) >= step) {
                out.append((char) ((distance > 0 ? 0xF011 : 0xF021) + i));
                distance += distance > 0 ? -step : step;
            }
        }
        out.append(FONT_END);
        x = px;
        return this;
    }

    TabLine gap(int px) {
        return moveTo(x + px);
    }

    /** Symbol of monsters:tab, advance = width + 1 */
    TabLine glyph(int px, char glyph, int advance, String color) {
        moveTo(px);
        out.append("<color:").append(color).append(">").append(FONT).append(glyph).append(FONT_END).append("</color>");
        x += advance;
        return this;
    }

    TabLine glyph(char glyph, int advance, String color) {
        return glyph(x, glyph, advance, color);
    }

    TabLine text(int px, String text, String color) {
        moveTo(px);
        out.append("<color:").append(color).append(">").append(escape(text)).append("</color>");
        x += width(text);
        return this;
    }

    TabLine text(String text, String color) {
        return text(x, text, color);
    }

    TabLine bold(String text, String color) {
        out.append("<color:").append(color).append("><bold>").append(escape(text)).append("</bold></color>");
        x += width(text) + text.length();
        return this;
    }

    /** Small capitals */
    TabLine small(String text, String color) {
        StringBuilder chars = new StringBuilder();
        for (char c : text.toCharArray()) {
            int index = SMALL.indexOf(Character.toUpperCase(c));
            chars.append(c == ' ' ? SMALL_SPACE : index >= 0 ? (char) (0xE100 + index) : c);
        }
        out.append("<color:").append(color).append(">").append(FONT).append(chars).append(FONT_END).append("</color>");
        x += smallWidth(text);
        return this;
    }

    /** Text that ends with its last pixel at px */
    TabLine textRight(int px, String text, String color) {
        return text(px - width(text) + 2, text, color);
    }

    String end() {
        return out.toString();
    }

    String end(int px) {
        moveTo(px);
        return end();
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
            total += switch (Character.toUpperCase(c)) {
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
