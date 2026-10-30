package de.ethicbuilds.monsters.lang;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Set;

/***
 * Menu title from the design session: one background glyph, texts at fixed spots in their own line fonts
 * (ASCII only plus the arrow and the coin). Used by the workbench and the round end card.
 */
public final class MenuTitle {
    public record Part(String text, TextColor color) {}

    private static final char COIN = '';
    private final Key font;
    private final String lines;
    private final Set<String> big;
    private final TextComponent.Builder title = Component.text();
    private int x = 8;

    private MenuTitle(String font, char background, String lines, Set<String> big) {
        this.font = Key.key("monsters", font);
        this.lines = lines;
        this.big = big;
        title.append(Component.text("" + background + "").font(this.font).color(NamedTextColor.WHITE));
    }

    public static MenuTitle workbench() {
        return new MenuTitle("menu", '', "werkbank/", Set.of("preis"));
    }

    public static MenuTitle roundEnd(boolean english) {
        return new MenuTitle("rundenende", english ? '' : '', "rundenende/", Set.of("welle", "gesamt"));
    }

    /** Name top left in the normal title font */
    public MenuTitle name(String text) {
        moveTo(8);
        title.append(Component.text(text));
        x += width("", text);
        return this;
    }

    public MenuTitle glyph(char glyph, int px, int imageWidth) {
        moveTo(px);
        title.append(Component.text(glyph).font(font).color(NamedTextColor.WHITE));
        x = px + imageWidth + 1;
        return this;
    }

    public MenuTitle left(String line, int px, Part... parts) {
        moveTo(px);
        Key lineFont = Key.key("monsters", lines + line);
        for (Part part : parts) {
            title.append(Component.text(part.text()).font(lineFont).color(part.color()));
            x += width(line, part.text());
        }
        return this;
    }

    public MenuTitle centered(String line, int middle, Part... parts) {
        return left(line, middle - total(line, parts) / 2, parts);
    }

    public MenuTitle right(String line, int edge, Part... parts) {
        return left(line, edge - total(line, parts), parts);
    }

    public Component build() {
        return title.build();
    }

    private int total(String line, Part... parts) {
        int total = 0;
        for (Part part : parts) total += width(line, part.text());
        return total;
    }

    private void moveTo(int px) {
        String spaces = Pixels.spaces(px - x);
        if (!spaces.isEmpty()) title.append(Component.text(spaces).font(font));
        x = px;
    }

    public int width(String line, String text) {
        int total = 0;
        for (char c : text.toCharArray()) {
            total += big.contains(line) ? (c == '.' || c == ',' || c == ' ' ? 3 : c == COIN ? 11 : 7) : advance(c);
        }
        return total;
    }

    private static int advance(char c) {
        if (c == '→') return 7;
        if (c == COIN) return 8;
        return Pixels.advance(c);
    }
}
