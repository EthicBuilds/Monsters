package de.ethicbuilds.monsters.player.manager;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Whether the Monsters pack arrived. Without it the symbols of the tab list, workbench and HUD are boxes:
 * the player gets a hint and the tab list shows plain text for them.
 */
public class PackStatus implements Listener {
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("monsters-pack".getBytes());
    private static final Set<UUID> missing = ConcurrentHashMap.newKeySet();

    public static boolean hasPack(UUID uuid) {
        return !missing.contains(uuid);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status != PlayerResourcePackStatusEvent.Status.ACCEPTED && status != PlayerResourcePackStatusEvent.Status.DOWNLOADED) Main.getINSTANCE().getLogger().info("Monsters-Paket bei " + player.getName() + ": " + status);
        switch (status) {
            case SUCCESSFULLY_LOADED -> missing.remove(player.getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                missing.add(player.getUniqueId());
                player.sendMessage(Lang.t(player,
                        Main.getINSTANCE().getMonstersPrefix() + "§cDas Monsters-Paket wurde nicht geladen, viele Symbole fehlen. §7Erlaube Server-Ressourcenpakete (Serverliste → Bearbeiten → Server-Ressourcenpakete: An) und betritt die Runde neu.",
                        Main.getINSTANCE().getMonstersPrefix() + "§cThe Monsters pack was not loaded, many symbols are missing. §7Allow server resource packs (server list → Edit → Server Resource Packs: Enabled) and join the round again."));
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        missing.remove(event.getPlayer().getUniqueId());
    }
}
