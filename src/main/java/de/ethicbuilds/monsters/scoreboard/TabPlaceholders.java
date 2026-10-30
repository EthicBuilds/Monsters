package de.ethicbuilds.monsters.scoreboard;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/***
 * Round statistics for the tab list like in CoD, layout from the design session (v4).
 * TAB on the proxy shows them: %monsters_tab_kopf_1..7%, %monsters_tab_fuss_1..7%, %monsters_tab_zeile% as tab name
 * and %monsters_sortierung% to sort by points.
 */
public class TabPlaceholders extends PlaceholderExpansion {
    private static final int WIDTH = 337, ROW_END = 324;
    private static final String TEXT = "#F2F5F8", LABEL = "#8A93A3", POINTS = "#F9A11E", HEADSHOTS = "#FF6B6B",
            REVIVES = "#3CDC6E", GOLD = "#FFCC2E", SILVER = "#C8D2DC", BRONZE = "#D08A4E", TEAM = "#49A2E8",
            DOWNED = "#FFB020", GONE = "#E05555", GONE_VALUE = "#5A616E", WEBSITE = "#3BDB9A";
    private static final String[] MEDAL_COLORS = {GOLD, SILVER, BRONZE};

    @Inject private GameManager gameManager;
    @Inject private WaveManager waveManager;
    @Inject private MonsterManager monsterManager;
    @Inject private UserManager userManager;
    @Inject private ScoreboardManager scoreboardManager;

    @Override
    public @NotNull String getIdentifier() {
        return "monsters";
    }

    @Override
    public @NotNull String getAuthor() {
        return "EthicBuilds";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        boolean round = gameManager.getCurrentPhase() != GamePhase.PRE_GAME;
        // Without the Monsters pack the symbols would be boxes, those viewers get plain text
        boolean plain = player != null && !de.ethicbuilds.monsters.player.manager.PackStatus.hasPack(player.getUniqueId());
        if (params.startsWith("tab_kopf_")) return !round ? "" : plain ? plainHead(number(params), english(player)) : head(number(params), english(player));
        if (params.startsWith("tab_fuss_")) return !round ? "" : plain ? plainFoot(number(params), english(player)) : foot(number(params), english(player));
        if (player == null) return "";
        GamePlayer gamePlayer = userManager.isGamePlayer(player.getUniqueId()) ? userManager.getGamePlayers().stream()
                .filter(p -> p.getPlayer().getUniqueId().equals(player.getUniqueId())).findFirst().orElse(null) : null;
        return switch (params) {
            case "tab_zeile" -> round && gamePlayer != null ? row(gamePlayer) : "<color:" + TEXT + ">" + player.getName();
            case "sortierung" -> gamePlayer == null ? "0" : String.valueOf(gone(gamePlayer) ? -1 : gamePlayer.getEarnedCoins());
            case "punkte" -> gamePlayer == null ? "0" : coins(gamePlayer.getEarnedCoins());
            case "platz" -> gamePlayer == null ? "" : String.valueOf(ranked().indexOf(gamePlayer) + 1);
            case "kills" -> gamePlayer == null ? "0" : String.valueOf(gamePlayer.getKilledZombies());
            case "wiederbelebungen" -> gamePlayer == null ? "0" : String.valueOf(gamePlayer.getRevives());
            default -> null;
        };
    }

    // Header and footer are asked for with the viewer
    private static boolean english(OfflinePlayer viewer) {
        Player online = viewer != null ? viewer.getPlayer() : null;
        return online != null && Lang.english(online);
    }

