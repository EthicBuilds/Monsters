package de.ethicbuilds.monsters.gameplay;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Overlay;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/***
 * "+20" with a coin (or the headshot crosshair) above the hit monster, only for the player who earned it (design session).
 * A text display rises 0.6 blocks in 15 ticks and fades out from tick 10. At most 6 per player, more hits add up in the newest.
 */
public final class CoinPopups {
    private static final int MAX_PER_PLAYER = 6;
    private static final int RISE_TICKS = 15, FADE_FROM = 10;
    private static final float RISE = 0.6F, SCALE = 0.8F;
    // Text opacity 0-3 counts as fully visible in the client, 4 is as good as gone
    private static final byte GONE = 4;
    private static final String HEADSHOT_COLOR = "#FF6B6B";

    private static final class Popup {
        private TextDisplay display;
        private int coins;
        private boolean headshot;
    }

    private static final Map<UUID, Deque<Popup>> active = new HashMap<>();

    private CoinPopups() {}

    public static void show(Player viewer, Location monster, int coins, boolean headshot) {
        Main plugin = Main.getINSTANCE();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Deque<Popup> popups = active.computeIfAbsent(viewer.getUniqueId(), id -> new ArrayDeque<>());
            popups.removeIf(popup -> !popup.display.isValid());
            if (popups.size() >= MAX_PER_PLAYER) {
                Popup newest = popups.peekLast();
                newest.coins += coins;
                newest.headshot |= headshot;
                newest.display.text(Overlay.parse(text(newest)));
                return;
            }

            Popup popup = new Popup();
            popup.coins = coins;
            popup.headshot = headshot;
            Location at = monster.clone().add(0, 2.1, 0);
            popup.display = at.getWorld().spawn(at, TextDisplay.class, display -> {
                display.setVisibleByDefault(false);
                display.setPersistent(false);
                display.text(Overlay.parse(text(popup)));
                display.setBillboard(Display.Billboard.CENTER);
                display.setShadowed(true);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setDefaultBackground(false);
                display.setTransformation(lift(0));
            });
            if (viewer.isOnline()) viewer.showEntity(plugin, popup.display);
            popups.addLast(popup);

            // Same speed in both steps: rise until the fade starts, then the rest while fading
            TextDisplay display = popup.display;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!display.isValid()) return;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(FADE_FROM - 1);
                display.setTransformation(lift(RISE * FADE_FROM / RISE_TICKS));
            }, 1L);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!display.isValid()) return;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(RISE_TICKS - FADE_FROM);
                display.setTransformation(lift(RISE));
                display.setTextOpacity(GONE);
            }, FADE_FROM);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                display.remove();
                popups.remove(popup);
                if (popups.isEmpty()) active.remove(viewer.getUniqueId(), popups);
            }, RISE_TICKS + 1);
        });
    }

    private static Transformation lift(float height) {
        return new Transformation(new Vector3f(0, height, 0), new AxisAngle4f(), new Vector3f(SCALE), new AxisAngle4f());
    }

    private static String text(Popup popup) {
        String symbol = popup.headshot
                ? "<color:" + HEADSHOT_COLOR + "><font:monsters:tab></font></color>"
                : Overlay.coin();
        return symbol + Overlay.space(3) + "<b>" + Overlay.num("+" + popup.coins) + "</b>";
    }
}
