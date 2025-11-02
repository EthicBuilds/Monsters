package de.ethicbuilds.monsters.gameplay.listener;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.map.MapConfiguration;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.elements.Door;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.map.elements.WeaponPoint;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.Weapon;
import de.ethicbuilds.monsters.weapons.manager.WeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;

public class WaveListener implements Listener {
    @Inject private GameManager gameManager;
    @Inject private MonsterManager monsterManager;
    @Inject private UserManager userManager;
    @Inject private MapManager mapManager;
    @Inject private WeaponManager weaponManager;
    @Inject private Main plugin;

    private boolean isAxeCooldDown;

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE
                || !(event.getEntity() instanceof Monster monster)
                || !(event.getDamager() instanceof Player player)) return;

        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null) return;

        if (userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        EnemyMonster enemyMonster = monsterManager.getEnemyMonster(monster);
        if (enemyMonster == null) return;

        if (player.getInventory().getItemInMainHand().getType() == Material.AIR) return;

        if (player.getInventory().getItemInMainHand().getType() == Material.WOODEN_AXE && isAxeCooldDown) return;

        if (player.getInventory().getItemInMainHand().getType() == Material.WOODEN_AXE) {
            startAxeCoolDown();
        }

        if (event.getFinalDamage() < monster.getHealth()) gamePlayer.setKilledZombies(gamePlayer.getKilledZombies() + 1);

        gamePlayer.addCoins(enemyMonster.getCoin());
        createFloatingHologram(enemyMonster.getMonster().getLocation(), String.format("§6+ %d Coins", enemyMonster.getCoin()));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        event.getDrops().clear();
        event.setDroppedExp(0);

        if (gameManager.getCurrentPhase() != GamePhase.WAVE
                || !(event.getEntity() instanceof Monster monster)) return;

        EnemyMonster enemyMonster = monsterManager.getEnemyMonster(monster);
        if (enemyMonster == null) return;

        monsterManager.removeMonster(enemyMonster);
        if (monsterManager.getMonsters().size() <= 3) {
            for (EnemyMonster m : monsterManager.getMonsters().values()) {
                m.getMonster().addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(EntityDeathEvent event) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE || !(event.getEntity() instanceof Player player)) return;

        userManager.killPlayer(player.getUniqueId());
        event.setCancelled(true);
    }


    @EventHandler
    public void onPlayerInteractWithMap(PlayerInteractEvent event) {
        event.setCancelled(true);

        if (gameManager.getCurrentPhase() != GamePhase.WAVE || event.getClickedBlock() == null) return;

        MapConfiguration mapConfiguration = mapManager.getMapConfiguration();

        GamePlayer gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;

        Location location = event.getClickedBlock().getLocation();

        for (Door door : mapConfiguration.getDoors()) {
            for (Location loc : door.getClickableLocations()) {
                if (loc.equals(location)) {
                    openDoor(gamePlayer, door);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractWithWeaponPoint(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) return;

        GamePlayer gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());

        if (gamePlayer == null || userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;

        WeaponPoint weaponPoint = mapManager.getMapConfiguration().getWeaponPoints().stream()
                .filter(wp -> wp.getLocation().stream()
                        .anyMatch(loc -> isSameBlockLocation(loc, armorStand.getLocation())))
                .findFirst()
                .orElse(null);

        if (weaponPoint == null) return;

        buyOrReloadWeapon(gamePlayer, weaponPoint);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE) return;

        userManager.removePlayer(event.getPlayer().getUniqueId());

        if (userManager.getGamePlayers().size() == userManager.getSpectators().size()) {
            gameManager.gameEnd();
        }
    }


    private boolean isSameBlockLocation(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) return false;
        if (!loc1.getWorld().equals(loc2.getWorld())) return false;
        return loc1.getBlockX() == loc2.getBlockX()
                && loc1.getBlockY() == loc2.getBlockY()
                && loc1.getBlockZ() == loc2.getBlockZ();
    }


    private void openDoor(GamePlayer gamePlayer, Door door) {
        if (gamePlayer.getCoins() < 1000) {
            gamePlayer.getPlayer().sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum diese Türe zu öffnen", 1000 - gamePlayer.getCoins()));
            return;
        }

        gamePlayer.setCoins(gamePlayer.getCoins() - 1000);

        int i = 0;
        for (Location location : door.getClickableLocations()) {
            i++;
            if (i == 1 || i == 2) continue;

            else if (i == 6) {
                i = 0;
            }

            location.getBlock().setType(Material.AIR);
        }
        door.getClickableLocations().clear();
        removeHolo(door.getHoloLocation());
        removeHolo(door.getHoloLocation().clone().subtract(0, 1, 0));

        Bukkit.broadcastMessage(String.format("%s§a%s hat den Bereich §7%s §afreigeschaltet!", plugin.getMonstersPrefix(), gamePlayer.getPlayer().getDisplayName(), door.getName()));

        for (MonsterSpawner monsterSpawner : mapManager.getMapConfiguration().getSpawners()) {
            if (monsterSpawner.getAreaName().equalsIgnoreCase(door.getName())) {
                monsterSpawner.setActive(true);
            }
        }
    }

    private void removeHolo(Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        World world = loc.getWorld();

        for (Entity e : world.getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            Location eloc = e.getLocation();
            if (eloc.getBlockX() == x && eloc.getBlockY() == y && eloc.getBlockZ() == z && eloc.getWorld().equals(world)) {
                if (e instanceof ArmorStand) ((ArmorStand) e).setHealth(0.0);
            }
        }
    }

    private void buyOrReloadWeapon(GamePlayer gamePlayer, WeaponPoint weaponPoint) {
        Weapon weaponPointWeapon;

        try {
            weaponPointWeapon = weaponManager.getWeaponNames()
                    .get(weaponPoint.getWeaponName().toLowerCase())
                    .getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        Weapon playerWeapon = gamePlayer.getWeapons().get(weaponPointWeapon.getItem().getType());

        if (playerWeapon == null) {
            if (gamePlayer.getCoins() < 1000) {
                gamePlayer.getPlayer().sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum diese Waffe zu kaufen", 1000 - gamePlayer.getCoins()));
                return;
            }

            for (int i : gamePlayer.getWeaponSlots()) {
                if (gamePlayer.getPlayer().getInventory().getItem(i).getType().equals(Material.LIGHT_GRAY_DYE)) {
                    gamePlayer.getPlayer().getInventory().setItem(i, weaponPointWeapon.getItem());

                    gamePlayer.addWeapon(weaponPointWeapon);
                    gamePlayer.setCoins(gamePlayer.getCoins() - 1000);
                    gamePlayer.getPlayer().sendMessage(String.format("%s§7Du hast §a%s §7gekauft! §c-1000 Coins!", plugin.getMonstersPrefix(), weaponPointWeapon.getName()));
                    return;
                }
            }
            ItemStack itemInHand = gamePlayer.getPlayer().getInventory().getItemInMainHand();
            if (gamePlayer.getWeapon(itemInHand) != null) {
                gamePlayer.removeWeapon(gamePlayer.getWeapon(itemInHand));
                gamePlayer.getPlayer().getInventory().setItemInMainHand(weaponPointWeapon.getItem());

                gamePlayer.addWeapon(weaponPointWeapon);
                gamePlayer.setCoins(gamePlayer.getCoins() - 1000);
                gamePlayer.getPlayer().sendMessage(String.format("§a%s §7freigeschaltet!", playerWeapon.getName()));
            } else if (gamePlayer.getWeapon(itemInHand) == null) {
                gamePlayer.getPlayer().sendMessage("§cBitte verwende einen Waffen Slot um eine neue Waffe zu kaufen!");
            }
        } else {
            if (gamePlayer.getCoins() < 500) {
                gamePlayer.getPlayer().sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum die Munition aufzufüllen", 500 - gamePlayer.getCoins()));
                return;
            }
            gamePlayer.setCoins(gamePlayer.getCoins() - 500);
            playerWeapon.refill();
            playerWeapon.reload(gamePlayer.getPlayer());
            gamePlayer.getPlayer().sendMessage(String.format("§7Munition von §a%s §7aufgefüllt! §c-500 Coins", playerWeapon.getName()));
        }
    }

    private void createFloatingHologram(Location location, String text) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ArmorStand hologram = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
            hologram.setCustomName(text);
            hologram.setCustomNameVisible(true);
            hologram.setInvisible(true);
            hologram.setMarker(true);
            hologram.setGravity(false);
            hologram.setInvulnerable(true);
            hologram.setCollidable(false);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (hologram.isDead() || ticks >= 100) {
                        hologram.remove();
                        cancel();
                        return;
                    }

                    Location newLoc = hologram.getLocation().add(0, 0.05, 0);
                    hologram.teleport(newLoc);

                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        });
    }
    private void startAxeCoolDown() {
        isAxeCooldDown = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                isAxeCooldDown = false;
            }
        }.runTaskLater(Main.getINSTANCE(), 40);
    }
}
