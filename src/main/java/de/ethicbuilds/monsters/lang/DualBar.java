package de.ethicbuilds.monsters.lang;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/***
 * Boss bar that everyone sees in their own language: one Adventure bar per language, each player is put into the
 * right one. Titles can be Components with glyphs or legacy § strings.
 */
public final class DualBar {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private record Viewer(Player player, boolean english) {}

    private final BossBar german, english;
    private final Map<UUID, Viewer> viewers = new HashMap<>();

    public DualBar(String de, String en, BarColor color, BarStyle style) {
        this(LEGACY.deserialize(de), LEGACY.deserialize(en), color, style);
    }

    public DualBar(Component de, Component en, BarColor color, BarStyle style) {
        german = BossBar.bossBar(de, 1, color(color), overlay(style));
        english = BossBar.bossBar(en, 1, color(color), overlay(style));
    }

    public void addPlayer(Player player) {
        boolean inEnglish = Lang.english(player);
        Viewer was = viewers.get(player.getUniqueId());
        // Same player object: already sees the right bar. After a rejoin the bar has to be shown again
        if (was != null && was.player() == player && was.english() == inEnglish) return;
        if (was != null) was.player().hideBossBar(was.english() ? english : german);
        viewers.put(player.getUniqueId(), new Viewer(player, inEnglish));
        player.showBossBar(inEnglish ? english : german);
    }

    public void removePlayer(Player player) {
        Viewer was = viewers.remove(player.getUniqueId());
        if (was != null) was.player().hideBossBar(was.english() ? english : german);
    }

    public void setTitle(String de, String en) {
        setTitle(LEGACY.deserialize(de), LEGACY.deserialize(en));
    }

    public void setTitle(Component de, Component en) {
        german.name(de);
        english.name(en);
    }

    public void setProgress(double progress) {
        float value = (float) Math.max(0, Math.min(1, progress));
        german.progress(value);
        english.progress(value);
    }

    public void setColor(BarColor color) {
        german.color(color(color));
        english.color(color(color));
    }

    public List<Player> getPlayers() {
        List<Player> players = new ArrayList<>();
        for (Viewer viewer : viewers.values()) {
            if (viewer.player().isOnline()) players.add(viewer.player());
        }
        return players;
    }

    public void removeAll() {
        for (Viewer viewer : viewers.values()) viewer.player().hideBossBar(viewer.english() ? english : german);
        viewers.clear();
    }

    private static BossBar.Color color(BarColor color) {
        return switch (color) {
            case PINK -> BossBar.Color.PINK;
            case BLUE -> BossBar.Color.BLUE;
            case RED -> BossBar.Color.RED;
            case GREEN -> BossBar.Color.GREEN;
            case YELLOW -> BossBar.Color.YELLOW;
            case PURPLE -> BossBar.Color.PURPLE;
            case WHITE -> BossBar.Color.WHITE;
        };
    }

    private static BossBar.Overlay overlay(BarStyle style) {
        return switch (style) {
            case SOLID -> BossBar.Overlay.PROGRESS;
            case SEGMENTED_6 -> BossBar.Overlay.NOTCHED_6;
            case SEGMENTED_10 -> BossBar.Overlay.NOTCHED_10;
            case SEGMENTED_12 -> BossBar.Overlay.NOTCHED_12;
            case SEGMENTED_20 -> BossBar.Overlay.NOTCHED_20;
        };
    }
}
