package de.ethicbuilds.monsters.map;

import de.ethicbuilds.monsters.map.elements.Door;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

import java.util.List;

@Getter
public class MapConfiguration {

    //Empty Constructor for Gson
    public MapConfiguration() {}

    private Location spawn;
    @Setter
    private List<Door> doors;
}
