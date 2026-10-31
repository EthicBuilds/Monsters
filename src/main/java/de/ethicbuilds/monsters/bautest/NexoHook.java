package de.ethicbuilds.monsters.bautest;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import com.nexomc.nexo.mechanics.furniture.IFurniturePacketManager;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

/***
 * Calls into Nexo, only loaded when Nexo runs (build server): its furniture lives in packets,
 * barriers and model are sent by Nexo itself
 */
final class NexoHook {
    private NexoHook() {}

    static void removeBarriers(ItemDisplay display, Player player) {
        FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic(display);
        IFurniturePacketManager packets = packets();
        if (mechanic != null && packets != null) packets.removeBarrierHitboxPacket(display, mechanic, player);
    }

    static void sendBarriers(ItemDisplay display, Player player) {
        FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic(display);
        IFurniturePacketManager packets = packets();
        if (mechanic != null && packets != null) packets.sendBarrierHitboxPacket(display, mechanic, player);
    }

    /** Nexo's own model again, after we set the item back to the stored one */
    static void showModel(ItemDisplay display) {
        FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic(display);
        IFurniturePacketManager packets = packets();
        if (mechanic != null && packets != null) packets.sendFurnitureMetadataPacket(display, mechanic);
    }

    static boolean isFurniture(ItemDisplay display) {
        return NexoFurniture.isFurniture(display);
    }

    private static IFurniturePacketManager packets() {
        FurnitureFactory factory = FurnitureFactory.Companion.instance();
        return factory == null ? null : factory.packetManager();
    }
}
