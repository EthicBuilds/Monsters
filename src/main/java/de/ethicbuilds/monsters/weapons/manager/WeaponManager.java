package de.ethicbuilds.monsters.weapons.manager;

import de.ethicbuilds.monsters.weapons.Pumpgun;
import de.ethicbuilds.monsters.weapons.Rifle;
import de.ethicbuilds.monsters.weapons.Sniper;
import de.ethicbuilds.monsters.weapons.Weapon;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class WeaponManager {
    private final Map<String, Class<? extends Weapon>> weaponNames = new HashMap<>(Map.ofEntries(
            Map.entry("schrot", Pumpgun.class),
            Map.entry("sturmgewehr", Rifle.class),
            Map.entry("sniper", Sniper.class)
    ));
}
