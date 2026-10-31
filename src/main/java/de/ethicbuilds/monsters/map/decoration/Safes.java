package de.ethicbuilds.monsters.map.decoration;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.items.GameItems;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.sound.Sounds;
import de.ethicbuilds.monsters.weapons.RocketLoot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/***
 * Safe of the map (user 31.10.): three code notes lie on crates, each shows one digit. Who has found all three
 * opens the safe once per round: coins and with luck the rocket launcher.
 */
public class Safes {
    public static final String SAFE = "tresor#", NOTE = "zettel#";
    static final String SAFE_ID = "monsters_tresor_zu";
    private static final String OPEN_ID = "monsters_tresor_offen", NOTE_ID = "monsters_codezettel";
    private static final int NOTES = 3, COINS = 3000;
    private static final double ROCKET_CHANCE = 0.3;
    // Crates register a moment later than the safe, the notes wait for them
    private static final long PLACE_DELAY = 60;
    private static final String COLOR = "#F9A11E";

    @Inject private Main plugin;
    @Inject private DecorationManager decorationManager;
    @Inject private CrateManager crateManager;
    @Inject private RocketLoot rocketLoot;

    private final List<ItemDisplay> safes = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private final int[] code = new int[NOTES];
    private boolean placed, opened;
    private int found;

    private record Note(ItemDisplay display, TextDisplay digit, Interaction hitbox, int position) {}

    void register(ItemDisplay display, Block block) {
        safes.add(display);
        decorationManager.hitbox(block, SAFE + (safes.size() - 1), 1.0F, 1.6F);
        if (!placed) {
            placed = true;
            for (int i = 0; i < NOTES; i++) code[i] = ThreadLocalRandom.current().nextInt(10);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> placeNotes(block), PLACE_DELAY);
        }
    }

    // On top of random crates, without crates around the safe
    private void placeNotes(Block safe) {
        List<Block> spots = new ArrayList<>(crateManager.crateBlocks());
        Collections.shuffle(spots);
        for (int i = 0; i < NOTES; i++) {
            Block on = i < spots.size() ? spots.get(i)
                    : safe.getRelative(ThreadLocalRandom.current().nextInt(-6, 7), 0, ThreadLocalRandom.current().nextInt(-6, 7)).getRelative(0, -1, 0);
            Location at = on.getLocation().add(0.5, 1.02, 0.5);
            ItemStack item = new ItemStack(Material.PAPER);
            GameItems.setModel(item, NOTE_ID);
            ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, note -> {
                note.setItemStack(item);
                note.setPersistent(false);
                note.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(ThreadLocalRandom.current().nextFloat() * 6.28F),
                        new Vector3f(1), new Quaternionf()));
            });
            int position = i;
            TextDisplay digit = at.getWorld().spawn(at.clone().add(0, 0.45, 0), TextDisplay.class, text -> {
                text.text(Component.text(code[position] + "", TextColor.fromHexString(COLOR)));
                text.setBillboard(Display.Billboard.CENTER);
                text.setBackgroundColor(Color.fromARGB(0x80, 0x14, 0x10, 0x0C));
                text.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8F), new AxisAngle4f()));
                text.setPersistent(false);
            });
            Interaction hitbox = decorationManager.hitbox(at.getBlock(), NOTE + notes.size(), 0.7F, 0.4F);
            hitbox.teleport(at.clone().subtract(0, 0.05, 0));
            notes.add(new Note(display, digit, hitbox, position));
        }
    }

    public void use(GamePlayer gamePlayer, String station) {
        Player player = gamePlayer.getPlayer();
        if (station.startsWith(NOTE)) {
            int index = Integer.parseInt(station.substring(NOTE.length()));
            if (index < 0 || index >= notes.size()) return;
            Note note = notes.get(index);
            if (!note.display().isValid()) return;
            note.display().remove();
            note.digit().remove();
            decorationManager.removeHitbox(note.hitbox());
            found++;
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0F, 1.0F);
            Lang.broadcast("%s§6%s §7hat einen §6Code-Zettel §7gefunden: §6%s §7(§6%d§7/§6%d§7)", "%s§6%s §7found a §6code note§7: §6%s §7(§6%d§7/§6%d§7)",
                    plugin.getMonstersPrefix(), player.getName(), codeSoFar(), found, NOTES);
            return;
        }
        int index = Integer.parseInt(station.substring(SAFE.length()));
        if (index < 0 || index >= safes.size()) return;
        if (opened) {
            Lang.note(player, Lang.t(player, "§7Der Tresor ist schon leer", "§7The safe is already empty"));
            return;
        }
        if (found < NOTES) {
            Lang.note(player, Lang.f(player, "§7Code: §6%s §7· finde die §6Code-Zettel §7auf den Kisten", "§7Code: §6%s §7· find the §6code notes §7on the crates", codeSoFar()));
            Sounds.deny(player);
            return;
        }
        open(gamePlayer, safes.get(index));
    }

    private String codeSoFar() {
        StringBuilder text = new StringBuilder();
        List<Integer> known = new ArrayList<>();
        for (Note note : notes) if (!note.display().isValid()) known.add(note.position());
        for (int i = 0; i < NOTES; i++) text.append(known.contains(i) ? String.valueOf(code[i]) : "_");
        return text.toString();
    }

    private void open(GamePlayer gamePlayer, ItemDisplay safe) {
        opened = true;
        Player player = gamePlayer.getPlayer();
        decorationManager.setModel(safe, OPEN_ID);
        Location at = safe.getLocation().add(0, 1, 0);
        at.getWorld().playSound(at, Sound.BLOCK_IRON_DOOR_OPEN, 1.0F, 0.7F);
        at.getWorld().spawnParticle(Particle.WAX_OFF, at, 20, 0.4, 0.4, 0.4, 0.05);
        gamePlayer.addCoins(COINS);
        Lang.broadcast("%s§6%s §7hat den §6Tresor §7geknackt! §6+%d Coins", "%s§6%s §7cracked the §6safe§7! §6+%d Coins",
                plugin.getMonstersPrefix(), player.getName(), COINS);
        Sounds.buy(player);
        if (ThreadLocalRandom.current().nextDouble() < ROCKET_CHANCE) rocketLoot.grant(gamePlayer, at);
    }
}
