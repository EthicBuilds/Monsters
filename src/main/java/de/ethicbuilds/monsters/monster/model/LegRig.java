package de.ethicbuilds.monsters.monster.model;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/***
 * Procedural spider legs (bones leg_l1..leg_r4 with _upper and _lower): every foot stays planted on the real ground,
 * steps in diagonal groups when the body walks away from it, the legs bend with 2-joint IK and the body lifts and
 * tilts with the ground under the feet. All model values in pixels, world values in blocks.
 */
public final class LegRig {
    private static final float SHIN = 34;
    private static final int STEP_TICKS = 4;
    private static final float TRIGGER_PX = 10, FOLLOW_PX = 4, AHEAD_PX = 4, ARC_PX = 6;
    private static final double RAY_UP = 1.5, RAY_LENGTH = 3, TELEPORT = 3;
    private static final float MAX_TILT = 20, SMOOTH = 0.3f;

    private static final class Leg {
        final boolean left, front;
        // 0 = L1 R2 L3 R4, 1 = R1 L2 R3 L4
        final int group;
        final Vector3f hip, rest;
        final float thigh;
        final Vector3f turn = new Vector3f(), upper = new Vector3f(), lower = new Vector3f();
        // Planted world point (the target while stepping) and the start of the current step
        double x, y, z, fromX, fromY, fromZ, distance;
        float progress;
        boolean stepping;
        // Current foot in root pixels, ground height without the step arc
        float footX, footY, footZ, ground;

        Leg(boolean left, boolean front, int group, BoneModel.Bone leg, BoneModel.Bone upper, BoneModel.Bone lower) {
            this.left = left;
            this.front = front;
            this.group = group;
            hip = new Vector3f(leg.pivot());
            thigh = lower.pivot().distance(upper.pivot());
            float shin = Math.signum(lower.pivot().x - upper.pivot().x) * SHIN;
            // Rest foot from the rest pose, like AnimatedModel draws it
            Matrix4f matrix = new Matrix4f().translate(hip);
            rotate(matrix, leg.rest());
            matrix.translate(upper.pivot().sub(leg.pivot(), new Vector3f()));
            rotate(matrix, upper.rest());
            matrix.translate(lower.pivot().sub(upper.pivot(), new Vector3f()));
            rotate(matrix, lower.rest());
            rest = matrix.transformPosition(new Vector3f(shin, 0, 0));
            turn.set(leg.rest());
            this.upper.set(upper.rest());
            this.lower.set(lower.rest());
        }

        private static void rotate(Matrix4f matrix, Vector3f rotation) {
            matrix.rotateZ((float) Math.toRadians(rotation.z))
                    .rotateY((float) Math.toRadians(rotation.y))
                    .rotateX((float) Math.toRadians(rotation.x));
        }
    }

    private final Leg[] legs;
    private final Map<String, Vector3f> rotations = new HashMap<>();
    private final float scale;
    private final Vector3f bodyPivot;
    private final float restHeight, pitchSpan, rollSpan;
    private final Location probe = new Location(null, 0, 0, 0);
    private final Vector down = new Vector(0, -1, 0);
    private final boolean[] air = new boolean[2];
    private final double[] need = new double[2];

    private boolean planted;
    private World lastWorld;
    private double lastX, lastY, lastZ;
    private double cos, sin;
    private float lift, pitch, roll;

    public LegRig(BoneModel model, float size) {
        scale = size / 16f;
        Map<String, BoneModel.Bone> bones = new HashMap<>();
        for (BoneModel.Bone bone : model.bones()) bones.put(bone.id(), bone);
        BoneModel.Bone body = bones.get("body");
        bodyPivot = body != null ? new Vector3f(body.pivot()) : new Vector3f(0, 20, 0);

        List<Leg> found = new ArrayList<>();
        for (char side : new char[]{'l', 'r'}) {
            for (int n = 1; n <= 4; n++) {
                String id = "leg_" + side + n;
                BoneModel.Bone leg = bones.get(id), upper = bones.get(id + "_upper"), lower = bones.get(id + "_lower");
                if (leg == null || upper == null || lower == null) continue;
                boolean left = side == 'l';
                Leg rig = new Leg(left, n <= 2, left == (n % 2 == 1) ? 0 : 1, leg, upper, lower);
                found.add(rig);
                rotations.put(id, rig.turn);
                rotations.put(id + "_upper", rig.upper);
                rotations.put(id + "_lower", rig.lower);
            }
        }
        legs = found.toArray(new Leg[0]);

        float height = 0, frontZ = 0, backZ = 0, leftX = 0, rightX = 0;
        int fronts = 0, lefts = 0;
        for (Leg leg : legs) {
            height += leg.rest.y;
            if (leg.front) {
                frontZ += leg.rest.z;
                fronts++;
            } else backZ += leg.rest.z;
            if (leg.left) {
                leftX += leg.rest.x;
                lefts++;
            } else rightX += leg.rest.x;
        }
        int count = Math.max(1, legs.length);
        restHeight = height / count;
        pitchSpan = Math.max(1, backZ / Math.max(1, count - fronts) - frontZ / Math.max(1, fronts));
        rollSpan = Math.max(1, leftX / Math.max(1, lefts) - rightX / Math.max(1, count - lefts));
    }

