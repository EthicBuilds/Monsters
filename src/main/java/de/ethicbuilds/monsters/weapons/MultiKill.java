package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/***
 * Several kills with one shot (sniper going through monsters): bonus coins and a short call out
 */
public final class MultiKill {
    private static final int BONUS_PER_KILL = 10;
    private static final String[] NAMES = {"", "", "Doppelkill", "Dreifachkill", "Vierfachkill", "Fünffachkill"};
    private static final String[] NAMES_EN = {"", "", "Double kill", "Triple kill", "Quad kill", "Penta kill"};

    private MultiKill() {}

    public static void reward(Player player, int kills) {
        GamePlayer gamePlayer = Main.getINSTANCE().getInjector().getInstance(UserManager.class).getGamePlayer(player.getUniqueId());
        if (gamePlayer == null) return;
        int bonus = BONUS_PER_KILL * (kills - 1) * kills / 2;
        gamePlayer.addCoins(bonus);
        AmmoHud.pause(player);
        int index = Math.min(kills, NAMES.length - 1);
        player.sendActionBar(Lang.f(player, "§6§l%s! §e+%d Bonus-Coins", "§6§l%s! §e+%d bonus coins", Lang.t(player, NAMES[index], NAMES_EN[index]), bonus));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.4F, 1.4F + 0.1F * kills);
    }
}
