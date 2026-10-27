package de.ethicbuilds.monsters.gameplay.manager;

import com.google.gson.Gson;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.map.MapManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/***
 * The server starts without a round and waits. As soon as the GameManager has copied the map and written
 * gameConfig.json, the map is loaded as world "round" and the round can begin. Heartbeats tell the GameManager
 * whether this server is waiting or has its round.
 */
public class RoundLoader {
    public static final String ROUND_WORLD = "round";
    private static final String STATUS_URL = "http://localhost:8080/gameManager/api/server/status";

    @Inject private Main plugin;
    @Inject private MapManager mapManager;
    @Inject private GameManager gameManager;

    @Getter
    private boolean loaded;
    private BukkitTask waitTask;
    private final HttpClient client = HttpClient.newHttpClient();

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::heartbeat, 20L, 100L);
        if (configReady()) {
            load();
            return;
        }
        waitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (configReady()) load();
        }, 10L, 10L);
    }

    private boolean configReady() {
        return new File("gameConfig.json").isFile() && new File("mapConfig.json").isFile() && new File(ROUND_WORLD, "level.dat").isFile();
    }

    private void load() {
        if (waitTask != null) waitTask.cancel();
        World world = new WorldCreator(ROUND_WORLD).createWorld();
        plugin.setWorld(world);
        mapManager.loadMapConfig();
        gameManager.loadGameConfig();
        gameManager.startPlayerCheck();
        loaded = true;
        heartbeat();
        plugin.getLogger().info("Runde geladen: " + gameManager.getGameConfig().getMapName());
    }

    private void heartbeat() {
        String body = new Gson().toJson(Map.of(
                "serverName", plugin.getConfig().getString("server-name", ""),
                "state", loaded ? "round" : "waiting"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(STATUS_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
}
