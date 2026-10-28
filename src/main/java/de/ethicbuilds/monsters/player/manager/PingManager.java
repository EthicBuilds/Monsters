package de.ethicbuilds.monsters.player.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.player.GamePlayer;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/***
 * Team ping: mouse wheel click (or F) marks the spot the player looks at, the team sees it through walls for 5 seconds
 */
public class PingManager implements Listener {
    private static final int RANGE = 80;
    private static final long LIFETIME = 5 * 20;
    private static final long COOLDOWN = 4000;
    private static final TextColor CYAN = TextColor.color(0x3EC9E6);

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;

    private final Map<UUID, Long> lastPing = new HashMap<>();

    // The wheel click only reaches the server when the player looks at something close by, F works at any distance
    @EventHandler(priority = EventPriority.LOW)
    public void onPickBlock(PlayerPickBlockEvent event) {
        event.setCancelled(true);
        ping(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickEntity(PlayerPickEntityEvent event) {
        event.setCancelled(true);
        ping(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        ping(event.getPlayer());
    }

    private void ping(Player player) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE || userManager.isPlayerDead(player.getUniqueId())) return;
        long now = System.currentTimeMillis();
        if (now - lastPing.getOrDefault(player.getUniqueId(), 0L) < COOLDOWN) return;

        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), RANGE, FluidCollisionMode.NEVER, true, 0.3,
                entity -> !entity.equals(player) && !(entity instanceof Display) && !(entity instanceof org.bukkit.entity.Interaction));
        if (hit == null) return;
        lastPing.put(player.getUniqueId(), now);

        Location spot = hit.getHitPosition().toLocation(player.getWorld()).add(0, 0.2, 0);
        TextDisplay marker = player.getWorld().spawn(spot, TextDisplay.class, display -> {
            display.setVisibleByDefault(false);
            display.text(Component.text("Hier", CYAN).append(Component.newline())
                    .append(Component.text(player.getName(), NamedTextColor.GRAY)).append(Component.newline())
                    .append(Component.text("▼", CYAN)));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setTextOpacity((byte) 200);
            display.setTransformation(new Transformation(new Vector3f(0, 0.4F, 0), new AxisAngle4f(), new Vector3f(2F), new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(4F);
            display.setPersistent(false);
        });
        for (GamePlayer gamePlayer : userManager.getGamePlayers()) {
            Player mate = gamePlayer.getPlayer();
            if (!mate.isOnline()) continue;
            mate.showEntity(plugin, marker);
            mate.playSound(mate.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7F, 1.8F);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, marker::remove, LIFETIME);
    }
}
