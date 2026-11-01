package de.ethicbuilds.monsters.commands;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.player.manager.UserManager;
import de.ethicbuilds.monsters.sound.Sounds;
import de.ethicbuilds.monsters.weapons.AmmoHud;
import de.ethicbuilds.monsters.weapons.Pistol;
import de.ethicbuilds.monsters.weapons.Pumpgun;
import de.ethicbuilds.monsters.weapons.Rifle;
import de.ethicbuilds.monsters.weapons.RocketLauncher;
import de.ethicbuilds.monsters.weapons.Sniper;
import de.ethicbuilds.monsters.weapons.Weapon;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/***
 * /testwaffe on the build server (user 01.11.): a real weapon with full ammo for a tester in function mode.
 * It goes when the tester leaves, like the rest of the test inventory.
 */
public class TestWeaponCommand implements TabExecutor {
    private static final Map<String, Supplier<Weapon>> WEAPONS = Map.of(
            "raketenwerfer", RocketLauncher::new,
            "sturm", Rifle::new,
            "sniper", Sniper::new,
            "schrot", Pumpgun::new,
            "pistole", Pistol::new);

    @Inject private UserManager userManager;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) return true;
        if (!BauTest.on() || !BauTest.active(player)) {
            Lang.note(player, Lang.t(player, "§7Nur im §aFunktionsmodus §7(/monster)", "§7Only in §afunction mode §7(/monster)"));
            return true;
        }
        Supplier<Weapon> make = args.length == 1 ? WEAPONS.get(args[0].toLowerCase()) : null;
        GamePlayer gamePlayer = userManager.getGamePlayer(player.getUniqueId());
        if (make == null || gamePlayer == null) {
            Lang.note(player, "§7/testwaffe <" + String.join("|", WEAPONS.keySet()) + ">");
            return true;
        }
        Weapon weapon = make.get();
        weapon.localize(player);
        // Already there: only the ammo is filled up
        Weapon owned = gamePlayer.getWeapon(new ItemStack(weapon.getItem().getType()));
        if (owned != null) {
            owned.refill();
            refresh(player, gamePlayer);
            Sounds.buy(player);
            return true;
        }
        int slot = slot(player, gamePlayer);
        Weapon replaced = gamePlayer.getWeapon(item(player, slot));
        if (replaced != null) gamePlayer.removeWeapon(replaced);
        weapon.refill();
        player.getInventory().setItem(slot, weapon.getItem());
        gamePlayer.addWeapon(weapon);
        refresh(player, gamePlayer);
        Sounds.buy(player);
        return true;
    }

    // A free weapon slot, else the last one is swapped
    private static int slot(Player player, GamePlayer gamePlayer) {
        int[] slots = gamePlayer.getWeaponSlots();
        for (int slot : slots) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() == Material.LIGHT_GRAY_DYE) return slot;
        }
        return slots[slots.length - 1];
    }

    private static ItemStack item(Player player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        return item != null ? item : new ItemStack(Material.AIR);
    }

    private static void refresh(Player player, GamePlayer gamePlayer) {
        Weapon held = gamePlayer.getWeapon(player.getInventory().getItemInMainHand());
        if (held != null) AmmoHud.refresh(player, held);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length != 1) return List.of();
        return WEAPONS.keySet().stream().filter(name -> name.startsWith(args[0].toLowerCase())).sorted().toList();
    }
}
