package de.ethicbuilds.monsters.scoreboard;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/***
 * Sidebar in the style of the network scoreboard (design session): labels left, values right, no red numbers.
 * Detailed again (user 02.11.) with the symbols and colours of the tab list: map, wave, left, time, the own coins
 * and kills, then the team with their coins. One board per player.
 */
public class ScoreboardManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "<bold><gradient:#F9A11E:#8B4BD8>MONSTERS</gradient></bold>";
    // 8 px margin behind every value
    private static final String MARGIN = "<font:monsters:hud></font>";
    private static final String COIN = " <white><font:monsters:hud></font></white>";
    // Colours and symbols of the tab list (TabPlaceholders, monsters:tab)
    private static final String TEXT = "<#F2F5F8>", LABEL = "<#8A93A3>", POINTS = "<#F9A11E>", DOWNED = "<#FFB020>", GONE = "<#E05555>";
    private static final char MAP = '\uE00B', WAVE = '\uE00C', LEFT = '\uE00D', TIME = '\uE00E', COINS = '\uE00A', KILLS = '\uE000',
            DOWNED_ICON = '\uE006', GONE_ICON = '\uE007';
    private static final int MAX_LINES = 15;
    private static final String WEBSITE = gradientLetters("EthicBuilds.de",
            "22D3E6", "0DD6D3", "28D7BD", "4AD7A5", "6AD48B", "89CF6F", "A5C953", "BEC136", "D5B718", "E9AC04", "F9A11E");

    @Inject private UserManager userManager;
    @Inject private WaveManager waveManager;
    @Inject private MonsterManager monsterManager;
    @Inject private GameManager gameManager;
    @Inject private Main plugin;

    // Wall clock start of the round, 0 before it
    private volatile long startedAt;

    private final Map<UUID, Board> boards = new HashMap<>();

    private record Line(Component left, Component right) {}

    private record Board(Scoreboard scoreboard, Objective objective, List<String> entries) {}

    public void init() {
        new BukkitRunnable() {
            @Override
            public void run() {
                update();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** The own board of the player */
    public Scoreboard getScoreboard(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(), uuid -> {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective sidebar = scoreboard.registerNewObjective("monsters", Criteria.DUMMY, MM.deserialize(TITLE));
            sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
            sidebar.numberFormat(NumberFormat.blank());
            return new Board(scoreboard, sidebar, new ArrayList<>());
        }).scoreboard();
    }

    /** Seconds since the round started */
    public int getTime() {
        // Counts from 1 like the old second counter did
        return startedAt == 0 ? 0 : (int) ((System.currentTimeMillis() - startedAt) / 1000) + 1;
    }

    public void startTime() {
        startedAt = System.currentTimeMillis();
    }

    private void update() {
        boards.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Board board = boards.get(player.getUniqueId());
            // Only players that show a Monsters board
            if (board == null || player.getScoreboard() != board.scoreboard()) continue;
            render(board, lines(player));
        }
    }

    private List<Line> lines(Player player) {
        boolean english = Lang.english(player);
        String map = gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";
        int time = getTime();
        List<Line> top = new ArrayList<>();
        top.add(line(MAP, english ? "Map" : "Karte", TEXT + "<map>", map));
        top.add(line(WAVE, english ? "Wave" : "Welle", POINTS + waveManager.getCurrentWave(), ""));
        top.add(line(LEFT, english ? "Left" : "Übrig", TEXT + monsterManager.remaining(), ""));
        top.add(line(TIME, english ? "Time" : "Zeit", TEXT + String.format("%02d:%02d", time / 60, time % 60), ""));

        List<Line> own = new ArrayList<>();
        GamePlayer self = userManager.getGamePlayer(player.getUniqueId());
        if (self != null) {
            own.add(line(COINS, "Coins", POINTS + coins(self, english) + COIN, ""));
            own.add(line(KILLS, "Kills", TEXT + self.getKilledZombies(), ""));
        }

        List<Line> team = new ArrayList<>();
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            if (gamePlayer == self) continue;
            team.add(teammate(gamePlayer, english));
        }

        // 15 lines at most: first the gaps go, then the team list is cut with "+N"
        List<Line> website = List.of(new Line(MM.deserialize(WEBSITE), Component.empty()));
        int fixed = top.size() + own.size() + website.size();
        int gaps = 4;
        while (gaps > 0 && fixed + gaps + team.size() > MAX_LINES) gaps--;
        int room = MAX_LINES - fixed - gaps;
        if (team.size() > room) {
            int hidden = team.size() - room + 1;
            team = new ArrayList<>(team.subList(0, Math.max(0, room - 1)));
            team.add(new Line(MM.deserialize(LABEL + "+" + hidden + (english ? " more" : " weitere")), Component.empty()));
        }
        List<Line> lines = new ArrayList<>();
        if (gaps-- > 0) lines.add(empty());
        lines.addAll(top);
        if (!own.isEmpty() && gaps-- > 0) lines.add(empty());
        lines.addAll(own);
        if (!team.isEmpty() && gaps-- > 0) lines.add(empty());
        lines.addAll(team);
        if (gaps > 0) lines.add(empty());
        lines.addAll(website);
        return lines;
    }

    // Teammate with his coins; downed with the tab symbol, left red and struck through
    private Line teammate(GamePlayer gamePlayer, boolean english) {
        Player teammate = gamePlayer.getPlayer();
        boolean gone = !teammate.isOnline();
        boolean downed = !gone && userManager.isPlayerDead(teammate.getUniqueId());
        String icon = gone ? GONE + tab(GONE_ICON) + " " : downed ? DOWNED + tab(DOWNED_ICON) + " " : "";
        String name = gone ? GONE + "<st><name></st>" : downed ? "<#9AA6B8><name>" : TEXT + "<name>";
        Component left = MM.deserialize(icon + name, Placeholder.unparsed("name", teammate.getName()));
        Component right = MM.deserialize((gone ? GONE : POINTS) + coins(gamePlayer, english) + COIN + MARGIN);
        return new Line(left, right);
    }

    private static String coins(GamePlayer gamePlayer, boolean english) {
        return String.format(english ? Locale.US : Locale.GERMANY, "%,d", gamePlayer.getCoins());
    }

    private static String tab(char symbol) {
        return "<font:monsters:tab>" + symbol + "</font>";
    }

    private static void render(Board board, List<Line> lines) {
        List<String> entries = board.entries();
        while (entries.size() > lines.size()) board.scoreboard().resetScores(entries.removeLast());
        while (entries.size() < lines.size()) entries.add("line" + entries.size());
        for (int i = 0; i < lines.size(); i++) {
            Score score = board.objective().getScore(entries.get(i));
            score.setScore(lines.size() - i);
            score.customName(lines.get(i).left());
            score.numberFormat(NumberFormat.fixed(lines.get(i).right()));
        }
    }

    // Symbol and label left, value right; text from the map config only as a placeholder, never as MiniMessage
    private static Line line(char symbol, String label, String value, String text) {
        return new Line(MM.deserialize("<white>" + tab(symbol) + "</white> " + LABEL + label),
                MM.deserialize(value + MARGIN, Placeholder.unparsed("map", text)));
    }

    private static Line empty() {
        // A blank custom name, an empty one would show the entry name
        return new Line(Component.text(" "), Component.empty());
    }

    private static String gradientLetters(String text, String... colors) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) out.append("<#").append(colors[Math.min(i, colors.length - 1)]).append(">").append(text.charAt(i));
        return out.toString();
    }
}
