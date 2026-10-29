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
import lombok.Getter;
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
import java.util.List;
import java.util.Locale;

/***
 * Sidebar in the style of the network scoreboard (design session): icons from monsters:hud,
 * labels left, values right, no red numbers
 */
public class ScoreboardManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "<bold><gradient:#F9A11E:#8B4BD8>MONSTERS</gradient></bold>";
    private static final String ICON = "<white><font:monsters:hud>%s</font></white> ";
    // 8 px margin behind every value
    private static final String GONE_COLOR = "<#E05555>";
    private static final String MARGIN = "<font:monsters:hud></font>";
    private static final String WEBSITE = gradientLetters("EthicBuilds.de",
            "22D3E6", "0DD6D3", "28D7BD", "4AD7A5", "6AD48B", "89CF6F", "A5C953", "BEC136", "D5B718", "E9AC04", "F9A11E");

    @Inject private UserManager userManager;
    @Inject private WaveManager waveManager;
    @Inject private MonsterManager monsterManager;
    @Inject private GameManager gameManager;
    @Inject private Main plugin;

    private Thread timeThread = null;
    private int time = 0;

    // German board, the English one has the same content with English labels
    @Getter
    private Scoreboard scoreboard;
    private Scoreboard scoreboardEn;
    private Objective objective, objectiveEn;
    private final List<String> entries = new ArrayList<>();

    private record Line(Component left, Component right) {}

    public void init() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        scoreboardEn = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = sidebar(scoreboard);
        objectiveEn = sidebar(scoreboardEn);

        new BukkitRunnable() {
            @Override
            public void run() {
                update();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private static Objective sidebar(Scoreboard board) {
        Objective sidebar = board.registerNewObjective("monsters", Criteria.DUMMY, MM.deserialize(TITLE));
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.numberFormat(NumberFormat.blank());
        return sidebar;
    }

    /** Scoreboard in the player's language */
    public Scoreboard getScoreboard(Player player) {
        return Lang.english(player) ? scoreboardEn : scoreboard;
    }

    /** Seconds since the round started */
    public int getTime() {
        return time;
    }

    public void startTime() {
        this.timeThread = new Thread(() -> {
            try {
                while (Thread.currentThread().isAlive()) {
                    time++;
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        this.timeThread.start();
    }

    private void update() {
        render(lines(false), lines(true));
        // Players with a Monsters board get the one of their language, also after changing it
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard current = player.getScoreboard();
            if (current != scoreboard && current != scoreboardEn) continue;
            Scoreboard own = getScoreboard(player);
            if (current != own) player.setScoreboard(own);
        }
    }

    private List<Line> lines(boolean english) {
        List<Line> lines = new ArrayList<>();
        String map = gameManager.getGameConfig() != null ? gameManager.getGameConfig().getMapName() : "";

        lines.add(empty());
        lines.add(line("", "Map", "<#F2F5F8>" + map));
        lines.add(line("", english ? "Wave" : "Runde", "<#F9A11E>" + waveManager.getCurrentWave()));
        lines.add(line("", english ? "Monsters" : "Monster", "<#F2F5F8>" + monsterManager.remaining()));
        lines.add(line("", english ? "Time" : "Zeit", "<#F2F5F8>" + String.format("%02d:%02d", time / 60, time % 60)));
        lines.add(empty());
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            boolean dead = userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId());
            // Left the round: the whole line red, so everyone sees the player is gone
            boolean gone = !gamePlayer.getPlayer().isOnline();
            String icon = String.format(ICON, dead ? "\uE01F" : "\uE01E");
            if (gone) icon = icon.replace("<white>", GONE_COLOR).replace("</white>", "");
            Component left = MM.deserialize(icon + (gone ? GONE_COLOR + "<st>" : dead ? "<#9AA6B8>" : "<#F2F5F8>") + "<name>",
                    Placeholder.unparsed("name", gamePlayer.getPlayer().getName()));
            Component right = MM.deserialize((gone ? GONE_COLOR : "<#F9A11E>") + String.format(english ? Locale.US : Locale.GERMANY, "%,d", gamePlayer.getCoins())
                    + " <white><font:monsters:hud>\uE017</font></white>" + MARGIN);
            lines.add(new Line(left, right));
        }
        // The sidebar shows 15 lines at most, with 8 players the gap above the website goes
        if (lines.size() < 14) lines.add(empty());
        lines.add(new Line(MM.deserialize(WEBSITE), Component.empty()));
        return lines;
    }

    // Both lists have the same lines
    private void render(List<Line> german, List<Line> english) {
        while (entries.size() > german.size()) {
            String entry = entries.removeLast();
            scoreboard.resetScores(entry);
            scoreboardEn.resetScores(entry);
        }
        while (entries.size() < german.size()) entries.add("line" + entries.size());
        fill(objective, german);
        fill(objectiveEn, english);
    }

    private void fill(Objective sidebar, List<Line> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Score score = sidebar.getScore(entries.get(i));
            score.setScore(lines.size() - i);
            score.customName(lines.get(i).left());
            score.numberFormat(NumberFormat.fixed(lines.get(i).right()));
        }
    }

    private static Line line(String icon, String label, String value) {
        return new Line(MM.deserialize(String.format(ICON, icon) + "<#9AA6B8>" + label), MM.deserialize(value + MARGIN));
    }

    private static Line empty() {
        return new Line(Component.text(" "), Component.empty());
    }

    private static String gradientLetters(String text, String... colors) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            out.append("<#").append(colors[Math.min(i, colors.length - 1)]).append(">").append(text.charAt(i));
        }
        return out.toString();
    }
}
