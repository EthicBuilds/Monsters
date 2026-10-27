package de.ethicbuilds.monsters.weapons;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/***
 * Ammo panel above the hearts (design session, font monsters:munition from the Monsters pack):
 * cartridge, 10 segments, magazine big, "/" and stock small
 */
public final class AmmoHud {
    private static final Key FONT = Key.key("monsters", "munition");
    private static final long PAUSE_MILLIS = 2000;
    private static final Map<UUID, Long> pausedUntil = new HashMap<>();

    private AmmoHud() {}

    /** Other ActionBar messages stay visible for a moment */
    public static void pause(Player player) {
        pausedUntil.put(player.getUniqueId(), System.currentTimeMillis() + PAUSE_MILLIS);
    }

    /** Shows the new numbers right away, e.g. after buying ammo */
    public static void refresh(Player player, Weapon weapon) {
        pausedUntil.remove(player.getUniqueId());
        show(player, weapon);
    }

    public static void show(Player player, Weapon weapon) {
        player.setLevel(0);
        player.setExp(weapon.getMaxAmmu() > 0 ? Math.max(0, Math.min(1, weapon.getAmmu() / (float) weapon.getMaxAmmu())) : 0);
        if (pausedUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        player.sendActionBar(panel(weapon.getMagazine(), weapon.getMaxMagazine(), weapon.getAmmu()));
    }

    /** No weapon in hand: the panel goes away right away instead of fading out after 3 seconds */
    public static void clear(Player player) {
        player.setLevel(0);
        player.setExp(0);
        if (pausedUntil.getOrDefault(player.getUniqueId(), 0L) <= System.currentTimeMillis()) player.sendActionBar(Component.empty());
    }

    static Component panel(int magazine, int maxMagazine, int stock) {
        boolean low = magazine <= Math.ceil(maxMagazine / 4.0);
        int lit = maxMagazine > 0 ? (int) Math.ceil(10.0 * magazine / maxMagazine) : 0;

        StringBuilder front = new StringBuilder("");
        for (int i = 0; i < 10; i++) front.append(i >= lit ? '' : low ? '' : '');
        front.append('');

        String mag = String.valueOf(Math.min(Math.max(magazine, 0), 99));
        StringBuilder magText = new StringBuilder(mag.length() < 2 ? "" : "");
        for (char c : mag.toCharArray()) magText.append((char) (0xE010 + c - '0'));

        String rest = String.valueOf(Math.min(Math.max(stock, 0), 9999));
        StringBuilder restText = new StringBuilder();
        for (char c : rest.toCharArray()) restText.append((char) (0xE021 + c - '0'));
        restText.append("".repeat(4 - rest.length())).append('');

        return Component.text().font(FONT).shadowColor(ShadowColor.none())
                .append(Component.text(front.toString(), NamedTextColor.WHITE))
                .append(Component.text(magText.toString(), TextColor.color(low ? 0xE84848 : 0xF2F5F8)))
                .append(Component.text("", TextColor.color(0x9AA6B8)))
                .append(Component.text(restText.toString(), TextColor.color(stock == 0 ? 0xE84848 : 0x9AA6B8)))
                .build();
    }
}
