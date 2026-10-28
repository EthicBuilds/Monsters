package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/***
 * Several kills with one shot (sniper going through monsters): bonus coins and a short call out
 */
public final class MultiKill {
    private static final int BONUS_PER_KILL = 50;
    private static final String[] NAMES = {"", "", "Doppelkill", "Dreifachkill", "Vierfachkill", "Fünffachkill"};

    private MultiKill() {}

    public static void reward(Player player, int kills) {
        GamePlayer gamePlayer = Main.getINSTANCE().getInjector().getInstance(UserManager.class).getGamePlayer(player.getUniqueId());
        if (gamePlayer == null) return;
        int bonus = BONUS_PER_KILL * (kills - 1) * kills / 2;
        gamePlayer.addCoins(bonus);
        AmmoHud.pause(player);
        player.sendActionBar(String.format("§6§l%s! §e+%d Bonus-Coins", NAMES[Math.min(kills, NAMES.length - 1)], bonus));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.4F, 1.4F + 0.1F * kills);
    }
}
