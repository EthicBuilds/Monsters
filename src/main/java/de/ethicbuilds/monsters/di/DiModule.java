package de.ethicbuilds.monsters.di;

import com.google.inject.AbstractModule;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.player.manager.UserManager;

public class DiModule extends AbstractModule {
    private final Main plugin;

    public DiModule(Main plugin) {
        this.plugin = plugin;
    }


    @Override
    protected void configure() {
        bind(Main.class).toInstance(plugin);

        bind(UserManager.class).asEagerSingleton();
        bind(GameManager.class).asEagerSingleton();
        bind(WaveManager.class).asEagerSingleton();
    }
}
