package ssjj.dark;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.zip.GZIPInputStream;

/** 原技能动作重定向和原粒子帧的只读运行资源；缺件直接报错，不静默换成旧贴片。 */
final class DarkRuntimeAssets {
    record Part(String name, float[] uv, int armor) {}
    record Batch(int material, boolean ground, float[] tiles, float[] cell, float[] vertices) {}
    record Material(String[] textures, float[][] st, float[] scroll, float[] noiseScroll,
                    float brightness, float alpha, float contrast, float warp, boolean masked) {}
    static final class Data {
        Part[] parts;
        float[][] avatar, weapon;
        float[] weaponUV;
        int[] weaponFaces;
        Batch[][] particles;
        JsonArray materials;
        Material[] preparedMaterials;
    }
    private static volatile Data loaded;

    /** Reload preparation owns all IO/JSON/decompression. Rendering only reads a
     * fully published immutable snapshot, and never waits on a worker or lock. */
    static Data get() {
        Data result = loaded;
        if (result == null) throw new IllegalStateException("黯水晶演出资源尚未完成客户端资源预热");
        return result;
    }

    @FunctionalInterface interface Source { InputStream open(String name) throws IOException; }

    static void publish(Data data) { loaded = data; }

    static Data decode(Source source) {
        try {
            Data data = new Data();
            JsonObject avatar = json(source, "avatar.json");
            JsonArray parts = avatar.getAsJsonArray("parts");
            data.parts = new Part[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                JsonObject p = parts.get(i).getAsJsonObject();
                data.parts[i] = new Part(p.get("name").getAsString(), floats(p.getAsJsonArray("uv")), p.get("armor").getAsInt());
            }
            data.weaponUV = floats(avatar.getAsJsonArray("weapon_uv"));
            float[] faces = floats(avatar.getAsJsonArray("weapon_faces"));
            data.weaponFaces = new int[faces.length];
            for (int i = 0; i < faces.length; i++) data.weaponFaces[i] = (int) faces[i];
            try (BinaryInput in = binary(source, "avatar.bin.gz")) {
                require(in.readInt() == 1, "全身动作版本");
                int count = in.readInt(), vertices = in.readInt(), weaponVertices = in.readInt();
                require(count == 91 && vertices == data.parts.length * 24 && weaponVertices > 0 && weaponVertices < 100000, "全身动作结构");
                data.avatar = readFrames(in, count, vertices * 3);
                data.weapon = readFrames(in, count, weaponVertices * 3);
                require(in.read() == -1, "全身动作尾部");
            }
            JsonObject particles = json(source, "particles.json");
            data.materials = particles.getAsJsonArray("materials");
            data.preparedMaterials = new Material[data.materials.size()];
            for (int i = 0; i < data.preparedMaterials.length; i++) {
                JsonObject material = data.materials.get(i).getAsJsonObject();
                String[] textures = new String[3];
                float[][] st = new float[3][];
                for (int j = 0; j < 3; j++) {
                    textures[j] = material.getAsJsonArray("textures").get(j).getAsString();
                    st[j] = floats(material.getAsJsonArray("st").get(j).getAsJsonArray());
                }
                data.preparedMaterials[i] = new Material(textures, st, floats(material.getAsJsonArray("scroll")),
                    floats(material.getAsJsonArray("noiseScroll")), material.get("brightness").getAsFloat(),
                    material.get("alpha").getAsFloat(), material.get("contrast").getAsFloat(),
                    material.get("warp").getAsFloat(), material.get("masked").getAsBoolean());
            }
            try (BinaryInput in = binary(source, "particles.bin.gz")) {
                require(in.readInt() == 1, "粒子帧版本");
                int count = in.readInt(), fps = in.readInt();
                require(count == 101 && fps == 20, "粒子时基");
                data.particles = new Batch[count][];
                for (int i = 0; i < count; i++) {
                    int groups = in.readInt();
                    require(groups >= 0 && groups <= 256, "粒子批次数");
                    data.particles[i] = new Batch[groups];
                    for (int j = 0; j < groups; j++) {
                        int material = in.readInt(), ground = in.readInt(), n = in.readInt();
                        require(material >= 0 && material < data.materials.size() && n >= 0 && n <= 100000, "粒子批次结构");
                        float[] tiles = read(in, 2), cell = read(in, 2);
                        data.particles[i][j] = new Batch(material, ground != 0, tiles, cell, read(in, n * 9));
                    }
                }
                require(in.read() == -1, "粒子帧尾部");
            }
            return data;
        } catch (IOException error) {
            throw new IllegalStateException("黯水晶原素材运行资源读取失败", error);
        }
    }

