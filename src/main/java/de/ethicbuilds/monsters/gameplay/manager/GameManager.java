package de.ethicbuilds.monsters.gameplay.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import org.bukkit.scheduler.BukkitRunnable;

public class GameManager {
    @Inject
    private Main plugin;
    public void gameStart() {
        new BukkitRunnable() {
            @Override
            public void run() {

            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
