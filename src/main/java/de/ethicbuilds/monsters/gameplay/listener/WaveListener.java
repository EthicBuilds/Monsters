package de.ethicbuilds.monsters.gameplay.listener;

import de.ethicbuilds.monsters.sound.Sounds;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.DoorManager;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.map.MapConfiguration;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.map.elements.Door;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.map.elements.WeaponPoint;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.HealthBars;
import de.ethicbuilds.monsters.monster.MonsterGuard;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.ShotDamage;
import de.ethicbuilds.monsters.weapons.Weapon;
import de.ethicbuilds.monsters.weapons.manager.GrenadeManager;
import de.ethicbuilds.monsters.weapons.manager.WeaponManager;
import de.ethicbuilds.monsters.weapons.manager.WorkbenchManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import de.ethicbuilds.monsters.powerup.PowerupManager;

import java.lang.reflect.InvocationTargetException;

public class WaveListener implements Listener {
    @Inject private GameManager gameManager;
    @Inject private MonsterManager monsterManager;
    @Inject private UserManager userManager;
    @Inject private MapManager mapManager;
    @Inject private WeaponManager weaponManager;
    @Inject private DoorManager doorManager;
    @Inject private DecorationManager decorationManager;
    @Inject private GrenadeManager grenadeManager;
    @Inject private WorkbenchManager workbenchManager;
    @Inject private PowerupManager powerupManager;
    @Inject private Main plugin;

    public static final int AMMO_PRICE = 500;
    private static final long AMMO_COOLDOWN = 1500;
    private final java.util.Map<java.util.UUID, Long> ammoCooldowns = new java.util.HashMap<>();

    private boolean isAxeCooldDown;

    private static final int DOOR_PRICE = 2500;
    private static final double DOOR_REACH = 5;
    private static final int HEADSHOT_COINS = 25;
    // Coins already put into a door by players who did not have enough
    private final java.util.Map<Door, Integer> doorPayments = new java.util.IdentityHashMap<>();

    // Insta-kill powerup: every hit by a player kills, only the boss keeps its health
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInstakill(EntityDamageByEntityEvent event) {
        if (!powerupManager.isInstakill() || !(event.getEntity() instanceof Monster monster) || !(event.getDamager() instanceof Player)) return;
        if (monsterManager.getEnemyMonster(monster) instanceof de.ethicbuilds.monsters.monster.MonsterSpiderBoss) return;
        event.setDamage(monster.getHealth() + 100);
    }

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

        // Coins only for shots, grenades and the knife, not for hitting with a gun or other items
        boolean knife = player.getInventory().getItemInMainHand().getType() == GameItems.KNIFE;
        if (!ShotDamage.isActive() && !knife) return;

        if (!ShotDamage.isActive() && knife && isAxeCooldDown) return;

        if (!ShotDamage.isActive() && knife) {
            startAxeCoolDown();
        }

        if (event.getFinalDamage() >= monster.getHealth()) gamePlayer.setKilledZombies(gamePlayer.getKilledZombies() + 1);

        boolean headshot = ShotDamage.isHeadshot();
        if (headshot) gamePlayer.setHeadshots(gamePlayer.getHeadshots() + 1);
        int coins = headshot ? HEADSHOT_COINS : 20;

        gamePlayer.addCoins(coins);
        createFloatingHologram(player, enemyMonster.getMonster().getLocation(),
                headshot ? String.format("§6+ %d Coins §c§lKOPFSCHUSS", coins) : String.format("§6+ %d Coins", coins));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        event.getDrops().clear();
        event.setDroppedExp(0);

        if (gameManager.getCurrentPhase() != GamePhase.WAVE
                || !(event.getEntity() instanceof Monster monster)) return;

