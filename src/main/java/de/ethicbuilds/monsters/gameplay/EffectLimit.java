package de.ethicbuilds.monsters.gameplay;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Thins out particles and sounds per player: with many monsters at once only so many packets per tick go out,
 * the rest is dropped
 */
public final class EffectLimit {
    private static final int PARTICLES_PER_TICK = 40, SOUNDS_PER_TICK = 12;
    private static boolean started;

    private EffectLimit() {}

    public static void start(Plugin plugin) {
        if (started) return;
        started = true;
        try {
            listen(plugin);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Effekt-Grenze aus: " + e.getMessage());
        }
    }

    private static void listen(Plugin plugin) {
        Map<UUID, int[]> sent = new ConcurrentHashMap<>();
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW,
                PacketType.Play.Server.WORLD_PARTICLES, PacketType.Play.Server.NAMED_SOUND_EFFECT, PacketType.Play.Server.ENTITY_SOUND) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.isPlayerTemporary()) return;
                // Per player: tick, particles, sounds
                int[] count = sent.computeIfAbsent(event.getPlayer().getUniqueId(), id -> new int[3]);
                int tick = Bukkit.getCurrentTick();
                synchronized (count) {
                    if (count[0] != tick) {
                        count[0] = tick;
                        count[1] = 0;
                        count[2] = 0;
                    }
                    boolean particle = event.getPacketType() == PacketType.Play.Server.WORLD_PARTICLES;
                    if (particle ? ++count[1] > PARTICLES_PER_TICK : ++count[2] > SOUNDS_PER_TICK) event.setCancelled(true);
                }
            }
        });
        Bukkit.getScheduler().runTaskTimer(plugin, () -> sent.keySet().removeIf(id -> Bukkit.getPlayer(id) == null), 1200L, 1200L);
    }
}