    /** Once per render update; base = entity location (feet), yaw = model yaw in degrees as AnimatedModel uses it, ticks = elapsed ticks since last call */
    public void update(Location base, float yaw, int ticks) {
        if (legs.length == 0) return;
        World world = base.getWorld();
        double bx = base.getX(), by = base.getY(), bz = base.getZ();
        double angle = Math.toRadians(180 - yaw);
        cos = Math.cos(angle);
        sin = Math.sin(angle);
        int elapsed = Math.max(1, ticks);
        double dx = bx - lastX, dy = by - lastY, dz = bz - lastZ;
        boolean teleported = world != lastWorld || dx * dx + dy * dy + dz * dz > TELEPORT * TELEPORT;
        lastWorld = world;
        lastX = bx;
        lastY = by;
        lastZ = bz;

        boolean snap = !planted || teleported;
        if (snap) plant(world, bx, by, bz);
        else step(world, bx, by, bz, dx / elapsed, dz / elapsed, elapsed);
        pose(bx, by, bz, snap);
    }

    /** Total rotation (degrees, x/y/z) for leg bones, null for every other bone; read only */
    public Vector3f rotation(String boneId) {
        return rotations.get(boneId);
    }

    public float bodyLift() {
        return lift;
    }

    public float bodyPitch() {
        return pitch;
    }

    public float bodyRoll() {
        return roll;
    }

    /** All feet go back to their rest points on the next update */
    public void reset() {
        planted = false;
    }

    private void plant(World world, double bx, double by, double bz) {
        for (Leg leg : legs) {
            leg.x = restX(leg, bx);
            leg.z = restZ(leg, bz);
            leg.y = ground(world, leg, leg.x, by, leg.z);
            leg.stepping = false;
        }
        planted = true;
    }

    private void step(World world, double bx, double by, double bz, double vx, double vz, int elapsed) {
        air[0] = air[1] = false;
        need[0] = need[1] = 0;
        for (Leg leg : legs) {
            if (!leg.stepping) continue;
            leg.progress += elapsed;
            if (leg.progress >= STEP_TICKS) leg.stepping = false;
            else air[leg.group] = true;
        }
        for (Leg leg : legs) {
            if (leg.stepping) continue;
            leg.distance = distance(leg, bx, by, bz);
            need[leg.group] = Math.max(need[leg.group], leg.distance);
        }

        // A group lifts only while the other one stands, the one further behind goes first
        double trigger = TRIGGER_PX * scale;
        boolean first = !air[1] && need[0] > trigger, second = !air[0] && need[1] > trigger;
        if (first && second) {
            if (need[1] > need[0]) first = false;
            else second = false;
        }
        if (!first && !second) return;
        int group = first ? 0 : 1;

        // Land a bit ahead of where the rest point will be when the step ends
        double speed = Math.sqrt(vx * vx + vz * vz), aheadX = 0, aheadZ = 0;
        if (speed > 0.001) {
            double ahead = AHEAD_PX * scale / speed + STEP_TICKS;
            aheadX = vx * ahead;
            aheadZ = vz * ahead;
        }
        // The rest of the group steps along, so the diagonal pairs stay together
        double follow = FOLLOW_PX * scale;
        for (Leg leg : legs) {
            if (leg.group != group || leg.stepping || leg.distance <= follow) continue;
            leg.fromX = leg.x;
            leg.fromY = leg.y;
            leg.fromZ = leg.z;
            leg.x = restX(leg, bx) + aheadX;
            leg.z = restZ(leg, bz) + aheadZ;
            leg.y = ground(world, leg, leg.x, by, leg.z);
            leg.progress = 0;
            leg.stepping = true;
        }
    }

    /** Horizontal distance to the rest point, feet outside the raycast range always step */
    private double distance(Leg leg, double bx, double by, double bz) {
        double height = leg.y - by - leg.rest.y * scale;
        if (height > RAY_UP || height < RAY_UP - RAY_LENGTH) return Double.MAX_VALUE;
        double x = leg.x - restX(leg, bx), z = leg.z - restZ(leg, bz);
        return Math.sqrt(x * x + z * z);
    }

