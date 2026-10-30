package de.ethicbuilds.monsters.commands;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/***
 * /locateMonsters for testing: number of living monsters, for the team also where they are
 */
public class LocateMonstersCommand implements CommandExecutor {
    @Inject
    private MonsterManager monsterManager;
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        commandSender.sendMessage(String.valueOf(monsterManager.getMonsters().size()));

        if (!commandSender.hasPermission("*")) return true;

        for (EnemyMonster monster : monsterManager.getMonsters().values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(monster.getMonster().getLocation().toString());
            }
        }

        return true;
    }
}
