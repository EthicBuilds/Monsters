package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.CrowbarLevel;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Nests: smashed with the crowbar they give coins and parts, afterwards fewer monsters come out of the spawners nearby
 */
public class Nests implements BuildPart {
    private static final String ID = "monsters_nest", DESTROYED_ID = "monsters_nest_zerstoert", STATION = "nest#";
    private static final int HITS = 8, COINS = 400, PARTS = 3;
    private static final long HIT_COOLDOWN = 400;
    // Spawners this close to a destroyed nest are skipped this often
    private static final double CALM_RADIUS = 12, CALM_SHARE = 0.6;
    private static final String COLOR = "#B455E0";

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;

    private final List<Nest> nests = new ArrayList<>();
    private static final List<Location> destroyed = new ArrayList<>();

    private static final class Nest {
        private final ItemDisplay display;
        private final Block block;
        private final Interaction hitbox;
        private final Transformation rest;
        private int hits;
        private long lastHit;

        private Nest(ItemDisplay display, Block block, Interaction hitbox) {
            this.display = display;
            this.block = block;
            this.hitbox = hitbox;
            this.rest = display.getTransformation();
        }
    }

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (!ID.equals(id)) return false;
        nests.add(new Nest(display, block, decorationManager.hitbox(block, STATION + nests.size(), 1.6F, 1.4F)));
        return true;
    }

    /** true: a monster should rather not come out here, a nest nearby was destroyed */
    public static boolean calmed(Location spawner) {
        for (Location nest : destroyed) {
            if (nest.getWorld().equals(spawner.getWorld()) && nest.distanceSquared(spawner) <= CALM_RADIUS * CALM_RADIUS) {
                return ThreadLocalRandom.current().nextDouble() < CALM_SHARE;
            }
        }
        return false;
    }

    @Override
    public boolean use(GamePlayer gamePlayer, String station) {
        if (!station.startsWith(STATION)) return false;
        int index;
        try {
            index = Integer.parseInt(station.substring(STATION.length()));
        } catch (NumberFormatException e) {
            return true;
        }
        if (index < 0 || index >= nests.size()) return true;
        hit(gamePlayer, nests.get(index));
        return true;
    }

    private void hit(GamePlayer gamePlayer, Nest nest) {
        Player player = gamePlayer.getPlayer();
        if (nest.hits >= HITS || !nest.display.isValid()) return;
        if (!GameItems.isCrowbar(player.getInventory().getItemInMainHand())) {
            Overlay.small(player, Overlay.icon(Overlay.COCOON, COLOR, grey("Zerschlage das Nest mit der ") + bold("Brechstange")),
                    Overlay.icon(Overlay.COCOON, COLOR, grey("Smash the nest with the ") + bold("Crowbar")));
            Sounds.deny(player);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - nest.lastHit < HIT_COOLDOWN / CrowbarLevel.speed(gamePlayer.getCrowbarLevel())) return;
        nest.lastHit = now;
        nest.hits++;

        player.swingMainHand();
        Location center = nest.block.getLocation().add(0.5, 0.5, 0.5);
        center.getWorld().spawnParticle(Particle.BLOCK, center, 10, 0.4, 0.3, 0.4, Material.SLIME_BLOCK.createBlockData());
        center.getWorld().playSound(center, Sound.ENTITY_SLIME_SQUISH, 1.0F, 0.7F + nest.hits * 0.05F);
        if (nest.hits < HITS) {
            // Shrinks a little with every hit
            float scale = 1 - 0.04F * nest.hits;
            nest.display.setInterpolationDelay(0);
            nest.display.setInterpolationDuration(3);
            nest.display.setTransformation(new Transformation(nest.rest.getTranslation(), nest.rest.getLeftRotation(),
                    new Vector3f(nest.rest.getScale()).mul(scale), nest.rest.getRightRotation()));
            Overlay.small(player, Overlay.icon(Overlay.COCOON, COLOR, bold("Nest") + grey(" · noch ") + Overlay.num(HITS - nest.hits) + grey(" Schläge")),
                    Overlay.icon(Overlay.COCOON, COLOR, bold("Nest") + grey(" · ") + Overlay.num(HITS - nest.hits) + grey(" hits to go")));
            return;
        }
        destroy(gamePlayer, nest, center);
    }

    private void destroy(GamePlayer gamePlayer, Nest nest, Location center) {
        destroyed.add(center);
        center.getWorld().spawnParticle(Particle.BLOCK, center, 50, 0.7, 0.5, 0.7, Material.SLIME_BLOCK.createBlockData());
        center.getWorld().spawnParticle(Particle.SQUID_INK, center, 15, 0.5, 0.4, 0.5, 0.05);
        center.getWorld().playSound(center, Sound.ENTITY_SLIME_DEATH, 1.2F, 0.6F);
        nest.display.setTransformation(nest.rest);
        decorationManager.setModel(nest.display, DESTROYED_ID);
        decorationManager.removeHitbox(nest.hitbox);
        gamePlayer.addCoins(COINS);
        Parts.give(gamePlayer, PARTS);
        Lang.broadcast("%s§d%s §7hat ein §dNest §7zerstört! Hier kommen jetzt weniger Monster.",
                "%s§d%s §7destroyed a §dnest§7! Fewer monsters come out here now.", plugin.getMonstersPrefix(), gamePlayer.getPlayer().getName());
    }

    private static String bold(String text) {
        return "<color:" + COLOR + "><b>" + text + "</b></color>";
    }

    private static String grey(String text) {
        return "<color:" + Overlay.GREY + ">" + text + "</color>";
    }
}
