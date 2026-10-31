package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.gameplay.LagGuard;
import de.ethicbuilds.monsters.bautest.Restore;
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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Power: the switch turns everything on (perk machines, flood lights). The generator gives 2 points per level,
 * every electric fence line takes one. Monsters at the generator knock it out for a while.
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
    // Nexo puts the light of the flood light at the top of the mast
    private static final int LAMP_HEIGHT = 3;
    private static final double SPARK_VIEW = 24, ARC_CHANCE = 0.3;
    private static final int MAX_PULSES = 16;
    private static final String[] NAMES = {"", "2 Strompunkte", "4 Strompunkte", "6 Strompunkte"},
            NAMES_EN = {"", "2 power points", "4 power points", "6 power points"};

    @Inject private DecorationManager decorationManager;
    @Inject private MonsterManager monsterManager;
    @Inject private UpgradeMenu upgradeMenu;

    private ItemDisplay powerSwitch;
    private ItemDisplay generator;
    private int level = 1, health = GENERATOR_HEALTH, outage;
    private boolean on;
    private final List<ItemDisplay> lamps = new ArrayList<>();
    private final List<Fence> fences = new ArrayList<>();
    // Fences with a gap of one block between them, arcs jump over it
    private final List<Fence[]> gaps = new ArrayList<>();
    private int step;

    // One segment; touching segments form one fence line that takes one point
    private static final class Fence {
        private final ItemDisplay display;
        // Id without an/aus, the shape stays (straight, ecke, t, ende)
        private final String base;
        private final List<Vector> wires;
        private Fence line = this;

        private Fence(ItemDisplay display, String base) {
            this.display = display;
            this.base = base;
            this.wires = FenceSparks.wires(display, base);
        }

        private Fence line() {
            return line == this ? this : (line = line.line());
        }
    }

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
            addFence(new Fence(display, id.substring(0, id.lastIndexOf('_') + 1)));
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

    private void addFence(Fence fence) {
        Location at = fence.display.getLocation();
        for (Fence other : fences) {
            Location there = other.display.getLocation();
            if (there.getWorld().equals(at.getWorld()) && there.getBlockY() == at.getBlockY()
                    && Math.abs(there.getBlockX() - at.getBlockX()) + Math.abs(there.getBlockZ() - at.getBlockZ()) == 1) {
                other.line().line = fence.line();
            }
            int dx = there.getBlockX() - at.getBlockX(), dz = there.getBlockZ() - at.getBlockZ();
            if (there.getWorld().equals(at.getWorld()) && there.getBlockY() == at.getBlockY() && (dx == 0 || dz == 0) && Math.abs(dx + dz) == 2) {
                Vector towards = new Vector(dx, 0, dz).normalize();
                if (FenceSparks.faces(fence.wires, towards) || FenceSparks.faces(other.wires, towards.clone().multiply(-1))) gaps.add(new Fence[]{fence, other});
            }
        }
        fences.add(fence);
    }

    // The first fence lines get the points, the rest stays dark
    private boolean fencePowered(Fence fence) {
        if (!isPowered()) return false;
        // The build server shows every fence working, the points only count in a round
        if (BauTest.on()) return true;
        List<Fence> lines = new ArrayList<>();
        for (Fence other : fences) if (!lines.contains(other.line())) lines.add(other.line());
        return lines.indexOf(fence.line()) < points();
    }

    @Override
    public void clear() {
        lamps.clear();
        fences.clear();
        gaps.clear();
        powerSwitch = null;
        generator = null;
        level = 1;
        health = GENERATOR_HEALTH;
        outage = 0;
        on = false;
    }

    @Override
    public boolean use(GamePlayer gamePlayer, String station) {
        if (SWITCH.equals(station)) {
            useSwitch(gamePlayer.getPlayer());
        } else if (GENERATOR.equals(station)) {
            if (generator == null) return true;
            upgradeMenu.open(gamePlayer, this::view, this::useGenerator);
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

    // What the upgrade menu shows: out of order means repair (restart), else the next level
    private UpgradeMenu.View view() {
        UpgradeMenu.Kind kind = outage > 0 ? UpgradeMenu.Kind.REPAIR : level < MAX_LEVEL ? UpgradeMenu.Kind.UPGRADE : UpgradeMenu.Kind.MAX;
        int cost = switch (kind) {
            case REPAIR -> RESTART_PRICE;
            case UPGRADE -> UPGRADE[level + 1];
            default -> 0;
        };
        int condition = outage > 0 ? 0 : (int) Math.round(100.0 * Math.max(0, health) / GENERATOR_HEALTH);
        return new UpgradeMenu.View("Generator", "generator", "ausbau_generator_" + level, MAX_LEVEL, level, kind, condition, cost, NAMES, NAMES_EN);
    }

    // The button of the upgrade menu. Out: restart for one part, otherwise the next level
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
        for (Fence fence : fences) {
            if (fencePowered(fence)) zap(fence.display);
        }
    }

    /** Every 2 ticks: electricity on the powered fences that a player can see */
    public void animate() {
        if (fences.isEmpty() || !isPowered()) return;
        step++;
        // Under load fewer sparks (user 31.10.: it must never lag): every second time, heavy only the arcs
        int load = LagGuard.level();
        List<Fence> powered = new ArrayList<>();
        for (Fence fence : fences) if (fence.display.isValid() && fencePowered(fence) && seen(fence)) powered.add(fence);
        if (load == 0 || load == 1 && step % 2 == 0) {
            for (int i = 0; i < Math.min(MAX_PULSES, powered.size()); i++) {
                Fence fence = powered.get((i + step) % powered.size());
                FenceSparks.pulse(FenceSparks.base(fence.display), fence.wires, step, fence.display.getEntityId());
            }
        }
        if (step % 10 != 0) return;
        for (Fence[] gap : gaps) {
            if (!powered.contains(gap[0]) || !powered.contains(gap[1]) || ThreadLocalRandom.current().nextDouble() >= ARC_CHANCE) continue;
            Location a = FenceSparks.base(gap[0].display), b = FenceSparks.base(gap[1].display);
            Vector towards = b.toVector().subtract(a.toVector()).normalize();
            FenceSparks.arc(FenceSparks.end(a, gap[0].wires, towards), FenceSparks.end(b, gap[1].wires, towards.clone().multiply(-1)));
        }
    }

    private static boolean seen(Fence fence) {
        Location at = fence.display.getLocation();
        for (Player player : at.getWorld().getPlayers()) if (player.getLocation().distanceSquared(at) <= SPARK_VIEW * SPARK_VIEW) return true;
        return false;
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
        for (Fence fence : fences) {
            if (fence.display.isValid()) decorationManager.setModel(fence.display, fence.base + (fencePowered(fence) ? "an" : "aus"));
        }
    }

    private static void light(Block block, boolean on) {
        if (on && block.getType().isAir()) {
            Light light = (Light) Material.LIGHT.createBlockData();
            light.setLevel(15);
            Restore.block(block);
            block.setBlockData(light, false);
        } else if (!on && block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
    }

    private static String grey(String text) {
        return "<color:" + Overlay.GREY + ">" + text + "</color>";
    }
}
