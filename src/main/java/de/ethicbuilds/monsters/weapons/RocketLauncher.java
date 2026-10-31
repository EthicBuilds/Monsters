package de.ethicbuilds.monsters.weapons;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/***
 * Rocket launcher built from scrap (design session), only as loot from bosses: one rocket per shot, it flies as a
 * display and explodes on a wall or a monster, scrap flies around
 */
public class RocketLauncher extends Weapon {
    private static final String MODEL = "monsters_raketenwerfer", EMPTY = "monsters_raketenwerfer_leer", ROCKET = "monsters_rakete";
    private static final String[] SCRAP = {"monsters_schrott_1", "monsters_schrott_2", "monsters_schrott_3"};
    private static final double SPEED = 1.5, STEP = 0.5, HIT = 0.8, RADIUS = 4.5, MAX_DISTANCE = 60;
    private static final int SCRAP_TICKS = 15;

    public RocketLauncher() {
        this.item = new ItemStack(Material.IRON_NUGGET);
        this.name = "§6Raketenwerfer";
        this.nameEn = "§6Rocket Launcher";
        this.damage = 60.0;
        this.fireRate = 30;
        this.maxAmmu = 6;
        this.maxMagazine = 1;
        this.particle = Particle.SMOKE;
        // The workbench icon is gui/waffe_raketenwerfer, the hand model is the Nexo item
        this.model = "raketenwerfer";
        this.upgradePrice = 30000;
        initialize();
        GameItems.setModel(item, MODEL);
    }

    // One rocket, then the next one is loaded straight away (magazine of one)
    @Override
    public void shoot(Player player) {
        if (isCoolDown || ammu <= 0 && magazine <= 0) return;
        startCoolDown(player);
        if (magazine <= 0) reload(player);
        if (magazine <= 0) return;
        magazine--;
        Location start = player.getEyeLocation();
        Vector direction = aim(player);
        player.getWorld().playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 0.6F);
        Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).shotNoise(player);
        showEmpty(player, true);
        launch(player, start.clone().add(direction.clone().multiply(0.8)), direction);
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            showEmpty(player, false);
            reload(player);
            AmmoHud.show(player, this);
        }, fireRate);
        AmmoHud.show(player, this);
    }

    // The launcher shows no rocket while it reloads, also when the player switched to another slot meanwhile
    private void showEmpty(Player player, boolean empty) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType() != item.getType()) continue;
            GameItems.setModel(stack, empty ? EMPTY : MODEL);
            player.getInventory().setItem(slot, stack);
        }
    }

    private void launch(Player player, Location start, Vector direction) {
        ItemStack rocketItem = new ItemStack(Material.PAPER);
        GameItems.setModel(rocketItem, ROCKET);
        Location at = start.clone().setDirection(direction);
        ItemDisplay rocket = player.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(rocketItem);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTeleportDuration(1);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
        });
        new BukkitRunnable() {
            double travelled;

            @Override
            public void run() {
                if (!rocket.isValid()) {
                    cancel();
                    return;
                }
                Location now = rocket.getLocation();
                for (double moved = 0; moved < SPEED; moved += STEP) {
                    now.add(direction.clone().multiply(STEP));
                    travelled += STEP;
                    if (now.getBlock().getType().isSolid() || travelled > MAX_DISTANCE || hitsMonster(now)) {
                        explode(player, now);
                        rocket.remove();
                        cancel();
                        return;
                    }
                }
                rocket.teleport(now.setDirection(direction));
                rocket.getWorld().spawnParticle(Particle.SMOKE, now, 3, 0.05, 0.05, 0.05, 0.01);
                rocket.getWorld().spawnParticle(Particle.FLAME, now, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }.runTaskTimer(Main.getINSTANCE(), 1L, 1L);
    }

    private static boolean hitsMonster(Location at) {
        for (Monster monster : at.getWorld().getNearbyEntitiesByType(Monster.class, at, HIT)) {
            if (monster.isValid() && !monster.isDead()) return true;
        }
        return false;
    }

    private void explode(Player shooter, Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2F, 0.9F);
        world.spawnParticle(Particle.EXPLOSION, at, 3, 0.6, 0.3, 0.6, 0);
        world.spawnParticle(Particle.FLAME, at, 20, 1.0, 0.5, 1.0, 0.05);
        world.spawnParticle(Particle.LARGE_SMOKE, at, 12, 0.8, 0.5, 0.8, 0.02);
        Main.getINSTANCE().getInjector().getInstance(MonsterManager.class).grenadeNoise(at);
        for (Monster monster : world.getNearbyEntitiesByType(Monster.class, at, RADIUS)) {
            double distance = monster.getLocation().distance(at);
            if (distance > RADIUS) continue;
            ShotDamage.deal(monster, damage * (1 - distance / RADIUS) + damage * 0.2, shooter);
        }
        for (String scrap : SCRAP) flyAway(at, scrap);
    }

    // Scrap pieces fly up and away and drop again, then they are gone
    private static void flyAway(Location from, String model) {
        ItemStack item = new ItemStack(Material.PAPER);
        GameItems.setModel(item, model);
        var random = ThreadLocalRandom.current();
        Vector way = new Vector(random.nextDouble(-1, 1), random.nextDouble(0.6, 1.2), random.nextDouble(-1, 1)).normalize().multiply(0.35);
        ItemDisplay piece = from.getWorld().spawn(from.clone().add(0, 0.3, 0), ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setTeleportDuration(1);
            display.setPersistent(false);
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6F), new Quaternionf()));
        });
        new BukkitRunnable() {
            int ticks;
            final Vector velocity = way.clone();

            @Override
            public void run() {
                if (!piece.isValid() || ++ticks > SCRAP_TICKS) {
                    piece.remove();
                    cancel();
                    return;
                }
                velocity.setY(velocity.getY() - 0.06);
                Location next = piece.getLocation().add(velocity);
                if (next.getBlock().getType().isSolid()) velocity.zero();
                else piece.teleport(next);
            }
        }.runTaskTimer(Main.getINSTANCE(), 1L, 1L);
    }
}
