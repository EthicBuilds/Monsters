package de.ethicbuilds.monsters.gameplay.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.dto.StopServerDto;
import de.ethicbuilds.monsters.event.WaveEvents;
import de.ethicbuilds.monsters.gameplay.RoundEndCard;
import de.ethicbuilds.monsters.gameplay.listener.JoinMeListener;
import de.ethicbuilds.monsters.gameplay.model.GameConfig;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.adapter.LocationAdapter;
import de.ethicbuilds.monsters.monster.TrackerGlow;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.perk.PerkManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.SpectatorHud;
import de.ethicbuilds.monsters.reward.DailyChallenge;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.powerup.PowerupManager;
import de.ethicbuilds.monsters.reward.RoundReward;
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
import java.time.Duration;
import java.util.function.ToIntFunction;


public class GameManager {

    private static final String START_COLOR = "#3CDC6E";
    private static final int END_SECONDS = 30;
    private static final String STOP_URL = "http://localhost:8080/gameManager/api/server/stop";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

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
    private int startIn;
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
                if (getCurrentPhase() != GamePhase.PRE_GAME) {
                    this.cancel();
                    return;
                }
                if (Bukkit.getOnlinePlayers().isEmpty()) {
                    if (System.currentTimeMillis() - lastPlayerOnlineTime >= 30_000) {
                        requestStop();
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
                if (!userManager.isFull()) {
                    startIn = 0;
                    this.cancel();
                    return;
                }
                startIn = i;

                if (i <= 5) {
                    String count = Overlay.num(i);
                    Overlay.mediumAll(START_COLOR, Overlay.line(Overlay.START, START_COLOR, "START", "in " + count + " Sekunden"),
                            Overlay.line(Overlay.START, START_COLOR, "START", "in " + count + " seconds"));
                }

                if (i <= 1) {
                    monsterManager.startMonsterCheck();
                    gameConfig.setPlayerCount(Bukkit.getOnlinePlayers().size());
                    waveManager.start();

                    gameStates.setCurrentPhase(GamePhase.WAVE);
                    powerupManager.start();
                    plugin.getInjector().getInstance(WaveEvents.class).start();
                    scoreboardManager.startTime();
                    RoundReward.start(plugin, () -> getCurrentPhase() == GamePhase.WAVE);

                    for (var gamePlayer : userManager.getGamePlayers()) {
                        // JoinMe only before the start, the slot stays empty for lying on the ground
                        gamePlayer.getPlayer().getInventory().setItem(JoinMeListener.SLOT, null);
                        for (var weapon : gamePlayer.getWeapons().values()) {
                            weapon.refill();
                            weapon.reload(gamePlayer.getPlayer());
                        }
                    }

                    Overlay.mediumAll(START_COLOR, Overlay.line(Overlay.START, START_COLOR, "LOS!", "Überlebt so lange wie möglich"),
                            Overlay.line(Overlay.START, START_COLOR, "GO!", "survive as long as you can"));

                    startIn = 0;
                    userManager.startMonsterTrackerTask();
                    plugin.getInjector().getInstance(SpectatorHud.class).start();
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
        startIn = 0;
    }

    /** Seconds until the start while the countdown runs, otherwise 0 */
    public int getStartIn() {
        return startIn;
    }

    // Who did what this round, best player first
    private void sendSummary() {
        List<GamePlayer> players = new ArrayList<>(userManager.getGamePlayers());
        players.sort(Comparator.comparingInt(GamePlayer::getKilledZombies).reversed());
        for (Player player : Bukkit.getOnlinePlayers()) summary(player, players).forEach(player::sendMessage);
        var rewards = RoundReward.payOut(players, waveManager.getCurrentWave(), plugin.getMonstersPrefix());
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

    private void award(List<String> lines, Player viewer, List<GamePlayer> players, String title, ToIntFunction<GamePlayer> value) {
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

        requestStop();

        for (Player player : Bukkit.getOnlinePlayers()) {
            String wave = Overlay.num(waveManager.getCurrentWave());
            Overlay.big(player, Overlay.RED, Overlay.glyph(Overlay.GAME_OVER, Overlay.WHITE), Overlay.glyph(Overlay.GAME_OVER_EN, Overlay.WHITE),
                    Overlay.line(Overlay.FINISH, Overlay.RED, "Welle " + wave, "so weit habt ihr es geschafft"),
                    Overlay.line(Overlay.FINISH, Overlay.RED, "Wave " + wave, "that is how far you made it"));
        }
        sendSummary();

        mapManager.getMapConfiguration().getSpawn().getWorld().strikeLightning(mapManager.getMapConfiguration().getSpawn());

        // 30 seconds to look at the round end card, the last 5 counted down
        new BukkitRunnable() {
            int i = END_SECONDS;

            @Override
            public void run() {
                if(i <= 5) {
                    String count = Overlay.num(i);
                    Overlay.smallAll(Overlay.icon(Overlay.RESTART, Overlay.GREY, "<color:" + Overlay.GREY + ">Neustart in " + count + " Sekunden</color>"),
                            Overlay.icon(Overlay.RESTART, Overlay.GREY, "<color:" + Overlay.GREY + ">Restart in " + count + " seconds</color>"));
                }

                if(Bukkit.getOnlinePlayers().isEmpty() || i <= 1) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:kill @e");

                    Bukkit.shutdown();
                }
                i--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Tells the GameManager that this server stops; the round end goes on also when it does not answer */
    public void requestStop() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(STOP_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(new StopServerDto(plugin.getConfig().getString("server-name")))))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            plugin.getLogger().info("Stopp an den GameManager gemeldet: " + response.statusCode());
        } catch (IOException e) {
            plugin.getLogger().warning("GameManager nicht erreichbar: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

}
