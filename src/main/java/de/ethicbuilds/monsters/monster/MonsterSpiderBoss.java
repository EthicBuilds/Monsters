package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;

/***
 * Boss of every tenth wave: the big amethyst weaver with a lot of health and a boss bar for everyone
 */
public class MonsterSpiderBoss extends MonsterSpider {
    private BossBar bar;

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
            }
        }.runTaskTimer(Main.getINSTANCE(), 0L, 10L);
    }

    @Override
    public void onDeath() {
        super.onDeath();
        if (bar != null) bar.removeAll();
    }
}
