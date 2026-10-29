package de.ethicbuilds.monsters.scoreboard;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/***
 * Round statistics for the tab list like in CoD, layout from the design session (always 320 px wide).
 * TAB on the proxy shows them: %monsters_tab_kopf_1..7%, %monsters_tab_fuss_1..7%, %monsters_tab_zeile% as tab name
 * and %monsters_sortierung% to sort by points.
 */
public class TabPlaceholders extends PlaceholderExpansion {
    private static final int WIDTH = 320, ROW_END = 307;
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
        if (params.startsWith("tab_kopf_")) return round ? head(number(params)) : "";
        if (params.startsWith("tab_fuss_")) return round ? foot(number(params)) : "";
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

    private static int number(String params) {
        try {
            return Integer.parseInt(params.substring(params.lastIndexOf('_') + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String head(int line) {
        return switch (line) {
            case 1 -> new TabLine(0).glyph(0, '', 161, "white").glyph(160, '', 161, "white")
                    .glyph(108, '', 105, "white").end(WIDTH);
            case 4 -> new TabLine(0)
                    .glyph(11, '', 17, "white").small(30, "KARTE", LABEL)
                    .glyph(135, '', 17, "white").small(154, "WELLE", LABEL)
                    .glyph(197, '', 17, "white").small(216, "MONSTER", LABEL)
                    .glyph(259, '', 17, "white").small(278, "ZEIT", LABEL).end(WIDTH);
            case 5 -> {
                String map = gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";
                int time = scoreboardManager.getTime();
                yield new TabLine(0).text(30, map, TEXT).text(154, String.valueOf(waveManager.getCurrentWave()), TEXT)
                        .text(216, monsterManager.remaining() + " übrig", TEXT)
                        .text(278, String.format("%02d:%02d", time / 60, time % 60), TEXT).end(WIDTH);
            }
            case 7 -> new TabLine(0).small(13, "#", LABEL).small(26, "SPIELER", LABEL)
                    .smallCentered(148, "PUNKTE", LABEL).smallCentered(189, "KILLS", LABEL)
                    .smallCentered(226, "HEADSHOTS", LABEL).smallCentered(266, "RETTUNGEN", LABEL).end(WIDTH);
            default -> "";
        };
    }

    private String foot(int line) {
        List<GamePlayer> ranked = ranked();
        return switch (line) {
            case 1 -> new TabLine(0).glyph(0, '', 161, "white").glyph(160, '', 161, "white").end(WIDTH);
            case 2 -> {
                long playing = ranked.stream().filter(p -> !gone(p)).count();
                TabLine tab = new TabLine(0).glyph(11, '', 17, TEAM).bold(30, "TEAM", TEAM);
                tab.small(tab.x() + 5, playing + " SPIELER", LABEL);
                if (!ranked.isEmpty()) {
                    tab.glyph(171, '', 17, GOLD).bold(190, "MVP", GOLD);
                    tab.text(tab.x() + 4, ranked.getFirst().getPlayer().getName(), TEXT);
                }
                yield tab.end(WIDTH);
            }
            case 3 -> {
                int kills = ranked.stream().mapToInt(GamePlayer::getKilledZombies).sum();
                int headshots = ranked.stream().mapToInt(GamePlayer::getHeadshots).sum();
                int revives = ranked.stream().mapToInt(GamePlayer::getRevives).sum();
                TabLine tab = new TabLine(0);
                tab.glyph(30, '', 8, TEXT).text(tab.x() + 2, String.valueOf(kills), TEXT);
                tab.glyph(74, '', 9, HEADSHOTS).text(tab.x() + 2, String.valueOf(headshots), HEADSHOTS);
                tab.glyph(118, '', 8, REVIVES).text(tab.x() + 2, String.valueOf(revives), REVIVES);
                if (!ranked.isEmpty()) {
                    tab.glyph(190, '', 9, "white").text(200, coins(ranked.getFirst().getEarnedCoins()), POINTS);
                    tab.small(tab.x() + 5, "MEISTE PUNKTE", LABEL);
                }
                yield tab.end(WIDTH);
            }
            case 5 -> {
                TabLine tab = new TabLine(0).moveTo(34);
                legend(tab, '', 8, TEXT, "KILLS");
                legend(tab, '', 9, HEADSHOTS, "HEADSHOTS");
                legend(tab, '', 8, REVIVES, "RETTUNGEN");
                legend(tab, '', 8, DOWNED, "AM BODEN");
                tab.glyph(tab.x(), '', 9, GONE).small(tab.x() + 2, "VERLASSEN", LABEL);
                yield tab.end(WIDTH);
            }
            case 6 -> new TabLine(0).text((WIDTH - TabLine.width("EthicBuilds.de")) / 2, "EthicBuilds.de", WEBSITE).end(WIDTH);
            default -> "";
        };
    }

    private static void legend(TabLine tab, char glyph, int advance, String color, String word) {
        tab.glyph(tab.x(), glyph, advance, color).small(tab.x() + 2, word, LABEL);
        tab.moveTo(tab.x() + 8);
    }

    // The player's own line: name starts at x 9 after the head, the line ends at x 307
    private String row(GamePlayer gamePlayer) {
        List<GamePlayer> ranked = ranked();
        int place = ranked.indexOf(gamePlayer) + 1;
        boolean gone = gone(gamePlayer);
        boolean downed = !gone && userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId());

        TabLine tab = new TabLine(9);
        tab.glyph(0, (char) (place >= 1 && place <= 3 && !gone ? 0xE040 + place : 0xE040), 23, "white");
        char rest = gone ? '' : downed ? '' : '';
        tab.glyph(23, rest, 138, "white").glyph(160, (char) (rest + 1), 161, "white");

        if (place >= 1 && place <= 3 && !gone) tab.glyph(12, (char) (0xE002 + place), 8, MEDAL_COLORS[place - 1]);
        else tab.text(15 - (TabLine.width(String.valueOf(place)) - 1) / 2, String.valueOf(place), LABEL);

        tab.text(26, gamePlayer.getPlayer().getName(), gone ? GONE : downed ? LABEL : TEXT);
        tab.glyph(126, '', 9, "white").textRight(169, coins(gamePlayer.getEarnedCoins()), gone ? GONE_VALUE : POINTS);
        tab.glyph(175, '', 8, gone ? GONE_VALUE : TEXT).textRight(202, String.valueOf(gamePlayer.getKilledZombies()), gone ? GONE_VALUE : TEXT);
        tab.glyph(208, '', 9, gone ? GONE_VALUE : HEADSHOTS).textRight(243, String.valueOf(gamePlayer.getHeadshots()), gone ? GONE_VALUE : HEADSHOTS);
        tab.glyph(249, '', 8, gone ? GONE_VALUE : REVIVES).textRight(282, String.valueOf(gamePlayer.getRevives()), gone ? GONE_VALUE : REVIVES);
        if (gone) tab.glyph(291, '', 9, GONE);
        else if (downed) tab.glyph(291, '', 8, DOWNED);
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
}
