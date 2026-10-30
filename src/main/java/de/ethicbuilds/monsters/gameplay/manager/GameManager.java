package de.ethicbuilds.monsters.gameplay.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.dto.StopServerDto;
import de.ethicbuilds.monsters.gameplay.RoundEndCard;
import de.ethicbuilds.monsters.gameplay.model.GameConfig;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.adapter.LocationAdapter;
import de.ethicbuilds.monsters.monster.TrackerGlow;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.perk.PerkManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.reward.DailyChallenge;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.powerup.PowerupManager;
import de.ethicbuilds.monsters.scoreboard.ScoreboardManager;
import de.ethicbuilds.monsters.statistic.manager.StatisticManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


public class GameManager {

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private GameStates gameStates;
    @Inject private WaveManager waveManager;
    @Inject private ScoreboardManager scoreboardManager;
    @Inject private MapManager mapManager;
    @Inject private MonsterManager monsterManager;
    @Inject private StatisticManager statisticManager;
    @Inject private PowerupManager powerupManager;
    @Inject private PerkManager perkManager;
    @Inject private RoundEndCard roundEndCard;

    private final Gson gson;

    @Getter
    private GameConfig gameConfig;
    private BukkitTask gameStartTask;
    private String roundKey = "";

    private long lastPlayerOnlineTime = System.currentTimeMillis();

    public GameManager() {
        gson = new GsonBuilder()
                .registerTypeAdapter(Location.class, new LocationAdapter())
                .setPrettyPrinting()
                .create();
    }

