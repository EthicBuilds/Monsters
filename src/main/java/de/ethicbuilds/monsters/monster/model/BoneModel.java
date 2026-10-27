package de.ethicbuilds.monsters.monster.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/***
 * Animated Blockbench model (resources/mobs/<id>.json, made with EggBattle tools/bbmob.js): bones with pivot (pixels)
 * and rest pose (degrees), animations with keyframes per bone, linear or smooth (Catmull-Rom like Blockbench).
 * Parents come before their children in the bone list.
 */
public final class BoneModel {
    public record Bone(String id, String parent, Vector3f pivot, Vector3f rest, boolean hasModel) {}

    private record Channel(float[] times, float[][] values, boolean[] smooth, boolean loop) {
        void sample(float time, Vector3f out) {
            int last = times.length - 1;
            if (time <= times[0]) {
                out.set(values[0][0], values[0][1], values[0][2]);
                return;
            }
            if (time >= times[last]) {
                out.set(values[last][0], values[last][1], values[last][2]);
                return;
            }
            int i = 1;
            while (times[i] < time) i++;
            float f = (time - times[i - 1]) / (times[i] - times[i - 1]);
            float[] a = values[i - 1];
            float[] b = values[i];
            if (!smooth[i - 1] && !smooth[i]) {
                out.set(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f);
                return;
            }
            boolean wrap = loop && times.length >= 3;
            float[] before = i >= 2 ? values[i - 2] : wrap ? values[last - 1] : a;
            float[] after = i + 1 <= last ? values[i + 1] : wrap ? values[1] : b;
            out.set(catmullRom(f, before[0], a[0], b[0], after[0]), catmullRom(f, before[1], a[1], b[1], after[1]),
                    catmullRom(f, before[2], a[2], b[2], after[2]));
        }

        // Like three.js SplineCurve, which Blockbench uses for smooth keyframes
        private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
            float v0 = (p2 - p0) * 0.5f;
            float v1 = (p3 - p1) * 0.5f;
            float t2 = t * t;
            float t3 = t * t2;
            return (2 * p1 - 2 * p2 + v0 + v1) * t3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2 + v0 * t + p1;
        }
    }

    public static final class Animation {
        private final float length;
        private final boolean loop;
        private final Map<String, Map<String, Channel>> bones = new HashMap<>();

        private Animation(JsonObject json) {
            length = json.get("length").getAsFloat();
            loop = json.has("loop") && json.get("loop").getAsBoolean();
            for (Map.Entry<String, JsonElement> bone : json.getAsJsonObject("bones").entrySet()) {
                Map<String, Channel> channels = new HashMap<>();
                for (Map.Entry<String, JsonElement> channel : bone.getValue().getAsJsonObject().entrySet()) {
                    JsonArray keys = channel.getValue().getAsJsonArray();
                    float[] times = new float[keys.size()];
                    float[][] values = new float[keys.size()][];
                    boolean[] smooth = new boolean[keys.size()];
                    for (int i = 0; i < keys.size(); i++) {
                        JsonArray key = keys.get(i).getAsJsonArray();
                        times[i] = key.get(0).getAsFloat();
                        values[i] = new float[]{key.get(1).getAsFloat(), key.get(2).getAsFloat(), key.get(3).getAsFloat()};
                        smooth[i] = key.size() > 4 && "catmullrom".equals(key.get(4).getAsString());
                    }
                    channels.put(channel.getKey(), new Channel(times, values, smooth, loop));
                }
                bones.put(bone.getKey(), channels);
            }
        }

        public float length() {
            return length;
        }

        public boolean loops() {
            return loop;
        }

        /** @return false if the animation does not move this channel of the bone */
        public boolean sample(String bone, String channel, float time, Vector3f out) {
            Map<String, Channel> channels = bones.get(bone);
            Channel keys = channels != null ? channels.get(channel) : null;
            if (keys == null) return false;
            keys.sample(time, out);
            return true;
        }
    }

    private final String id;
    private final float modelScale;
    private final List<Bone> bones = new ArrayList<>();
    private final Map<String, Animation> animations = new HashMap<>();

    private BoneModel(JsonObject json) {
        id = json.get("id").getAsString();
        modelScale = json.has("modelScale") ? json.get("modelScale").getAsFloat() : 1;
        for (JsonElement element : json.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
            boolean hasModel = !bone.has("model") || bone.get("model").getAsBoolean();
            bones.add(new Bone(bone.get("id").getAsString(), parent, vector(bone.getAsJsonArray("pivot")), vector(bone.getAsJsonArray("rest")), hasModel));
        }
        for (Map.Entry<String, JsonElement> animation : json.getAsJsonObject("animations").entrySet()) {
            animations.put(animation.getKey(), new Animation(animation.getValue().getAsJsonObject()));
        }
    }

    public static BoneModel load(InputStream in) throws IOException {
        if (in == null) throw new IOException("Model missing in the plugin");
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new BoneModel(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    private static Vector3f vector(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    public String id() {
        return id;
    }

    /** Bone item models are stored smaller (Minecraft allows -16..32), this scales them back */
    public float modelScale() {
        return modelScale;
    }

    public List<Bone> bones() {
        return bones;
    }

    public Animation animation(String name) {
        return animations.get(name);
    }
}
