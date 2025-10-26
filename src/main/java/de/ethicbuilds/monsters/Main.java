package de.ethicbuilds.monsters;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.ethicbuilds.monsters.di.DiModule;
import de.ethicbuilds.monsters.gameplay.listener.AfterGameListener;
import de.ethicbuilds.monsters.gameplay.listener.GameListener;
import de.ethicbuilds.monsters.gameplay.listener.PreGameListener;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import de.ethicbuilds.monsters.test.LocateMonsters;
import de.ethicbuilds.monsters.test.TestCommand;
import de.ethicbuilds.monsters.test.TestListener;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Main extends JavaPlugin {
    /*** Todolist:
     * TODO: ADD Money Management
     * TODO: Connect Map with Weapons
     * TODO: Connect Map with Doors
     * TODO: Death Handling
     * TODO: Last Details
     */

    private Injector injector;

    @Getter
    private static Main INSTANCE;
    @Getter
    private World world;

    @Override
    public void onEnable() {
        // Plugin startup logic
        INSTANCE = this;
        injector = Guice.createInjector(new DiModule(INSTANCE));

        world = Bukkit.getWorld("world");

        injector.getInstance(MapManager.class).loadMapConfig();
        injector.getInstance(ScoreboardManager.class).init();

        registerCommandsAndListeners();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void registerCommandsAndListeners() {
        Objects.requireNonNull(getCommand("monstersTest")).setExecutor(injector.getInstance(TestCommand.class));
        Objects.requireNonNull(getCommand("locateMonsters")).setExecutor(injector.getInstance(LocateMonsters.class));

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(injector.getInstance(TestListener.class), this);

        pm.registerEvents(injector.getInstance(PreGameListener.class), this);
        pm.registerEvents(injector.getInstance(AfterGameListener.class), this);
        pm.registerEvents(injector.getInstance(WaveListener.class), this);
//        pm.registerEvents(injector.getInstance(GameListener.class), this);
    }
}
