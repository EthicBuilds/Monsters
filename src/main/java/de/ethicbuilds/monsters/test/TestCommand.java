package de.ethicbuilds.monsters.test;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.map.MapManager;
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
    private MapManager mapManager;
    @Inject
    private GameManager gameManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {
        var player = (Player) sender;

        gameManager.gameStart();

//        mapManager.createMapConfig();

//        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
//        Pistol pistol = new Pistol();
//        gamePlayer.addWeapon(pistol);
//
//        gamePlayer.getPlayer().getInventory().setItem(1, pistol.getItem());



        return false;
    }
}
