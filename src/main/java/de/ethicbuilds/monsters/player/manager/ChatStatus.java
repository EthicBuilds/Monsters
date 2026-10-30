package de.ethicbuilds.monsters.player.manager;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.ethicbuilds.monsters.Main;
import org.bukkit.entity.Player;

/***
 * Tells EthicChat on the proxy who is down or only watching: they write grey in the chat until they live again
 */
public final class ChatStatus {
    public static final String CHANNEL = "ethicbuilds:monsters";

    private ChatStatus() {}

    public static void send(Player player, boolean dead) {
        if (!player.isOnline()) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("status");
        out.writeUTF(player.getUniqueId().toString());
        out.writeUTF(dead ? "tot" : "lebt");
        player.sendPluginMessage(Main.getINSTANCE(), CHANNEL, out.toByteArray());
    }
}
