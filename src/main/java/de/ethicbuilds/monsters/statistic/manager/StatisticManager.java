package de.ethicbuilds.monsters.statistic.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.statistic.dto.RoundStatsDto;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatisticManager {
    @Inject
    private GameManager gameManager;
    @Inject
    private WaveManager waveManager;
    @Inject
    private UserManager userManager;

    private Map<UUID, RoundStatsDto> roundStats = new HashMap<>();
    private Gson gson;

    public StatisticManager() {
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void saveStats(GamePlayer gamePlayer) {
        roundStats.put(
                gamePlayer.getPlayer().getUniqueId(),
                new RoundStatsDto(
                        gamePlayer.getPlayer().getUniqueId(),
                        gameManager.getGameConfig().getMapName(),
                        waveManager.getCurrentWave(),
                        gamePlayer.getKilledZombies()
                )
        );

        if (roundStats.size() == userManager.getGamePlayers().size()) sendRoundStats();
    }

    private void sendRoundStats() {
        try {
            HttpClient client = HttpClient.newHttpClient();


            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/gameManager/api/statistic/save"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(roundStats.values())))
                    .build();


            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                System.err.println("Something went wrong!");
                System.err.println(response.body());
                System.err.println(response.statusCode());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
