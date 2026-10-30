package de.ethicbuilds.monsters.player.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.DualBar;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/***
 * For downed players and watchers: a bar with the wave, the monsters left and the health of every teammate
 */
public class SpectatorHud {
    private static final int COMPACT_FROM = 4;
    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private WaveManager waveManager;
    @Inject private MonsterManager monsterManager;
    @Inject private UserManager userManager;

    private final DualBar bar = new DualBar("", "", BarColor.WHITE, BarStyle.SOLID);

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
        bar.setTitle(String.format("§6Welle %d §8· §f%d Monster §8| %s", waveManager.getCurrentWave(), monsterManager.remaining(), team(false)),
                String.format("§6Wave %d §8· §f%d Monsters §8| %s", waveManager.getCurrentWave(), monsterManager.remaining(), team(true)));
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline()) continue;
            if (userManager.isPlayerDead(player.getUniqueId())) bar.addPlayer(player);
            else bar.removePlayer(player);
        }
    }

    private String team(boolean english) {
        StringBuilder team = new StringBuilder();
        // More than 4 names do not fit in the bar, then only who is still standing
        if (userManager.getGamePlayers().size() > COMPACT_FROM) {
            long down = userManager.getGamePlayers().stream().filter(p -> userManager.isPlayerDead(p.getPlayer().getUniqueId())).count();
            team.append("§f").append(userManager.getGamePlayers().size() - down).append(english ? " standing §8· §c" : " stehen §8· §c")
                    .append(down).append(english ? " downed" : " am Boden");
        } else {
            for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                Player player = gamePlayer.getPlayer();
                if (!team.isEmpty()) team.append(" §8· ");
                if (!player.isOnline()) team.append("§8").append(player.getName());
                else if (userManager.isPlayerDead(player.getUniqueId())) team.append("§c").append(player.getName()).append(english ? " §7downed" : " §7am Boden");
                else team.append("§f").append(player.getName()).append(" §c❤ ").append((int) Math.ceil(player.getHealth() / 2));
            }
        }
        return team.toString();
    }
}
