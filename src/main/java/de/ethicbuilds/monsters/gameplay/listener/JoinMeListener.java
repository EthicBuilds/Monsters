package de.ethicbuilds.monsters.gameplay.listener;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.gameplay.model.GameConfig;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.manager.UserManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/***
 * JoinMe in the waiting phase: the bell sends a call to lobby and build servers through the proxy,
 * the proxy checks the cooldown and answers in chat itself
 */
public class JoinMeListener implements Listener {
    public static final String CHANNEL = "ethicbuilds:joinme";
    public static final int SLOT = 8;
    private static final long CLICK_COOLDOWN = 2000;

    @Inject private Main plugin;
    @Inject private GameManager gameManager;
    @Inject private UserManager userManager;

    private final Map<UUID, Long> lastClick = new HashMap<>();

    public static ItemStack item(Player player) {
        ItemStack item = new ItemStack(Material.BELL);
        item.editMeta(meta -> {
            meta.setDisplayName("§6JoinMe");
            meta.setLore(Lang.english(player)
                    ? List.of("§7Calls players from the lobby and", "§7build servers into your round")
                    : List.of("§7Ruft Spieler aus Lobby und", "§7Bauservern in deine Runde"));
        });
        return item;
    }

    // Runs after GameListener cancels every interaction
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) return;
        if (event.getItem() == null || event.getItem().getType() != Material.BELL) return;
        event.setCancelled(true);
        send(event.getPlayer());
    }

    private void send(Player player) {
        if (gameManager.getCurrentPhase() != GamePhase.PRE_GAME) return;
        if (userManager.isFull()) {
            player.sendMessage(plugin.getMonstersPrefix() + Lang.t(player, "§cDie Runde ist schon voll", "§cThe round is already full"));
            return;
        }
        // Only against double clicks, the proxy has the real cooldown
        long now = System.currentTimeMillis();
        if (now - lastClick.getOrDefault(player.getUniqueId(), 0L) < CLICK_COOLDOWN) return;
        lastClick.put(player.getUniqueId(), now);

        GameConfig config = gameManager.getGameConfig();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("senden");
        out.writeUTF("Monsters");
        out.writeUTF(gameManager.getRoundKey());
        out.writeBoolean(player.getUniqueId().toString().equals(config.getErsteller()));
        out.writeUTF(String.format("%s · %d/%d", config.getMapName(), userManager.getGamePlayers().size(), config.getPlayerCount()));
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }
}
