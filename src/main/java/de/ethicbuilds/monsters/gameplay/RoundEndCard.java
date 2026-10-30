package de.ethicbuilds.monsters.gameplay;

import com.google.inject.Inject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.GamePlayer;
import de.ethicbuilds.monsters.reward.DailyChallenge;
import de.ethicbuilds.monsters.reward.EthicCoins;
import de.ethicbuilds.monsters.reward.Payout;
import de.ethicbuilds.monsters.reward.RoundReward;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/***
 * Round end card a few seconds after the game over title: wave, own stats, MVP and where the EthicCoins came from.
 * Read only. First win and daily challenge are booked later, the card fills them in when they arrive.
 */
public class RoundEndCard implements Listener {
    private static final long OPEN_DELAY = 60;
    private static final int SIZE = 45;
    private static final int MVP_SLOT = 4;
    private static final int WAVE_SLOT = 10, KILLS_SLOT = 12, HEADSHOTS_SLOT = 14, REVIVES_SLOT = 16;
    private static final int ROUND_SLOT = 28, KILL_COINS_SLOT = 29, RANK_SLOT = 30, FIRST_WIN_SLOT = 31, CHALLENGE_SLOT = 32, TOTAL_SLOT = 34;
    private static final int CLOSE_SLOT = 40;

    private static final TextColor LIGHT = TextColor.color(0xF2F5F8);
    private static final TextColor GREY = TextColor.color(0x9AA6B8);
    private static final TextColor GOLD = TextColor.color(0xFFCC2E);
    private static final TextColor GREEN = TextColor.color(0x3BDB9A);
    private static final TextColor RED = TextColor.color(0xFF8A80);

    @Inject private Main plugin;

