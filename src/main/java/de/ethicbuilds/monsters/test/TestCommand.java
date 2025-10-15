package de.ethicbuilds.monsters.test;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.Pistol;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TestCommand implements CommandExecutor {
    @Inject
    private UserManager userManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {
        GamePlayer gamePlayer = new GamePlayer((Player) sender);
        Pistol pistol = new Pistol();
        gamePlayer.addWeapon(pistol);
        userManager.addPlayer(gamePlayer);

        gamePlayer.getPlayer().getInventory().setItem(0, pistol.getItem());

        return false;
    }
}
