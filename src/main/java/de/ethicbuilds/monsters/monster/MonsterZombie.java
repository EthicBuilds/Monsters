package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.monster.model.AnimatedModel;
import de.ethicbuilds.monsters.monster.model.BoneModel;
import de.ethicbuilds.monsters.monster.model.Corpses;
import de.ethicbuilds.monsters.sound.Sounds;
import de.ethicbuilds.monsters.weapons.ShotDamage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class MonsterZombie extends EnemyMonster {
    // Crawling out of the ground takes 1.2 seconds, meanwhile it does not walk yet
    private static final long SPAWN_TICKS = 24;
    private static final long MOAN_MIN = 6 * 20, MOAN_MAX = 12 * 20;
    // Elite tiers wear their armor as plates on the model, the protection of the old helmet stays
    private static final String[] ARMOR = {null, "zombie_bronze", "zombie_silber", "zombie_gold", "zombie_diamant", "zombie_netherit"};
    private static final double[] ARMOR_POINTS = {0, 1, 2, 2, 3, 3};
    // Random pieces, each with the tier of the wave or one lower: helmet, chest with sleeves, trousers
    private static final String[][] PIECES = {{"kopf"}, {"koerper", "arm_r", "arm_l"}, {"bein_r", "bein_l"}};
    private static final double PIECE_CHANCE = 0.65;
    // Plates break off in this order while the health goes down, with shards of their material (design session)
    private static final String[][] SPLINTER = {{"kopf"}, {"arm_r", "arm_l"}, {"bein_r", "bein_l"}, {"koerper"}};
    private static final Material[] SHARDS = {null, Material.COPPER_INGOT, Material.IRON_INGOT,
            Material.GOLD_INGOT, Material.DIAMOND, Material.NETHERITE_INGOT};
    // Limb loss (design session): under half health a small chance per hit, once per zombie; from gold on never
    private static final double LIMB_CHANCE = 0.04, LIMB_HEALTH = 0.5, ARM_DAMAGE = 0.8, CRAWL_SPEED = 0.45, CRAWL_SCALE = 0.35;
    private static final int LIMB_SAFE_TIER = 3, LIMB_LIFETIME = 60;
    // Recoil in degrees: body and head, a headshot throws the head back harder
    private static final float RECOIL_BODY = 12, RECOIL_HEAD = 8, HEADSHOT_BODY = 6, HEADSHOT_HEAD = 28;
    // Model per kind (design session), a kind without its model in the plugin stays the vanilla mob
    private static final Map<EntityType, String> KIND_MODELS = Map.of(EntityType.ZOMBIE, "zombie",
            EntityType.HUSK, "wuestenzombie", EntityType.PARCHED, "vertrockneter", EntityType.ZOMBIE_VILLAGER, "dorfzombie",
            EntityType.VINDICATOR, "diener");
    private static final Map<String, Optional<BoneModel>> loaded = new HashMap<>();
    // Now and then a plain zombie carries a torch that really lights up its surroundings
    private static final double TORCH_CHANCE = 0.1;
    private static final String TORCH_MODEL = "zombie_fackel";

    private AnimatedModel model;
    private BoneModel bones;
    private Voice voice;
    private boolean limbLost, crawling;
    private final Map<String, Integer> plateTier = new HashMap<>();
    private final LinkedList<String[]> plates = new LinkedList<>();
    private int plateGroups;
    private double platePoints;

    public MonsterZombie() {
        this(EntityType.ZOMBIE, "§7Zombie");
    }

    /** Same zombie values for the other kinds of the later waves (husk, parched, zombie villager, vindicator) */
    public MonsterZombie(EntityType type, String name) {
        this.health = 20;
        this.damage = 2;
        this.speed = 1;
        this.name = name;
        this.type = type;
        this.coin = 20;
    }

    static Optional<BoneModel> load(String id) {
        return loaded.computeIfAbsent(id, key -> {
            var in = MonsterZombie.class.getResourceAsStream("/mobs/" + key + ".json");
            if (in == null) return Optional.empty();
            try {
                return Optional.of(BoneModel.load(in));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Model of the design session (/mobs/<id>.json, zombie bones), null = vanilla; other kinds override it */
    protected String modelId() {
        return getClass() == MonsterZombie.class ? KIND_MODELS.get(type) : null;
    }

    /** Drawn bigger than designed, e.g. the mini bosses */
    protected float modelSize() {
        return 1.0F;
    }

    protected Voice voice() {
        return Voice.of(type);
    }

    // Own model from the design session, elite tiers with armor plates (those only fit the zombie shape)
    private boolean modeled() {
        String id = modelId();
        if (id == null || !Main.getINSTANCE().getConfig().getBoolean("zombie-model", true)) return false;
        bones = load(id).orElse(null);
        if (bones != null && type == EntityType.ZOMBIE && getClass() == MonsterZombie.class
                && ThreadLocalRandom.current().nextDouble() < TORCH_CHANCE) {
            bones = load(TORCH_MODEL).orElse(bones);
        }
        return bones != null;
    }

    /** The model, null while the kind spawns as a vanilla zombie */
    protected AnimatedModel model() {
        return model;
    }

    @Override
    protected void onSpawn() {
        if (!modeled()) return;
        monster.setInvisible(true);
        // Vanilla armor would float around the model, also what it picks up or gets a tick later
        monster.setCanPickupItems(false);
        var equipment = monster.getEquipment();
        if (equipment != null) equipment.clear();
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (monster.isValid() && monster.getEquipment() != null) monster.getEquipment().clear();
        }, 1L);
        model = new AnimatedModel(bones, monster, modelSize());
        // Front in walking direction: the body yaw of the mob lags behind and points sideways in a crowd
        model.enableSteering(false);
        voice = voice();
        if (bones.id().equals(TORCH_MODEL)) TorchLight.follow(monster);
        int tier = bones.id().startsWith("zombie") ? Math.min(getTier(), ARMOR.length - 1) : 0;
        if (tier > 0) {
            var random = ThreadLocalRandom.current();
            Map<String, String> plates = new HashMap<>();
            double points = 0;
            int first = random.nextInt(PIECES.length);
            for (int piece = 0; piece < PIECES.length; piece++) {
                // At least one piece, so the tier can be seen
                if (piece != first && random.nextDouble() > PIECE_CHANCE) continue;
                int material = Math.max(1, tier - random.nextInt(2));
                for (String bone : PIECES[piece]) {
                    plates.put(bone, ARMOR[material] + "/" + bone);
                    plateTier.put(bone, material);
                }
                points += ARMOR_POINTS[material] / (double) PIECES.length;
            }
            model.addLayer(plates);
            var armor = monster.getAttribute(Attribute.ARMOR);
            if (armor != null) armor.setBaseValue(armor.getBaseValue() + points);
            for (String[] group : SPLINTER) {
                String[] worn = Arrays.stream(group).filter(plateTier::containsKey).toArray(String[]::new);
                if (worn.length > 0) this.plates.add(worn);
            }
            plateGroups = this.plates.size();
            platePoints = points / Math.max(1, plateGroups);
        }
        if (isSprinter()) model.setWalk("rennen");
        model.play("spawn");
        // Own sounds, so the moan comes together with the open mouth
        monster.setSilent(true);
        moanLater();
        if (monster instanceof Mob mob) {
            mob.setAware(false);
            Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
                if (mob.isValid() && !PauseManager.isPaused()) mob.setAware(true);
            }, SPAWN_TICKS);
        }
    }

    // With n groups the k-th breaks once the health is below 1 - k/(n+1)
    private void splinter() {
        var maxHealth = monster.getAttribute(Attribute.MAX_HEALTH);
        if (plates.isEmpty() || maxHealth == null || !monster.isValid() || monster.isDead()) return;
        double left = monster.getHealth() / maxHealth.getValue();
        while (!plates.isEmpty() && left < 1 - (plateGroups - plates.size() + 1) / (double) (plateGroups + 1)) {
            for (String bone : plates.removeFirst()) {
                Location at = model.removeLayer(bone);
                if (at == null) continue;
                at.getWorld().spawnParticle(Particle.ITEM, at, 10, 0.15, 0.15, 0.15, 0.08,
                        new ItemStack(SHARDS[plateTier.get(bone)]));
                Sounds.splinter(at);
            }
            var armor = monster.getAttribute(Attribute.ARMOR);
            if (armor != null) armor.setBaseValue(Math.max(0, armor.getBaseValue() - platePoints));
        }
    }

    @Override
    public boolean armored() {
        return !plates.isEmpty();
    }

    @Override
    protected void onNoise(boolean on) {
        if (model != null) model.stroll(on && !crawling);
    }

    /** Bosses keep their limbs */
    protected boolean losesLimbs() {
        return true;
    }

    private void maybeLoseLimb() {
        var maxHealth = monster.getAttribute(Attribute.MAX_HEALTH);
        if (limbLost || !losesLimbs() || getTier() >= LIMB_SAFE_TIER || maxHealth == null || monster.getHealth() > maxHealth.getValue() * LIMB_HEALTH) return;
        var random = ThreadLocalRandom.current();
        if (random.nextDouble() >= LIMB_CHANCE) return;
        limbLost = true;
        String[] limbs = {"arm_r", "arm_l", "bein_r", "bein_l"};
        String limb = limbs[random.nextInt(limbs.length)];
        Location at = model.boneLocation(limb);
        model.hideBone(limb);
        if (at != null) dropLimb(limb, at);
        if (limb.startsWith("arm")) {
            var attack = monster.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attack != null) attack.setBaseValue(attack.getBaseValue() * ARM_DAMAGE);
            return;
        }
        // Without a leg it crawls on: slow and low
        crawling = true;
        model.setIdle("kriechen");
        var movement = monster.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(movement.getBaseValue() * CRAWL_SPEED);
        var scale = monster.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(CRAWL_SCALE);
        model.measureAgain();
    }

    // The limb falls off, rolls a bit and fades after 3 seconds
    private void dropLimb(String limb, Location at) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("monsters", bones.id() + "/" + limb)));
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(bones.modelScale()), new AxisAngle4f()));
        });
        at.getWorld().spawnParticle(Particle.BLOCK, at, 16, 0.2, 0.2, 0.2, Material.SLIME_BLOCK.createBlockData());
        at.getWorld().spawnParticle(Particle.BLOCK, at, 10, 0.2, 0.2, 0.2, Material.PURPLE_WOOL.createBlockData());
        at.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5F, 1.6F);
        boolean[] sinking = {false};
        Corpses.add(new Corpses.Corpse() {
            @Override
            public void sink() {
                sinking[0] = true;
            }

            @Override
            public boolean isGone() {
                return !display.isValid();
            }
        });
        var random = ThreadLocalRandom.current();
        Vector push = new Vector(random.nextDouble(-0.08, 0.08), 0.12, random.nextDouble(-0.08, 0.08));
        new BukkitRunnable() {
            int tick = 0;
            final Location spot = at.clone();
            final Vector velocity = push.clone();

            @Override
            public void run() {
                tick += 2;
                if (tick > LIMB_LIFETIME || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }
                if (sinking[0]) {
                    spot.subtract(0, 0.1, 0);
                    display.teleport(spot);
                    if (spot.getY() < at.getY() - 1.2) display.remove();
                    return;
                }
                Location next = spot.clone().add(velocity);
                if (next.getBlock().isPassable()) spot.add(velocity);
                velocity.setY(Math.max(-0.5, velocity.getY() - 0.08));
                if (!next.getBlock().isPassable()) velocity.multiply(new Vector(0.5, 0, 0.5));
                spot.setYaw(spot.getYaw() + 25);
                display.teleport(spot);
            }
        }.runTaskTimer(Main.getINSTANCE(), 2L, 2L);
    }

    // Every 6 to 12 seconds head back, mouth open and the moan
    private void moanLater() {
        long delay = ThreadLocalRandom.current().nextLong(MOAN_MIN, MOAN_MAX);
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (!monster.isValid() || monster.isDead()) return;
            if (!PauseManager.isPaused()) {
                if (!crawling) model.play("stoehnen");
                voice.moan(monster.getLocation());
            }
            moanLater();
        }, delay);
    }

    @Override
    public void onDeath() {
        if (model == null) return;
        // Falls away from the killer, crawling it just collapses
        Player killer = monster.getKiller();
        model.die(killer != null && !crawling ? killer.getLocation() : null);
        voice.death(monster.getLocation());
    }

    @Override
    public void onAttack() {
        // Crawling it bites from the ground
        if (model != null && !crawling) model.play("beissen");
    }

    @Override
    public void onHit(Player by) {
        if (model == null) return;
        boolean head = ShotDamage.isHeadshot();
        if (!crawling) model.recoil(by.getLocation(), head ? HEADSHOT_BODY : RECOIL_BODY, head ? HEADSHOT_HEAD : RECOIL_HEAD);
        maybeLoseLimb();
        // The damage is dealt after this event
        Main.getINSTANCE().getServer().getScheduler().runTask(Main.getINSTANCE(), this::splinter);
        voice.hurt(monster.getLocation());
    }
}
