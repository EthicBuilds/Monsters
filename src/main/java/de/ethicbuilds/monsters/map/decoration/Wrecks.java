package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.gameplay.manager.DoorManager;
import de.ethicbuilds.monsters.gameplay.manager.WaveManager;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.elements.Door;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/***
 * Car wrecks of the map: searched with the crowbar once per wave, inside is what a crate has
 */
public class Wrecks {
    public static final String STATION = "wrack#";

    @Inject private DoorManager doorManager;
    @Inject private WaveManager waveManager;
    @Inject private CrateManager crateManager;

    private final List<Wreck> wrecks = new ArrayList<>();

    private static final class Wreck {
        private final List<Location> blocks;
        private final Location center;
        private int searchedWave = -1;

        private Wreck(List<Location> blocks, Location center) {
            this.blocks = blocks;
            this.center = center;
        }
    }

    static boolean isWreck(String id) {
        return id.startsWith("monsters_autowrack") || id.startsWith("monsters_wrack_");
    }

    /** The barriers around the furniture block are the car body, the hitbox sits in their middle; returns the station */
    String register(Block block) {
        List<Location> blocks = new ArrayList<>();
        double x = 0, y = 0, z = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block part = block.getRelative(dx, dy, dz);
                    if (part.getType() != Material.BARRIER) continue;
                    blocks.add(part.getLocation());
                    x += dx;
                    y += dy;
                    z += dz;
                }
            }
        }
        if (blocks.isEmpty()) blocks.add(block.getLocation());
        int n = blocks.size();
        Location center = block.getLocation().add(0.5 + x / n, 0, 0.5 + z / n);
        wrecks.add(new Wreck(blocks, center));
        return STATION + (wrecks.size() - 1);
    }

    Location center(String station) {
        Wreck wreck = wreck(station);
        return wreck == null ? null : wreck.center;
    }

    public void search(GamePlayer gamePlayer, String station) {
        Wreck wreck = wreck(station);
        if (wreck == null) return;
        Player player = gamePlayer.getPlayer();
        if (!GameItems.isCrowbar(player.getInventory().getItemInMainHand())) {
            Lang.note(player, Lang.t(player, "§7Durchsuche das Wrack mit der §6Brechstange", "§7Search the wreck with the §6Crowbar"));
            Sounds.deny(player);
            return;
        }
        int wave = waveManager.getCurrentWave();
        if (wreck.searchedWave == wave) {
            Lang.note(player, Lang.t(player, "§7Schon durchsucht · nächste Welle wieder", "§7Already searched · again next wave"));
            Sounds.deny(player);
            return;
        }
        Door body = new Door("Wrack", new ArrayList<>(wreck.blocks), wreck.center);
        doorManager.breakOpen(gamePlayer, body, () -> {
            if (wreck.searchedWave == waveManager.getCurrentWave()) return;
            wreck.searchedWave = waveManager.getCurrentWave();
            Location at = wreck.center.clone().add(0, 1, 0);
            at.getWorld().playSound(at, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0F, 0.7F);
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 6, 0.6, 0.3, 0.6, 0.01);
            crateManager.search(gamePlayer, at);
        });
    }

    private Wreck wreck(String station) {
        try {
            int index = Integer.parseInt(station.substring(STATION.length()));
            return index >= 0 && index < wrecks.size() ? wrecks.get(index) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
