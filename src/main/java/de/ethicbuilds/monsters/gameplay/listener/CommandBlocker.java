package de.ethicbuilds.monsters.gameplay.listener;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;
import java.util.Set;

/***
 * Commands that break a round: /spawn from UtilityCommands teleports into the air, players die from fall damage
 */
public class CommandBlocker implements Listener {
    private static final Set<String> BLOCKED = Set.of("spawn");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!BLOCKED.contains(label.substring(label.indexOf(':') + 1))) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Main.getINSTANCE().getMonstersPrefix() + Lang.t(event.getPlayer(), "§7Diesen Befehl gibt es in Monsters nicht.", "§7This command does not exist in Monsters."));
    }

    @EventHandler
    public void onTabList(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(command -> BLOCKED.contains(command.substring(command.indexOf(':') + 1).toLowerCase(Locale.ROOT)));
    }
}
