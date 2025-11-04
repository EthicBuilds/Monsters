package de.ethicbuilds.monsters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import de.ethicbuilds.monsters.di.DiModule;
import de.ethicbuilds.monsters.gameplay.listener.GameListener;
import de.ethicbuilds.monsters.gameplay.listener.PreGameListener;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import de.ethicbuilds.monsters.test.LocateMonsters;
import de.ethicbuilds.monsters.commands.CreateMapConfigCommand;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/***
 * @author CodedByGruba
 */

public final class Main extends JavaPlugin {
    private Gson gson;

    @Getter
    private Injector injector;
    @Getter
    private static Main INSTANCE;
    @Getter
    private World world;
    @Getter
    private final String monstersPrefix = "§7[§x§0§B§7§2§1§2M§x§0§B§8§1§1§4o§x§0§A§9§1§1§6n§x§0§A§A§0§1§8s§x§0§A§B§0§1§At§x§0§A§B§F§1§Ce§x§0§9§C§F§1§Er§x§0§9§D§E§2§0s§7] ";

    @Override
    public void onEnable() {
        // Plugin startup logic
        INSTANCE = this;
        injector = Guice.createInjector(new DiModule(INSTANCE));

        gson = new GsonBuilder().setPrettyPrinting().create();

        saveDefaultConfig();
        world = Bukkit.getWorld("world");

        injector.getInstance(MapManager.class).loadMapConfig();
        injector.getInstance(GameManager.class).loadGameConfig();
        injector.getInstance(GameManager.class).startPlayerCheck();
        injector.getInstance(ScoreboardManager.class).init();

        registerCommandsAndListeners();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void registerCommandsAndListeners() {
        Objects.requireNonNull(getCommand("createMapConfig")).setExecutor(injector.getInstance(CreateMapConfigCommand.class));
        Objects.requireNonNull(getCommand("locateMonsters")).setExecutor(injector.getInstance(LocateMonsters.class));

        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(injector.getInstance(PreGameListener.class), this);
        pm.registerEvents(injector.getInstance(WaveListener.class), this);
        pm.registerEvents(injector.getInstance(GameListener.class), this);
    }

}
