package de.ethicbuilds.monsters.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileReader;

public class MapManager {
    private final Gson gson;

    private MapConfiguration mapConfiguration;

    public MapManager() {
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void loadMapConfig() {
        try(FileReader reader = new FileReader("Path")) {
            mapConfiguration = gson.fromJson(reader, MapConfiguration.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
