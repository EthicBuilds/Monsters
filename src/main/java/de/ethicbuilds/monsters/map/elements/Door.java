package de.ethicbuilds.monsters.map.elements;

import lombok.Getter;
import org.bukkit.Location;

import java.util.List;

@Getter
public class Door {
    public Door(String name, List<Location> clickableLocations, Location holoLocation) {
        this.name = name;
        this.clickableLocations = clickableLocations;
        this.holoLocation = holoLocation;
    }

    private String name;
    private List<Location> clickableLocations;
    private Location holoLocation;
}