    public void startPlayerCheck() {
        lastPlayerOnlineTime = System.currentTimeMillis();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (getCurrentPhase() != GamePhase.PRE_GAME) this.cancel();
                if (Bukkit.getOnlinePlayers().isEmpty()) {
                    if (System.currentTimeMillis() - lastPlayerOnlineTime >= 30_000) {
                        HttpClient client = HttpClient.newHttpClient();

                        var dto = new StopServerDto(plugin.getConfig().getString("server-name"));

                        var request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/gameManager/api/server/stop"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(dto)))
                                .build();

                        try {
                            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                            System.out.println(response);
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        Bukkit.shutdown();
                    }
                } else {
                    lastPlayerOnlineTime = System.currentTimeMillis();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void gameStart() {
        Lang.broadcast("%s§aDas Spiel beginnt in 10 Sekunden!", "%s§aThe game starts in 10 seconds!", plugin.getMonstersPrefix());
        gameStartTask = new BukkitRunnable() {
            int i = 10;
            @Override
            public void run() {
                if(!userManager.isFull()) this.cancel();

                if (i <= 5) {
                    Lang.noteAll("%s§aDas Spiel startet in %d Sekunden!", "%s§aThe game starts in %d seconds!", plugin.getMonstersPrefix(), i);
                }

                if (i <= 1) {
                    monsterManager.startMonsterCheck();
                    gameConfig.setPlayerCount(Bukkit.getOnlinePlayers().size());
                    waveManager.start();

                    gameStates.setCurrentPhase(GamePhase.WAVE);
                    powerupManager.start();
                    plugin.getInjector().getInstance(de.ethicbuilds.monsters.event.WaveEvents.class).start();
                    scoreboardManager.startTime();
                    de.ethicbuilds.monsters.reward.RoundReward.start(plugin, () -> getCurrentPhase() == GamePhase.WAVE);

                    for (var gamePlayer : userManager.getGamePlayers()) {
                        // JoinMe only before the start, the slot stays empty for lying on the ground
                        gamePlayer.getPlayer().getInventory().setItem(de.ethicbuilds.monsters.gameplay.listener.JoinMeListener.SLOT, null);
                        for (var weapon : gamePlayer.getWeapons().values()) {
                            weapon.refill();
                            weapon.reload(gamePlayer.getPlayer());
                        }
                    }

                    broadcastTitle(plugin.getMonstersPrefix(), "§cÜberlebe so lange wie möglich!", "§cSurvive as long as you can!");

                    userManager.startMonsterTrackerTask();
                    plugin.getInjector().getInstance(de.ethicbuilds.monsters.player.manager.SpectatorHud.class).start();
                    if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                        plugin.getInjector().getInstance(TrackerGlow.class).start();
                    }

                    this.cancel();
                }

                i--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void interruptGameStart() {
        if (gameStartTask != null) {
            gameStartTask.cancel();
        }
    }

    // Who did what this round, best player first
    private void sendSummary() {
        List<GamePlayer> players = new ArrayList<>(userManager.getGamePlayers());
        players.sort(Comparator.comparingInt(GamePlayer::getKilledZombies).reversed());
        for (Player player : Bukkit.getOnlinePlayers()) summary(player, players).forEach(player::sendMessage);
        var rewards = de.ethicbuilds.monsters.reward.RoundReward.payOut(players, waveManager.getCurrentWave(), plugin.getMonstersPrefix());
        var challenges = DailyChallenge.check(players, waveManager.getCurrentWave());
        roundEndCard.show(players, waveManager.getCurrentWave(), rewards, challenges);
    }

    private List<String> summary(Player viewer, List<GamePlayer> players) {
        List<String> lines = new ArrayList<>();
        lines.add("§8§m                                              ");
        lines.add(Lang.f(viewer, "%s§fRunde vorbei auf §6%s §7· §fWelle §6%d", "%s§fRound over on §6%s §7· §fWave §6%d", plugin.getMonstersPrefix(),
                gameConfig.getMapName(), waveManager.getCurrentWave()));
        for (int i = 0; i < players.size(); i++) {
            GamePlayer gamePlayer = players.get(i);
            lines.add(Lang.f(viewer, "§%s%d. §f%s §8» §a%d Kills §8· §c%d Kopfschüsse §8· §d%d wiederbelebt §8· §6%,d Coins",
                    "§%s%d. §f%s §8» §a%d Kills §8· §c%d Headshots §8· §d%d Revives §8· §6%,d Coins",
                    i == 0 ? "6" : "7", i + 1, gamePlayer.getPlayer().getName(), gamePlayer.getKilledZombies(), gamePlayer.getHeadshots(),
                    gamePlayer.getRevives(), gamePlayer.getEarnedCoins()));
        }
        lines.addAll(awards(viewer, players));
        lines.add("§8§m                                              ");
        return lines;
    }

    // Best of the round in a few categories, only when someone did something there
    private List<String> awards(Player viewer, List<GamePlayer> players) {
        List<String> lines = new ArrayList<>();
        award(lines, viewer, players, Lang.t(viewer, "Meiste Kills", "Most kills"), GamePlayer::getKilledZombies);
        award(lines, viewer, players, Lang.t(viewer, "Meiste Kopfschüsse", "Most headshots"), GamePlayer::getHeadshots);
        award(lines, viewer, players, Lang.t(viewer, "Bester Retter", "Most revives"), GamePlayer::getRevives);
        award(lines, viewer, players, Lang.t(viewer, "Meiste Coins", "Most coins"), GamePlayer::getEarnedCoins);
        if (!lines.isEmpty()) lines.addFirst(plugin.getMonstersPrefix() + "§6§lMVP");
        return lines;
    }

    private void award(List<String> lines, Player viewer, List<GamePlayer> players, String title, java.util.function.ToIntFunction<GamePlayer> value) {
        players.stream().max(Comparator.comparingInt(value)).filter(best -> value.applyAsInt(best) > 0).ifPresent(best ->
                lines.add(Lang.f(viewer, "§7%s §8» §f%s §8(§e%,d§8)", "§7%s §8» §f%s §8(§e%,d§8)", title, best.getPlayer().getName(),
                        value.applyAsInt(best))));
    }

    public void gameEnd() {
        gameStates.setCurrentPhase(GamePhase.AFTER_GAME);
        perkManager.clear();
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            statisticManager.saveStats(gamePlayer);
        }

        HttpClient client = HttpClient.newHttpClient();

        var dto = new StopServerDto(plugin.getConfig().getString("server-name"));

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/gameManager/api/server/stop"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(dto)))
                .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(Lang.t(player, "§4Spiel Vorbei", "§4Game Over"), Lang.f(player, "§cIhr habt es bis Runde §7%d §cgeschafft!", "§cYou made it to wave §7%d§c!", waveManager.getCurrentWave()));
        }
        sendSummary();

        mapManager.getMapConfiguration().getSpawn().getWorld().strikeLightning(mapManager.getMapConfiguration().getSpawn());

        new BukkitRunnable() {
            int i = 10;

            @Override
            public void run() {
                if(i <= 5) {
                    Lang.broadcast("%s§cDer Server startet in §4%d §cSekuden neu", "%s§cServer restarts in §4%d §cseconds", plugin.getMonstersPrefix(), i);
                }

                if(Bukkit.getOnlinePlayers().isEmpty() || i <= 1) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:kill @e");

                    Lang.broadcast("%s§cDer Server staret §4jetzt §cneu!", "%s§cServer restarts §4now§c!", plugin.getMonstersPrefix());
                    Bukkit.shutdown();
                }
                i--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void loadGameConfig() {
        try(FileReader reader = new FileReader("gameConfig.json")) {
            gameConfig = gson.fromJson(reader, GameConfig.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        roundKey = String.valueOf(System.currentTimeMillis());
    }

    /** New for every loaded round, the proxy allows the creator's extra JoinMe once per round with it */
    public String getRoundKey() {
        return roundKey;
    }

    public GamePhase getCurrentPhase() {
        return gameStates.getCurrentPhase();
    }

    private void broadcastTitle(String s, String de, String en) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(s, Lang.t(p, de, en));
        }
    }
}
