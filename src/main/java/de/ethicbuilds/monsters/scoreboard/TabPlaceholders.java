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
 * Round statistics for the tab list like in CoD, layout from the design session (v3).
 * TAB on the proxy shows them: %monsters_tab_kopf_1..5%, %monsters_tab_fuss_1..8%, %monsters_tab_zeile% as tab name
 * and %monsters_sortierung% to sort by points.
 */
public class TabPlaceholders extends PlaceholderExpansion {
    private static final int ROW_END = 247;
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
        if (params.startsWith("tab_kopf_")) return round ? head(number(params), english(player)) : "";
        if (params.startsWith("tab_fuss_")) return round ? foot(number(params), english(player)) : "";
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

    // Every header and footer line is centered on its own and at most 150 px wide (other clients cut wider ones)
    private String head(int line, boolean english) {
        return switch (line) {
            case 1 -> new TabLine(0).glyph('', 151, "white").gap(-129).glyph('', 105, "white").gap(23).end();
            case 4 -> {
                String map = gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";
                yield new TabLine(0).glyph('', 9, "white").gap(2).small(english ? "MAP" : "KARTE", LABEL).gap(4).text(map, TEXT).end();
            }
            case 5 -> {
                int time = scoreboardManager.getTime();
                yield new TabLine(0).glyph('', 9, "white").gap(2).small(english ? "WAVE" : "WELLE", LABEL).gap(3)
                        .text(String.valueOf(waveManager.getCurrentWave()), TEXT).gap(5)
                        .glyph('', 9, "white").gap(2).text(String.valueOf(monsterManager.remaining()), TEXT).gap(3)
                        .small(english ? "LEFT" : "ÜBRIG", LABEL).gap(5)
                        .glyph('', 9, "white").gap(2).text(String.format("%02d:%02d", time / 60, time % 60), TEXT).end();
            }
            default -> "";
        };
    }

    private String foot(int line, boolean english) {
        List<GamePlayer> ranked = ranked();
        return switch (line) {
            case 1 -> {
                int kills = ranked.stream().mapToInt(GamePlayer::getKilledZombies).sum();
                int headshots = ranked.stream().mapToInt(GamePlayer::getHeadshots).sum();
                int revives = ranked.stream().mapToInt(GamePlayer::getRevives).sum();
                // Measured once at 0, then drawn centered over the footer board
                TabLine team = team(new TabLine(0), kills, headshots, revives);
                TabLine tab = new TabLine(0).glyph('', 151, "white").moveTo((150 - team.x()) / 2);
                yield team(tab, kills, headshots, revives).end(150);
            }
            case 2 -> ranked.isEmpty() ? "" : new TabLine(0).glyph('', 9, GOLD).gap(2).bold("MVP", GOLD).gap(4)
                    .text(ranked.getFirst().getPlayer().getName(), TEXT).end();
            case 3 -> ranked.isEmpty() ? "" : new TabLine(0).glyph('', 9, "white").gap(1).text(points(ranked.getFirst().getEarnedCoins()), POINTS)
                    .gap(4).small(english ? "MOST POINTS" : "MEISTE PUNKTE", LABEL).end();
            case 4 -> legend(new TabLine(0), '', 9, "white", english ? "POINTS" : "PUNKTE").gap(6)
                    .glyph('', 8, TEXT).gap(2).small("KILLS", LABEL).end();
            case 5 -> legend(new TabLine(0), '', 9, HEADSHOTS, "HEADSHOTS").gap(6)
                    .glyph('', 8, REVIVES).gap(2).small(english ? "REVIVES" : "RETTUNGEN", LABEL).end();
            case 6 -> legend(new TabLine(0), '', 8, DOWNED, english ? "DOWNED" : "AM BODEN").gap(6)
                    .glyph('', 9, GONE).gap(2).small(english ? "LEFT GAME" : "VERLASSEN", LABEL).end();
            case 7 -> new TabLine(0).text("EthicBuilds.de", WEBSITE).end();
            default -> "";
        };
    }

    private static TabLine team(TabLine tab, int kills, int headshots, int revives) {
        return tab.glyph('', 9, TEAM).gap(2).bold("TEAM", TEAM).gap(5)
                .glyph('', 8, TEXT).gap(1).text(count(kills), TEXT).gap(4)
                .glyph('', 9, HEADSHOTS).gap(1).text(count(headshots), HEADSHOTS).gap(4)
                .glyph('', 8, REVIVES).gap(1).text(String.valueOf(Math.min(99, revives)), REVIVES);
    }

    private static TabLine legend(TabLine tab, char glyph, int advance, String color, String word) {
        return tab.glyph(glyph, advance, color).gap(2).small(word, LABEL);
    }

    // The player's own line, 238 px from the head (x 9) to x 247, the ping follows
    String row(GamePlayer gamePlayer) {
        List<GamePlayer> ranked = ranked();
        int place = ranked.indexOf(gamePlayer) + 1;
        boolean gone = gone(gamePlayer);
        boolean downed = !gone && userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId());
        boolean medal = place >= 1 && place <= 3 && !gone;

        TabLine tab = new TabLine(9);
        tab.glyph(0, (char) (medal ? 0xE040 + place : 0xE040), 21, "white");
        tab.glyph(21, gone ? '' : downed ? '' : '', 240, "white");

        // The place field shows the status, otherwise the medal or the place
        if (gone) tab.glyph(10, '', 9, GONE);
        else if (downed) tab.glyph(10, '', 8, DOWNED);
        else if (medal) tab.glyph(10, (char) (0xE002 + place), 8, MEDAL_COLORS[place - 1]);
        else tab.text(15 - (TabLine.width(String.valueOf(place)) - 1) / 2, String.valueOf(place), LABEL);

        tab.text(23, gamePlayer.getPlayer().getName(), gone ? GONE : downed ? LABEL : TEXT);
        tab.glyph(115, '', 9, "white").textRight(157, points(gamePlayer.getEarnedCoins()), gone ? GONE_VALUE : POINTS);
        tab.glyph(161, '', 8, gone ? GONE_VALUE : TEXT).textRight(189, count(gamePlayer.getKilledZombies()), gone ? GONE_VALUE : TEXT);
        tab.glyph(193, '', 9, gone ? GONE_VALUE : HEADSHOTS).textRight(221, count(gamePlayer.getHeadshots()), gone ? GONE_VALUE : HEADSHOTS);
        tab.glyph(225, '', 8, gone ? GONE_VALUE : REVIVES).textRight(245, String.valueOf(Math.min(99, gamePlayer.getRevives())), gone ? GONE_VALUE : REVIVES);
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

    // Short forms so the fields fit: up to 99.999 points, then 317k
    private static String points(int points) {
        return points < 100_000 ? coins(points) : points / 1000 + "k";
    }

    // Up to 999, then 1,8k, from 10.000 on 12k
    private static String count(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 10_000) return String.format(Locale.GERMANY, "%.1fk", count / 100 / 10.0);
        return count / 1000 + "k";
    }
}
