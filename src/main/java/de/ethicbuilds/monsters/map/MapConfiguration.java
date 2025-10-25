package de.ethicbuilds.monsters.map;

import de.ethicbuilds.monsters.map.elements.Door;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.map.elements.WeaponPoint;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

import java.util.List;

@Getter @Setter
public class MapConfiguration {

    //Empty Constructor for Gson
    public MapConfiguration() {}

    private Location spawn;
    private List<Door> doors;
    private List<MonsterSpawner> spawners;
    private List<WeaponPoint> weaponPoints;
}
