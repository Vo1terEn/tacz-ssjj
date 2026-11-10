package ssjj.dark;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Original qiangshen body meshes and textures, prepared once during resource reload. */
@Mod.EventBusSubscriber(modid = DarkCrystalMod.ID, value = Dist.CLIENT)
public final class DarkBladeEffects {
    private static final String NODE = "ssjj_dark_blade_effects";
    private static final ResourceLocation BINARY = new ResourceLocation("ssjj_dark", "runtime/blade_effects.bin.gz");
    private static final ResourceLocation MANIFEST = new ResourceLocation("ssjj_dark", "runtime/blade_effects.json");
    private static Data active;
    private static final Matrix4f AVATAR = new Matrix4f();

    static final class Layer {
        int texture, billboard, vertices, particles, blend;
        float[] nativeMap, avatarMap, geometry, life, states;
    }
    static final class Data {
        Layer[] layers;
        Layer[][][] groups;
        int[] referenceIndices;
        float[] reference;
        ResourceLocation[] textures;
        RenderType[] types;
        final Matrix4f mapped = new Matrix4f(), particle = new Matrix4f(), combined = new Matrix4f();
        final float[] state = new float[20];
        final Vector3f point = new Vector3f();
        final Vector3f[] basis = { new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f() };
        final Matrix4f inverseReference = new Matrix4f(), targetFrame = new Matrix4f();
        final DrawingSink sink = new DrawingSink();
        final Layer[] single = new Layer[1];
    }

