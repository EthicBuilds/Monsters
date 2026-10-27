package de.ethicbuilds.monsters.weapons.manager;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/***
 * Workbench title from the design session: one background (monsters:menu), overlays and texts at fixed spots.
 * Texts use the fonts monsters:werkbank/<line>, ASCII only plus the arrow and the coin.
 */
final class WorkbenchTitle {
    record Part(String text, TextColor color) {}

    private static final Key MENU = Key.key("monsters", "menu");
    private final TextComponent.Builder title = Component.text();
    private int x = 8;

    WorkbenchTitle() {
        title.append(Component.text("").font(MENU).color(NamedTextColor.WHITE));
    }

    // Name top left in the normal title font
    WorkbenchTitle name(String text) {
        moveTo(8);
        title.append(Component.text(text));
        x += width("", text);
        return this;
    }

    WorkbenchTitle glyph(char glyph, int px, int imageWidth) {
        moveTo(px);
        title.append(Component.text(glyph).font(MENU).color(NamedTextColor.WHITE));
        x = px + imageWidth + 1;
        return this;
    }

    WorkbenchTitle left(String line, int px, Part... parts) {
        moveTo(px);
        Key font = Key.key("monsters", "werkbank/" + line);
        for (Part part : parts) {
            title.append(Component.text(part.text()).font(font).color(part.color()));
            x += width(line, part.text());
        }
        return this;
    }

    WorkbenchTitle centered(String line, int middle, Part... parts) {
        int total = 0;
        for (Part part : parts) total += width(line, part.text());
        return left(line, middle - total / 2, parts);
    }

    Component build() {
        return title.build();
    }

    private void moveTo(int px) {
        int distance = px - x;
        StringBuilder spaces = new StringBuilder();
        for (int i = 7; i >= 0; i--) {
            int step = 1 << i;
            while (Math.abs(distance) >= step) {
                spaces.append((char) ((distance > 0 ? 0xF011 : 0xF021) + i));
                distance += distance > 0 ? -step : step;
            }
        }
        if (!spaces.isEmpty()) title.append(Component.text(spaces.toString()).font(MENU));
        x = px;
    }

    static int width(String line, String text) {
        int total = 0;
        for (char c : text.toCharArray()) {
            total += line.equals("preis") ? (c == '.' || c == ' ' ? 3 : c == '' ? 11 : 7) : advance(c);
        }
        return total;
    }

    private static int advance(char c) {
        if ("!',.:;i|".indexOf(c) >= 0) return 2;
        if ("`l".indexOf(c) >= 0) return 3;
        if ("\"()*I[]t{} ".indexOf(c) >= 0) return 4;
        if ("<>fk".indexOf(c) >= 0) return 5;
        if (c == '@' || c == '~' || c == '→') return 7;
        if (c == '') return 8;
        return 6;
    }
}
