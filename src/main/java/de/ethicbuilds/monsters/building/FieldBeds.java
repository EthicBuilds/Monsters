package de.ethicbuilds.monsters.building;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import de.ethicbuilds.monsters.map.decoration.HoloText;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/***
 * Field bed (user 31.10./10.11.): unlocked once for 8 parts, then everyone who is dead comes back at it in the next wave
 * instead of at the spawn. With several beds the last unlocked one counts.
 */
public class FieldBeds implements BuildPart {
    private static final String LOCKED_ID = "monsters_feldbett_gesperrt", FREE_ID = "monsters_feldbett_frei", STATION = "feldbett#";
    private static final int PRICE = 8;
    private static final String COLOR = "#5D6A47";

    // Read when the dead come back, null without an unlocked bed
    private static Location respawn;

    @Inject private DecorationManager decorationManager;

    private final List<Bed> beds = new ArrayList<>();

    private static final class Bed {
        private final ItemDisplay display;
        private final Block block;
        private DecorationManager.Sign sign;

        private Bed(ItemDisplay display, Block block) {
            this.display = display;
            this.block = block;
        }
    }

    /** Where the dead come back in the next wave, null for the spawn */
    public static Location respawn() {
        return respawn;
    }

    @Override
    public boolean register(ItemDisplay display, String id, Block block) {
        if (!LOCKED_ID.equals(id) && !FREE_ID.equals(id)) return false;
        Bed bed = new Bed(display, block);
        decorationManager.hitbox(block, STATION + beds.size(), 1.0F, 0.7F);
        beds.add(bed);
        if (FREE_ID.equals(id)) respawn = spot(bed);
        else bed.sign = decorationManager.sign(block, HoloText.PARTS, "Feldbett", "Field bed", HoloText.parts(PRICE, false),
                HoloText.parts(PRICE, true), "Rechtsklick zum Freischalten", "Right-click to unlock", 1.0);
        return true;
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
        if (index < 0 || index >= beds.size()) return true;
        Bed bed = beds.get(index);
        Player player = gamePlayer.getPlayer();
        if (bed.sign == null) {
            Overlay.small(player, Overlay.line((char) 0, COLOR, "FELDBETT", "Tote steigen hier in der nächsten Welle ein"),
                    Overlay.line((char) 0, COLOR, "FIELD BED", "the dead come back here in the next wave"));
            return true;
        }
        if (!Parts.take(gamePlayer, PRICE)) return true;
        decorationManager.removeSign(bed.sign);
        bed.sign = null;
        decorationManager.setModel(bed.display, FREE_ID);
        respawn = spot(bed);
        Location at = bed.block.getLocation().add(0.5, 0.5, 0.5);
        at.getWorld().playSound(at, Sound.BLOCK_WOOL_PLACE, 1.0F, 0.8F);
        Sounds.buy(player);
        Lang.broadcast("%s§a%s §7hat das §aFeldbett §7freigeschaltet: Wer stirbt, steigt dort in der nächsten Welle wieder ein!",
                "%s§a%s §7unlocked the §afield bed§7: whoever dies comes back there in the next wave!",
                Main.getINSTANCE().getMonstersPrefix(), player.getName());
        return true;
    }

    private static Location spot(Bed bed) {
        Location at = bed.block.getLocation().add(0.5, 0, 0.5);
        at.setYaw(bed.display.getLocation().getYaw());
        return at;
    }

    @Override
    public void clear() {
        beds.clear();
        respawn = null;
    }
}
