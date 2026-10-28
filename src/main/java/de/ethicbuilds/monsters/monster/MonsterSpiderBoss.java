package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;

/***
 * Boss of every tenth wave: the big amethyst weaver with a lot of health, a boss bar for everyone and its crystal volley
 */
public class MonsterSpiderBoss extends MonsterSpider {
    // Crystal volley every 6 to 9 seconds (checked every half second)
    private static final int MIN_VOLLEY = 12, MAX_VOLLEY = 18;

    private BossBar bar;
    private int nextVolley = MIN_VOLLEY;

    public MonsterSpiderBoss(int players) {
        super();
        this.health = 300 + 200 * players;
        this.damage = 6;
        this.speed = 0.9;
        this.name = "§5Amethyst-Weberin";
        this.coin = 500;
        this.modelSize = 1.0F;
        this.hitboxScale = 3.0;
        this.room = 2;
        this.attackCooldown = 30;
    }

    @Override
    protected boolean showsHealthBar() {
        return false;
    }

    @Override
    protected void onSpawn() {
        super.onSpawn();
        bar = Bukkit.createBossBar("§5§lAmethyst-Weberin", BarColor.PURPLE, BarStyle.SEGMENTED_10);
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

                if (--nextVolley > 0) return;
                nextVolley = java.util.concurrent.ThreadLocalRandom.current().nextInt(MIN_VOLLEY, MAX_VOLLEY + 1);
                // Rears up with the crystal animation, the crystals leave its back shortly after
                if (model != null) model.play("kristall");
                CrystalBarrage.fire(monster, monster.getHealth() < health / 2 ? 3 : 1);
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 10L);
    }

    @Override
    public void onDeath() {
        super.onDeath();
        if (bar != null) bar.removeAll();
    }
}