    private double ground(World world, Leg leg, double x, double by, double z) {
        probe.setWorld(world);
        probe.setX(x);
        probe.setY(by + RAY_UP);
        probe.setZ(z);
        RayTraceResult hit = world.rayTraceBlocks(probe, down, RAY_LENGTH, FluidCollisionMode.NEVER, true);
        // A ray starting inside a block (low ceiling) finds no ground
        double y = hit == null || hit.getHitPosition().getY() > by + RAY_UP - 0.01 ? by : hit.getHitPosition().getY();
        return y + leg.rest.y * scale;
    }

    private double restX(Leg leg, double bx) {
        return bx + (leg.rest.x * cos + leg.rest.z * sin) * scale;
    }

    private double restZ(Leg leg, double bz) {
        return bz + (leg.rest.z * cos - leg.rest.x * sin) * scale;
    }

    private void pose(double bx, double by, double bz, boolean snap) {
        float height = 0, front = 0, back = 0, left = 0, right = 0;
        int fronts = 0, lefts = 0;
        for (Leg leg : legs) {
            double x = leg.x, y = leg.y, z = leg.z, ground = leg.y;
            if (leg.stepping) {
                double t = leg.progress / STEP_TICKS;
                x = leg.fromX + (leg.x - leg.fromX) * t;
                z = leg.fromZ + (leg.z - leg.fromZ) * t;
                ground = leg.fromY + (leg.y - leg.fromY) * t;
                y = ground + Math.sin(Math.PI * t) * ARC_PX * scale;
            }
            // World to root pixels, inverse of root rotateY(180 - yaw).scale(size / 16)
            double wx = x - bx, wz = z - bz;
            leg.footX = (float) ((wx * cos - wz * sin) / scale);
            leg.footY = (float) ((y - by) / scale);
            leg.footZ = (float) ((wx * sin + wz * cos) / scale);
            leg.ground = (float) ((ground - by) / scale);

            height += leg.ground;
            if (leg.front) {
                front += leg.ground;
                fronts++;
            } else back += leg.ground;
            if (leg.left) {
                left += leg.ground;
                lefts++;
            } else right += leg.ground;
        }
        int count = legs.length;
        float liftTarget = height / count - restHeight;
        float pitchTarget = fronts == 0 || fronts == count ? 0
                : tilt(front / fronts - back / (count - fronts), pitchSpan);
        float rollTarget = lefts == 0 || lefts == count ? 0
                : tilt(left / lefts - right / (count - lefts), rollSpan);
        if (snap) {
            lift = liftTarget;
            pitch = pitchTarget;
            roll = rollTarget;
        } else {
            lift += (liftTarget - lift) * SMOOTH;
            pitch += (pitchTarget - pitch) * SMOOTH;
            roll += (rollTarget - roll) * SMOOTH;
        }

        // Root to body space: undo the lift, then roll (Z) and pitch (X) around the body pivot
        double p = Math.toRadians(pitch), r = Math.toRadians(roll);
        float cp = (float) Math.cos(p), sp = (float) Math.sin(p), cr = (float) Math.cos(r), sr = (float) Math.sin(r);
        for (Leg leg : legs) {
            float qx = leg.footX - bodyPivot.x, qy = leg.footY - bodyPivot.y - lift, qz = leg.footZ - bodyPivot.z;
            float x = qx * cr + qy * sr, y = qy * cr - qx * sr;
            float y2 = y * cp + qz * sp, z = qz * cp - y * sp;
            solve(leg, x + bodyPivot.x, y2 + bodyPivot.y, z + bodyPivot.z);
        }
    }

    private static float tilt(float height, float span) {
        float degrees = (float) Math.toDegrees(Math.atan2(height, span));
        return Math.max(-MAX_TILT, Math.min(MAX_TILT, degrees));
    }

    /** 2-joint IK from the hip to the foot (body pixels), bending in the vertical plane through both */
    private static void solve(Leg leg, float fx, float fy, float fz) {
        float dx = fx - leg.hip.x, dz = fz - leg.hip.z, h = leg.hip.y - fy;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        float l1 = leg.thigh, l2 = SHIN;
        float reach = clamp((float) Math.sqrt(d * d + h * h), Math.abs(l1 - l2) + 0.5f, l1 + l2 - 0.5f);
        double alpha = Math.atan2(-h, d) + Math.acos(clamp((l1 * l1 + reach * reach - l2 * l2) / (2 * l1 * reach), -1, 1));
        double knee = Math.acos(clamp((l1 * l1 + l2 * l2 - reach * reach) / (2 * l1 * l2), -1, 1));
        float turn = (float) Math.toDegrees(leg.left ? Math.atan2(-dz, dx) : -Math.atan2(-dz, -dx));
        float upper = (float) Math.toDegrees(alpha), lower = 180 - (float) Math.toDegrees(knee);
        leg.turn.set(0, turn, 0);
        leg.upper.set(0, 0, leg.left ? upper : -upper);
        leg.lower.set(0, 0, leg.left ? -lower : lower);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