    private static int number(String params) {
        try {
            return Integer.parseInt(params.substring(params.lastIndexOf('_') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Every line with content is exactly 337 px, the rows too (9 head + 315 + 13 ping), so the column heads sit over the values.
    // No text lies on frame glyphs: other clients may draw them after the text.
    private String head(int line, boolean english) {
        return switch (line) {
            case 2 -> new TabLine(0).glyph(0, '\uE030', 169, "white").glyph(168, '\uE031', 170, "white")
                    .glyph(116, '\uE020', 105, "white").end(WIDTH);
            case 4 -> new TabLine(0)
                    .glyph(8, '\uE00B', 9, "white").moveTo(19).small(english ? "MAP" : "KARTE", LABEL)
                    .glyph(162, '\uE00C', 9, "white").moveTo(173).small(english ? "WAVE" : "WELLE", LABEL)
                    .glyph(220, '\uE00D', 9, "white").moveTo(231).small("MONSTER", LABEL)
                    .glyph(288, '\uE00E', 9, "white").moveTo(299).small(english ? "TIME" : "ZEIT", LABEL).end(WIDTH);
            case 5 -> {
                String map = gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";
                int time = scoreboardManager.getTime();
                yield new TabLine(0).text(19, map, TEXT).text(173, String.valueOf(waveManager.getCurrentWave()), TEXT)
                        .text(231, String.valueOf(monsterManager.remaining()), TEXT).gap(3).small(english ? "LEFT" : "\u00DCBRIG", LABEL)
                        .text(299, String.format("%02d:%02d", time / 60, time % 60), TEXT).end(WIDTH);
            }
            case 7 -> {
                TabLine tab = new TabLine(0);
                centered(tab, 9, 21, "#");
                tab.moveTo(27).small(english ? "PLAYER" : "SPIELER", LABEL);
                centered(tab, 133, 187, english ? "POINTS" : "PUNKTE");
                centered(tab, 191, 233, "KILLS");
                centered(tab, 237, 279, "HEADSHOTS");
                centered(tab, 283, 318, english ? "REVIVES" : "RETTUNGEN");
                yield tab.end(WIDTH);
            }
            default -> "";
        };
    }

    private String plainHead(int line, boolean english) {
        String map = gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";
        int time = scoreboardManager.getTime();
        return switch (line) {
            case 2 -> "<bold><gradient:#F9A11E:#8B4BD8>MONSTERS</gradient></bold>";
            case 4 -> String.format("<color:%s>%s</color> <color:%s>%s</color> <color:%s>·</color> <color:%s>%s</color> <color:%s>%d</color> <color:%s>·</color> <color:%s>%d</color> <color:%s>%s</color> <color:%s>·</color> <color:%s>%02d:%02d</color>",
                    LABEL, english ? "Map" : "Karte", TEXT, map.replace("<", ""), LABEL, LABEL, english ? "Wave" : "Welle", TEXT, waveManager.getCurrentWave(),
                    LABEL, TEXT, monsterManager.remaining(), LABEL, english ? "left" : "übrig", LABEL, TEXT, time / 60, time % 60);
            default -> "";
        };
    }

    private String plainFoot(int line, boolean english) {
        List<GamePlayer> ranked = ranked();
        return switch (line) {
            case 2 -> ranked.isEmpty() ? "" : String.format("<color:%s><bold>MVP</bold></color> <color:%s>%s</color> <color:%s>%s</color>",
                    GOLD, TEXT, ranked.getFirst().getPlayer().getName(), POINTS, points(ranked.getFirst().getEarnedCoins()));
            case 6 -> "<color:" + WEBSITE + ">EthicBuilds.de</color>";
            default -> "";
        };
    }

    private static void centered(TabLine tab, int from, int to, String word) {
        tab.moveTo((from + to + 1) / 2 - TabLine.smallWidth(word) / 2).small(word, LABEL);
    }

    private String foot(int line, boolean english) {
        List<GamePlayer> ranked = ranked();
        return switch (line) {
            case 1 -> new TabLine(0).glyph(0, '\uE032', 169, "white").glyph(168, '\uE033', 170, "white").end(WIDTH);
            case 2 -> {
                int kills = ranked.stream().mapToInt(GamePlayer::getKilledZombies).sum();
                int headshots = ranked.stream().mapToInt(GamePlayer::getHeadshots).sum();
                int revives = ranked.stream().mapToInt(GamePlayer::getRevives).sum();
                yield new TabLine(0).glyph(8, '\uE008', 9, TEAM).gap(2).bold("TEAM", TEAM).gap(5)
                        .glyph('\uE000', 8, TEXT).gap(1).text(count(kills), TEXT).gap(6)
                        .glyph('\uE001', 9, HEADSHOTS).gap(1).text(count(headshots), HEADSHOTS).gap(6)
                        .glyph('\uE002', 8, REVIVES).gap(1).text(String.valueOf(revives), REVIVES).end(WIDTH);
            }
            case 3 -> ranked.isEmpty() ? "" : new TabLine(0).glyph(8, '\uE009', 9, GOLD).gap(2).bold("MVP", GOLD).gap(4)
                    .text(ranked.getFirst().getPlayer().getName(), TEXT).gap(6)
                    .glyph('\uE00A', 9, "white").gap(1).text(points(ranked.getFirst().getEarnedCoins()), POINTS).gap(4)
                    .small(english ? "MOST POINTS" : "MEISTE PUNKTE", LABEL).end(WIDTH);
            case 5 -> {
                char[] glyphs = {'\uE00A', '\uE000', '\uE001', '\uE002', '\uE006', '\uE007'};
                int[] advances = {9, 8, 9, 8, 8, 9};
                String[] colors = {"white", TEXT, HEADSHOTS, REVIVES, DOWNED, GONE};
                String[] words = english ? new String[]{"POINTS", "KILLS", "HEADSHOTS", "REVIVES", "DOWNED", "LEFT GAME"}
                        : new String[]{"PUNKTE", "KILLS", "HEADSHOTS", "RETTUNGEN", "AM BODEN", "VERLASSEN"};
                int width = 6 * (words.length - 1);
                for (int i = 0; i < words.length; i++) width += advances[i] + 2 + TabLine.smallWidth(words[i]);
                TabLine tab = new TabLine(0).moveTo((WIDTH - width) / 2);
                for (int i = 0; i < words.length; i++) {
                    if (i > 0) tab.gap(6);
                    tab.glyph(glyphs[i], advances[i], colors[i]).gap(2).small(words[i], LABEL);
                }
                yield tab.end(WIDTH);
            }
            case 6 -> new TabLine(0).text((WIDTH - TabLine.width("EthicBuilds.de")) / 2, "EthicBuilds.de", WEBSITE).end(WIDTH);
            default -> "";
        };
    }

    // The player's own line: from the head (x 9) to x 324, column lines first, then the values over the plain background
    String row(GamePlayer gamePlayer) {
        List<GamePlayer> ranked = ranked();
        int place = ranked.indexOf(gamePlayer) + 1;
        boolean gone = gone(gamePlayer);
        boolean downed = !gone && userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId());

        TabLine tab = new TabLine(9);
        tab.glyph(0, '\uE044', 133, "white").glyph(168, '\uE045', 115, "white");

        // The place field shows the status, otherwise the medal or the place
        if (gone) tab.glyph(11, '\uE007', 9, GONE);
        else if (downed) tab.glyph(11, '\uE006', 8, DOWNED);
        else if (place >= 1 && place <= 3) tab.glyph(11, (char) (0xE002 + place), 8, MEDAL_COLORS[place - 1]);
        else tab.text(15 - (TabLine.width(String.valueOf(place)) - 1) / 2, String.valueOf(place), LABEL);

        tab.text(27, gamePlayer.getPlayer().getName(), gone ? GONE : downed ? LABEL : TEXT);
        tab.glyph(134, '\uE00A', 9, "white").textRight(187, points(gamePlayer.getEarnedCoins()), gone ? GONE_VALUE : POINTS);
        tab.glyph(192, '\uE000', 8, gone ? GONE_VALUE : TEXT).textRight(233, count(gamePlayer.getKilledZombies()), gone ? GONE_VALUE : TEXT);
        tab.glyph(238, '\uE001', 9, gone ? GONE_VALUE : HEADSHOTS).textRight(279, count(gamePlayer.getHeadshots()), gone ? GONE_VALUE : HEADSHOTS);
        tab.glyph(284, '\uE002', 8, gone ? GONE_VALUE : REVIVES).textRight(318, String.valueOf(gamePlayer.getRevives()), gone ? GONE_VALUE : REVIVES);
        return tab.end(ROW_END);
    }

    // By points, who left at the bottom
    private List<GamePlayer> ranked() {
        return userManager.getGamePlayers().stream()
                .sorted(Comparator.comparing(TabPlaceholders::gone).thenComparing(Comparator.comparingInt(GamePlayer::getEarnedCoins).reversed()))
                .toList();
    }

    private static boolean gone(GamePlayer gamePlayer) {
        return !gamePlayer.getPlayer().isOnline();
    }

    private static String coins(int coins) {
        return String.format(Locale.GERMANY, "%,d", coins);
    }

    // Short forms so the fields fit: up to 999.999 points, then 1,2M
    private static String points(int points) {
        return points < 1_000_000 ? coins(points) : String.format(Locale.GERMANY, "%.1fM", points / 100_000 / 10.0);
    }

    // Up to 9.999, from 10.000 on 12k
    private static String count(int count) {
        return count < 10_000 ? coins(count) : count / 1000 + "k";
    }
}
