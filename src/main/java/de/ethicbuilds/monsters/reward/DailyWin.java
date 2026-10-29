package de.ethicbuilds.monsters.reward;

import de.ethicbuilds.monsters.Main;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/***
 * First win of the day: +50 EthicCoins once a day for the best player of a round, the same in every minigame.
 * Remembered as a temporary LuckPerms node that runs out at midnight German time
 * (hasPermission does not work for this, team ranks have *).
 */
public final class DailyWin {
    private static final String NODE = "ethiccoins.tagessieg.monsters";
    private static final long COINS = 50;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private DailyWin() {}

    public static void reward(Player player, String prefix) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return;
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            boolean done = user.getNodes(NodeType.PERMISSION).stream().anyMatch(node -> node.getKey().equals(NODE) && !node.hasExpired());
            if (done) return;
            Instant midnight = ZonedDateTime.now(BERLIN).toLocalDate().plusDays(1).atStartOfDay(BERLIN).toInstant();
            user.data().add(PermissionNode.builder(NODE).expiry(midnight).build());
            luckPerms.getUserManager().saveUser(user).join();
            Bukkit.getScheduler().runTask(Main.getINSTANCE(), () -> {
                if (!player.isOnline()) return;
                long coins = EthicCoins.withRankBonus(player, COINS);
                if (EthicCoins.deposit(player.getUniqueId(), coins, "Monsters Tagessieg")) {
                    player.sendMessage(String.format("%s§6Erster Sieg heute! §e+%d EthicCoins", prefix, coins));
                }
            });
        });
    }
}
