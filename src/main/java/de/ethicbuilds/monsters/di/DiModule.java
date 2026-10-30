package de.ethicbuilds.monsters.di;

import com.google.inject.AbstractModule;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.building.BuildManager;
import de.ethicbuilds.monsters.building.Deployables;
import de.ethicbuilds.monsters.building.Elevators;
import de.ethicbuilds.monsters.building.Flares;
import de.ethicbuilds.monsters.building.Nests;
import de.ethicbuilds.monsters.building.PowerGrid;
import de.ethicbuilds.monsters.building.Ziplines;
import de.ethicbuilds.monsters.event.WaveEvents;
import de.ethicbuilds.monsters.gameplay.RoundEndCard;
import de.ethicbuilds.monsters.gameplay.manager.DoorManager;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.gameplay.manager.RoundLoader;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.decoration.CrateManager;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.perk.PerkManager;
import de.ethicbuilds.monsters.player.manager.ReviveManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.player.manager.WatcherManager;
import de.ethicbuilds.monsters.powerup.PowerupManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import de.ethicbuilds.monsters.statistic.manager.StatisticManager;
import de.ethicbuilds.monsters.weapons.manager.GrenadeManager;
import de.ethicbuilds.monsters.weapons.manager.WeaponManager;
import de.ethicbuilds.monsters.weapons.manager.WorkbenchManager;

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
        bind(ReviveManager.class).asEagerSingleton();
        bind(WatcherManager.class).asEagerSingleton();
        bind(GameManager.class).asEagerSingleton();
        bind(RoundLoader.class).asEagerSingleton();
        bind(WaveManager.class).asEagerSingleton();
        bind(PauseManager.class).asEagerSingleton();
        bind(MapManager.class).asEagerSingleton();
        bind(MonsterManager.class).asEagerSingleton();
        bind(ScoreboardManager.class).asEagerSingleton();
        bind(WeaponManager.class).asEagerSingleton();
        bind(StatisticManager.class).asEagerSingleton();
        bind(DoorManager.class).asEagerSingleton();
        bind(DecorationManager.class).asEagerSingleton();
        bind(CrateManager.class).asEagerSingleton();
        bind(GrenadeManager.class).asEagerSingleton();
        bind(PowerupManager.class).asEagerSingleton();
        bind(WorkbenchManager.class).asEagerSingleton();
        bind(PerkManager.class).asEagerSingleton();
        bind(RoundEndCard.class).asEagerSingleton();
        bind(WaveEvents.class).asEagerSingleton();
        bind(BuildManager.class).asEagerSingleton();
        bind(PowerGrid.class).asEagerSingleton();
        bind(Nests.class).asEagerSingleton();
        bind(Flares.class).asEagerSingleton();
        bind(Ziplines.class).asEagerSingleton();
        bind(Deployables.class).asEagerSingleton();
        bind(Elevators.class).asEagerSingleton();

        //Repositories
        bind(GameStates.class).asEagerSingleton();
    }
}
