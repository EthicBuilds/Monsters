package de.ethicbuilds.monsters.player.manager;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.player.GamePlayer;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import net.kyori.adventure.key.Key;
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
 * Team ping: mouse wheel click (or F) marks the spot the player looks at up to 50 blocks away, the team sees it
 * through walls for 5 seconds. Downed players can ping as well, only more rarely.
 */
public class PingManager implements Listener {
    public static final int RANGE = 50;
    private static final long LIFETIME = 5 * 20;
    // Founder and up (they have the coin boost, the team has *) every second, others every 5 seconds, downed every 20
    private static final long COOLDOWN = 5000, FOUNDER_COOLDOWN = 1000, DOWNED_COOLDOWN = 20000;
    private static final int FADE = 20;
    private static final TextColor NAME_COLOR = TextColor.color(0xA0F2FA);
    private static final Key PING_FONT = Key.key("monsters", "ping");

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;

    private final Map<UUID, Long> lastPing = new HashMap<>();
    private final Map<UUID, TextDisplay> markers = new HashMap<>();

    // The wheel click only reaches the server for blocks within the interaction range, so that range is 50 in Monsters
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

    // Left or right click with the monster tracker in hand
    @EventHandler(priority = EventPriority.LOW)
    public void onTracker(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getAction() == org.bukkit.event.block.Action.PHYSICAL) return;
        if (event.getPlayer().getInventory().getItemInMainHand().getType() != org.bukkit.Material.COMPASS) return;
        ping(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        ping(event.getPlayer());
    }

    private void ping(Player player) {
        if (gameManager.getCurrentPhase() != GamePhase.WAVE || !userManager.isGamePlayer(player.getUniqueId())) return;
        boolean downed = userManager.isPlayerDead(player.getUniqueId());
        long cooldown = downed ? DOWNED_COOLDOWN : player.hasPermission("ethiccoins.boost.50") ? FOUNDER_COOLDOWN : COOLDOWN;
        long now = System.currentTimeMillis();
        long wait = lastPing.getOrDefault(player.getUniqueId(), 0L) + cooldown - now;
        if (wait > 0) {
            String seconds = " " + Overlay.num((wait + 999) / 1000 + " s");
            String grey = "<color:" + Overlay.GREY + ">";
            Overlay.small(player, Overlay.icon(Overlay.PING, Overlay.GREY, grey + "Ping bereit in</color>" + seconds),
                    Overlay.icon(Overlay.PING, Overlay.GREY, grey + "Ping ready in</color>" + seconds));
            return;
        }

        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), RANGE, FluidCollisionMode.NEVER, true, 0.3,
                entity -> !entity.equals(player) && !(entity instanceof Display) && !(entity instanceof org.bukkit.entity.Interaction)
                        && !(entity instanceof org.bukkit.entity.Mannequin));
        if (hit == null) return;
        lastPing.put(player.getUniqueId(), now);

        Location spot = hit.getHitPosition().toLocation(player.getWorld());
        TextDisplay marker = player.getWorld().spawn(spot, TextDisplay.class, display -> {
            display.setVisibleByDefault(false);
            // Name above the pin from the design session, the pin image is 43 px high and reaches up into the empty lines
            display.text(Component.text(player.getName(), NAME_COLOR).append(Component.text("\n\n\n\n\n"))
                    .append(Component.text("\uE000", NamedTextColor.WHITE).font(PING_FONT)));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setDefaultBackground(false);
            display.setTransformation(new Transformation(new Vector3f(0, -0.1F, 0), new AxisAngle4f(), new Vector3f(2F), new AxisAngle4f()));
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
        TextDisplay old = markers.put(player.getUniqueId(), marker);
        if (old != null && old.isValid()) fade(old);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            marker.remove();
            markers.remove(player.getUniqueId(), marker);
        }, LIFETIME);
    }

    // Old ping shrinks away within a second
    private void fade(TextDisplay marker) {
        Transformation now = marker.getTransformation();
        marker.setInterpolationDelay(0);
        marker.setInterpolationDuration(FADE);
        marker.setTransformation(new Transformation(now.getTranslation(), now.getLeftRotation(), new Vector3f(0), now.getRightRotation()));
        plugin.getServer().getScheduler().runTaskLater(plugin, marker::remove, FADE);
    }
}
