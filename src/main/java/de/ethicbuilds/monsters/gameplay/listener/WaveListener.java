package de.ethicbuilds.monsters.gameplay.listener;

import de.ethicbuilds.monsters.event.WaveEvents;
import de.ethicbuilds.monsters.monster.MonsterSpiderBoss;
import de.ethicbuilds.monsters.reward.RoundReward;
import de.ethicbuilds.monsters.sound.Sounds;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.DoorManager;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.map.MapConfiguration;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.map.MapManager;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.map.elements.Door;
import de.ethicbuilds.monsters.map.elements.MonsterSpawner;
import de.ethicbuilds.monsters.map.elements.WeaponPoint;
import de.ethicbuilds.monsters.monster.EnemyMonster;
import de.ethicbuilds.monsters.monster.HealthBars;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.perk.Perk;
import de.ethicbuilds.monsters.perk.PerkManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.gameplay.CoinPopups;
import de.ethicbuilds.monsters.monster.EliteTier;
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

import de.ethicbuilds.monsters.powerup.PowerupManager;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

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
    @Inject private PerkManager perkManager;
    @Inject private Main plugin;

    public static final int AMMO_PRICE = 500;
    private static final long AMMO_COOLDOWN = 1500;
    private final Map<UUID, Long> ammoCooldowns = new HashMap<>();

    // Knife coins at most every 2 seconds, for each player on their own
    private static final long KNIFE_COOLDOWN = 2000;
    private final Map<UUID, Long> knifeCoins = new HashMap<>();

    private static final int DOOR_PRICE = 2500;
    private static final double DOOR_REACH = 5;
    private static final int HEADSHOT_COINS = 25;
    // Coins already put into a door by players who did not have enough
    private final Map<Door, Integer> doorPayments = new IdentityHashMap<>();

    // Insta-kill powerup: shots, grenades and the knife kill at once, not the bare hand. Only the boss keeps its health
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInstakill(EntityDamageByEntityEvent event) {
        if (!powerupManager.isInstakill() || !(event.getEntity() instanceof Monster monster) || !(event.getDamager() instanceof Player player)) return;
        if (!ShotDamage.isActive() && player.getInventory().getItemInMainHand().getType() != GameItems.KNIFE) return;
        if (monsterManager.getEnemyMonster(monster) instanceof MonsterSpiderBoss) return;
        event.setDamage(monster.getHealth() + 100);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE
                || !(event.getEntity() instanceof Monster monster)
                || !(event.getDamager() instanceof Player player)) return;

        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null) return;

        // A grenade thrown before going down still counts
        if (userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId()) && !ShotDamage.isActive()) {
            event.setCancelled(true);
            return;
        }

        EnemyMonster enemyMonster = monsterManager.getEnemyMonster(monster);
        if (enemyMonster == null) return;
        // Buried or frozen (pause): no hit, no coins
        if (monster.isInvulnerable()) {
            event.setCancelled(true);
            return;
        }
        // Shields and the like, insta-kill goes through
        double factor = enemyMonster.damageFactor(player);
        if (factor != 1 && !powerupManager.isInstakill()) event.setDamage(event.getDamage() * factor);
        enemyMonster.onHit(player);

        // Coins only for shots, grenades and the knife, not for hitting with a gun or other items.
        // The hand does not matter for grenades, after the last one it is empty
        boolean knife = player.getInventory().getItemInMainHand().getType() == GameItems.KNIFE;
        if (!ShotDamage.isActive() && !knife) return;

        if (!ShotDamage.isActive() && knife) {
            long now = System.currentTimeMillis();
            if (now - knifeCoins.getOrDefault(player.getUniqueId(), 0L) < KNIFE_COOLDOWN) return;
            knifeCoins.put(player.getUniqueId(), now);
        }

        boolean kill = event.getFinalDamage() >= monster.getHealth();
        if (kill) gamePlayer.setKilledZombies(gamePlayer.getKilledZombies() + 1);

        // Only headshot kills count (tab, awards, challenge, stats), the coin bonus stays for every headshot
        boolean headshot = ShotDamage.isHeadshot();
        if (headshot && kill) gamePlayer.setHeadshots(gamePlayer.getHeadshots() + 1);
        int coins = (int) Math.round(((headshot ? HEADSHOT_COINS : 20) + EliteTier.extraCoins(enemyMonster.getTier()))
                * WaveEvents.coinFactor());

        gamePlayer.addCoins(coins);
        RoundReward.markActive(player.getUniqueId());
        CoinPopups.show(player, enemyMonster.getMonster().getLocation(), coins, headshot);
        if (WaveEvents.isGoldRush()) {
            monster.getWorld().spawnParticle(Particle.DUST, monster.getLocation().add(0, 1.2, 0), 6, 0.3, 0.4, 0.3, WaveEvents.GOLD);
        }
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
        if (monsterManager.remaining() <= 3) {
            for (EnemyMonster m : monsterManager.getMonsters().values()) {
                // Invisible carriers (scarabs, spiders) would show through, their model glows instead
                if (m.getMonster().isInvisible()) {
                    m.getMonster().getPassengers().forEach(part -> { if (part instanceof ItemDisplay) part.setGlowing(true); });
                } else {
                    m.getMonster().addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1));
                }
            }
        }
    }

    // The fireworks of the boss victory are only for the eyes
    @EventHandler(priority = EventPriority.LOWEST)
    public void onShowFirework(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(MonsterSpiderBoss.SHOW_FIREWORK)) {
            event.setCancelled(true);
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
        if (!(event.getEntity() instanceof Monster monster)) return;
        // The bar only shows for a few seconds after a hit
        EnemyMonster enemyMonster = monsterManager.getEnemyMonster(monster);
        if (enemyMonster != null && enemyMonster.showsHealthBar() && plugin.getConfig().getBoolean("health-bars", true)) HealthBars.show(monster);
        HealthBars.updateLater(monster);
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

        useStation(event.getPlayer(), station);
    }

    // Hitting a station works like right-clicking it
    @EventHandler
    public void onPlayerHitStation(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction interaction)) return;
        String station = decorationManager.getStation(interaction);
        if (station == null) return;
        event.setCancelled(true);
        useStation(event.getPlayer(), station);
    }

    private void useStation(Player player, String station) {
        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (gamePlayer == null || userManager.isPlayerDead(gamePlayer.getPlayer().getUniqueId())) return;

        Perk perk = Perk.byFurniture(station);
        if (station.equals(DecorationManager.AMMO_STATION)) {
            refillHeldWeapon(gamePlayer);
        } else if (station.equals(DecorationManager.WORKBENCH_STATION)) {
            workbenchManager.use(gamePlayer);
        } else if (perk != null) {
            perkManager.buy(gamePlayer, perk);
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
            player.sendActionBar(Lang.t(player, "§7Halte die Waffe in der Hand, die du auffüllen willst", "§7Hold the weapon you want to refill"));
            return;
        }
        if (weapon.isFull()) {
            player.sendActionBar(Lang.t(player, "§cMunition bereits voll!", "§cAmmo already full!"));
            Sounds.deny(player);
            return;
        }
        if (gamePlayer.getCoins() < AMMO_PRICE) {
            Lang.note(player, Lang.f(player, "§cDu brauchst noch §6%d Coins §cum die Munition aufzufüllen", "§cYou need §6%d more Coins §cto refill ammo", AMMO_PRICE - gamePlayer.getCoins()));
            Sounds.deny(player);
            return;
        }

        ammoCooldowns.put(player.getUniqueId(), now);
        gamePlayer.setCoins(gamePlayer.getCoins() - AMMO_PRICE);
        weapon.refill();
        weapon.reload(player);
        AmmoHud.refresh(player, weapon);
        Lang.note(player, Lang.f(player, "§7Munition von §a%s §7aufgefüllt! §c-%d Coins", "§7Refilled ammo for §a%s§7! §c-%d Coins", weapon.getName(player), AMMO_PRICE));
        Sounds.buy(player);
    }

    private void breakDoor(GamePlayer gamePlayer, Door door) {
        Player player = gamePlayer.getPlayer();

        if (!GameItems.isCrowbar(player.getInventory().getItemInMainHand())) {
            AmmoHud.pause(player);
            player.sendActionBar(Lang.t(player, "§cBrich die Tür mit der §6Brechstange §cauf!", "§cBreak the door open with the §6Crowbar§c!"));
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
            Lang.note(player, Lang.f(player, "§cNoch §6%d Coins §cfür diese Tür, legt zusammen!", "§cThis door still needs §6%d Coins§c, chip in!", remaining));
            Sounds.deny(player);
            return;
        }
        gamePlayer.setCoins(0);
        int paid = doorPayments.merge(door, coins, Integer::sum);
        updateDoorPrice(door, DOOR_PRICE - paid);
        Lang.noteAll("%s§a%s §7legt §6%d Coins §7für §a%s §7ein (§6%d§7/§6%d§7). Noch §6%d Coins§7, legt zusammen!",
                "%s§a%s §7puts §6%d Coins §7into §a%s §7(§6%d§7/§6%d§7). §6%d Coins §7to go, chip in!",
                plugin.getMonstersPrefix(), player.getName(), coins, door.getName(), paid, DOOR_PRICE, DOOR_PRICE - paid);
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0F, 1.2F);
    }

    // Price line under the door shows what is still missing
    private void updateDoorPrice(Door door, int remaining) {
        Location line = door.getHoloLocation().clone().add(0.5, -0.3, 0.5);
        for (Entity entity : line.getWorld().getNearbyEntities(line, 0.3, 0.3, 0.3)) {
            if (entity instanceof ArmorStand stand) stand.setCustomName(String.format("§6%d Coins", remaining));
        }
    }

    private void openDoor(GamePlayer gamePlayer, Door door) {
        Player player = gamePlayer.getPlayer();
        int remaining = DOOR_PRICE - doorPayments.getOrDefault(door, 0);

        if (gamePlayer.getCoins() < remaining) {
            Lang.note(player, Lang.f(player, "§cDu brauchst noch §6%d Coins §cum diese Türe zu öffnen", "§cYou need §6%d more Coins §cto open this door", remaining - gamePlayer.getCoins()));
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

        Lang.broadcast("%s§a%s hat den Bereich §7%s §afreigeschaltet!", "%s§a%s unlocked the area §7%s§a!", plugin.getMonstersPrefix(), player.getDisplayName(), door.getName());
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

        Player player = gamePlayer.getPlayer();
        weaponPointWeapon.localize(player);
        Weapon playerWeapon = gamePlayer.getWeapons().get(weaponPointWeapon.getItem().getType());

        if (playerWeapon == null) {
            if (gamePlayer.getCoins() < weaponPointWeapon.getPrice()) {
                Lang.note(player, Lang.f(player, "§cDu brauchst noch §6%d Coins §cum diese Waffe zu kaufen", "§cYou need §6%d more Coins §cto buy this weapon", weaponPointWeapon.getPrice() - gamePlayer.getCoins()));
                Sounds.deny(player);
                return;
            }

            for (int i : gamePlayer.getWeaponSlots()) {
                ItemStack slot = player.getInventory().getItem(i);
                if (slot != null && slot.getType() == Material.LIGHT_GRAY_DYE) {
                    player.getInventory().setItem(i, weaponPointWeapon.getItem());

                    gamePlayer.addWeapon(weaponPointWeapon);
                    gamePlayer.setCoins(gamePlayer.getCoins() - weaponPointWeapon.getPrice());
                    Lang.note(player, Lang.f(player, "%s§7Du hast §a%s §7gekauft! §c-%d Coins!", "%s§7You bought §a%s§7! §c-%d Coins!", plugin.getMonstersPrefix(), weaponPointWeapon.getName(player), weaponPointWeapon.getPrice()));
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
                Lang.note(player, Lang.f(player, "%s§7Du hast §a%s §7gekauft! §c-%d Coins!", "%s§7You bought §a%s§7! §c-%d Coins!", plugin.getMonstersPrefix(), weaponPointWeapon.getName(player), weaponPointWeapon.getPrice()));
                Sounds.buy(player);
            } else if (gamePlayer.getWeapon(itemInHand) == null) {
                Lang.note(player, Lang.t(player, "§cBitte verwende einen Waffen Slot um eine neue Waffe zu kaufen!", "§cUse a weapon slot to buy a new weapon!"));
                Sounds.deny(player);
            }
        } else {
            // Weapon crates only sell weapons, ammo comes from the ammo crates
            player.sendActionBar(Lang.t(player, "§7Du hast diese Waffe schon! Munition gibt es an der §aMunitionskiste", "§7You already have this weapon! Get ammo at the §aAmmo crate"));
            Sounds.deny(player);
        }
    }
}
