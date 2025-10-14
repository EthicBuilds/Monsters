package de.ethicbuilds.monsters;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.ethicbuilds.monsters.di.DiModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private Injector injector;

    @Override
    public void onEnable() {
        // Plugin startup logic
        injector = Guice.createInjector(new DiModule(this));

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
