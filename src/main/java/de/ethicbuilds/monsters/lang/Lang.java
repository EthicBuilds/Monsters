package de.ethicbuilds.monsters.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;

/***
 * German or English per player, like in the whole network: the LuckPerms meta "lang" (de/en),
 * without a choice the language of the Minecraft client. Every text is written in both languages where it is used.
 */
public final class Lang {
    private static final String META = "lang";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Lang() {}

    public static boolean english(Player player) {
        if (player == null) return false;
        String code = null;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                code = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player).getMetaValue(META);
            } catch (IllegalStateException ignored) {
                // LuckPerms not ready yet
            }
        }
        if (code != null && code.trim().equalsIgnoreCase("de")) return false;
        if (code != null && code.trim().equalsIgnoreCase("en")) return true;
        return !"de".equals(player.locale().getLanguage());
    }

    public static String t(Player player, String de, String en) {
        return english(player) ? en : de;
    }

    /** String.format in the player's language, numbers with dots (German) or commas (English) */
    public static String f(Player player, String de, String en, Object... args) {
        boolean english = english(player);
        return String.format(english ? Locale.US : Locale.GERMANY, english ? en : de, args);
    }

    /** MiniMessage text in the player's language */
    public static Component mm(Player player, String de, String en) {
        return MM.deserialize(t(player, de, en));
    }

    /** Short note in the action bar instead of the chat, without the prefix; the ammo panel waits a moment */
    public static void note(Player player, String legacy) {
        String prefix = de.ethicbuilds.monsters.Main.getINSTANCE().getMonstersPrefix();
        if (legacy.startsWith(prefix)) legacy = legacy.substring(prefix.length());
        de.ethicbuilds.monsters.weapons.AmmoHud.pause(player);
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacy));
    }

    /** Note for everyone online, the console gets the German one */
    public static void noteAll(String de, String en, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) note(player, f(player, de, en, args));
        Bukkit.getConsoleSender().sendMessage(String.format(Locale.GERMANY, de, args));
    }

    /** Chat message to everyone online, each in their language, the console gets the German one */
    public static void broadcast(String de, String en, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(f(player, de, en, args));
        Bukkit.getConsoleSender().sendMessage(String.format(Locale.GERMANY, de, args));
    }
}
