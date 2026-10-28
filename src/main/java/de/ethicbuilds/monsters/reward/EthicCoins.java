package de.ethicbuilds.monsters.reward;

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
        int bonus = player.hasPermission("ethiccoins.boost.100") ? 100 : player.hasPermission("ethiccoins.boost.50") ? 50 : 0;
        return Math.round(amount * (100 + bonus) / 100d);
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
