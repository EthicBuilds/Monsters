package de.ethicbuilds.monsters.map.elements;

import lombok.Getter;
import org.bukkit.Location;

import java.util.List;

@Getter
public class WeaponPoint {
    public WeaponPoint(List<Location> location, String weaponName) {
        this.location = location;
        this.weaponName = weaponName;
    }

    private List<Location> location;
    private String weaponName;
}
