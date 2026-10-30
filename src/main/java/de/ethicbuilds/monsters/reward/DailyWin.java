package de.ethicbuilds.monsters.reward;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

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

    /** Completes with what was paid, Payout.NONE when the player already had it today */
    public static CompletableFuture<Payout> reward(Player player, String prefix) {
        CompletableFuture<Payout> result = new CompletableFuture<>();
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            result.complete(Payout.NONE);
            return result;
        }
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            boolean done = user.getNodes(NodeType.PERMISSION).stream().anyMatch(node -> node.getKey().equals(NODE) && !node.hasExpired());
            if (done) {
                result.complete(Payout.NONE);
                return;
            }
            Instant midnight = ZonedDateTime.now(BERLIN).toLocalDate().plusDays(1).atStartOfDay(BERLIN).toInstant();
            user.data().add(PermissionNode.builder(NODE).expiry(midnight).build());
            luckPerms.getUserManager().saveUser(user).join();
            Bukkit.getScheduler().runTask(Main.getINSTANCE(), () -> {
                long coins = EthicCoins.withRankBonus(player, COINS);
                if (player.isOnline() && EthicCoins.deposit(player.getUniqueId(), coins, "Monsters Tagessieg")) {
                    player.sendMessage(Lang.f(player, "%s§6Erster Sieg heute! §e+%d EthicCoins", "%s§6First win today! §e+%d EthicCoins", prefix, coins));
                    result.complete(new Payout(COINS, coins));
                } else {
                    result.complete(Payout.NONE);
                }
            });
        }).exceptionally(e -> {
            result.complete(Payout.NONE);
            return null;
        });
        return result;
    }
}
