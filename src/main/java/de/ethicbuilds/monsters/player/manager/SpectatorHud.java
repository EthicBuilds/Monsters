package de.ethicbuilds.monsters.player.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/***
 * For downed players and watchers: a bar with the wave, the monsters left and the health of every teammate
 */
public class SpectatorHud {
    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private WaveManager waveManager;
    @Inject private MonsterManager monsterManager;
    @Inject private UserManager userManager;

    private final BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (gameManager.getCurrentPhase() != GamePhase.WAVE) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                update();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void update() {
        StringBuilder team = new StringBuilder();
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!team.isEmpty()) team.append(" §8· ");
            if (!player.isOnline()) team.append("§8").append(player.getName());
            else if (userManager.isPlayerDead(player.getUniqueId())) team.append("§c").append(player.getName()).append(" §7am Boden");
            else team.append("§f").append(player.getName()).append(" §c❤ ").append((int) Math.ceil(player.getHealth() / 2));
        }
        bar.setTitle(String.format("§6Welle %d §8· §f%d Monster §8| %s", waveManager.getCurrentWave(), monsterManager.remaining(), team));
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline()) continue;
            if (userManager.isPlayerDead(player.getUniqueId())) bar.addPlayer(player);
            else bar.removePlayer(player);
        }
    }
}
