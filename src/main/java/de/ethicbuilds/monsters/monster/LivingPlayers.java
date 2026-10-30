package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/***
 * Players that abilities of the monsters may hit: in the round, online and not down
 */
final class LivingPlayers {
    private static UserManager users;
    private static GameManager game;

    private LivingPlayers() {}

    static List<Player> near(Location at, double radius) {
        UserManager users = users();
        List<Player> near = new ArrayList<>();
        for (GamePlayer gamePlayer : users.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline() || users.isPlayerDead(player.getUniqueId()) || !player.getWorld().equals(at.getWorld())) continue;
            if (player.getLocation().distanceSquared(at) <= radius * radius) near.add(player);
        }
        return near;
    }

    static boolean alive(Player player) {
        return player != null && player.isOnline() && !users().isPlayerDead(player.getUniqueId());
    }

    /** Wave under way and not paused: only then abilities start and hurt */
    static boolean waveRunning() {
        return !PauseManager.isPaused()
                && game().getCurrentPhase() == GamePhase.WAVE;
    }

    private static UserManager users() {
        if (users == null) users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
        return users;
    }

    private static GameManager game() {
        if (game == null) game = Main.getINSTANCE().getInjector().getInstance(GameManager.class);
        return game;
    }
}
