package ssjj.dark;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.shaders.Uniform;
import java.lang.reflect.Method;
import java.util.ArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** 服务端时钟驱动镜头与原粒子。场景遮罩在特效下层，仅脚底外圈接受人物深度遮挡。 */
@Mod.EventBusSubscriber(modid = DarkCrystalMod.ID, value = Dist.CLIENT)
public final class DarkPresentation {
    private static final VertexFormat FORMAT = new VertexFormat(ImmutableMap.of("Position", DefaultVertexFormat.f_85804_, "UV0", DefaultVertexFormat.f_85806_, "Color", DefaultVertexFormat.f_85805_));
    private static final VertexFormat BLACK = new VertexFormat(ImmutableMap.of("Position", DefaultVertexFormat.f_85804_, "Color", DefaultVertexFormat.f_85805_));
    private static ShaderInstance shader, dimShader;
    private static Object level;
    private static CameraType savedView;
    private static long cameraStart = Long.MIN_VALUE;
    private static boolean owned;
    private static TextureTarget terrainDepth, sceneDepth;
    private static boolean terrainReady;
    private static Method setCameraPosition;
    private record Material(DarkRuntimeAssets.Material source, ResourceLocation[] textures) {}
    private static DarkRuntimeAssets.Data materialData;
    private static Material[] materials;
    private static ShaderInstance uniformShader;
    private static Uniform[] uniforms;
    private static final String[] UNIFORM_NAMES = { "MainST", "MaskST", "NoiseST", "Scroll", "NoiseScroll",
        "Tiles", "Cell", "Clock", "Brightness", "Alpha", "Contrast", "Warp", "Masked" };
    private static final ArrayList<Player> VIEWS = new ArrayList<>();

    public record View(DarkVisualState.Cast cast, float elapsed, float chargeSeconds, float sourceSeconds, float recovery, float particleClock) {}

    static View view(Player player, float partial) {
        return visual(player, partial, false);
    }

    private static View ringView(Player player, float partial) {
        return visual(player, partial, true);
    }

    private static View visual(Player player, float partial, boolean ring) {
        if (player == null || !player.m_6084_() || player.m_5833_() || DarkSkills.grade(player.m_21205_()) != 2) return null;
        var cast = DarkVisualState.CASTS.get(player.m_20148_());
        if (cast == null || cast.entityId() != player.m_19879_() || cast.stopped()) return null;
        float elapsed = ((player.m_9236_().m_46467_() - cast.startTick()) + partial) / 20f;
        float charge = Math.max(.05f, cast.chargeTicks() / 20f);
        if (elapsed < 0 || (ring ? !cast.ringActive(player.m_9236_().m_46467_(), partial) : elapsed > charge + 1.2f)) return null;
        float source = Math.min(3, elapsed / charge * 3);
        float recovery = smooth((elapsed - charge - .35f) / .55f);
        float clock = elapsed <= charge ? elapsed / charge * 2.3f : 2.3f + elapsed - charge;
        return new View(cast, elapsed, charge, source, recovery, clock);
    }