    static InputStream stream(String name) throws IOException {
        InputStream in = DarkRuntimeAssets.class.getResourceAsStream("/assets/ssjj_dark/runtime/" + name);
        if (in == null) throw new IOException("缺少 " + name);
        return in;
    }
    private static JsonObject json(Source source, String name) throws IOException {
        try (InputStream in = source.open(name)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static final class BinaryInput extends DataInputStream {
        final byte[] scratch = new byte[65536];
        final FloatBuffer floats = ByteBuffer.wrap(scratch).order(ByteOrder.BIG_ENDIAN).asFloatBuffer();
        BinaryInput(InputStream stream) { super(stream); }
    }
    private static BinaryInput binary(Source source, String name) throws IOException {
        // DataInputStream.readFloat otherwise reaches GZIPInputStream one byte
        // at a time. Buffer the *inflated* bytes as well as the compressed input.
        return new BinaryInput(new BufferedInputStream(new GZIPInputStream(
            new BufferedInputStream(source.open(name), 65536), 65536), 65536));
    }
    private static void require(boolean valid, String field) throws IOException { if (!valid) throw new IOException("资源校验失败：" + field); }
    private static float[] read(BinaryInput in, int size) throws IOException {
        float[] a = new float[size];
        for (int offset = 0; offset < size;) {
            int count = Math.min(size - offset, in.scratch.length / 4);
            in.readFully(in.scratch, 0, count * 4);
            in.floats.position(0); in.floats.get(a, offset, count);
            offset += count;
        }
        for (float value : a) require(Float.isFinite(value), "非有限浮点数");
        return a;
    }
    private static float[][] readFrames(BinaryInput in, int count, int size) throws IOException {
        float[][] frames = new float[count][];
        for (int i = 0; i < count; i++) frames[i] = read(in, size);
        return frames;
    }
    static float[] floats(JsonArray array) {
        float[] result = new float[count(array)];
        flatten(array, result, 0);
        return result;
    }
    private static int count(JsonArray array) {
        int count = 0;
        for (var element : array) count += element.isJsonArray() ? count(element.getAsJsonArray()) : 1;
        return count;
    }
    private static int flatten(JsonArray array, float[] out, int offset) {
        for (var element : array) {
            if (element.isJsonArray()) offset = flatten(element.getAsJsonArray(), out, offset);
            else out[offset++] = element.getAsFloat();
        }
        return offset;
    }
    static float sample(float[][] frames, float seconds, float recover, int index) {
        float t = Math.max(0, Math.min(90, seconds * 30));
        int i = (int) t, j = Math.min(90, i + 1);
        float value = frames[i][index] * (1 - (t - i)) + frames[j][index] * (t - i);
        return value * (1 - recover) + frames[0][index] * recover;
    }
    /** One interpolation per unique vertex, shared by skin/outerwear/armor. */
    static void sampleInto(float[][] frames, float seconds, float recover, float[] output) {
        float t = Math.max(0, Math.min(90, seconds * 30));
        int i = (int)t, j = Math.min(90, i + 1);
        float fraction = t - i, active = 1 - recover;
        float[] first = frames[i], second = frames[j], rest = frames[0];
        for (int k = 0; k < first.length; k++)
            output[k] = (first[k] * (1 - fraction) + second[k] * fraction) * active + rest[k] * recover;
    }
    private DarkRuntimeAssets() {}
}
