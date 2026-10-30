package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import de.ethicbuilds.monsters.gameplay.manager.GameManager;
import de.ethicbuilds.monsters.lang.Overlay;
import de.ethicbuilds.monsters.player.manager.UserManager;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/***
 * Cocoon trap of the boss weaver from level 5 (design session): the player is spun in and stuck, the teammates
 * have to hit the cocoon open. Meanwhile it hurts a little every second, after 8 seconds it breaks by itself.
 */
public final class Cocoon implements Listener {
    private static final int HITS = 6, MAX_SECONDS = 8;
    private static final double DAMAGE = 2;
    private static final String COLOR = "#ECE8F0";
    private static final Set<UUID> trapped = new HashSet<>();

    private final Player player;
    private final LivingEntity boss;
    private final Location spot;
    private final ItemDisplay display;
    private final Interaction hitbox;
    private int hitsLeft = HITS;
    private int seconds;
    private boolean done;

    private Cocoon(Player player, LivingEntity boss) {
        this.player = player;
        this.boss = boss;
        this.spot = player.getLocation().clone();
        display = spot.getWorld().spawn(spot, ItemDisplay.class, d -> {
            d.setItemStack(model(1));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(false);
        });
        hitbox = spot.getWorld().spawn(spot, Interaction.class, box -> {
            box.setInteractionWidth(1.0F);
            box.setInteractionHeight(2.0F);
            box.setResponsive(true);
            box.setPersistent(false);
        });
    }

    public static void trap(Player player, LivingEntity boss) {
        if (!trapped.add(player.getUniqueId())) return;
        Cocoon cocoon = new Cocoon(player, boss);
        Bukkit.getPluginManager().registerEvents(cocoon, Main.getINSTANCE());
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOL_PLACE, 1.0F, 0.8F);
        Overlay.medium(player, COLOR, Overlay.line(Overlay.COCOON, COLOR, "EINGESPONNEN", "Mitspieler befreien dich"),
                Overlay.line(Overlay.COCOON, COLOR, "WEBBED", "teammates free you"));
        cocoon.tick();
    }

    private static ItemStack model(int stage) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("monsters", "kokon/" + stage)));
        return item;
    }

    private void tick() {
        new BukkitRunnable() {
            @Override
            public void run() {
                UserManager users = Main.getINSTANCE().getInjector().getInstance(UserManager.class);
                boolean round = Main.getINSTANCE().getInjector().getInstance(GameManager.class).getCurrentPhase() == GamePhase.WAVE;
                if (done || !player.isOnline() || users.isPlayerDead(player.getUniqueId()) || !round || seconds >= MAX_SECONDS) {
                    free(!done && player.isOnline());
                    cancel();
                    return;
                }
                seconds++;
                player.damage(DAMAGE, boss.isValid() ? boss : null);
                String name = "<color:" + COLOR + "><b>" + player.getName() + "</b></color> ";
                for (Player other : spot.getWorld().getPlayers()) {
                    if (other.equals(player) || other.getLocation().distanceSquared(spot) > 40 * 40) continue;
                    Overlay.small(other, Overlay.icon(Overlay.COCOON, COLOR, name + "<color:" + Overlay.GREY + ">ist eingesponnen – schlag den Kokon!</color>"),
                            Overlay.icon(Overlay.COCOON, COLOR, name + "<color:" + Overlay.GREY + ">is webbed – hit the cocoon!</color>"));
                }
            }
        }.runTaskTimer(Main.getINSTANCE(), 20L, 20L);
    }

    private void hit(Player by) {
        if (done || by.equals(player)) return;
        hitsLeft--;
        spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 1, 0), 8, 0.3, 0.6, 0.3, Material.COBWEB.createBlockData());
        spot.getWorld().playSound(spot, Sound.BLOCK_WOOL_BREAK, 0.8F, 1.2F);
        if (hitsLeft <= 0) {
            done = true;
            free(true);
            return;
        }
        display.setItemStack(model(hitsLeft <= HITS / 3 ? 3 : hitsLeft <= HITS * 2 / 3 ? 2 : 1));
    }

    private void free(boolean burst) {
        done = true;
        trapped.remove(player.getUniqueId());
        HandlerList.unregisterAll(this);
        display.remove();
        hitbox.remove();
        if (!burst) return;
        spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, Material.WHITE_WOOL.createBlockData());
        spot.getWorld().playSound(spot, Sound.BLOCK_WOOL_BREAK, 1.0F, 0.8F);
        spot.getWorld().playSound(spot, Sound.ENTITY_SPIDER_DEATH, 0.4F, 1.6F);
    }

    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!event.getAttacked().equals(hitbox)) return;
        event.setCancelled(true);
        hit(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!event.getRightClicked().equals(hitbox)) return;
        event.setCancelled(true);
        hit(event.getPlayer());
    }

    // Stuck in the cocoon, looking around still works
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.getPlayer().equals(player) || !event.hasChangedPosition()) return;
        Location to = spot.clone();
        to.setYaw(event.getTo().getYaw());
        to.setPitch(event.getTo().getPitch());
        event.setTo(to);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) free(false);
    }
}
