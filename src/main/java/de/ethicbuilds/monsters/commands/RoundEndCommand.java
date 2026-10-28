package de.ethicbuilds.monsters.commands;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/***
 * /rundenende for the team and the console: ends the running round the regular way, with summary and statistics
 */
public class RoundEndCommand implements CommandExecutor {
    @Inject
    private GameManager gameManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("*")) return true;
        if (gameManager.getCurrentPhase() != GamePhase.WAVE) {
            sender.sendMessage("§cGerade läuft keine Runde.");
            return true;
        }
        gameManager.gameEnd();
        return true;
    }
}
