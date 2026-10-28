package de.ethicbuilds.monsters.gameplay.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.dto.StopServerDto;
import de.ethicbuilds.monsters.gameplay.model.GameConfig;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.repository.GameStates;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.adapter.LocationAdapter;
import de.ethicbuilds.monsters.monster.TrackerGlow;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.reward.DailyChallenge;
import de.ethicbuilds.monsters.reward.EthicCoins;
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
    private static final long WINNER_ETHICCOINS = 50;

    @Inject private Main plugin;
    @Inject private UserManager userManager;
    @Inject private GameStates gameStates;
    @Inject private WaveManager waveManager;
    @Inject private ScoreboardManager scoreboardManager;
    @Inject private MapManager mapManager;
    @Inject private MonsterManager monsterManager;
    @Inject private StatisticManager statisticManager;
    @Inject private PowerupManager powerupManager;

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
        broadcastMessage(String.format("%s§aDas Spiel beginnt in 20 Sekunden!", plugin.getMonstersPrefix()));
        gameStartTask = new BukkitRunnable() {
            int i = 20;
            @Override
            public void run() {
                if(!userManager.isFull()) this.cancel();

                if (i <= 5) {
                    broadcastMessage(String.format("%s§aDas spiel startet in %d Sekunden!", plugin.getMonstersPrefix(), i));
                }

                if (i <= 1) {
                    monsterManager.startMonsterCheck();
                    gameConfig.setPlayerCount(Bukkit.getOnlinePlayers().size());
                    waveManager.start();

                    gameStates.setCurrentPhase(GamePhase.WAVE);
                    powerupManager.start();
                    scoreboardManager.startTime();

                    for (var gamePlayer : userManager.getGamePlayers()) {
                        // JoinMe only before the start, the slot stays empty for lying on the ground
                        gamePlayer.getPlayer().getInventory().setItem(de.ethicbuilds.monsters.gameplay.listener.JoinMeListener.SLOT, null);
                        for (var weapon : gamePlayer.getWeapons().values()) {
                            weapon.refill();
                            weapon.reload(gamePlayer.getPlayer());
                        }
                    }

                    broadcastTitle(plugin.getMonstersPrefix(), "§cÜberlebe so lange wie möglich!");

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
        List<String> lines = new ArrayList<>();
        lines.add("§8§m                                              ");
        lines.add(String.format("%s§fRunde vorbei auf §6%s §7· §fWelle §6%d", plugin.getMonstersPrefix(),
                gameConfig.getMapName(), waveManager.getCurrentWave()));
        for (int i = 0; i < players.size(); i++) {
            GamePlayer gamePlayer = players.get(i);
            lines.add(String.format("§%s%d. §f%s §8» §a%d Kills §8· §c%d Kopfschüsse §8· §d%d wiederbelebt §8· §6%s Coins",
                    i == 0 ? "6" : "7", i + 1, gamePlayer.getPlayer().getName(), gamePlayer.getKilledZombies(), gamePlayer.getHeadshots(),
                    gamePlayer.getRevives(), String.format(java.util.Locale.GERMANY, "%,d", gamePlayer.getEarnedCoins())));
        }
        lines.add("§8§m                                              ");
        for (Player player : Bukkit.getOnlinePlayers()) lines.forEach(player::sendMessage);
        rewardWinner(players);
        DailyChallenge.check(players, waveManager.getCurrentWave());
    }

    // The best player of the round wins and gets EthicCoins for the network
    private void rewardWinner(List<GamePlayer> ranked) {
        if (ranked.isEmpty()) return;
        Player winner = ranked.getFirst().getPlayer();
        if (!winner.isOnline()) return;
        long coins = EthicCoins.withRankBonus(winner, WINNER_ETHICCOINS);
        if (!EthicCoins.deposit(winner.getUniqueId(), coins, "Monsters Rundensieg")) return;
        Bukkit.broadcastMessage(String.format("%s§6%s §7gewinnt die Runde und bekommt §6%d EthicCoins§7!", plugin.getMonstersPrefix(), winner.getName(), coins));
    }

    public void gameEnd() {
        gameStates.setCurrentPhase(GamePhase.AFTER_GAME);
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
            player.sendTitle("§4Spiel Vorbei", String.format("§cIhr habt es bis Runde §7%d §cgeschaft!", waveManager.getCurrentWave()));
        }
        sendSummary();

        mapManager.getMapConfiguration().getSpawn().getWorld().strikeLightning(mapManager.getMapConfiguration().getSpawn());

        new BukkitRunnable() {
            int i = 10;

            @Override
            public void run() {
                if(i <= 5) {
                    broadcastMessage(String.format("%s§cDer Server startet in §4%d §cSekuden neu", plugin.getMonstersPrefix(), i));
                }

                if(Bukkit.getOnlinePlayers().isEmpty() || i <= 1) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:kill @e");

                    broadcastMessage(String.format("%s§cDer Server staret §4jetzt §cneu!", plugin.getMonstersPrefix()));
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

    private void broadcastMessage(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    private void broadcastTitle(String s, String s2) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(s, s2);
        }
    }
}
