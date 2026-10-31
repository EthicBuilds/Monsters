package de.ethicbuilds.monsters.reward;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.lang.Lang;
import de.ethicbuilds.monsters.player.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/***
 * Today's challenge from the GameManager: shown when joining, checked at the end of the round.
 * Whoever makes it gets EthicCoins once per day.
 */
public final class DailyChallenge {
    private static final String API = "http://localhost:8080/gameManager/api/statistic/challenge";
    private static final Gson GSON = new Gson();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static volatile JsonObject today;

    private DailyChallenge() {}

    public static void load() {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getINSTANCE(), () -> {
            try {
                HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(URI.create(API)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) today = GSON.fromJson(response.body(), JsonObject.class);
            } catch (Exception e) {
                Main.getINSTANCE().getLogger().warning("Daily challenge not loaded: " + e.getMessage());
            }
        });
    }

    public static void announce(Player player) {
        JsonObject challenge = today;
        // A waiting server can stand overnight, then the next day's challenge is fetched
        if (challenge == null || !challenge.get("date").getAsString().equals(LocalDate.now().toString())) load();
        if (challenge == null) return;
        player.sendMessage(Lang.f(player, "%s§eTagesaufgabe: §f%s §8· §6%d EthicCoins", "%s§eDaily challenge: §f%s §8· §6%d EthicCoins",
                Main.getINSTANCE().getMonstersPrefix(), text(player, challenge), challenge.get("reward").getAsInt()));
    }

    /** Today's challenge in the player's language, null while not loaded */
    public static String describe(Player player) {
        JsonObject challenge = today;
        return challenge == null ? null : text(player, challenge);
    }

    // The GameManager only sends the German text, the English one is built from type and goal
    private static String text(Player player, JsonObject challenge) {
        String german = challenge.get("text").getAsString();
        if (!Lang.english(player)) return german;
        int goal = challenge.get("goal").getAsInt();
        return switch (challenge.get("type").getAsString()) {
            case "WAVE" -> "Reach wave " + goal;
            case "HEADSHOTS" -> goal + " headshots in one round";
            case "REVIVES" -> "Revive " + goal + " teammates";
            case "KILLS" -> goal + " kills in one round";
            default -> german;
        };
    }

    /** At the end of the round: everyone who made it today gets the reward once. Returns the players who made it */
    public static Map<UUID, CompletableFuture<Payout>> check(List<GamePlayer> players, int wave) {
        Map<UUID, CompletableFuture<Payout>> results = new HashMap<>();
        JsonObject challenge = today;
        if (challenge == null) return results;
        String type = challenge.get("type").getAsString();
        int goal = challenge.get("goal").getAsInt();
        int reward = challenge.get("reward").getAsInt();
        for (GamePlayer gamePlayer : players) {
            int value = switch (type) {
                case "WAVE" -> wave;
                case "HEADSHOTS" -> gamePlayer.getHeadshots();
                case "REVIVES" -> gamePlayer.getRevives();
                case "KILLS" -> gamePlayer.getKilledZombies();
                default -> 0;
            };
            if (value < goal) continue;
            Player player = gamePlayer.getPlayer();
            CompletableFuture<Payout> result = new CompletableFuture<>();
            results.put(player.getUniqueId(), result);
            Bukkit.getScheduler().runTaskAsynchronously(Main.getINSTANCE(), () -> {
                if (!claim(player)) {
                    result.complete(Payout.NONE);
                    return;
                }
                Bukkit.getScheduler().runTask(Main.getINSTANCE(), () -> {
                    long coins = EthicCoins.withRankBonus(player, reward);
                    if (!player.isOnline() || !EthicCoins.deposit(player.getUniqueId(), coins, "Monsters Tagesaufgabe")) {
                        result.complete(Payout.NONE);
                        return;
                    }
                    player.sendMessage(Lang.f(player, "%s§aTagesaufgabe geschafft! §6+%d EthicCoins%s", "%s§aDaily challenge done! §6+%d EthicCoins%s", Main.getINSTANCE().getMonstersPrefix(), coins,
                            EthicCoins.bonusNote(player, reward, coins)));
                    result.complete(new Payout(reward, coins));
                });
            });
        }
        return results;
    }

    private static boolean claim(Player player) {
        try {
            String body = "{\"uuid\":\"" + player.getUniqueId() + "\"}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/claim")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && GSON.fromJson(response.body(), JsonObject.class).get("ok").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
}
