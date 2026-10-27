package de.ethicbuilds.monsters.commands;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/***
 * /bosswelle for the team: the next wave is the boss wave, to test the amethyst weavers
 */
public class BossWaveCommand implements CommandExecutor {
    @Inject
    private WaveManager waveManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("*")) return true;
        waveManager.skipToBossWave();
        sender.sendMessage("§aDie nächste Welle ist die Boss-Welle.");
        return true;
    }
}
