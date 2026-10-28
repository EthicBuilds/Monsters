package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.weapons.manager.GrenadeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/***
 * One stronger zombie per wave with a name and a boss bar, it leaves a grenade or coins for whoever kills it
 */
public class MonsterMiniBoss extends MonsterZombie {
    private static final String[] NAMES = {"Grabwächter", "Wüstenfürst", "Knochenbrecher", "Mumienkönig", "Sandschlund"};
    private static final int LOOT_COINS = 300;

    private final String title;
    private BossBar bar;

    public MonsterMiniBoss(int wave) {
        super(EntityType.ZOMBIE, "§c" + NAMES[wave % NAMES.length]);
        this.title = NAMES[wave % NAMES.length];
        this.health = 60 + 15 * wave;
        this.damage = 4;
        this.coin = 100;
    }

    @Override
    protected void onSpawn() {
        // Normal size (under 2 blocks), so it fits through every spawn hole; the gold helmet and boss bar mark it
        var attack = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(damage);
        EntityEquipment equipment = monster.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            equipment.setHelmetDropChance(0);
            equipment.setChestplateDropChance(0);
        }

        bar = Bukkit.createBossBar("§c§l" + title, BarColor.RED, BarStyle.SEGMENTED_6);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!monster.isValid() || monster.isDead()) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
                bar.setProgress(Math.max(0, Math.min(1, monster.getHealth() / health)));
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 10L);
    }

    @Override
    public void onDeath() {
        if (bar != null) bar.removeAll();
        Player killer = monster.getKiller();
        if (killer == null) return;

        var injector = Main.getINSTANCE().getInjector();
        GamePlayer gamePlayer = injector.getInstance(UserManager.class).getGamePlayer(killer.getUniqueId());
        if (gamePlayer == null) return;

        // Grenade if there is room for one, otherwise coins
        boolean grenade = ThreadLocalRandom.current().nextBoolean() && injector.getInstance(GrenadeManager.class).give(killer);
        if (!grenade) gamePlayer.addCoins(LOOT_COINS);
        Bukkit.broadcastMessage(String.format("%s§a%s §7hat §c%s §7besiegt und bekommt %s§7!", Main.getINSTANCE().getMonstersPrefix(),
                killer.getName(), title, grenade ? "§aeine Granate" : "§6" + LOOT_COINS + " Coins"));
        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.4F);
        monster.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, monster.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.3);
    }
}
