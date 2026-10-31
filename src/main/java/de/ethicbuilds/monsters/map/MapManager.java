package de.ethicbuilds.monsters.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.listener.WaveListener;
import de.ethicbuilds.monsters.map.adapter.LocationAdapter;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.map.elements.Door;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.map.elements.WeaponPoint;
import de.ethicbuilds.monsters.weapons.Weapon;
import de.ethicbuilds.monsters.weapons.manager.WeaponManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapManager {
    private final Gson gson;

    @Getter
    private MapConfiguration mapConfiguration;

    @Inject
    public Main plugin;
    @Inject
    private WeaponManager weaponManager;
    @Inject
    private DecorationManager decorationManager;

    public MapManager() {
        gson = new GsonBuilder()
                .registerTypeAdapter(Location.class, new LocationAdapter())
                .setPrettyPrinting()
                .create();
    }

    public void loadMapConfig() {
        try(FileReader reader = new FileReader("mapConfig.json")) {
            mapConfiguration = gson.fromJson(reader, MapConfiguration.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Entity entity : plugin.getWorld().getEntities()) {
            if (!(entity instanceof Player) && !DecorationManager.isFurniture(entity)) entity.remove();
        }
        decorationManager.registerLoaded(plugin.getWorld());
        plugin.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        plugin.getWorld().setGameRule(GameRule.MAX_ENTITY_CRAMMING, 0);
        // Always night
        plugin.getWorld().setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        plugin.getWorld().setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        plugin.getWorld().setStorm(false);
        // Like /time set night, not midnight
        plugin.getWorld().setTime(13000);

        createDoorHolos();
        // Maps with weapon crates as decoration do not show the old weapon points
        if (!Boolean.TRUE.equals(mapConfiguration.getHideWeaponPoints())) createWeaponPointHolos();
    }

    public void createMapConfig() {
        World world = plugin.getWorld();

        MapConfiguration mapConfiguration = new MapConfiguration();

        mapConfiguration.setSpawn(new Location(world, 0, 72, 0));

        var targetBlocks = findTargetBlocks(mapConfiguration.getSpawn());

        var spawners = findSpawners(targetBlocks);
        var doors = findDoors(targetBlocks);
        var weaponPoints = findWeaponPoints(targetBlocks);

        mapConfiguration.setSpawners(spawners);
        mapConfiguration.setDoors(doors);
        mapConfiguration.setWeaponPoints(weaponPoints);

        plugin.getLogger().info(String.format("Found %d Target Blocks, %d Spawner, %d Doors, %d Weapon Points",
                targetBlocks.size(), spawners.size(), doors.size(), weaponPoints.size()));

        try (FileWriter writer = new FileWriter("mapConfig.json")) {
            gson.toJson(mapConfiguration, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("mapConfig.json not written: " + e.getMessage());
        }
    }

    private void createDoorHolos() {
        for (Door door : mapConfiguration.getDoors()) decorationManager.doorSign(door, WaveListener.DOOR_PRICE);
    }

    // The old points on the wall: a station like the weapon crates, so the click buys or refills as before
    private void createWeaponPointHolos() {
        for (WeaponPoint weaponPoint : mapConfiguration.getWeaponPoints()) {
            boolean first = true;
            for (Location location : weaponPoint.getLocation()) {
                location.getBlock().setType(Material.AIR);
                decorationManager.weaponPoint(location.getBlock(), weaponPoint.getWeaponName().toLowerCase(), first);
                first = false;
            }
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:kill @e[type=minecraft:item]");
    }

    private List<Location> findTargetBlocks(Location center) {
        World world = center.getWorld();
        List<Location> targets = new ArrayList<>();

        int startX = center.getBlockX() - 500;
        int endX = center.getBlockX() + 500;
        int startY = Math.max(center.getBlockY() - 50, world.getMinHeight());
        int endY = Math.min(center.getBlockY() + 50, world.getMaxHeight());
        int startZ = center.getBlockZ() - 500;
        int endZ = center.getBlockZ() + 500;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.TARGET) {
                        targets.add(block.getLocation());
                    }
                }
            }
        }

        return targets;
    }

    private List<MonsterSpawner> findSpawners(List<Location> targetBlocks) {
        List<MonsterSpawner> foundSpawner = new ArrayList<>();
        if (targetBlocks.isEmpty()) return foundSpawner;

        Set<String> blockPositions = new HashSet<>();
        for (Location loc : targetBlocks) {
            blockPositions.add(key(loc));
        }

        World world = targetBlocks.get(0).getWorld();
        Set<String> processed = new HashSet<>();

        for (Location loc : targetBlocks) {
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            if (processed.contains(key(world, x, y, z))) continue;

            if (blockPositions.contains(key(world, x + 1, y, z)) &&
                    blockPositions.contains(key(world, x, y, z + 1)) &&
                    blockPositions.contains(key(world, x + 1, y, z + 1))) {

                List<Location> patternBlocks = new ArrayList<>();
                patternBlocks.add(new Location(world, x, y, z));
                patternBlocks.add(new Location(world, x + 1, y, z));
                patternBlocks.add(new Location(world, x, y, z + 1));
                patternBlocks.add(new Location(world, x + 1, y, z + 1));

                for (Location p : patternBlocks) processed.add(key(p));

                for (Location p : patternBlocks) {
                    Block above = world.getBlockAt(p.getBlockX(), p.getBlockY() + 1, p.getBlockZ());
                    if (above.getState() instanceof Sign sign) {
                        StringBuilder text = new StringBuilder();
                        for (String line : sign.getLines()) {
                            if (!line.isBlank()) text.append(line).append(" ");
                        }

                        var spawner = new MonsterSpawner(
                                text.toString().trim().toLowerCase(),
                                patternBlocks);

                        if (spawner.getAreaName().equals("spawn")) {
                            spawner.setActive(true);
                        }

                        foundSpawner.add(spawner);
                        break;
                    }
                }
            }
        }

        return foundSpawner;
    }

    private List<Door> findDoors(List<Location> targetBlocks) {
        List<Door> foundDoors = new ArrayList<>();
        if (targetBlocks.isEmpty()) return foundDoors;

        World world = targetBlocks.get(0).getWorld();
        Set<String> blockPositions = new HashSet<>();
        for (Location loc : targetBlocks) blockPositions.add(key(loc));

        Set<String> processed = new HashSet<>();

        for (Location loc : targetBlocks) {
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            if (processed.contains(key(world, x, y, z))) continue;

            if (blockPositions.contains(key(world, x + 1, y, z)) &&
                    blockPositions.contains(key(world, x + 2, y, z))) {

                List<Location> pattern = List.of(
                        new Location(world, x, y, z),
                        new Location(world, x + 1, y, z),
                        new Location(world, x + 2, y, z)
                );

                pattern.forEach(p -> processed.add(key(p)));

                List<Location> fullColumnBlocks = new ArrayList<>();
                for (Location base : pattern) {
                    for (int i = 0; i <= 5; i++) {
                        fullColumnBlocks.add(base.clone().add(0, i, 0));
                    }
                }

                Location middle = pattern.get(1);

                Block signBlock = null;
                if (isSign(world.getBlockAt(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ() + 1))) {
                    signBlock = world.getBlockAt(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ() + 1);
                } else if (isSign(world.getBlockAt(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ() - 1))) {
                    signBlock = world.getBlockAt(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ() - 1);
                }

                String signText = null;
                Location aboveSign = null;

                if (signBlock != null) {
                    Sign sign = (Sign) signBlock.getState();
                    StringBuilder text = new StringBuilder();
                    for (String line : sign.getLines()) {
                        if (!line.isBlank()) text.append(line).append(" ");
                    }
                    signText = text.toString().trim();
                    aboveSign = signBlock.getLocation().add(0, 3, 0);
                }

                Door door = new Door(signText, fullColumnBlocks, aboveSign);

                foundDoors.add(door);
                continue;
            }

            if (blockPositions.contains(key(world, x, y, z + 1)) &&
                    blockPositions.contains(key(world, x, y, z + 2))) {

                List<Location> pattern = List.of(
                        new Location(world, x, y, z),
                        new Location(world, x, y, z + 1),
                        new Location(world, x, y, z + 2)
                );

                pattern.forEach(p -> processed.add(key(p)));

                List<Location> fullColumnBlocks = new ArrayList<>();
                for (Location base : pattern) {
                    for (int i = 0; i <= 5; i++) {
                        fullColumnBlocks.add(base.clone().add(0, i, 0));
                    }
                }

                Location middle = pattern.get(1);

                Block signBlock = null;
                if (isSign(world.getBlockAt(middle.getBlockX() + 1, middle.getBlockY(), middle.getBlockZ()))) {
                    signBlock = world.getBlockAt(middle.getBlockX() + 1, middle.getBlockY(), middle.getBlockZ());
                } else if (isSign(world.getBlockAt(middle.getBlockX() - 1, middle.getBlockY(), middle.getBlockZ()))) {
                    signBlock = world.getBlockAt(middle.getBlockX() - 1, middle.getBlockY(), middle.getBlockZ());
                }

                String signText = null;
                Location aboveSign = null;

                if (signBlock != null) {
                    Sign sign = (Sign) signBlock.getState();
                    StringBuilder text = new StringBuilder();
                    for (String line : sign.getLines()) {
                        if (!line.isBlank()) text.append(line).append(" ");
                    }
                    signText = text.toString().trim();
                    aboveSign = signBlock.getLocation().add(0, 3, 0);
                }

                Door door = new Door(signText, fullColumnBlocks, aboveSign);

                foundDoors.add(door);
            }
        }

        return foundDoors;
    }

    private List<WeaponPoint> findWeaponPoints(List<Location> targetBlocks) {
        List<WeaponPoint> weaponPoints = new ArrayList<>();
        if (targetBlocks.isEmpty()) return weaponPoints;

        World world = targetBlocks.get(0).getWorld();
        Set<String> blockPositions = new HashSet<>();
        for (Location loc : targetBlocks) blockPositions.add(key(loc));

        Set<String> processed = new HashSet<>();

        for (Location loc : targetBlocks) {
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            if (processed.contains(key(world, x, y, z))) continue;

            if (blockPositions.contains(key(world, x, y + 1, z))) {

                List<Location> pattern = List.of(
                        new Location(world, x, y, z),
                        new Location(world, x, y + 1, z)
                );

                pattern.forEach(p -> processed.add(key(p)));

                Location top = pattern.get(1);
                Block signBlock = null;

                Block[] sides = new Block[] {
                        world.getBlockAt(top.getBlockX() + 1, top.getBlockY(), top.getBlockZ()),
                        world.getBlockAt(top.getBlockX() - 1, top.getBlockY(), top.getBlockZ()),
                        world.getBlockAt(top.getBlockX(), top.getBlockY(), top.getBlockZ() + 1),
                        world.getBlockAt(top.getBlockX(), top.getBlockY(), top.getBlockZ() - 1)
                };

                for (Block side : sides) {
                    if (isSign(side)) {
                        signBlock = side;
                        break;
                    }
                }

                if (signBlock != null) {
                    Sign sign = (Sign) signBlock.getState();
                    StringBuilder text = new StringBuilder();
                    for (String line : sign.getLines()) {
                        if (!line.isBlank()) text.append(line).append(" ");
                    }

                    String signText = text.toString().trim();
                    weaponPoints.add(new WeaponPoint(pattern, signText));
                }
            }
        }

        return weaponPoints;
    }


    private boolean isSign(Block block) {
        return block.getType() == Material.OAK_SIGN || block.getType() == Material.OAK_WALL_SIGN;
    }

    private String key(Location loc) {
        return key(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private String key(World world, int x, int y, int z) {
        return world.getName() + ":" + x + "," + y + "," + z;
    }
}