package de.ethicbuilds.monsters.gameplay.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public class GameConfig {

    /***
     * Empty Constructor for Gson
     */
    public GameConfig() {

    }

    @Setter
    private int playerCount;
    private String mapName;
    private List<String> mapBuilder;
}
