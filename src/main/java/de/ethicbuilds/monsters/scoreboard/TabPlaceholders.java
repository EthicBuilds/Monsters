package de.ethicbuilds.monsters.scoreboard;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.PackStatus;
import de.ethicbuilds.monsters.player.manager.UserManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/***
 * Round statistics for the tab list like in CoD, layout from the design session (v5).
 * TAB on the proxy shows them: %monsters_tab_kopf_1..8%, %monsters_tab_fuss_1..7%, %monsters_tab_zeile% as tab name
 * and %monsters_sortierung% to sort by points.
 */
public class TabPlaceholders extends PlaceholderExpansion {
    private static final int WIDTH = 355, ROW_END = 342;
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
        boolean plain = player != null && !PackStatus.hasPack(player.getUniqueId());
        if (params.startsWith("tab_kopf_")) {
            int line = number(params);
            if (!round) return plain ? plainWaitHead(line, english(player)) : waitHead(line, english(player));
            return plain ? plainHead(line, english(player)) : head(line, english(player));
        }
        if (params.startsWith("tab_fuss_")) {
            int line = number(params);
            if (!round) return plain ? plainWaitFoot(line, english(player)) : waitFoot(line, english(player));
            return plain ? plainFoot(line, english(player)) : foot(line, english(player));
        }
        if (player == null) return "";
        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        return switch (params) {
            case "tab_zeile" -> gamePlayer == null ? "<color:" + TEXT + ">" + player.getName() : round ? row(gamePlayer) : waitRow(gamePlayer);
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

    // Every line with content is exactly 355 px, the rows too (9 head + 333 + 13 ping), so the column heads sit over the values.
    // Only lines as decoration, never under text; 4 px between elements, 8 px between groups (design session, v5).
    private String head(int line, boolean english) {
        return switch (line) {
            case 2, 7 -> rule();
            case 3 -> new TabLine(0).glyph(125, '\uE021', 105, "white").end(WIDTH);
            case 6 -> centered(tab -> {
                String map = gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";
                int time = scoreboardManager.getTime();
                tab.glyph('\uE00B', 9, "white").gap(2).small(english ? "MAP" : "KARTE", LABEL).gap(4).text(map, TEXT).gap(8)
                        .glyph('\uE00C', 9, "white").gap(2).small(english ? "WAVE" : "WELLE", LABEL).gap(4).text(String.valueOf(waveManager.getCurrentWave()), TEXT).gap(8)
                        .glyph('\uE00D', 9, "white").gap(2).text(String.valueOf(monsterManager.remaining()), TEXT).gap(4).small(english ? "LEFT" : "\u00DCBRIG", LABEL).gap(8)
                        .glyph('\uE00E', 9, "white").gap(2).text(String.format("%02d:%02d", time / 60, time % 60), TEXT);
            });
            case 8 -> {
                TabLine tab = new TabLine(0).moveTo(24).small(english ? "PLAYER" : "SPIELER", LABEL);
                column(tab, 180, '\uE00A', 9, "white", english ? "POINTS" : "PUNKTE");
                column(tab, 228, '\uE000', 8, LABEL, "KILLS");
                column(tab, 284, '\uE001', 9, LABEL, "HEADSHOTS");
                column(tab, 340, '\uE002', 8, LABEL, english ? "REVIVES" : "RETTUNGEN");
                yield tab.end(WIDTH);
            }
            default -> "";
        };
    }

    private static String rule() {
        return new TabLine(0).glyph(0, '\uE050', 179, "white").glyph(178, '\uE051', 177, "white").end(WIDTH);
    }

    // Column head: symbol and word, ending at the end of the column like the values below
    private static void column(TabLine tab, int end, char glyph, int advance, String color, String word) {
        int width = advance + 2 + TabLine.smallWidth(word);
        tab.glyph(end - width + 2, glyph, advance, color).gap(2).small(word, LABEL);
    }

    // Drawn once to measure, then again in the middle of the 355 px
    private static String centered(Consumer<TabLine> draw) {
        TabLine measure = new TabLine(0);
        draw.accept(measure);
        TabLine tab = new TabLine(0).moveTo((WIDTH - measure.x()) / 2);
        draw.accept(tab);
        return tab.end(WIDTH);
    }

    private String foot(int line, boolean english) {
        List<GamePlayer> ranked = ranked();
        return switch (line) {
            case 1, 7 -> rule();
            case 2 -> {
                int kills = ranked.stream().mapToInt(GamePlayer::getKilledZombies).sum();
                int headshots = ranked.stream().mapToInt(GamePlayer::getHeadshots).sum();
                int revives = ranked.stream().mapToInt(GamePlayer::getRevives).sum();
                yield centered(tab -> tab.glyph('\uE008', 9, TEAM).gap(2).bold("TEAM", TEAM).gap(8)
                        .glyph('\uE000', 8, TEXT).gap(2).text(count(kills), TEXT).gap(8)
                        .glyph('\uE001', 9, HEADSHOTS).gap(2).text(count(headshots), HEADSHOTS).gap(8)
                        .glyph('\uE002', 8, REVIVES).gap(2).text(String.valueOf(revives), REVIVES));
            }
            case 3 -> ranked.isEmpty() ? "" : centered(tab -> tab.glyph('\uE009', 9, GOLD).gap(2).bold("MVP", GOLD).gap(4)
                    .text(ranked.getFirst().getPlayer().getName(), TEXT).gap(8)
                    .glyph('\uE00A', 9, "white").gap(2).text(points(ranked.getFirst().getEarnedCoins()), POINTS).gap(4)
                    .small(english ? "MOST POINTS" : "MEISTE PUNKTE", LABEL));
            case 5 -> {
                char[] glyphs = {'\uE00A', '\uE000', '\uE001', '\uE002', '\uE006', '\uE007'};
                int[] advances = {9, 8, 9, 8, 8, 9};
                String[] colors = {"white", TEXT, HEADSHOTS, REVIVES, DOWNED, GONE};
                String[] words = english ? new String[]{"POINTS", "KILLS", "HEADSHOTS", "REVIVES", "DOWNED", "LEFT GAME"}
                        : new String[]{"PUNKTE", "KILLS", "HEADSHOTS", "RETTUNGEN", "AM BODEN", "VERLASSEN"};
                yield centered(tab -> {
                    for (int i = 0; i < words.length; i++) {
                        if (i > 0) tab.gap(8);
                        tab.glyph(glyphs[i], advances[i], colors[i]).gap(2).small(words[i], LABEL);
                    }
                });
            }
            case 6 -> centered(tab -> tab.text("EthicBuilds.de", WEBSITE));
            default -> "";
        };
    }

    // The player's own line: from the head (x 9) to x 342, only the place symbol and text, values under their column heads
    String row(GamePlayer gamePlayer) {
        List<GamePlayer> ranked = ranked();
        int place = ranked.indexOf(gamePlayer) + 1;
        boolean gone = gone(gamePlayer);
        boolean downed = !gone && userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId());

        TabLine tab = new TabLine(9);
        // The place field shows the status, otherwise the medal or the place
        if (gone) tab.glyph(12, '\uE007', 9, GONE);
        else if (downed) tab.glyph(12, '\uE006', 8, DOWNED);
        else if (place >= 1 && place <= 3) tab.glyph(12, (char) (0xE002 + place), 8, MEDAL_COLORS[place - 1]);
        else tab.text(16 - (TabLine.width(String.valueOf(place)) - 1) / 2, String.valueOf(place), LABEL);

        tab.text(24, gamePlayer.getPlayer().getName(), gone ? GONE : downed ? LABEL : TEXT);
        tab.textRight(180, points(gamePlayer.getEarnedCoins()), gone ? GONE_VALUE : POINTS);
        tab.textRight(228, count(gamePlayer.getKilledZombies()), gone ? GONE_VALUE : TEXT);
        tab.textRight(284, count(gamePlayer.getHeadshots()), gone ? GONE_VALUE : HEADSHOTS);
        tab.textRight(340, String.valueOf(gamePlayer.getRevives()), gone ? GONE_VALUE : REVIVES);
        return tab.end(ROW_END);
    }

