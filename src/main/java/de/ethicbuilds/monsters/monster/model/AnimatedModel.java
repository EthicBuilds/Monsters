package de.ethicbuilds.monsters.monster.model;

import de.ethicbuilds.monsters.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/***
 * Blockbench model on an invisible monster: one ItemDisplay per bone rides on the monster, the bones are posed
 * every 2 ticks (rest pose + animation, children inside their parents, like Blockbench)
 */
public class AnimatedModel {
    private static final int TICKS = 2;
    private static final String IDLE = "stehen", WALK = "laufen", DEATH = "tod";

    private final BoneModel model;
    private final LivingEntity base;
    private final float size;
    private final ItemDisplay[] parts;
    private final int[] parents;
    private final BukkitTask task;

    private float time;
    private String action;
    private float actionTime;
    private boolean dying;
    private float yaw;
    private float offsetY = Float.NaN;
    private Location lastPosition;

    /**
     * @param size model size, 1 = as designed in Blockbench
     */
    public AnimatedModel(BoneModel model, LivingEntity base, float size) {
        this.model = model;
        this.base = base;
        this.size = size;
        List<BoneModel.Bone> bones = model.bones();
        parts = new ItemDisplay[bones.size()];
        parents = new int[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            parents[i] = indexOf(bones.get(i).parent());
            if (!bones.get(i).hasModel()) continue;
            parts[i] = spawnPart(bones.get(i));
            base.addPassenger(parts[i]);
        }
        yaw = base.getBodyYaw();
        lastPosition = base.getLocation();
        task = base.getServer().getScheduler().runTaskTimer(Main.getINSTANCE(), this::tick, 1L, TICKS);
    }

    /** One-shot animation, e.g. the bite */
    public void play(String animation) {
        if (dying || model.animation(animation) == null) return;
        action = animation;
        actionTime = 0;
    }

    /** Death animation where the monster died, then the model disappears */
    public void die() {
        if (dying) return;
        dying = true;
        action = DEATH;
        actionTime = 0;
        // The death animation lies on the ground, also when the monster died in the air or while climbing
        Location at = ground(base.getLocation());
        for (ItemDisplay part : parts) {
            if (part == null) continue;
            part.leaveVehicle();
            Location spot = at.clone();
            spot.setYaw(0);
            spot.setPitch(0);
            part.teleport(spot);
        }
    }

    private static Location ground(Location at) {
        Location ground = at.clone();
        for (int i = 0; i < 8 && !ground.clone().subtract(0, 0.05, 0).getBlock().isSolid(); i++) {
            ground.setY(Math.floor(ground.getY() - 0.05));
        }
        return ground;
    }

    public void remove() {
        task.cancel();
        for (ItemDisplay part : parts) {
            if (part != null) part.remove();
        }
    }

    private void tick() {
        if (!dying && !base.isValid()) die();

        float step = TICKS / 20f;
        time += step;
        if (action != null) actionTime += step;

        if (Float.isNaN(offsetY)) {
            for (ItemDisplay part : parts) {
                if (part != null && part.isInsideVehicle()) {
                    offsetY = (float) (part.getLocation().getY() - base.getLocation().getY());
                    break;
                }
            }
        }

        BoneModel.Animation one = action != null ? model.animation(action) : null;
        if (one != null && actionTime > one.length()) {
            if (dying) {
                remove();
                return;
            }
            action = null;
            one = null;
        }

        if (!dying) {
            yaw = base.getBodyYaw();
            Location now = base.getLocation();
            boolean moving = now.getWorld().equals(lastPosition.getWorld()) && now.distanceSquared(lastPosition) > 0.0004;
            lastPosition = now;
            render(model.animation(moving ? WALK : IDLE), one);
        } else {
            render(null, one);
        }
    }

    private void render(BoneModel.Animation loop, BoneModel.Animation one) {
        List<BoneModel.Bone> bones = model.bones();
        Matrix4f[] world = new Matrix4f[bones.size()];
        // Passengers sit higher than the feet of the monster, the model stands on the ground; the front is -Z
        float lift = dying || Float.isNaN(offsetY) ? 0 : offsetY;
        Matrix4f root = new Matrix4f().translate(0, -lift, 0).rotateY((float) Math.toRadians(180 - yaw)).scale(size / 16f);
        Vector3f rotation = new Vector3f(), position = new Vector3f(), scale = new Vector3f();
        Vector3f value = new Vector3f();
        float loopTime = loop != null && loop.length() > 0 ? time % loop.length() : 0;

        for (int i = 0; i < bones.size(); i++) {
            BoneModel.Bone bone = bones.get(i);
            rotation.set(0);
            position.set(0);
            scale.set(1);
            // The one-shot animation wins for every bone it moves
            for (String channel : new String[]{"rotation", "position", "scale"}) {
                Vector3f out = channel.equals("rotation") ? rotation : channel.equals("position") ? position : scale;
                if (one != null && one.sample(bone.id(), channel, actionTime, value)) out.set(value);
                else if (loop != null && loop.sample(bone.id(), channel, loopTime, value)) out.set(value);
            }
            rotation.add(bone.rest());
            int parent = parents[i];
            Vector3f origin = parent < 0 ? new Vector3f() : bones.get(parent).pivot();
            world[i] = new Matrix4f(parent < 0 ? root : world[parent])
                    .translate(bone.pivot().x - origin.x + position.x, bone.pivot().y - origin.y + position.y, bone.pivot().z - origin.z + position.z)
                    .rotateZ((float) Math.toRadians(rotation.z))
                    .rotateY((float) Math.toRadians(rotation.y))
                    .rotateX((float) Math.toRadians(rotation.x))
                    .scale(Math.max(0.001f, scale.x), Math.max(0.001f, scale.y), Math.max(0.001f, scale.z));

            ItemDisplay part = parts[i];
            if (part == null || !part.isValid()) continue;
            // Item models measure in pixels with the pivot in the middle; displays turn their item by 180 degrees
            part.setInterpolationDelay(0);
            part.setTransformationMatrix(new Matrix4f(world[i]).scale(16 * model.modelScale()).rotateY((float) Math.PI));
        }
    }

    private ItemDisplay spawnPart(BoneModel.Bone bone) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("monsters", model.id() + "/" + bone.id())));
        Location at = base.getLocation();
        at.setYaw(0);
        at.setPitch(0);
        return base.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setPersistent(false);
            display.setInterpolationDuration(TICKS);
            // Otherwise a part disappears as soon as its centre is out of view
            display.setDisplayWidth(8 * size);
            display.setDisplayHeight(4 * size);
        });
    }

    private int indexOf(String id) {
        if (id == null) return -1;
        List<BoneModel.Bone> bones = model.bones();
        for (int i = 0; i < bones.size(); i++) if (bones.get(i).id().equals(id)) return i;
        return -1;
    }
}
