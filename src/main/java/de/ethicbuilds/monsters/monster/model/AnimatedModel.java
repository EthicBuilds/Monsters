package de.ethicbuilds.monsters.monster.model;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.gameplay.LagGuard;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/***
 * Blockbench model on an invisible monster: one ItemDisplay per bone rides on the monster, the bones are posed
 * every 2 ticks (rest pose + animation, children inside their parents, like Blockbench), far from the players less often
 */
public class AnimatedModel implements Corpses.Corpse {
    private static final int TICKS = 2;
    // Near every time, up to 24 blocks every 2nd, beyond every 3rd as one display in rest pose (design session)
    private static final double NEAR_DISTANCE = 12, DETAIL_DISTANCE = 24, LOD_BACK = 20;
    private static final int MID_EVERY = 2, FAR_EVERY = 3;
    private static final float SINK_PER_TICK = 0.05F, SINK_DEPTH = 1.6F;
    // Hit recoil on top of every animation: body and head pushed away from the hit, quickly back
    private static final float RECOIL_RISE = 0.05F, RECOIL_BACK = 0.3F;
    private static final String BODY = "koerper", HEAD = "kopf";
    // Killed it turns to the killer and falls backwards, lies a moment and sinks into the ground
    private static final String DEATH_BACK = "tod_hinten";
    private static final float LIE_SECONDS = 1.5F, DEATH_TURN = 45;
    private static final String IDLE = "stehen", WALK = "laufen", DEATH = "tod";
    // Walk animation, quicker monsters run instead
    private String walk = WALK;
    // The walk plays faster or slower with the real speed, so the steps fit (blocks per tick of a normal walk)
    private static final double WALK_SPEED = 0.12, MIN_RATE = 0.6, MAX_RATE = 1.6;
    // Slower than this it strolls instead of walking, if the model has it
    private static final double STROLL_SPEED = 0.05;
    private static final String STROLL = "schlendern";
    private String idle = IDLE;
    private boolean noStroll;
    private Matrix4f[] lastWorld;
    // Reused every frame: bone matrices in model space and the pose values of the current bone
    private final Matrix4f[] world;
    private final Matrix4f root = new Matrix4f();
    private final Vector3f rotation = new Vector3f(), position = new Vector3f(), scale = new Vector3f();
    private static final Vector3f NO_PIVOT = new Vector3f();

    private final BoneModel model;
    private final LivingEntity base;
    private final float size;
    private final ItemDisplay[] parts;
    // Second display per bone with the same pose, e.g. armor plates of the design session
    private ItemDisplay[] layer;
    // Far away only this one display shows, the bones keep their items here meanwhile
    private ItemDisplay lodPart;
    private boolean far;
    private ItemStack[] hidden;
    private boolean stroll;
    private boolean sinking, removed;
    private float sunk;
    private float recoilTime = Float.NaN, deathYaw = Float.NaN, lying;
    private final Vector3f bodyRecoil = new Vector3f(), headRecoil = new Vector3f();
    // Legs on the real ground (spiders), only near players; the model turns itself instead of following the body yaw
    private LegRig rig;
    private boolean rigActive;
    private boolean steering;
    private org.bukkit.entity.Entity faceTarget;
    // Turning speed of a steered model, degrees per tick
    private static final float TURN_PER_TICK = 12;
    private final int[] parents;
    private final BukkitTask task;