    // Waiting for players: logo, map, players and start, then the names under the column head
    private String waitHead(int line, boolean english) {
        return switch (line) {
            case 2, 7 -> rule();
            case 3 -> new TabLine(0).glyph(125, '', 105, "white").end(WIDTH);
            case 6 -> centered(tab -> {
                tab.glyph('', 9, "white").gap(2).small(english ? "MAP" : "KARTE", LABEL).gap(4).text(mapName(), TEXT).gap(8)
                        .glyph('', 9, "white").gap(2).small(english ? "PLAYERS" : "SPIELER", LABEL).gap(4)
                        .text(userManager.getGamePlayers().size() + "/" + playerCount(), TEXT).gap(8)
                        .glyph('', 9, "white").gap(2);
                int startIn = gameManager.getStartIn();
                if (startIn > 0) tab.small("START IN", LABEL).gap(4).text(startIn + "s", REVIVES);
                else tab.small(english ? "WAITING" : "WARTEN", LABEL);
            });
            case 8 -> new TabLine(0).moveTo(24).small(english ? "PLAYER" : "SPIELER", LABEL).end(WIDTH);
            default -> "";
        };
    }

    private String waitFoot(int line, boolean english) {
        return switch (line) {
            case 1, 7 -> rule();
            case 2 -> centered(tab -> {
                int startIn = gameManager.getStartIn();
                if (startIn > 0) {
                    tab.bold("START IN " + startIn + "s", REVIVES);
                    return;
                }
                int missing = Math.max(0, playerCount() - userManager.getGamePlayers().size());
                tab.glyph('', 9, TEAM).gap(2).small(english ? "WAITING FOR" : "WARTEN AUF", LABEL).gap(4).text(String.valueOf(missing), TEXT)
                        .gap(4).small(english ? (missing == 1 ? "PLAYER" : "PLAYERS") : "SPIELER", LABEL);
            });
            case 3 -> centered(tab -> tab.small(english ? "INVITE PLAYERS WITH JOINME" : "MIT JOINME MITSPIELER EINLADEN", LABEL));
            case 6 -> centered(tab -> tab.text("EthicBuilds.de", WEBSITE));
            default -> "";
        };
    }

