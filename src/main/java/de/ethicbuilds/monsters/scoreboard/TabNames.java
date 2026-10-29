package de.ethicbuilds.monsters.scoreboard;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/***
 * The statistics row of every player in the tab list, set here on the server instead of by TAB on the proxy,
 * so only this server sees it and the lobby keeps the normal names. Before the round just the name.
 */
public class TabNames {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;
    @Inject private TabPlaceholders tab;

    private static final int RESEND_EVERY = 5;

    private final Map<UUID, String> shown = new HashMap<>();
    private int ticks;

    public void start() {
        if (!plugin.getConfig().getBoolean("tab-player-lines", true)) return;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
    }

    private void update() {
        boolean round = gameManager.getCurrentPhase() != GamePhase.PRE_GAME;
        // Every 5 seconds sent anyway, in case TAB cleared the names after a join or server change
        boolean resend = ++ticks % RESEND_EVERY == 0;
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline()) continue;
            String row = round ? tab.row(gamePlayer) : "";
            if (row.equals(shown.put(player.getUniqueId(), row)) && !resend) continue;
            player.playerListName(round ? MM.deserialize(row) : null);
        }
    }
}