    private float time, walkTime;
    private String action;
    private float actionTime;
    private boolean dying;
    private float yaw;
    private float offsetY = Float.NaN;
    private Location lastPosition;
    private int skipped;
    private int interpolation = TICKS;
    // Bones drawn bigger than designed, e.g. the crystals of a stronger boss
    private final Map<Integer, Float> extraScale = new HashMap<>();

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
        world = new Matrix4f[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            world[i] = new Matrix4f();
            parents[i] = indexOf(bones.get(i).parent());
            if (!bones.get(i).hasModel()) continue;
            parts[i] = spawnPart(bones.get(i));
            base.addPassenger(parts[i]);
        }
        yaw = base.getBodyYaw();
        lastPosition = base.getLocation();
        task = base.getServer().getScheduler().runTaskTimer(Main.getINSTANCE(), this::tick, 1L, TICKS);
    }

    /**
     * Other item models for all bones (same shapes, e.g. the tinted ones of the design session) in one colour,
     * single bones can get their own model (e.g. glowing eyes)
     */
    public void restyle(String folder, org.bukkit.Color tint, Map<String, String> overrides) {
        List<BoneModel.Bone> bones = model.bones();
        for (int i = 0; i < bones.size(); i++) {
            if (parts[i] == null || !parts[i].isValid()) continue;
            String id = overrides.getOrDefault(bones.get(i).id(), folder + "/" + bones.get(i).id());
            ItemStack item = new ItemStack(Material.PAPER);
            item.editMeta(meta -> meta.setItemModel(new NamespacedKey("monsters", id)));
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA,
                    io.papermc.paper.datacomponent.item.CustomModelData.customModelData().addColor(tint).build());
            parts[i].setItemStack(item);
        }
    }

    /** The model turns itself: walking head first, facing its target when standing; with legs also feet on the ground (IK) */
    public void enableSteering(boolean legs) {
        if (legs) rig = new LegRig(model, size);
        steering = true;
    }

    /** Whom the model looks at while it stands */
    public void face(org.bukkit.entity.Entity target) {
        faceTarget = target;
    }

    /** Whether the front points at the spot, within this many degrees */
    public boolean faces(Location at, double maxDegrees) {
        if (!steering) return true;
        double dx = at.getX() - base.getLocation().getX(), dz = at.getZ() - base.getLocation().getZ();
        if (dx * dx + dz * dz < 0.01) return true;
        return Math.abs(angleTo(yaw, yawOf(dx, dz))) <= maxDegrees;
    }

    private static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float angleTo(float from, float to) {
        float d = (to - from) % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    /** A second item model on single bones (bone -> item model) that follows all animations */
    public void addLayer(Map<String, String> models) {
        List<BoneModel.Bone> bones = model.bones();
        layer = new ItemDisplay[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            String id = models.get(bones.get(i).id());
            if (parts[i] == null || id == null) continue;
            layer[i] = spawnPart(id);
            base.addPassenger(layer[i]);
        }
    }

    /** All bones whose name starts like this are drawn bigger */
    public void scaleBones(String prefix, float scale) {
        List<BoneModel.Bone> bones = model.bones();
        for (int i = 0; i < bones.size(); i++) if (bones.get(i).id().startsWith(prefix)) extraScale.put(i, scale);
    }

    /** Outline in one colour, null switches it off */
    public void glow(org.bukkit.Color color) {
        for (ItemDisplay part : parts) {
            if (part == null || !part.isValid()) continue;
            part.setGlowing(color != null);
            part.setGlowColorOverride(color);
        }
    }

    public void setWalk(String animation) {
        if (model.animation(animation) != null) walk = animation;
    }

    /** Stays in this pose also when standing, e.g. crawling without a leg */
    public void setIdle(String animation) {
        if (model.animation(animation) == null) return;
        idle = animation;
        walk = animation;
        noStroll = true;
    }

    /** A bone (and its plate) disappears, e.g. a lost arm */
    public void hideBone(String id) {
        List<BoneModel.Bone> bones = model.bones();
        for (int i = 0; i < bones.size(); i++) {
            // Children go along, e.g. the weapon in the hand
            if (!bones.get(i).id().equals(id) && !descends(i, id)) continue;
            if (parts[i] != null) parts[i].remove();
            if (layer != null && layer[i] != null) {
                layer[i].remove();
                layer[i] = null;
            }
        }
    }

    private boolean descends(int bone, String ancestor) {
        for (int p = parents[bone]; p >= 0; p = parents[p]) if (model.bones().get(p).id().equals(ancestor)) return true;
        return false;
    }

    /** Only the plate of a bone comes off, e.g. broken armor; returns where it was */
    public Location removeLayer(String id) {
        int i = indexOf(id);
        if (i < 0 || layer == null || layer[i] == null) return null;
        Location at = boneLocation(id);
        layer[i].remove();
        layer[i] = null;
        return at;
    }

    private ItemStack[] concealed;

    /** Hidden for a while (e.g. dug into the ground), the bones keep moving */
    public void conceal(boolean on) {
        List<ItemDisplay> all = all();
        if (on && concealed == null) {
            concealed = new ItemStack[all.size()];
            for (int i = 0; i < all.size(); i++) {
                ItemDisplay part = all.get(i);
                if (part == null || !part.isValid()) continue;
                concealed[i] = part.getItemStack();
                part.setItemStack(new ItemStack(Material.AIR));
            }
        } else if (!on && concealed != null) {
            for (int i = 0; i < all.size() && i < concealed.length; i++) {
                ItemDisplay part = all.get(i);
                if (part != null && part.isValid() && concealed[i] != null) part.setItemStack(concealed[i]);
            }
            concealed = null;
        }
    }

    /** Strolls also at normal speed, e.g. on the way to a noise */
    public void stroll(boolean on) {
        stroll = on;
    }

    /** Where the bone is drawn right now, null before the first frame */
    public Location boneLocation(String id) {
        if (lastWorld == null || Float.isNaN(offsetY)) return null;
        List<BoneModel.Bone> bones = model.bones();
        for (int i = 0; i < bones.size(); i++) {
            if (!bones.get(i).id().equals(id) || lastWorld[i] == null) continue;
            Vector3f at = lastWorld[i].getTranslation(new Vector3f());
            return base.getLocation().add(at.x, at.y + offsetY, at.z);
        }
        return null;
    }

    /** The monster changed size, the riding parts sit at another height */
    public void measureAgain() {
        offsetY = Float.NaN;
    }

    /** One-shot animation, e.g. the bite */
    public void play(String animation) {
        if (dying || model.animation(animation) == null) return;
        action = animation;
        actionTime = 0;
    }

    /** Pushed away from where the hit came from, body and head in degrees, on top of what it plays */
    public void recoil(Location from, float body, float head) {
        if (dying || far || from == null || !from.getWorld().equals(base.getWorld())) return;
        Location at = base.getLocation();
        Vector3f push = new Vector3f((float) (at.getX() - from.getX()), 0, (float) (at.getZ() - from.getZ()));
        if (push.lengthSquared() < 0.0001F) return;
        push.normalize().rotateY((float) -Math.toRadians(180 - yaw));
        // +X tilts the top backwards (+Z), +Z tilts it to -X
        bodyRecoil.set(body * push.z, 0, -body * push.x);
        headRecoil.set(head * push.z, 0, -head * push.x);
        recoilTime = 0;
    }

    public void die() {
        die(null);
    }

    /** Death animation where the monster died; knowing the killer it falls away from him, if the model can */
    public void die(Location from) {
        if (dying) return;
        String death = DEATH;
        if (from != null && from.getWorld().equals(base.getWorld()) && model.animation(DEATH_BACK) != null) {
            death = DEATH_BACK;
            deathYaw = yawOf(from.getX() - base.getLocation().getX(), from.getZ() - base.getLocation().getZ());
        }
        // Without a death animation the model just goes
        if (model.animation(death) == null) {
            remove();
            return;
        }
        if (far) showBones();
        if (lodPart != null) lodPart.remove();
        dying = true;
        Corpses.add(this);
        action = death;
        actionTime = 0;
        // The death animation lies on the ground, also when the monster died in the air or while climbing
        Location at = ground(base.getLocation());
        for (ItemDisplay part : all()) {
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

    private List<ItemDisplay> all() {
        List<ItemDisplay> all = new java.util.ArrayList<>(java.util.Arrays.asList(parts));
        if (layer != null) all.addAll(java.util.Arrays.asList(layer));
        return all;
    }

    @Override
    public void sink() {
        sinking = true;
    }

    @Override
    public boolean isGone() {
        return removed;
    }

    public void remove() {
        removed = true;
        task.cancel();
        if (lodPart != null) lodPart.remove();
        for (ItemDisplay part : all()) {
            if (part != null) part.remove();
        }
    }

    private void tick() {
        if (!dying && !base.isValid()) die();

        float step = TICKS / 20f;
        time += step;
        if (action != null) actionTime += step;
        if (!Float.isNaN(recoilTime) && (recoilTime += step) > RECOIL_RISE + RECOIL_BACK) recoilTime = Float.NaN;

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
                // Stays down on the last frame, then sinks
                actionTime = one.length();
                if ((lying += step) > LIE_SECONDS) sinking = true;
            } else {
                action = null;
                one = null;
            }
        }

        if (!dying) {
            Location now = base.getLocation();
            double nearest = nearestPlayer(now);
            switchLod(nearest);
            boolean near = nearest < DETAIL_DISTANCE * DETAIL_DISTANCE;
            int every = (far || !near ? FAR_EVERY : nearest < NEAR_DISTANCE * NEAR_DISTANCE ? 1 : MID_EVERY)
                    * LagGuard.slowdown();
            if (++skipped < every) return;
            skipped = 0;
            int elapsed = TICKS * every;
            setInterpolation(elapsed);
            boolean same = now.getWorld().equals(lastPosition.getWorld());
            boolean moving = same && now.distanceSquared(lastPosition) > 0.0004;
            if (!steering) {
                yaw = base.getBodyYaw();
            } else {
                // Head first in walking direction, standing it turns to its target, at most 12 degrees per tick
                Float want = null;
                if (moving) want = yawOf(now.getX() - lastPosition.getX(), now.getZ() - lastPosition.getZ());
                else if (faceTarget != null && faceTarget.isValid() && faceTarget.getWorld().equals(now.getWorld())) {
                    want = yawOf(faceTarget.getLocation().getX() - now.getX(), faceTarget.getLocation().getZ() - now.getZ());
                }
                if (want != null) {
                    float turn = angleTo(yaw, want), max = TURN_PER_TICK * elapsed;
                    yaw += Math.max(-max, Math.min(max, turn));
                }
            }
            rigActive = rig != null && near && !far;
            if (rigActive) rig.update(now, yaw, elapsed);
            else if (rig != null) rig.reset();
            double speed = moving ? now.distance(lastPosition) / elapsed : 0;
            if (moving) walkTime += step * (float) Math.max(MIN_RATE, Math.min(MAX_RATE, speed / WALK_SPEED)) * every;
            lastPosition = now;
            String loop = !moving ? idle : (stroll || !noStroll && speed < STROLL_SPEED) && walk.equals(WALK) && model.animation(STROLL) != null ? STROLL : walk;
            if (far && model.lod() != null) renderLod();
            else render(model.animation(loop), one);
        } else {
            // Too many corpses: the oldest sinks into the ground
            if (sinking && (sunk += SINK_PER_TICK * TICKS) > SINK_DEPTH) {
                remove();
                return;
            }
            if (!Float.isNaN(deathYaw)) yaw += Math.max(-DEATH_TURN, Math.min(DEATH_TURN, angleTo(yaw, deathYaw)));
            setInterpolation(TICKS);
            render(null, one);
        }
    }

    // Squared distance to the nearest player
    private double nearestPlayer(Location at) {
        double nearest = Double.MAX_VALUE;
        for (org.bukkit.entity.Player player : at.getWorld().getPlayers()) {
            nearest = Math.min(nearest, player.getLocation().distanceSquared(at));
        }
        return nearest;
    }

    // Far away one display in rest pose instead of all bones, back a bit closer so it does not flicker
    private void switchLod(double nearest) {
        BoneModel.Lod lod = model.lod();
        if (lod == null && !model.hasFarHide()) return;
        if (!far && nearest > DETAIL_DISTANCE * DETAIL_DISTANCE) {
            far = true;
            List<ItemDisplay> all = all();
            List<BoneModel.Bone> bones = model.bones();
            hidden = new ItemStack[all.size()];
            for (int i = 0; i < all.size(); i++) {
                ItemDisplay part = all.get(i);
                // Without a whole model only the small bones go
                if (part == null || !part.isValid() || lod == null && !model.hiddenFar(bones.get(i % bones.size()).id())) continue;
                hidden[i] = part.getItemStack();
                part.setItemStack(new ItemStack(Material.AIR));
            }
            if (lod == null) return;
            if (lodPart == null || !lodPart.isValid()) {
                lodPart = spawnPart(lod.model());
                base.addPassenger(lodPart);
            } else {
                lodPart.setItemStack(item(lod.model()));
            }
        } else if (far && nearest < LOD_BACK * LOD_BACK) {
            showBones();
        }
    }

    private void showBones() {
        far = false;
        List<ItemDisplay> all = all();
        for (int i = 0; hidden != null && i < all.size() && i < hidden.length; i++) {
            ItemDisplay part = all.get(i);
            if (part != null && part.isValid() && hidden[i] != null) part.setItemStack(hidden[i]);
        }
        hidden = null;
        if (lodPart != null && lodPart.isValid()) lodPart.setItemStack(new ItemStack(Material.AIR));
    }

    private void renderLod() {
        if (lodPart == null || !lodPart.isValid()) return;
        BoneModel.Lod lod = model.lod();
        float lift = Float.isNaN(offsetY) ? 0 : offsetY;
        Vector3f center = lod.center();
        lodPart.setInterpolationDelay(0);
        lodPart.setTransformationMatrix(new Matrix4f().translate(0, -lift, 0).rotateY((float) Math.toRadians(180 - yaw)).scale(size / 16f)
                .translate(center.x, center.y, center.z).scale(16 * lod.scale()).rotateY((float) Math.PI));
    }

    private void setInterpolation(int ticks) {
        if (ticks == interpolation) return;
        interpolation = ticks;
        for (ItemDisplay part : all()) {
            if (part != null && part.isValid()) part.setInterpolationDuration(ticks);
        }
        if (lodPart != null && lodPart.isValid()) lodPart.setInterpolationDuration(ticks);
    }

    private void render(BoneModel.Animation loop, BoneModel.Animation one) {
        List<BoneModel.Bone> bones = model.bones();
        lastWorld = world;
        // Passengers sit higher than the feet of the monster, the model stands on the ground; the front is -Z
        float lift = (dying || Float.isNaN(offsetY) ? 0 : offsetY) + sunk;
        root.identity().translate(0, -lift, 0).rotateY((float) Math.toRadians(180 - yaw)).scale(size / 16f);
        float clock = loop != null && model.animation(walk) == loop ? walkTime : time;
        float loopTime = loop != null && loop.length() > 0 ? clock % loop.length() : 0;

        for (int i = 0; i < bones.size(); i++) {
            BoneModel.Bone bone = bones.get(i);
            BoneModel.Track oneTrack = one != null ? one.track(i) : null;
            BoneModel.Track loopTrack = loop != null ? loop.track(i) : null;
            rotation.set(0);
            position.set(0);
            scale.set(1);
            pose(oneTrack, loopTrack, BoneModel.ROTATION, loopTime, rotation);
            pose(oneTrack, loopTrack, BoneModel.POSITION, loopTime, position);
            pose(oneTrack, loopTrack, BoneModel.SCALE, loopTime, scale);
            rotation.add(bone.rest());
            if (!Float.isNaN(recoilTime) && !dying) {
                float k = recoilTime < RECOIL_RISE ? recoilTime / RECOIL_RISE : (float) Math.pow(Math.max(0, 1 - (recoilTime - RECOIL_RISE) / RECOIL_BACK), 2);
                if (bone.id().equals(BODY)) rotation.add(bodyRecoil.x * k, 0, bodyRecoil.z * k);
                else if (bone.id().equals(HEAD)) rotation.add(headRecoil.x * k, 0, headRecoil.z * k);
            }
            if (rigActive) {
                Vector3f leg = rig.rotation(bone.id());
                if (leg != null) {
                    rotation.set(leg);
                    position.set(0);
                } else if (bone.id().equals("body")) {
                    // The body only follows the ground, otherwise the feet would slide
                    rotation.set(bone.rest()).add(rig.bodyPitch(), 0, rig.bodyRoll());
                    position.set(0, rig.bodyLift(), 0);
                }
            }
            int parent = parents[i];
            Vector3f origin = parent < 0 ? NO_PIVOT : bones.get(parent).pivot();
            world[i].set(parent < 0 ? root : world[parent])
                    .translate(bone.pivot().x - origin.x + position.x, bone.pivot().y - origin.y + position.y, bone.pivot().z - origin.z + position.z)
                    .rotateZ((float) Math.toRadians(rotation.z))
                    .rotateY((float) Math.toRadians(rotation.y))
                    .rotateX((float) Math.toRadians(rotation.x))
                    .scale(Math.max(0.001f, scale.x), Math.max(0.001f, scale.y), Math.max(0.001f, scale.z));

            ItemDisplay part = parts[i];
            if (part == null || !part.isValid() || far && hidden != null && hidden[i] != null) continue;
            // Item models measure in pixels with the pivot in the middle; displays turn their item by 180 degrees
            part.setInterpolationDelay(0);
            Matrix4f matrix = new Matrix4f(world[i]).scale(16 * model.modelScale()).rotateY((float) Math.PI);
            Float extra = extraScale.get(i);
            if (extra != null) matrix.scale(extra);
            part.setTransformationMatrix(matrix);
            if (layer != null && layer[i] != null && layer[i].isValid()) {
                layer[i].setInterpolationDelay(0);
                layer[i].setTransformationMatrix(matrix);
            }
        }
    }

    // The one-shot animation wins for every channel it moves
    private void pose(BoneModel.Track one, BoneModel.Track loop, int channel, float loopTime, Vector3f out) {
        if (one != null && one.sample(channel, actionTime, out)) return;
        if (loop != null) loop.sample(channel, loopTime, out);
    }

    private ItemDisplay spawnPart(BoneModel.Bone bone) {
        return spawnPart(model.id() + "/" + bone.id());
    }

    private static ItemStack item(String modelId) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("monsters", modelId)));
        return item;
    }

    private ItemDisplay spawnPart(String modelId) {
        ItemStack item = item(modelId);
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
