package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.monster.manager.MonsterManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/***
 * Power: the switch turns everything on (perk machines, flood lights). The generator gives 2 points per level,
 * every electric fence takes one. Monsters at the generator knock it out for a while.
 * Maps without a switch always have power.
 */
public class PowerGrid implements BuildPart {
    public static final String SWITCH = "strom", GENERATOR = "generator";
    private static final String SWITCH_ID = "monsters_stromschalter_", GENERATOR_ID = "monsters_generator_",
            LAMP_ID = "monsters_flutlicht_", FENCE_ID = "monsters_elektrozaun_";
    private static final String COLOR = "#FFD84A";
    private static final int MAX_LEVEL = 3, POINTS_PER_LEVEL = 2;
    private static final int[] UPGRADE = {0, 0, 6, 12};
    private static final int GENERATOR_HEALTH = 60, OUTAGE_SECONDS = 20, RESTART_PRICE = 1;
    private static final double GENERATOR_REACH = 2.0, FENCE_REACH = 1.3;
    private static final double FENCE_DAMAGE = 4;
    private static final int HIT = 10;
    private static final int LAMP_HEIGHT = 3;

    @Inject private DecorationManager decorationManager;
    @Inject private MonsterManager monsterManager;

    private ItemDisplay powerSwitch;
    private ItemDisplay generator;
    private int level = 1, health = GENERATOR_HEALTH, outage;
    private boolean on;
    private final List<ItemDisplay> lamps = new ArrayList<>();
    private final List<ItemDisplay> fences = new ArrayList<>();

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (id.startsWith(SWITCH_ID)) {
            powerSwitch = display;
            decorationManager.hitbox(block, SWITCH, 1.0F, 1.6F);
        } else if (id.startsWith(GENERATOR_ID)) {
            generator = display;
            level = Math.max(1, Math.min(MAX_LEVEL, level(id)));
            decorationManager.hitbox(block, GENERATOR, 1.6F, 1.6F);
        } else if (id.startsWith(LAMP_ID)) {
            lamps.add(display);
        } else if (id.startsWith(FENCE_ID)) {
            fences.add(display);
        } else {
            return false;
        }
        show();
        return true;
    }

    private static int level(String id) {
        try {
            return Integer.parseInt(id.substring(id.lastIndexOf('_') + 1));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Without a switch in the map there is always power */
    public boolean isPowered() {
        return powerSwitch == null || (on && outage == 0);
    }

    private int points() {
        return generator == null ? POINTS_PER_LEVEL : POINTS_PER_LEVEL * level;
    }

    // The first fences get the points, the rest stays dark
    private boolean fencePowered(int index) {
        return isPowered() && index < points();
    }

    @Override
    public boolean use(GamePlayer gamePlayer, String station) {
        if (SWITCH.equals(station)) {
            useSwitch(gamePlayer.getPlayer());
        } else if (GENERATOR.equals(station)) {
            useGenerator(gamePlayer);
        } else {
            return false;
        }
        return true;
    }

    private void useSwitch(Player player) {
        if (on) {
            Overlay.small(player, Overlay.icon(Overlay.STORM, COLOR, grey("Der Strom ist schon an")),
                    Overlay.icon(Overlay.STORM, COLOR, grey("The power is already on")));
            return;
        }
        on = true;
        show();
        Lang.broadcast("%s§e%s §7hat den §eStrom §7eingeschaltet!", "%s§e%s §7turned the §epower §7on!",
                Main.getINSTANCE().getMonstersPrefix(), player.getName());
        Overlay.mediumAll(COLOR, Overlay.line(Overlay.STORM, COLOR, "STROM AN", "Perks und Fallen laufen"),
                Overlay.line(Overlay.STORM, COLOR, "POWER ON", "perks and traps are running"));
        Location at = powerSwitch.getLocation();
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.5F, 1.2F);
    }

    // Out: restart for one part, otherwise the next level
    private void useGenerator(GamePlayer gamePlayer) {
        Player player = gamePlayer.getPlayer();
        if (outage > 0) {
            if (!Parts.take(gamePlayer, RESTART_PRICE)) return;
            restart();
            Sounds.buy(player);
            return;
        }
        if (level >= MAX_LEVEL) {
            Overlay.small(player, Overlay.icon(Overlay.STORM, COLOR, grey("Generator voll ausgebaut · ") + Overlay.num(points()) + grey(" Strompunkte")),
                    Overlay.icon(Overlay.STORM, COLOR, grey("Generator fully upgraded · ") + Overlay.num(points()) + grey(" power points")));
            Sounds.deny(player);
            return;
        }
        int price = UPGRADE[level + 1];
        if (!Parts.take(gamePlayer, price)) return;
        level++;
        decorationManager.setModel(generator, GENERATOR_ID + level);
        show();
        Overlay.small(player, Overlay.icon(Overlay.STORM, COLOR, "<b>Generator Stufe " + level + "</b> " + grey("· ") + Overlay.num(points()) + grey(" Strompunkte · -") + Parts.price(price, false)),
                Overlay.icon(Overlay.STORM, COLOR, "<b>Generator level " + level + "</b> " + grey("· ") + Overlay.num(points()) + grey(" power points · -") + Parts.price(price, true)));
        Sounds.buy(player);
        Location at = generator.getLocation();
        at.getWorld().playSound(at, Sound.BLOCK_ANVIL_USE, 1.0F, 0.9F);
    }

    @Override
    public void tick() {
        if (outage > 0 && --outage == 0) restart();
        if (generator != null && on && outage == 0) damageGenerator();
        if (!isPowered()) return;
        for (int i = 0; i < fences.size(); i++) {
            if (fencePowered(i)) zap(fences.get(i));
        }
    }

    private void damageGenerator() {
        Location center = generator.getLocation().add(0, 0.5, 0);
        int attackers = 0;
        for (Monster monster : monsterManager.getMonsters().keySet()) {
            if (monster.isValid() && monster.getWorld().equals(center.getWorld()) && monster.getLocation().distanceSquared(center) <= GENERATOR_REACH * GENERATOR_REACH) attackers++;
        }
        if (attackers == 0) return;
        health -= attackers * HIT;
        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 0.5, 0), 6, 0.4, 0.4, 0.4, 0.05);
        center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.7F, 1.3F);
        if (health > 0) return;
        outage = OUTAGE_SECONDS;
        show();
        center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.02);
        center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.5F, 0.8F);
        Overlay.mediumAll(COLOR, Overlay.line(Overlay.STORM, COLOR, "STROM AUS", "Generator neu starten"),
                Overlay.line(Overlay.STORM, COLOR, "POWER DOWN", "restart the generator"));
    }

    private void restart() {
        outage = 0;
        health = GENERATOR_HEALTH;
        show();
        Location at = generator.getLocation();
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.2F, 1.2F);
        Overlay.smallAll(Overlay.icon(Overlay.STORM, COLOR, "<b>Strom wieder da</b>"), Overlay.icon(Overlay.STORM, COLOR, "<b>Power is back</b>"));
    }

    // Monsters at a powered fence get a shock and slow down
    private void zap(ItemDisplay fence) {
        if (!fence.isValid()) return;
        Location center = fence.getLocation().add(0, 0.5, 0);
        boolean hit = false;
        for (Monster monster : monsterManager.getMonsters().keySet()) {
            if (!monster.isValid() || !monster.getWorld().equals(center.getWorld())) continue;
            Location at = monster.getLocation();
            double dx = at.getX() - center.getX(), dz = at.getZ() - center.getZ();
            if (dx * dx + dz * dz > FENCE_REACH * FENCE_REACH || Math.abs(at.getY() - fence.getLocation().getY()) > 1.5) continue;
            monster.damage(FENCE_DAMAGE);
            monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false));
            monster.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, monster.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.1);
            hit = true;
        }
        if (hit) center.getWorld().playSound(center, Sound.BLOCK_BEEHIVE_WORK, 0.8F, 2.0F);
    }

    // Models and light follow the power
    private void show() {
        boolean powered = isPowered();
        if (powerSwitch != null && powerSwitch.isValid()) decorationManager.setModel(powerSwitch, SWITCH_ID + (on ? "an" : "aus"));
        for (ItemDisplay lamp : lamps) {
            if (!lamp.isValid()) continue;
            decorationManager.setModel(lamp, LAMP_ID + (powered ? "an" : "aus"));
            light(lamp.getLocation().getBlock().getRelative(0, LAMP_HEIGHT, 0), powered);
        }
        for (int i = 0; i < fences.size(); i++) {
            if (fences.get(i).isValid()) decorationManager.setModel(fences.get(i), FENCE_ID + (fencePowered(i) ? "an" : "aus"));
        }
    }

    private static void light(Block block, boolean on) {
        if (on && block.getType().isAir()) {
            Light light = (Light) Material.LIGHT.createBlockData();
            light.setLevel(15);
            block.setBlockData(light, false);
        } else if (!on && block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
    }

    private static String grey(String text) {
        return "<color:" + Overlay.GREY + ">" + text + "</color>";
    }
}
