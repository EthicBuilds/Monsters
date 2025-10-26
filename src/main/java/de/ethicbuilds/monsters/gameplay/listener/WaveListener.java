package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class WaveListener implements Listener {
    @Inject
    private GameManager gameManager;
    @Inject
    private MonsterManager monsterManager;
    @Inject
    private UserManager userManager;


    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE
                || !(event.getEntity() instanceof Monster monster)
                || !(event.getDamager() instanceof Player player)) return;

        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null) return;

        EnemyMonster enemyMonster = monsterManager.getEnemyMonster(monster);
        if (enemyMonster == null) return;

        gamePlayer.addCoins(enemyMonster.getCoin());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        event.getDrops().clear();

        if (gameManager.getCurrentPhase() != GamePhase.WAVE
                || !(event.getEntity() instanceof Monster monster)) return;

        EnemyMonster enemyMonster = monsterManager.getEnemyMonster(monster);
        if (enemyMonster == null) return;

        monsterManager.removeMonster(enemyMonster);
    }
}
