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
    private static BoneModel zombie;

    private AnimatedModel model;

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

    private static BoneModel zombie() {
        if (zombie == null) {
            try {
                zombie = BoneModel.load(MonsterZombie.class.getResourceAsStream("/mobs/zombie.json"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return zombie;
    }

    // Own model from the design session for the plain zombie, elite tiers with armor plates
    private boolean modeled() {
        return getClass() == MonsterZombie.class && type == EntityType.ZOMBIE
                && Main.getINSTANCE().getConfig().getBoolean("zombie-model", true);
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
        model = new AnimatedModel(zombie(), monster, 1.0F);
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
                for (String bone : PIECES[piece]) plates.put(bone, ARMOR[material] + "/" + bone);
                points += ARMOR_POINTS[material] / (double) PIECES.length;
            }
            model.addLayer(plates);
            var armor = monster.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
            if (armor != null) armor.setBaseValue(armor.getBaseValue() + points);
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

    // Every 6 to 12 seconds head back, mouth open and the moan
    private void moanLater() {
        long delay = java.util.concurrent.ThreadLocalRandom.current().nextLong(MOAN_MIN, MOAN_MAX);
        Main.getINSTANCE().getServer().getScheduler().runTaskLater(Main.getINSTANCE(), () -> {
            if (!monster.isValid() || monster.isDead()) return;
            if (!PauseManager.isPaused()) {
                model.play("stoehnen");
                monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_AMBIENT, 1.0F, 1.0F);
            }
            moanLater();
        }, delay);
    }

    @Override
    public void onDeath() {
        if (model == null) return;
        model.die();
        monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_DEATH, 1.0F, 1.0F);
    }

    @Override
    public void onAttack() {
        if (model != null) model.play("beissen");
    }

    @Override
    public void onHit() {
        if (model == null) return;
        model.play("getroffen");
        monster.getWorld().playSound(monster.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_HURT, 1.0F, 1.0F);
    }
}
