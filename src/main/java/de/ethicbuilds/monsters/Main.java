package de.ethicbuilds.monsters;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.ethicbuilds.monsters.di.DiModule;
import de.ethicbuilds.monsters.gameplay.listener.AfterGameListener;
import de.ethicbuilds.monsters.gameplay.listener.GameListener;
import de.ethicbuilds.monsters.gameplay.listener.PreGameListener;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.test.TestCommand;
import de.ethicbuilds.monsters.test.TestListener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.Predicate;

public final class Main extends JavaPlugin {

    private Injector injector;
    private static Main INSTANCE;

    public static Main getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        INSTANCE = this;
        injector = Guice.createInjector(new DiModule(INSTANCE));

        registerCommandsAndListeners();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void registerCommandsAndListeners() {
        Objects.requireNonNull(getCommand("monstersTest")).setExecutor(injector.getInstance(TestCommand.class));

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(injector.getInstance(TestListener.class), this);

        pm.registerEvents(injector.getInstance(PreGameListener.class), this);
        pm.registerEvents(injector.getInstance(AfterGameListener.class), this);
        pm.registerEvents(injector.getInstance(WaveListener.class), this);
        pm.registerEvents(injector.getInstance(GameListener.class), this);
    }
}
