package de.ethicbuilds.monsters.di;

import com.google.inject.AbstractModule;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import de.ethicbuilds.monsters.weapons.manager.WeaponManager;

public class DiModule extends AbstractModule {
    private final Main plugin;

    public DiModule(Main plugin) {
        this.plugin = plugin;
    }


    @Override
    protected void configure() {
        //Plugin
        bind(Main.class).toInstance(plugin);

        //Manager
        bind(UserManager.class).asEagerSingleton();
        bind(GameManager.class).asEagerSingleton();
        bind(WaveManager.class).asEagerSingleton();
        bind(MapManager.class).asEagerSingleton();
        bind(MonsterManager.class).asEagerSingleton();
        bind(ScoreboardManager.class).asEagerSingleton();
        bind(WeaponManager.class).asEagerSingleton();

        //Repositories
        bind(GameStates.class).asEagerSingleton();
    }
}
