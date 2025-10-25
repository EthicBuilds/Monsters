package de.ethicbuilds.monsters.map.elements;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

import java.util.List;

@Getter
public class MonsterSpawner {
    public MonsterSpawner(String areaName, List<Location> locations) {
        this.areaName = areaName;
        this.locations = locations;
    }

    private String areaName;
    @Setter
    private boolean isActive;
    private List<Location> locations;
}