    @FunctionalInterface
    interface VertexSink { void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a); }

    /** Exact fixed-size source cache. No file, JSON or GZIP work happens in rendering. */
    static Data decode(InputStream binary, InputStream manifest) {
        try (var input = new DataInputStream(new GZIPInputStream(binary)); manifest) {
            var json = JsonParser.parseString(new String(manifest.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            int version = input.readInt(), layerCount = input.readInt(), variantCount = input.readInt(), phases = input.readInt();
            if (version != 1 || layerCount < 1 || layerCount > 256 || variantCount != 12 || phases != 64) throw new IOException("Invalid blade-effect cache header");
            Data result = new Data();
            result.referenceIndices = new int[] { input.readInt(), input.readInt(), input.readInt() };
            result.reference = floats(input, 9);
            var textures = json.getAsJsonArray("textures");
            if (textures.size() < 1 || textures.size() > 64) throw new IOException("Invalid blade-effect texture count");
            result.textures = new ResourceLocation[textures.size()];
            for (int i = 0; i < textures.size(); i++) result.textures[i] = new ResourceLocation(textures.get(i).getAsJsonObject().get("id").getAsString());
            var blends = json.getAsJsonArray("layer_blends");
            if (blends == null || blends.size() != layerCount) throw new IOException("Missing source blend modes");
            result.layers = new Layer[layerCount];
            long totalFloats = 0;
            for (int i = 0; i < layerCount; i++) {
                Layer layer = new Layer();
                layer.blend = blends.get(i).getAsInt();
                if (layer.blend != 1 && layer.blend != 10) throw new IOException("Unsupported source blend");
                layer.texture = input.readInt(); layer.billboard = input.readInt(); layer.vertices = input.readInt(); layer.particles = input.readInt();
                if (layer.texture < 0 || layer.texture >= textures.size() || layer.vertices < 4 || layer.vertices > 32768 || layer.vertices % 4 != 0 || layer.particles < 1 || layer.particles > 64) throw new IOException("Invalid blade-effect layer");
                totalFloats += 24L + layer.vertices * 5L + layer.particles * (2L + 64L * 20L);
                if (totalFloats > 4_000_000) throw new IOException("Blade-effect cache exceeds limit");
                layer.nativeMap = floats(input, 12); layer.avatarMap = floats(input, 12);
                layer.geometry = floats(input, layer.vertices * 5); layer.life = floats(input, layer.particles * 2); layer.states = floats(input, layer.particles * 64 * 20);
                for (int p = 0; p < layer.particles; p++) if (layer.life[p * 2] < .01f) throw new IOException("Invalid particle lifetime");
                result.layers[i] = layer;
            }
            result.groups = new Layer[12][][];
            for (int i = 0; i < variantCount; i++) {
                int grade = input.readInt(), skin = input.readInt(), count = input.readInt();
                if (grade < 0 || grade > 2 || skin < 0 || skin > 3 || count < 1 || count > layerCount || result.groups[grade * 4 + skin] != null) throw new IOException("Invalid blade-effect variant");
                ArrayList<ArrayList<Layer>> grouped = new ArrayList<>();
                for (int t = 0; t < textures.size(); t++) grouped.add(new ArrayList<>());
                for (int l = 0; l < count; l++) {
                    int index = input.readInt();
                    if (index < 0 || index >= layerCount) throw new IOException("Invalid blade-effect layer index");
                    Layer layer = result.layers[index]; grouped.get(layer.texture).add(layer);
                }
                Layer[][] rows = new Layer[textures.size()][];
                for (int t = 0; t < textures.size(); t++) rows[t] = grouped.get(t).toArray(Layer[]::new);
                result.groups[grade * 4 + skin] = rows;
            }
            if (input.read() != -1) throw new IOException("Unexpected blade-effect trailing bytes");
            for (Layer[][] group : result.groups) if (group == null) throw new IOException("Missing blade-effect variant");
            referenceFrame(result);
            return result;
        } catch (IOException error) { throw new IllegalStateException("Cannot prepare original blade effects", error); }
    }

    private static float[] floats(DataInputStream input, int count) throws IOException {
        byte[] bytes = input.readNBytes(count * 4);
        if (bytes.length != count * 4) throw new IOException("Truncated blade-effect cache");
        float[] values = new float[count]; ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(values);
        for (float value : values) if (!Float.isFinite(value)) throw new IOException("Nonfinite blade-effect value");
        return values;
    }

    @Mod.EventBusSubscriber(modid = DarkCrystalMod.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((barrier, resources, prepProfiler, applyProfiler, prepExecutor, applyExecutor) ->
                CompletableFuture.supplyAsync(() -> {
                    try { return decode(resources.m_215595_(BINARY), resources.m_215595_(MANIFEST)); }
                    catch (IOException error) { throw new IllegalStateException(error); }
                }, prepExecutor).thenCompose(barrier::m_6769_).thenComposeAsync(data -> {
                    var manager = Minecraft.m_91087_().m_91097_();
                    CompletableFuture<?>[] jobs = new CompletableFuture<?>[data.textures.length];
                    for (int i = 0; i < jobs.length; i++) jobs[i] = manager.m_118501_(data.textures[i], prepExecutor);
                    return CompletableFuture.allOf(jobs).thenRunAsync(() -> {
                        data.types = new RenderType[data.textures.length * 2];
                        for (int i = 0; i < data.textures.length; i++) {
                            data.types[i*2] = DarkBladePass.create(data.textures[i], true);
                            data.types[i*2+1] = DarkBladePass.create(data.textures[i], false);
                        }
                        active = data;
                        com.mojang.logging.LogUtils.getLogger().info("[DarkBladeFX] Cache ready: {} layers, {} textures", data.layers.length, data.textures.length);
                    }, applyExecutor);
                }, applyExecutor));
        }
    }

    /** Does not create tags on unrelated/invalid TaCZ stacks. */
    static int variant(ItemStack stack) {
        if (stack == null || IGun.getIGunOrNull(stack) == null) return -1;
        var tag = stack.m_41783_();
        if (tag == null || !tag.m_128425_("GunId", 8)) return -1;
        int grade = DarkMeleeRules.grade(DarkMeleeRules.canonical(tag.m_128461_("GunId")));
        if (grade < 0) return -1;
        ResourceLocation scope = IGun.getIGunOrNull(stack).getAttachmentId(stack, AttachmentType.SCOPE);
        int skin = scope == null || !"ssjj_dark".equals(scope.m_135827_()) ? 0 : switch (scope.m_135815_()) {
            case "skin_mix" -> 1; case "skin_nightguardian" -> 2; case "skin_pure" -> 3; default -> 0;
        };
        return grade * 4 + skin;
    }

    public static void bind(BedrockGunModel model) {
        if (model == null) return;
        BedrockPart blade = model.getNode("blade");
        if (blade == null) return;
        for (BedrockPart child : blade.children) if (NODE.equals(child.name)) return;
        Binding binding = new Binding(model);
        blade.addChild(new EffectPart(binding, blade));
    }
    private static final class EffectPart extends FunctionalBedrockPart {
        EffectPart(Binding binding, BedrockPart owner) { super(part -> binding.renderer, NODE); this.parent = owner; }
    }
    private static final class Binding {
        final BedrockGunModel model;
        final Matrix4f captured = new Matrix4f();
        int profile = -1, savedOverlay;
        boolean queued;
        final IFunctionalRenderer renderer = this::capture;
        final IFunctionalRenderer deferred = (pose, ignored, context, light, overlay) -> {
            try {
                var mc = Minecraft.m_91087_();
                render(active, profile, captured, false, mc.m_91269_().m_110104_(), savedOverlay, seconds());
            } finally { queued = false; profile = -1; }
        };
        Binding(BedrockGunModel model) { this.model = model; }
        void capture(PoseStack pose, VertexConsumer ignored, net.minecraft.world.item.ItemDisplayContext context, int light, int overlay) {
            profile = variant(model.getCurrentGunItem());
            captured.set(pose.m_85850_().m_252922_()); savedOverlay = overlay;
            // BedrockModel flushes the opaque render type before delegates.
            // Preserve the animated blade matrix now, draw only after that flush.
            if (!queued) { queued = true; model.delegateRender(deferred); }
        }
    }
    private static void held(LivingEntity entity) {
        if (entity == null || variant(entity.m_21205_()) < 0) return;
        TimelessAPI.getGunDisplay(entity.m_21205_()).ifPresent(display -> { display.warmUpModel(); bind(display.getGunModel()); });
    }
    @SubscribeEvent public static void hand(BeforeRenderHandEvent event) { held(Minecraft.m_91087_().f_91074_); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void player(RenderPlayerEvent.Pre event) { held(event.getEntity()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void living(RenderLivingEvent.Pre<?, ?> event) { held(event.getEntity()); }
    @SubscribeEvent public static void screen(ScreenEvent.Render.Pre event) { held(Minecraft.m_91087_().f_91074_); }
    private static float seconds() {
        var mc = Minecraft.m_91087_();
        return mc.f_91073_ == null ? 0 : (mc.f_91073_.m_46467_() + mc.m_91296_()) / 20f;
    }

    /** The original skill avatar uses asjj_p3 rather than the native asjj_p1 mesh. */
    public static void drawAvatar(Matrix4f matrix, ItemStack stack, float[] weaponPose, MultiBufferSource buffers, int overlay, float seconds) {
        Data data = active; int variant = variant(stack);
        if (data == null || variant < 0 || !avatarMatrix(data, weaponPose, matrix, AVATAR)) return;
        render(data, variant, AVATAR, true, buffers, overlay, seconds);
    }

    static boolean avatarMatrix(Data data, float[] pose, Matrix4f parent, Matrix4f output) {
        int[] ids = data.referenceIndices;
        for (int id : ids) if (id < 0 || id * 3 + 2 >= pose.length) return false;
        Vector3f p0 = data.basis[0].set(pose[ids[0]*3],pose[ids[0]*3+1],pose[ids[0]*3+2]);
        Vector3f px = data.basis[1].set(pose[ids[1]*3],pose[ids[1]*3+1],pose[ids[1]*3+2]).sub(p0);
        if (px.lengthSquared() < 1e-10f) return false;
        px.normalize();
        Vector3f pz = data.basis[2].set(pose[ids[2]*3],pose[ids[2]*3+1],pose[ids[2]*3+2]).sub(p0).cross(px).negate();
        if (pz.lengthSquared() < 1e-10f) return false;
        pz.normalize(); Vector3f py = data.basis[3].set(pz).cross(px);
        data.targetFrame.identity().m00(px.x).m01(px.y).m02(px.z).m10(py.x).m11(py.y).m12(py.z).m20(pz.x).m21(pz.y).m22(pz.z).m30(p0.x).m31(p0.y).m32(p0.z);
        output.set(parent).mul(data.targetFrame).mul(data.inverseReference); return true;
    }

    private static void referenceFrame(Data data) {
        float[] s = data.reference;
        Vector3f p0 = new Vector3f(s[0],s[1],s[2]);
        Vector3f x = new Vector3f(s[3],s[4],s[5]).sub(p0).normalize();
        Vector3f z = new Vector3f(x).cross(new Vector3f(s[6],s[7],s[8]).sub(p0)).normalize();
        Vector3f y = new Vector3f(z).cross(x);
        data.inverseReference.identity().m00(x.x).m01(x.y).m02(x.z).m10(y.x).m11(y.y).m12(y.z).m20(z.x).m21(z.y).m22(z.z).m30(p0.x).m31(p0.y).m32(p0.z).invert();
    }

    private static Matrix4f affine(Matrix4f output, float[] values, int offset) {
        return output.set(values[offset],values[offset+4],values[offset+8],0,
            values[offset+1],values[offset+5],values[offset+9],0,
            values[offset+2],values[offset+6],values[offset+10],0,
            values[offset+3],values[offset+7],values[offset+11],1);
    }

    static void emit(Data data, Layer[] layers, Matrix4f pose, boolean avatar, float seconds, VertexSink sink) {
        if (!Float.isFinite(seconds)) return;
        float[] state = data.state;
        for (Layer layer : layers) {
            data.mapped.set(pose).mul(affine(data.particle, avatar ? layer.avatarMap : layer.nativeMap, 0));
            for (int p = 0; p < layer.particles; p++) {
                double age = seconds / layer.life[p*2] + layer.life[p*2+1]; age -= Math.floor(age);
                float phase = (float)age * 63; int a = Math.min(62, (int)phase), b = a+1; float t = phase-a;
                int oa = (p*64+a)*20, ob = (p*64+b)*20;
                for (int j = 0; j < 20; j++) state[j] = layer.states[oa+j]*(1-t)+layer.states[ob+j]*t;
                if (state[15] <= .001f) continue;
                data.combined.set(data.mapped).mul(affine(data.particle, state, 0));
                float scaleX = 1, scaleY = 1, c = 1, s = 0;
                if (layer.billboard != 4) {
                    scaleX = (float)Math.sqrt(data.combined.m00()*data.combined.m00()+data.combined.m01()*data.combined.m01()+data.combined.m02()*data.combined.m02());
                    scaleY = (float)Math.sqrt(data.combined.m10()*data.combined.m10()+data.combined.m11()*data.combined.m11()+data.combined.m12()*data.combined.m12());
                    float angle = (float)Math.atan2(state[4], state[0]); c = (float)Math.cos(angle); s = (float)Math.sin(angle);
                }
                // The pass disables culling: emit each source face only once.
                for (int quad = 0; quad < layer.vertices; quad += 4) for (int k = 0; k < 4; k++) {
                    int index = (quad+k)*5;
                    float x = layer.geometry[index], y = layer.geometry[index+1], z = layer.geometry[index+2];
                    if (layer.billboard == 4) data.combined.transformPosition(data.point.set(x,y,z));
                    else data.point.set(data.combined.m30()+x*scaleX*c-y*scaleY*s, data.combined.m31()+x*scaleX*s+y*scaleY*c, data.combined.m32());
                    float u = layer.geometry[index+3]*state[18]+state[16], v = 1-(layer.geometry[index+4]*state[19]+state[17]);
                    sink.vertex(data.point.x,data.point.y,data.point.z,u,v,state[12],state[13],state[14],state[15]);
                }
            }
        }
    }

    private static void render(Data data, int variant, Matrix4f matrix, boolean avatar, MultiBufferSource buffers, int overlay, float seconds) {
        if (data == null || data.types == null || variant < 0 || variant >= 12) return;
        // A functional model child runs while TaCZ may still be filling its own
        // BufferBuilder. Switching the shared BufferSource ends that builder and
        // invalidates its retained VertexConsumer. Never borrow that source.
        DarkBladePass.draw(data, variant, matrix, avatar, overlay, seconds);
    }
    static MultiBufferSource.BufferSource newIsolatedBuffers() {
        return MultiBufferSource.m_109898_(new com.mojang.blaze3d.vertex.BufferBuilder(262144));
    }
    static void submit(Data data, int variant, Matrix4f matrix, boolean avatar,
                       MultiBufferSource.BufferSource source, int overlay, float seconds) {
        Layer[][] groups = data.groups[variant];
        for (int texture = 0; texture < groups.length; texture++) {
            if (groups[texture].length == 0) continue;
            for (int mode = 0; mode < 2; mode++) {
                RenderType type = data.types[texture*2+mode];
                VertexConsumer consumer = null;
                for (Layer layer : groups[texture]) {
                    if ((layer.blend == 1 ? 0 : 1) != mode) continue;
                    if (consumer == null) consumer = source.m_6299_(type);
                    data.sink.consumer = consumer; data.sink.overlay = overlay;
                    data.single[0] = layer;
                    emit(data, data.single, matrix, avatar, seconds, data.sink);
                }
                if (consumer != null) source.m_109912_(type);
            }
        }
    }

    private static final class DrawingSink implements VertexSink {
        VertexConsumer consumer; int overlay;
        @Override public void vertex(float x,float y,float z,float u,float v,float r,float g,float b,float a) {
            consumer.m_5483_(x,y,z).m_85950_(r,g,b,a).m_7421_(u,v).m_86008_(overlay).m_85969_(15728880).m_5601_(0,1,0).m_5752_();
        }
    }
    private DarkBladeEffects() {}
}
