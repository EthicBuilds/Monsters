package de.ethicbuilds.monsters.scoreboard;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
 * Short (user 31.10.), so the tab list does not cover it: wave, monsters left and the own coins, one board per player.
 */
public class ScoreboardManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "<bold><gradient:#F9A11E:#8B4BD8>MONSTERS</gradient></bold>";
    // 8 px margin behind every value
    private static final String MARGIN = "<font:monsters:hud></font>";
    private static final String COIN = " <white><font:monsters:hud></font></white>";

    @Inject private UserManager userManager;
    @Inject private WaveManager waveManager;
    @Inject private MonsterManager monsterManager;
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
        List<Line> lines = new ArrayList<>();
        lines.add(line(english ? "Wave" : "Runde", "<#F9A11E>" + waveManager.getCurrentWave()));
        lines.add(line(english ? "Monsters" : "Monster", "<#F2F5F8>" + monsterManager.remaining()));
        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer != null) {
            lines.add(line("Coins", "<#F9A11E>" + String.format(english ? Locale.US : Locale.GERMANY, "%,d", gamePlayer.getCoins()) + COIN));
        }
        return lines;
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

    private static Line line(String label, String value) {
        return new Line(MM.deserialize("<#9AA6B8>" + label), MM.deserialize(value + MARGIN));
    }
}
