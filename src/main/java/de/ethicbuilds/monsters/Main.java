package de.ethicbuilds.monsters;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.ethicbuilds.monsters.commands.RoundEndCommand;
import de.ethicbuilds.monsters.commands.SurrenderCommand;
import de.ethicbuilds.monsters.di.DiModule;
import de.ethicbuilds.monsters.gameplay.EffectLimit;
import de.ethicbuilds.monsters.gameplay.RoundEndCard;
import de.ethicbuilds.monsters.gameplay.listener.CommandBlocker;
import de.ethicbuilds.monsters.gameplay.listener.GameListener;
import de.ethicbuilds.monsters.gameplay.listener.JoinMeListener;
import de.ethicbuilds.monsters.gameplay.listener.PreGameListener;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.gameplay.manager.RoundLoader;
import de.ethicbuilds.monsters.map.decoration.CrateManager;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.monster.MonsterGuard;
import de.ethicbuilds.monsters.perk.PerkManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.ChatStatus;
import de.ethicbuilds.monsters.player.manager.PackStatus;
import de.ethicbuilds.monsters.player.manager.PingManager;
import de.ethicbuilds.monsters.player.manager.ClassManager;
import de.ethicbuilds.monsters.weapons.RocketLoot;
import de.ethicbuilds.monsters.player.manager.ReviveManager;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.player.manager.WatcherManager;
import de.ethicbuilds.monsters.reward.DailyChallenge;
import de.ethicbuilds.monsters.scoreboard.TabNames;
import de.ethicbuilds.monsters.scoreboard.TabPlaceholders;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import de.ethicbuilds.monsters.weapons.SniperScope;
import de.ethicbuilds.monsters.weapons.Weapon;
import de.ethicbuilds.monsters.weapons.manager.WorkbenchManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import de.ethicbuilds.monsters.commands.BossWaveCommand;
import de.ethicbuilds.monsters.commands.LocateMonstersCommand;
import de.ethicbuilds.monsters.commands.CreateMapConfigCommand;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/***
 * @author CodedByGruba
 */

public final class Main extends JavaPlugin {
    @Getter
    private Injector injector;
    @Getter
    private static Main INSTANCE;
    @Getter @Setter
    private World world;
    @Getter
    private final String monstersPrefix = "§7[§x§0§B§7§2§1§2M§x§0§B§8§1§1§4o§x§0§A§9§1§1§6n§x§0§A§A§0§1§8s§x§0§A§B§0§1§At§x§0§A§B§F§1§Ce§x§0§9§C§F§1§Er§x§0§9§D§E§2§0s§7] ";

    @Override
    public void onEnable() {
        INSTANCE = this;
        injector = Guice.createInjector(new DiModule(INSTANCE));

        saveDefaultConfig();
        injector.getInstance(ScoreboardManager.class).init();

        registerCommandsAndListeners();
        startAmmoHud();
        EffectLimit.start(this);
        // Waits for the GameManager to hand over a round, then loads its map
        injector.getInstance(RoundLoader.class).start();
    }

    // Ammo panel refreshed every second while a weapon is in hand
    private void startAmmoHud() {
        UserManager userManager = injector.getInstance(UserManager.class);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
                Weapon weapon = gamePlayer.getWeapon(gamePlayer.getPlayer().getInventory().getItemInMainHand());
                if (weapon != null && !userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) AmmoHud.show(gamePlayer.getPlayer(), weapon);
            }
        }, 20L, 20L);
    }

    private void registerCommandsAndListeners() {
        Objects.requireNonNull(getCommand("createMapConfig")).setExecutor(injector.getInstance(CreateMapConfigCommand.class));
        Objects.requireNonNull(getCommand("locateMonsters")).setExecutor(injector.getInstance(LocateMonstersCommand.class));
        Objects.requireNonNull(getCommand("bosswelle")).setExecutor(injector.getInstance(BossWaveCommand.class));
        Objects.requireNonNull(getCommand("rundenende")).setExecutor(injector.getInstance(RoundEndCommand.class));
        Objects.requireNonNull(getCommand("aufgeben")).setExecutor(injector.getInstance(SurrenderCommand.class));

        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(injector.getInstance(PreGameListener.class), this);
        pm.registerEvents(injector.getInstance(WaveListener.class), this);
        pm.registerEvents(injector.getInstance(GameListener.class), this);
        pm.registerEvents(injector.getInstance(DecorationManager.class), this);
        pm.registerEvents(injector.getInstance(CrateManager.class), this);
        pm.registerEvents(new MonsterGuard(), this);
        pm.registerEvents(injector.getInstance(ReviveManager.class), this);
        pm.registerEvents(injector.getInstance(ClassManager.class), this);
        pm.registerEvents(injector.getInstance(RocketLoot.class), this);
        pm.registerEvents(injector.getInstance(WorkbenchManager.class), this);
        pm.registerEvents(injector.getInstance(PerkManager.class), this);
        pm.registerEvents(injector.getInstance(RoundEndCard.class), this);
        DailyChallenge.load();
        pm.registerEvents(injector.getInstance(PingManager.class), this);
        pm.registerEvents(injector.getInstance(JoinMeListener.class), this);
        pm.registerEvents(new CommandBlocker(), this);
        pm.registerEvents(new SniperScope(), this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, JoinMeListener.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, ChatStatus.CHANNEL);
        pm.registerEvents(injector.getInstance(WatcherManager.class), this);
        pm.registerEvents(injector.getInstance(PauseManager.class), this);
        pm.registerEvents(new PackStatus(), this);
        if (pm.isPluginEnabled("PlaceholderAPI")) injector.getInstance(TabPlaceholders.class).register();
        injector.getInstance(TabNames.class).start();
    }

}
