package de.ethicbuilds.monsters.building;

import de.ethicbuilds.monsters.player.GamePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;

/***
 * One kind of build part: takes its Nexo furniture from the map and handles clicks on its stations
 */
interface BuildPart {
    /** false when the id is not one of this part */
    boolean register(ItemDisplay display, String id, Block block);

    /** false when the station is not one of this part */
    boolean use(GamePlayer gamePlayer, String station);

    /** Every second while the wave runs */
    default void tick() {}
}
