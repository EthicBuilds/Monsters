package de.ethicbuilds.monsters.commands;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/***
 * /aufgeben: the team votes, when everyone still there agrees the round ends the regular way
 * with summary, statistics and record. A vote runs for one minute.
 */
public class SurrenderCommand implements CommandExecutor {
    private static final long VOTE_MILLIS = 60_000;

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;

    private final Set<UUID> votes = new HashSet<>();
    private long voteStart;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player) || !userManager.isGamePlayer(player.getUniqueId())) return true;
        if (gameManager.getCurrentPhase() != GamePhase.WAVE) {
            player.sendMessage(plugin.getMonstersPrefix() + "§cGerade läuft keine Runde.");
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - voteStart > VOTE_MILLIS) {
            votes.clear();
            voteStart = now;
        }
        if (!votes.add(player.getUniqueId())) {
            player.sendMessage(plugin.getMonstersPrefix() + "§7Du hast schon zugestimmt.");
            return true;
        }

        List<Player> present = userManager.getGamePlayers().stream().map(GamePlayer::getPlayer).filter(Player::isOnline).toList();
        long agreed = present.stream().filter(p -> votes.contains(p.getUniqueId())).count();
        if (agreed >= present.size()) {
            Bukkit.broadcastMessage(plugin.getMonstersPrefix() + "§cDas Team gibt auf.");
            votes.clear();
            gameManager.gameEnd();
            return true;
        }
        Component vote = Component.text(plugin.getMonstersPrefix() + "§e" + player.getName() + " §7will aufgeben §8(§f" + agreed + "/" + present.size() + "§8) ")
                .append(Component.text("[Zustimmen]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/aufgeben"))
                        .hoverEvent(HoverEvent.showText(Component.text("Runde beenden, Statistik wird gespeichert"))));
        present.forEach(p -> p.sendMessage(vote));
        return true;
    }
}
