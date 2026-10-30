package de.ethicbuilds.monsters.monster;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.manager.PauseManager;
import de.ethicbuilds.monsters.monster.model.AnimatedModel;
import de.ethicbuilds.monsters.monster.model.BoneModel;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

import java.io.IOException;

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
    private static final org.bukkit.Material[] SHARDS = {null, org.bukkit.Material.COPPER_INGOT, org.bukkit.Material.IRON_INGOT,
            org.bukkit.Material.GOLD_INGOT, org.bukkit.Material.DIAMOND, org.bukkit.Material.NETHERITE_INGOT};
    // Limb loss (design session): under half health a small chance per hit, once per zombie; from gold on never
    private static final double LIMB_CHANCE = 0.04, LIMB_HEALTH = 0.5, ARM_DAMAGE = 0.8, CRAWL_SPEED = 0.45, CRAWL_SCALE = 0.35;
    private static final int LIMB_SAFE_TIER = 3, LIMB_LIFETIME = 60;
    // Recoil in degrees: body and head, a headshot throws the head back harder
    private static final float RECOIL_BODY = 12, RECOIL_HEAD = 8, HEADSHOT_BODY = 6, HEADSHOT_HEAD = 28;
    // Loaded models by id, empty when the plugin does not have one (yet)
    private static final java.util.Map<String, java.util.Optional<BoneModel>> MODELS = new java.util.HashMap<>();

    private AnimatedModel model;
    private boolean limbLost, crawling;
    private final java.util.Map<String, Integer> plateTier = new java.util.HashMap<>();
    private final java.util.LinkedList<String[]> plates = new java.util.LinkedList<>();
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

    private static BoneModel bones(String id) {
        return MODELS.computeIfAbsent(id, key -> {
            var in = MonsterZombie.class.getResourceAsStream("/mobs/" + key + ".json");
            if (in == null) return java.util.Optional.empty();
            try {
                return java.util.Optional.of(BoneModel.load(in));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).orElse(null);
    }

    /** Model of the design session (/mobs/<id>.json, zombie bones), null = vanilla; other kinds override it */
    protected String modelId() {
        return getClass() == MonsterZombie.class && type == EntityType.ZOMBIE ? "zombie" : null;
    }

    // Own model from the design session, elite tiers with armor plates
    private boolean modeled() {
        return modelId() != null && bones(modelId()) != null
                && Main.getINSTANCE().getConfig().getBoolean("zombie-model", true);
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
        model = new AnimatedModel(bones(modelId()), monster, 1.0F);
        int tier = Math.min(getTier(), ARMOR.length - 1);
        if (tier > 0) {
            var random = java.util.concurrent.ThreadLocalRandom.current();
            java.util.Map<String, String> plates = new java.util.HashMap<>();
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
            var armor = monster.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
            if (armor != null) armor.setBaseValue(armor.getBaseValue() + points);
            for (String[] group : SPLINTER) {
                String[] worn = java.util.Arrays.stream(group).filter(plateTier::containsKey).toArray(String[]::new);
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
        var maxHealth = monster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (plates.isEmpty() || maxHealth == null || !monster.isValid() || monster.isDead()) return;
        double left = monster.getHealth() / maxHealth.getValue();
        while (!plates.isEmpty() && left < 1 - (plateGroups - plates.size() + 1) / (double) (plateGroups + 1)) {
            for (String bone : plates.removeFirst()) {
                org.bukkit.Location at = model.removeLayer(bone);
                if (at == null) continue;
                at.getWorld().spawnParticle(org.bukkit.Particle.ITEM, at, 10, 0.15, 0.15, 0.15, 0.08,
                        new org.bukkit.inventory.ItemStack(SHARDS[plateTier.get(bone)]));
                de.ethicbuilds.monsters.sound.Sounds.splinter(at);
            }
            var armor = monster.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
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

    private void maybeLoseLimb() {
        var maxHealth = monster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (limbLost || getTier() >= LIMB_SAFE_TIER || maxHealth == null || monster.getHealth() > maxHealth.getValue() * LIMB_HEALTH) return;
        var random = java.util.concurrent.ThreadLocalRandom.current();
        if (random.nextDouble() >= LIMB_CHANCE) return;
        limbLost = true;
        String[] limbs = {"arm_r", "arm_l", "bein_r", "bein_l"};
        String limb = limbs[random.nextInt(limbs.length)];
        org.bukkit.Location at = model.boneLocation(limb);
        model.hideBone(limb);
        if (at != null) dropLimb(limb, at);
        if (limb.startsWith("arm")) {
            var attack = monster.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
            if (attack != null) attack.setBaseValue(attack.getBaseValue() * ARM_DAMAGE);
            return;
        }
        // Without a leg it crawls on: slow and low
        crawling = true;
        model.setIdle("kriechen");
        var movement = monster.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(movement.getBaseValue() * CRAWL_SPEED);
        var scale = monster.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        if (scale != null) scale.setBaseValue(CRAWL_SCALE);
        model.measureAgain();
    }

    // The limb falls off, rolls a bit and fades after 3 seconds
    private void dropLimb(String limb, org.bukkit.Location at) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER);
        item.editMeta(meta -> meta.setItemModel(new org.bukkit.NamespacedKey("monsters", modelId() + "/" + limb)));
        org.bukkit.entity.ItemDisplay display = at.getWorld().spawn(at, org.bukkit.entity.ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(bones(modelId()).modelScale()), new org.joml.AxisAngle4f()));
        });
        at.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, at, 16, 0.2, 0.2, 0.2, org.bukkit.Material.SLIME_BLOCK.createBlockData());
        at.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, at, 10, 0.2, 0.2, 0.2, org.bukkit.Material.PURPLE_WOOL.createBlockData());
        at.getWorld().playSound(at, org.bukkit.Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5F, 1.6F);
        boolean[] sinking = {false};
        de.ethicbuilds.monsters.monster.model.Corpses.add(new de.ethicbuilds.monsters.monster.model.Corpses.Corpse() {
            @Override
            public void sink() {
                sinking[0] = true;
            }

            @Override
            public boolean isGone() {
                return !display.isValid();
            }
        });
        var random = java.util.concurrent.ThreadLocalRandom.current();
        org.bukkit.util.Vector push = new org.bukkit.util.Vector(random.nextDouble(-0.08, 0.08), 0.12, random.nextDouble(-0.08, 0.08));
        new org.bukkit.scheduler.BukkitRunnable() {
            int tick = 0;
            final org.bukkit.Location spot = at.clone();
            final org.bukkit.util.Vector velocity = push.clone();

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
                org.bukkit.Location next = spot.clone().add(velocity);
                if (next.getBlock().isPassable()) spot.add(velocity);
                velocity.setY(Math.max(-0.5, velocity.getY() - 0.08));
                if (!next.getBlock().isPassable()) velocity.multiply(new org.bukkit.util.Vector(0.5, 0, 0.5));
                spot.setYaw(spot.getYaw() + 25);
                display.teleport(spot);
            }
        }.runTaskTimer(Main.getINSTANCE(), 2L, 2L);
    }

    // Every 6 to 12 seconds head back, mouth open and the moan
    private void moanLater() {
        long delay = java.util.concurrent.ThreadLocalRandom.current().nextLong(MOAN_MIN, MOAN_MAX);
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (!monster.isValid() || monster.isDead()) return;
            if (!PauseManager.isPaused()) {
                if (!crawling) model.play("stoehnen");
                monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_AMBIENT, 1.0F, 1.0F);
            }
            moanLater();
        }, delay);
    }

    @Override
    public void onDeath() {
        if (model == null) return;
        // Falls away from the killer, crawling it just collapses
        org.bukkit.entity.Player killer = monster.getKiller();
        model.die(killer != null && !crawling ? killer.getLocation() : null);
        monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_DEATH, 1.0F, 1.0F);
    }

    @Override
    public void onAttack() {
        // Crawling it bites from the ground
        if (model != null && !crawling) model.play("beissen");
    }

    @Override
    public void onHit(org.bukkit.entity.Player by) {
        if (model == null) return;
        boolean head = de.ethicbuilds.monsters.weapons.ShotDamage.isHeadshot();
        if (!crawling) model.recoil(by.getLocation(), head ? HEADSHOT_BODY : RECOIL_BODY, head ? HEADSHOT_HEAD : RECOIL_HEAD);
        maybeLoseLimb();
        // The damage is dealt after this event
        Main.getINSTANCE().getServer().getScheduler().runTask(Main.getINSTANCE(), this::splinter);
        monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_HURT, 1.0F, 1.0F);
    }
}