        HealthBars.remove(monster);
        // Halloween: orange to purple dust, sometimes a soul
        Location body = monster.getLocation().add(0, 1, 0);
        body.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, body, 12, 0.3, 0.5, 0.3,
                new Particle.DustTransition(Color.fromRGB(0xF9A11E), Color.fromRGB(0x8B4BD8), 1.2F));
        if (Math.random() < 0.3) body.getWorld().spawnParticle(Particle.SOUL, body, 2, 0.2, 0.3, 0.2, 0.02);
        EnemyMonster enemyMonster = monsterManager.getEnemyMonster(monster);
        if (enemyMonster == null) return;

        enemyMonster.onDeath();
        monsterManager.removeMonster(enemyMonster);
        powerupManager.dropFrom(monster.getLocation());
        if (monsterManager.getMonsters().size() <= 3) {
            for (EnemyMonster m : monsterManager.getMonsters().values()) {
                m.getMonster().addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMonsterAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Monster monster) || !(event.getEntity() instanceof Player)) return;
        EnemyMonster enemyMonster = monsterManager.getEnemyMonster(monster);
        if (enemyMonster != null) enemyMonster.onAttack();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMonsterHealthChange(EntityDamageEvent event) {
        if (event.getEntity() instanceof Monster monster) HealthBars.updateLater(monster);
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

        if (gameManager.getCurrentPhase() != GamePhase.WAVE || event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND) return;

        MapConfiguration mapConfiguration = mapManager.getMapConfiguration();

        GamePlayer gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;

        Location location = event.getClickedBlock().getLocation();
        // The block reach is 30 for the team ping, doors still need the player right in front of them
        if (event.getPlayer().getEyeLocation().distance(location.clone().add(0.5, 0.5, 0.5)) > DOOR_REACH) return;

        for (Door door : mapConfiguration.getDoors()) {
            for (Location loc : door.getClickableLocations()) {
                if (loc.equals(location)) {
                    breakDoor(gamePlayer, door);
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

        buyOrReloadWeapon(gamePlayer, weaponPoint.getWeaponName());
    }

    @EventHandler
    public void onPlayerInteractWithStation(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Interaction interaction)) return;

        String station = decorationManager.getStation(interaction);
        if (station == null) return;

        GamePlayer gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;

        if (station.equals(DecorationManager.AMMO_STATION)) {
            refillHeldWeapon(gamePlayer);
        } else if (station.equals(DecorationManager.WORKBENCH_STATION)) {
            workbenchManager.use(gamePlayer);
        } else {
            buyOrReloadWeapon(gamePlayer, station);
        }
    }

    // Hitting a station works like right-clicking it
    @EventHandler
    public void onPlayerHitStation(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction interaction)) return;
        String station = decorationManager.getStation(interaction);
        if (station == null) return;
        event.setCancelled(true);

        GamePlayer gamePlayer = userManager.getGamePlayer(event.getPlayer().getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;

        if (station.equals(DecorationManager.AMMO_STATION)) {
            refillHeldWeapon(gamePlayer);
        } else if (station.equals(DecorationManager.WORKBENCH_STATION)) {
            workbenchManager.use(gamePlayer);
        } else {
            buyOrReloadWeapon(gamePlayer, station);
        }
    }

    @EventHandler
    public void onPlayerThrowGrenade(PlayerInteractEvent event) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!GameItems.isGrenade(event.getItem()) || userManager.isPlayerDead(event.getPlayer().getUniqueId())) return;

        grenadeManager.throwGrenade(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE) return;

        // Stays in the round and may come back, the others play on
        userManager.leave(event.getPlayer().getUniqueId());

        if (userManager.allPlayersDead()) {
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


    // Ammo crate: fills the weapon in hand once, short cooldown so nobody pays twice
    private void refillHeldWeapon(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        long now = System.currentTimeMillis();
        if (now - ammoCooldowns.getOrDefault(player.getUniqueId(), 0L) < AMMO_COOLDOWN) return;

        Weapon weapon = gamePlayer.getWeapon(player.getInventory().getItemInMainHand());
        AmmoHud.pause(player);
        if (weapon == null) {
            player.sendActionBar("§7Halte die Waffe in der Hand, die du auffüllen willst");
            return;
        }
        if (weapon.isFull()) {
            player.sendActionBar("§cMunition bereits voll!");
            Sounds.deny(player);
            return;
        }
        if (gamePlayer.getCoins() < AMMO_PRICE) {
            player.sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum die Munition aufzufüllen", AMMO_PRICE - gamePlayer.getCoins()));
            Sounds.deny(player);
            return;
        }

        ammoCooldowns.put(player.getUniqueId(), now);
        gamePlayer.setCoins(gamePlayer.getCoins() - AMMO_PRICE);
        weapon.refill();
        weapon.reload(player);
        AmmoHud.refresh(player, weapon);
        player.sendMessage(String.format("§7Munition von §a%s §7aufgefüllt! §c-%d Coins", weapon.getName(), AMMO_PRICE));
        Sounds.buy(player);
    }

    private void breakDoor(GamePlayer gamePlayer, Door door) {
        Player player = gamePlayer.getPlayer();

        if (!GameItems.isCrowbar(player.getInventory().getItemInMainHand())) {
            AmmoHud.pause(player);
            player.sendActionBar("§cBrich die Tür mit der §6Brechstange §cauf!");
            Sounds.deny(player);
            return;
        }
        int remaining = DOOR_PRICE - doorPayments.getOrDefault(door, 0);
        if (gamePlayer.getCoins() < remaining) {
            contribute(gamePlayer, door, remaining);
            return;
        }

        doorManager.breakOpen(gamePlayer, door, () -> openDoor(gamePlayer, door));
    }

    // Not enough coins: everything the player has goes into the door, the team fills up the rest
    private void contribute(GamePlayer gamePlayer, Door door, int remaining) {
        Player player = gamePlayer.getPlayer();
        int coins = gamePlayer.getCoins();
        if (coins <= 0) {
            player.sendMessage(String.format("§cNoch §6%d Coins §cfür diese Tür, legt zusammen!", remaining));
            Sounds.deny(player);
            return;
        }
        gamePlayer.setCoins(0);
        int paid = doorPayments.merge(door, coins, Integer::sum);
        updateDoorPrice(door, DOOR_PRICE - paid);
        Bukkit.broadcastMessage(String.format("%s§a%s §7legt §6%d Coins §7für §a%s §7ein (§6%d§7/§6%d§7). Noch §6%d Coins§7, legt zusammen!",
                plugin.getMonstersPrefix(), player.getName(), coins, door.getName(), paid, DOOR_PRICE, DOOR_PRICE - paid));
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 1.2F);
    }

    // Price line under the door shows what is still missing
    private void updateDoorPrice(Door door, int remaining) {
        Location line = door.getHoloLocation().clone().add(0.5, -0.3, 0.5);
        for (Entity entity : line.getWorld().getNearbyEntities(line, 0.3, 0.3, 0.3)) {
            if (entity instanceof ArmorStand stand) stand.setCustomName(String.format("§afür §6%d Coins", remaining));
        }
    }

    private void openDoor(GamePlayer gamePlayer, Door door) {
        Player player = gamePlayer.getPlayer();
        int remaining = DOOR_PRICE - doorPayments.getOrDefault(door, 0);

        if (gamePlayer.getCoins() < remaining) {
            player.sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum diese Türe zu öffnen", remaining - gamePlayer.getCoins()));
            Sounds.deny(player);
            return;
        }

        gamePlayer.setCoins(gamePlayer.getCoins() - remaining);
        doorPayments.remove(door);

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

        Bukkit.broadcastMessage(String.format("%s§a%s hat den Bereich §7%s §afreigeschaltet!", plugin.getMonstersPrefix(), player.getDisplayName(), door.getName()));
        userManager.broadcastSound(Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.1F);

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

    private void buyOrReloadWeapon(GamePlayer gamePlayer, String weaponName) {
        Weapon weaponPointWeapon;

        try {
            weaponPointWeapon = weaponManager.getWeaponNames()
                    .get(weaponName.toLowerCase())
                    .getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        Weapon playerWeapon = gamePlayer.getWeapons().get(weaponPointWeapon.getItem().getType());
        Player player = gamePlayer.getPlayer();

        if (playerWeapon == null) {
            if (gamePlayer.getCoins() < weaponPointWeapon.getPrice()) {
                player.sendMessage(String.format("§cDu brauchst noch §6%d Coins §cum diese Waffe zu kaufen", weaponPointWeapon.getPrice() - gamePlayer.getCoins()));
                Sounds.deny(player);
                return;
            }

            for (int i : gamePlayer.getWeaponSlots()) {
                if (player.getInventory().getItem(i).getType().equals(Material.LIGHT_GRAY_DYE)) {
                    player.getInventory().setItem(i, weaponPointWeapon.getItem());

                    gamePlayer.addWeapon(weaponPointWeapon);
                    gamePlayer.setCoins(gamePlayer.getCoins() - weaponPointWeapon.getPrice());
                    player.sendMessage(String.format("%s§7Du hast §a%s §7gekauft! §c-%d Coins!", plugin.getMonstersPrefix(), weaponPointWeapon.getName(), weaponPointWeapon.getPrice()));
                    Sounds.buy(player);
                    return;
                }
            }
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (gamePlayer.getWeapon(itemInHand) != null) {
                gamePlayer.removeWeapon(gamePlayer.getWeapon(itemInHand));
                player.getInventory().setItemInMainHand(weaponPointWeapon.getItem());

                gamePlayer.addWeapon(weaponPointWeapon);
                gamePlayer.setCoins(gamePlayer.getCoins() - weaponPointWeapon.getPrice());
                player.sendMessage(String.format("%s§7Du hast §a%s §7gekauft! §c-%d Coins!", plugin.getMonstersPrefix(), weaponPointWeapon.getName(), weaponPointWeapon.getPrice()));
                Sounds.buy(player);
            } else if (gamePlayer.getWeapon(itemInHand) == null) {
                player.sendMessage("§cBitte verwende einen Waffen Slot um eine neue Waffe zu kaufen!");
                Sounds.deny(player);
            }
        } else {
            // Weapon crates only sell weapons, ammo comes from the ammo crates
            player.sendActionBar("§7Du hast diese Waffe schon! Munition gibt es an der §aMunitionskiste");
            Sounds.deny(player);
        }
    }

    // Only the player who earned the coins sees them
    private void createFloatingHologram(Player viewer, Location location, String text) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ArmorStand hologram = location.getWorld().spawn(location, ArmorStand.class, stand -> {
                stand.setVisibleByDefault(false);
                stand.setCustomName(text);
                stand.setCustomNameVisible(true);
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setPersistent(false);
                stand.setCollidable(false);
            });
            MonsterGuard.markHolo(hologram);
            if (viewer.isOnline()) viewer.showEntity(plugin, hologram);

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