    /** Opens the card for every online player of the round, best player first in the list */
    public void show(List<GamePlayer> ranked, int wave, Map<UUID, RoundReward.Parts> rewards, Map<UUID, CompletableFuture<Payout>> challenges) {
        GamePlayer mvp = ranked.isEmpty() ? null : ranked.getFirst();
        for (GamePlayer gamePlayer : ranked) {
            Player player = gamePlayer.getPlayer();
            if (!player.isOnline()) continue;
            RoundReward.Parts parts = rewards.get(player.getUniqueId());
            Card card = new Card(gamePlayer, mvp, wave, parts, challenges.containsKey(player.getUniqueId()));
            if (parts != null) parts.firstWin().thenAccept(payout -> later(card, () -> card.firstWin = payout));
            else card.firstWin = Payout.NONE;
            CompletableFuture<Payout> challenge = challenges.get(player.getUniqueId());
            if (challenge != null) challenge.thenAccept(payout -> later(card, () -> card.challenge = payout));
            else card.challenge = Payout.NONE;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                card.inventory = Bukkit.createInventory(card, SIZE, title(player));
                fill(card);
                player.openInventory(card.inventory);
            }, OPEN_DELAY);
        }
    }

    // Design session: the glyph background title (like WorkbenchTitle) goes here, until then a plain title
    private static Component title(Player player) {
        return Component.text(Lang.t(player, "Auswertung", "Summary"));
    }

    // Results come from other threads: set on the main thread and redraw an open card
    private void later(Card card, Runnable update) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            update.run();
            if (card.inventory != null) fill(card);
        });
    }

    private void fill(Card card) {
        Player player = card.gamePlayer.getPlayer();
        Inventory inventory = card.inventory;

        if (card.mvp != null) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(card.mvp.getPlayer());
                meta.displayName(text("MVP: ", GOLD).append(text(card.mvp.getPlayer().getName(), LIGHT)));
                meta.lore(List.of(text(Lang.f(player, "%,d Kills", "%,d kills", card.mvp.getKilledZombies()), GREY)));
            });
            inventory.setItem(MVP_SLOT, head);
        }

        inventory.setItem(WAVE_SLOT, stat(Material.CLOCK, Lang.t(player, "Welle", "Wave"), number(player, card.wave),
                Lang.t(player, "So weit seid ihr gekommen", "How far you made it")));
        inventory.setItem(KILLS_SLOT, stat(Material.BONE, "Kills", number(player, card.gamePlayer.getKilledZombies()),
                Lang.t(player, "Deine besiegten Monster", "Monsters you killed")));
        inventory.setItem(HEADSHOTS_SLOT, stat(Material.SKELETON_SKULL, Lang.t(player, "Kopfschüsse", "Headshots"), number(player, card.gamePlayer.getHeadshots()),
                Lang.t(player, "Kills mit Kopfschuss", "Kills with a headshot")));
        inventory.setItem(REVIVES_SLOT, stat(Material.TOTEM_OF_UNDYING, Lang.t(player, "Wiederbelebt", "Revives"), number(player, card.gamePlayer.getRevives()),
                Lang.t(player, "Mitspieler, die du aufgehoben hast", "Teammates you got back up")));

        RoundReward.Parts parts = card.parts;
        int round = parts == null ? 0 : parts.round();
        int kills = parts == null ? 0 : parts.kills();
        String roundHint = Lang.t(player, "Aktive Minuten, Welle, Wiederbelebungen", "Active minutes, wave, revives");
        inventory.setItem(ROUND_SLOT, card.mvp == card.gamePlayer
                ? coins(player, Material.PAPER, Lang.t(player, "Runde", "Round"), round, roundHint, Lang.t(player, "Mit Bonus als Rundenbester", "With the best player bonus"))
                : coins(player, Material.PAPER, Lang.t(player, "Runde", "Round"), round, roundHint));
        inventory.setItem(KILL_COINS_SLOT, coins(player, Material.GOLD_NUGGET, "Kills", kills,
                Lang.t(player, "1 EthicCoin je 50 Kills", "1 EthicCoin per 50 kills")));

        inventory.setItem(FIRST_WIN_SLOT, card.firstWin == null
                ? pending(player, Material.GOLD_INGOT, Lang.t(player, "Erster Sieg", "First win"))
                : coins(player, Material.GOLD_INGOT, Lang.t(player, "Erster Sieg", "First win"), card.firstWin.base(),
                        card.firstWin.paid() > 0 ? Lang.t(player, "Dein erster Sieg heute!", "Your first win today!")
                                : Lang.t(player, "Für den Rundenbesten, einmal am Tag", "For the best player, once a day")));

        String challengeText = DailyChallenge.describe(player);
        List<String> challengeLore = new ArrayList<>();
        if (challengeText != null) challengeLore.add(challengeText);
        challengeLore.add(!card.challengeReached ? Lang.t(player, "Nicht geschafft", "Not done")
                : card.challenge == null ? Lang.t(player, "Wird gebucht …", "Booking …")
                : card.challenge.paid() > 0 ? Lang.t(player, "Geschafft!", "Done!")
                : Lang.t(player, "Heute schon erhalten", "Already claimed today"));
        inventory.setItem(CHALLENGE_SLOT, card.challenge == null
                ? pending(player, Material.WRITABLE_BOOK, Lang.t(player, "Tagesaufgabe", "Daily challenge"))
                : coins(player, Material.WRITABLE_BOOK, Lang.t(player, "Tagesaufgabe", "Daily challenge"), card.challenge.base(), challengeLore.toArray(String[]::new)));

        long bonus = (parts == null ? 0 : parts.bonus()) + paidBonus(card.firstWin) + paidBonus(card.challenge);
        int percent = EthicCoins.rankBonus(player);
        inventory.setItem(RANK_SLOT, coins(player, Material.NETHER_STAR, Lang.t(player, "Rangbonus", "Rank bonus"), bonus,
                percent > 0 ? Lang.f(player, "Dein Rang: +%d %%", "Your rank: +%d%%", percent) : Lang.t(player, "Kein Rangbonus", "No rank bonus"),
                "Founder +50 %, EthicFounder +100 %"));

        long total = (parts == null ? 0 : parts.paid()) + paid(card.firstWin) + paid(card.challenge);
        List<String> totalLore = new ArrayList<>();
        if (parts != null && parts.tooShort()) totalLore.add(Lang.t(player, "Die Runde war zu kurz für EthicCoins", "The round was too short for EthicCoins"));
        if (card.firstWin == null || card.challenge == null) totalLore.add(Lang.t(player, "Wird noch gebucht …", "Still booking …"));
        ItemStack totalItem = new ItemStack(Material.EMERALD);
        totalItem.editMeta(meta -> {
            meta.displayName(text(Lang.t(player, "Gesamt: ", "Total: "), LIGHT).decoration(TextDecoration.BOLD, true)
                    .append(text("+" + Lang.f(player, "%,d", "%,d", total) + " EthicCoins", GOLD).decoration(TextDecoration.BOLD, true)));
            meta.lore(totalLore.stream().map(line -> text(line, GREY)).toList());
        });
        inventory.setItem(TOTAL_SLOT, totalItem);

        ItemStack close = new ItemStack(Material.BARRIER);
        close.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("ethicbuilds", "gui/icon_zurueck"));
            meta.displayName(text(Lang.t(player, "Schließen", "Close"), LIGHT));
        });
        inventory.setItem(CLOSE_SLOT, close);
    }

    private static long paid(Payout payout) {
        return payout == null ? 0 : payout.paid();
    }

    private static long paidBonus(Payout payout) {
        return payout == null ? 0 : payout.bonus();
    }

    private static String number(Player player, long value) {
        return Lang.f(player, "%,d", "%,d", value);
    }

    private static ItemStack stat(Material material, String label, String value, String hint) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(text(label + ": ", GREY).append(text(value, LIGHT)));
            meta.lore(List.of(text(hint, GREY)));
        });
        return item;
    }

    private static ItemStack coins(Player player, Material material, String label, long coins, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(text(label + ": ", GREY).append(text("+" + number(player, coins) + " EthicCoins", coins > 0 ? GREEN : LIGHT)));
            meta.lore(java.util.Arrays.stream(lore).map(line -> text(line, GREY)).toList());
        });
        return item;
    }

    private static ItemStack pending(Player player, Material material, String label) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(text(label + ": ", GREY).append(text("…", LIGHT)));
            meta.lore(List.of(text(Lang.t(player, "Wird gebucht …", "Booking …"), RED)));
        });
        return item;
    }

    private static Component text(String content, TextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Card)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory() && event.getSlot() == CLOSE_SLOT) {
            event.getWhoClicked().closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Card) event.setCancelled(true);
    }

    private static final class Card implements InventoryHolder {
        private final GamePlayer gamePlayer;
        private final GamePlayer mvp;
        private final int wave;
        private final RoundReward.Parts parts;
        private final boolean challengeReached;
        private Inventory inventory;
        // null while still being booked
        private Payout firstWin, challenge;

        private Card(GamePlayer gamePlayer, GamePlayer mvp, int wave, RoundReward.Parts parts, boolean challengeReached) {
            this.gamePlayer = gamePlayer;
            this.mvp = mvp;
            this.wave = wave;
            this.parts = parts;
            this.challengeReached = challengeReached;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