    // Before the round only the team symbol and the name
    private static String waitRow(GamePlayer gamePlayer) {
        return new TabLine(9).glyph(12, '', 9, TEAM).text(24, gamePlayer.getPlayer().getName(), TEXT).end(ROW_END);
    }

    private String plainWaitHead(int line, boolean english) {
        return switch (line) {
            case 3 -> "<bold><gradient:#F9A11E:#8B4BD8>MONSTERS</gradient></bold>";
            case 6 -> String.format("<color:%s>%s</color> <color:%s>%s</color> <color:%s>·</color> <color:%s>%s</color> <color:%s>%d/%d</color>",
                    LABEL, english ? "Map" : "Karte", TEXT, mapName().replace("<", ""), LABEL, LABEL, english ? "Players" : "Spieler", TEXT,
                    userManager.getGamePlayers().size(), playerCount());
            default -> "";
        };
    }

    private String plainWaitFoot(int line, boolean english) {
        int startIn = gameManager.getStartIn();
        int missing = Math.max(0, playerCount() - userManager.getGamePlayers().size());
        return switch (line) {
            case 2 -> startIn > 0 ? String.format("<color:%s><bold>Start in %ds</bold></color>", REVIVES, startIn)
                    : String.format("<color:%s>%s</color> <color:%s>%d</color> <color:%s>%s</color>", LABEL, english ? "Waiting for" : "Warten auf",
                    TEXT, missing, LABEL, english ? (missing == 1 ? "player" : "players") : "Spieler");
            case 6 -> "<color:" + WEBSITE + ">EthicBuilds.de</color>";
            default -> "";
        };
    }

    private String mapName() {
        return gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";
    }

    private int playerCount() {
        return gameManager.getGameConfig() != null ? gameManager.getGameConfig().getPlayerCount() : 0;
    }

    // Without the Monsters pack: plain text in the lines where the logo and the info line are
    private String plainHead(int line, boolean english) {
        String map = gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";
        int time = scoreboardManager.getTime();
        return switch (line) {
            case 3 -> "<bold><gradient:#F9A11E:#8B4BD8>MONSTERS</gradient></bold>";
            case 6 -> String.format("<color:%s>%s</color> <color:%s>%s</color> <color:%s>\u00B7</color> <color:%s>%s</color> <color:%s>%d</color> <color:%s>\u00B7</color> <color:%s>%d</color> <color:%s>%s</color> <color:%s>\u00B7</color> <color:%s>%02d:%02d</color>",
                    LABEL, english ? "Map" : "Karte", TEXT, map.replace("<", ""), LABEL, LABEL, english ? "Wave" : "Welle", TEXT, waveManager.getCurrentWave(),
                    LABEL, TEXT, monsterManager.remaining(), LABEL, english ? "left" : "\u00FCbrig", LABEL, TEXT, time / 60, time % 60);
            default -> "";
        };
    }

    private String plainFoot(int line, boolean english) {
        List<GamePlayer> ranked = ranked();
        return switch (line) {
            case 3 -> ranked.isEmpty() ? "" : String.format("<color:%s><bold>MVP</bold></color> <color:%s>%s</color> <color:%s>%s</color>",
                    GOLD, TEXT, ranked.getFirst().getPlayer().getName(), POINTS, points(ranked.getFirst().getEarnedCoins()));
            case 6 -> "<color:" + WEBSITE + ">EthicBuilds.de</color>";
            default -> "";
        };
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