    @Mod.EventBusSubscriber(modid = DarkCrystalMod.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DarkCrystalMod.ID, "native_skill"), FORMAT), next -> shader = next);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DarkCrystalMod.ID, "skill_dimming"), BLACK), next -> dimShader = next);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.m_91087_();
        if (level != mc.f_91073_) {
            restore(mc); DarkVisualState.CASTS.clear(); level = mc.f_91073_; cameraStart = Long.MIN_VALUE;
            releaseDepth();
            invalidateRuntimeCaches();
            return;
        }
        // 旁观者自身没有施法状态也必须清理过期记录，否则会永久保留深度拷贝开销。
        if (mc.f_91073_ != null) {
            long now = mc.f_91073_.m_46467_();
            DarkVisualState.CASTS.values().removeIf(c -> now >= c.expiresAt() || now < c.startTick() - 20);
        }
        View view = view(mc.f_91074_, 0);
        if (view == null) { restore(mc); return; }
        if (cameraStart != view.cast().startTick()) {
            restore(mc); cameraStart = view.cast().startTick(); savedView = mc.f_91066_.m_92176_();
            owned = savedView == CameraType.FIRST_PERSON;
            if (owned) mc.f_91066_.m_92157_(CameraType.THIRD_PERSON_BACK);
        }
        if (owned && mc.f_91066_.m_92176_() != CameraType.THIRD_PERSON_BACK) owned = false;
        if (view.elapsed() > view.chargeSeconds() + .95f) restore(mc);
    }

    private static void restore(Minecraft mc) {
        if (owned && mc.f_91066_.m_92176_() == CameraType.THIRD_PERSON_BACK) mc.f_91066_.m_92157_(savedView);
        owned = false; savedView = null;
    }

    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.m_91087_();
        View view = view(mc.f_91074_, (float) event.getPartialTick());
        if (view == null || !owned) return;
        float back = 4.2f * smooth(view.elapsed() / .5f) * (1 - smooth((view.elapsed() - view.chargeSeconds() - .45f) / .5f));
        float yaw = view.cast().yaw(), pitch = event.getPitch();
        event.setYaw(yaw);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 eye = mc.f_91074_.m_20299_((float) event.getPartialTick());
        Vec3 forward = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        // m_82546_ 是减法：后方 = 眼点 - 前向 * 距离，不能再对距离取负。
        Vec3 desired = DarkCameraGeometry.behind(eye, forward, back);
        var hit = mc.f_91073_.m_45547_(new ClipContext(eye, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.f_91074_));
        if (hit.m_6662_() != HitResult.Type.MISS) desired = DarkCameraGeometry.towardEye(hit.m_82450_(), forward, .12);
        try {
            if (setCameraPosition == null) {
                setCameraPosition = Camera.class.getDeclaredMethod("m_90581_", Vec3.class);
                setCameraPosition.setAccessible(true);
            }
            setCameraPosition.invoke(event.getCamera(), desired);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("黯水晶镜头入口与MC1.20.1不匹配", error); }
        float age = view.elapsed() - view.chargeSeconds();
        if (age >= 0 && age < .5f) event.setRoll(event.getRoll() + (float) Math.sin(age * 56) * 1.6f * (1 - age / .5f));
    }

    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
        var view = view(Minecraft.m_91087_().f_91074_, (float) event.getPartialTick());
        if (view == null) return;
        float age = view.elapsed() - view.chargeSeconds();
        if (age >= 0 && age < .8f) event.setFOV(event.getFOV() + 9 * Math.sin(Math.PI * age / .8f));
    }

    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null || shader == null || dimShader == null) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            terrainReady = false;
            // Allocate the two depth targets while the final-tier weapon is
            // equipped, before the first activation needs depth copies.
            if (mc.f_91074_ != null && DarkSkills.grade(mc.f_91074_.m_21205_()) == 2) ensureDepth(mc.m_91385_());
        }
        if (DarkVisualState.CASTS.isEmpty()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS && hasBurst(mc, event.getPartialTick(), event.getCamera())) {
            ensureDepth(mc.m_91385_()); terrainDepth.m_83945_(mc.m_91385_()); mc.m_91385_().m_83947_(false); terrainReady = true;
        }
        // 最后统一压暗已画完的场景，再画外圈和爆发；不让透明地形、天气覆盖遮罩。
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            mc.m_91385_().m_83947_(false);
            View local = view(mc.f_91074_, event.getPartialTick());
            if (local != null) {
                float alpha = .70f * smooth(local.elapsed() / .45f) * (1 - smooth((local.elapsed() - local.chargeSeconds() - .2f) / 1.15f));
                darken(event, alpha);
            }
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var views = VIEWS;
        views.clear();
        for (var cast : DarkVisualState.CASTS.values()) {
            var entity = mc.f_91073_.m_6815_(cast.entityId());
            if (entity instanceof Player player && player.m_20148_().equals(cast.playerId()) && (view(player, event.getPartialTick()) != null || ringView(player, event.getPartialTick()) != null)
                && player.m_20318_(event.getPartialTick()).m_82554_(event.getCamera().m_90583_()) < 96 * 96) views.add(player);
        }
        try {
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
            RenderSystem.enableBlend(); RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            for (Player player : views) draw(event, player, true);
            if (terrainReady) {
                sceneDepth.m_83945_(mc.m_91385_()); mc.m_91385_().m_83945_(terrainDepth); mc.m_91385_().m_83947_(false);
            }
            try { for (Player player : views) draw(event, player, false); }
            finally { if (terrainReady) { mc.m_91385_().m_83945_(sceneDepth); mc.m_91385_().m_83947_(false); } }
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableDepthTest(); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
            views.clear();
        }
    }

    private static boolean hasBurst(Minecraft mc, float partial, Camera camera) {
        for (var cast : DarkVisualState.CASTS.values()) {
            var entity = mc.f_91073_.m_6815_(cast.entityId());
            if (entity instanceof Player player && view(player, partial) != null
                && player.m_20318_(partial).m_82554_(camera.m_90583_()) < 96 * 96) return true;
        }
        return false;
    }

    private static void draw(RenderLevelStageEvent event, Player player, boolean ground) {
        View view = ground ? ringView(player, event.getPartialTick()) : view(player, event.getPartialTick());
        if (view == null) return;
        var data = DarkRuntimeAssets.get();
        prepareMaterials(data);
        float sampleClock = ground ? DarkVisualState.ringClock(view.particleClock()) : view.particleClock();
        int frame = Math.max(0, Math.min(data.particles.length - 1, (int) (sampleClock * 20)));
        Vec3 origin = player.m_20318_(event.getPartialTick()).m_82546_(event.getCamera().m_90583_());
        Matrix4f matrix = DarkCameraGeometry.worldToView(event.getCamera().m_90590_(), event.getCamera().m_90589_(), origin)
            .rotateY((float) Math.toRadians(-view.cast().yaw()));
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(matrix);
        RenderSystem.setShader(() -> shader);
        for (var batch : data.particles[frame]) {
            if (batch.ground() != ground) continue;
            Material prepared = materials[batch.material()];
            var material = prepared.source();
            for (int i = 0; i < 3; i++) {
                RenderSystem.setShaderTexture(i, prepared.textures()[i]);
            }
            vec(0, material.st()[0]); vec(1, material.st()[1]); vec(2, material.st()[2]);
            vec(3, material.scroll()); vec(4, material.noiseScroll()); vec(5, batch.tiles()); vec(6, batch.cell());
            scalar(7, view.particleClock()); scalar(8, material.brightness()); scalar(9, material.alpha());
            scalar(10, material.contrast()); scalar(11, material.warp()); scalar(12, material.masked() ? 1 : 0);
            DarkParticleMeshes.draw(batch, FORMAT, projection, shader);
        }
    }

    private static void prepareMaterials(DarkRuntimeAssets.Data data) {
        if (materialData != data) {
            Material[] next = new Material[data.preparedMaterials.length];
            for (int i = 0; i < next.length; i++) {
                var material = data.preparedMaterials[i];
                ResourceLocation[] textures = new ResourceLocation[3];
                for (int j = 0; j < 3; j++) textures[j] = new ResourceLocation(material.textures()[j]);
                next[i] = new Material(material, textures);
            }
            materials = next; materialData = data;
        }
        if (uniformShader != shader) {
            uniforms = new Uniform[UNIFORM_NAMES.length];
            for (int i = 0; i < uniforms.length; i++) uniforms[i] = shader.m_173348_(UNIFORM_NAMES[i]);
            uniformShader = shader;
        }
    }

    static void invalidateRuntimeCaches() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(DarkPresentation::invalidateRuntimeCaches); return; }
        materialData = null; materials = null; uniformShader = null; uniforms = null;
        DarkParticleMeshes.clear();
    }

    private static void darken(RenderLevelStageEvent event, float alpha) {
        if (alpha <= 0) return;
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        try {
            RenderSystem.setShader(() -> dimShader);
            // 顶点着色器直接输出裁剪空间，不依赖视角、近裁面或世界变换。
            Matrix4f matrix = new Matrix4f();
            BufferBuilder b = Tesselator.m_85913_().m_85915_(); b.m_166779_(VertexFormat.Mode.QUADS, BLACK);
            b.m_252986_(matrix, -1, -1, 0).m_85950_(0, 0, 0, alpha).m_5752_();
            b.m_252986_(matrix, 1, -1, 0).m_85950_(0, 0, 0, alpha).m_5752_();
            b.m_252986_(matrix, 1, 1, 0).m_85950_(0, 0, 0, alpha).m_5752_();
            b.m_252986_(matrix, -1, 1, 0).m_85950_(0, 0, 0, alpha).m_5752_();
            BufferUploader.m_231202_(b.m_231175_());
        } finally { RenderSystem.depthMask(true); RenderSystem.enableDepthTest(); RenderSystem.enableCull(); RenderSystem.disableBlend(); }
    }
    private static void vec(int index, float[] a) {
        var u = uniforms[index]; if (u == null) return;
        if (a.length == 2) u.m_7971_(a[0], a[1]); else if (a.length == 4) u.m_5805_(a[0], a[1], a[2], a[3]);
    }
    private static void scalar(int index, float value) { var u = uniforms[index]; if (u != null) u.m_5985_(value); }
    private static void ensureDepth(RenderTarget main) {
        if (terrainDepth != null && terrainDepth.f_83915_ == main.f_83915_ && terrainDepth.f_83916_ == main.f_83916_) return;
        releaseDepth(); terrainDepth = new TextureTarget(main.f_83915_, main.f_83916_, true, Minecraft.f_91002_); sceneDepth = new TextureTarget(main.f_83915_, main.f_83916_, true, Minecraft.f_91002_);
    }
    private static void releaseDepth() { if (terrainDepth != null) terrainDepth.m_83930_(); if (sceneDepth != null) sceneDepth.m_83930_(); terrainDepth = sceneDepth = null; terrainReady = false; }
    static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private DarkPresentation() {}
}
