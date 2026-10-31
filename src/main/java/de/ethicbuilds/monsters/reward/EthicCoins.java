package de.ethicbuilds.monsters.reward;

import de.ethicbuilds.monsters.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/***
 * EthicCoins of the network through the EconomyApi of EthicEconomy (by reflection, no hard dependency),
 * same as in EggBattle. Without EthicEconomy on the server nothing is paid.
 */
public final class EthicCoins {
    private static Object api;
    private static Method deposit;

    private EthicCoins() {}

    /** @return false if EthicEconomy is missing or the booking failed */
    public static boolean deposit(UUID player, long amount, String reason) {
        if (!ready()) return false;
        try {
            deposit.invoke(api, player, amount, reason);
            return true;
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("[Monsters] EthicCoins not paid: " + e.getMessage());
            return false;
        }
    }

    // Rank bonus like in the whole network: Founder +50 %, EthicFounder +100 %
    public static long withRankBonus(Player player, long amount) {
        return Math.round(amount * (100 + rankBonus(player)) / 100d);
    }

    private static final String BONUS_COLOR = "§x§D§9§8§C§F§F";

    /** Rank bonus part of a payout for the chat, like in all minigames, empty without bonus */
    public static String bonusNote(Player player, long base, long paid) {
        if (paid <= base) return "";
        String text = Lang.f(player, "+%d durch +%d %% Rang-Bonus", "+%d from +%d %% rank bonus", paid - base, rankBonus(player));
        return " §8(" + BONUS_COLOR + text + "§8)";
    }

    /** Hint at the round start, only for players with a rank bonus */
    public static void announceBonus(Player player, String prefix) {
        int bonus = rankBonus(player);
        if (bonus <= 0) return;
        player.sendMessage(prefix + Lang.f(player, "§7Dein Rang gibt dir " + BONUS_COLOR + "+%d %% EthicCoins §7in dieser Runde.",
                "§7Your rank gives you " + BONUS_COLOR + "+%d %% EthicCoins §7this round.", bonus));
    }

    /** Rank bonus in percent */
    public static int rankBonus(Player player) {
        return player.hasPermission("ethiccoins.boost.100") ? 100 : player.hasPermission("ethiccoins.boost.50") ? 50 : 0;
    }

    @SuppressWarnings("unchecked")
    private static boolean ready() {
        if (api != null) return true;
        Plugin economy = Bukkit.getPluginManager().getPlugin("EthicEconomy");
        if (economy == null || !economy.isEnabled()) return false;
        try {
            Class<Object> type = (Class<Object>) Class.forName("de.ethicbuilds.economy.api.EconomyApi", true, economy.getClass().getClassLoader());
            RegisteredServiceProvider<Object> provider = Bukkit.getServicesManager().getRegistration(type);
            if (provider == null) return false;
            api = provider.getProvider();
            deposit = type.getMethod("deposit", UUID.class, long.class, String.class);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
