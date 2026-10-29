package de.ethicbuilds.monsters.lang;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/***
 * Boss bar that everyone sees in their own language: one bar per language, each player is put into the right one
 */
public final class DualBar {
    private final BossBar german, english;

    public DualBar(String de, String en, BarColor color, BarStyle style) {
        german = Bukkit.createBossBar(de, color, style);
        english = Bukkit.createBossBar(en, color, style);
    }

    public void addPlayer(Player player) {
        boolean inEnglish = Lang.english(player);
        (inEnglish ? german : english).removePlayer(player);
        (inEnglish ? english : german).addPlayer(player);
    }

    public void setTitle(String de, String en) {
        german.setTitle(de);
        english.setTitle(en);
    }

    public void setProgress(double progress) {
        german.setProgress(progress);
        english.setProgress(progress);
    }

    public void setColor(BarColor color) {
        german.setColor(color);
        english.setColor(color);
    }

    public List<Player> getPlayers() {
        List<Player> players = new ArrayList<>(german.getPlayers());
        players.addAll(english.getPlayers());
        return players;
    }

    public void removeAll() {
        german.removeAll();
        english.removeAll();
    }
}
