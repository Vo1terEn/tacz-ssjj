package cn.ssjjares.client;

import cn.ssjjares.AresArmorItems;
import cn.ssjjares.AresCannons;
import cn.ssjjares.AresMissile;
import cn.ssjjcompat.client.FastEffectVertices;
import cn.ssjjdimension.client.DimensionMarkRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AresMissileRenderer extends EntityRenderer<AresMissile> {
    private static final ResourceLocation[] TEXTURES = {
        AresArmorItems.id("textures/effects/ares/missile_flame_red.png"),
        AresArmorItems.id("textures/effects/ares/missile_flame_gold.png")
    };
    private static final ResourceLocation HALO = AresArmorItems.id("textures/effects/ares/missile_flame_halo.png");
    private static final ResourceLocation SMOKE = AresArmorItems.id("textures/effects/ares/missile_flame_smoke.png");
    private static final ResourceLocation SPARK = AresArmorItems.id("textures/effects/ares/missile_flame_spark.png");
    private static final double MIN_PROJECTED_AXIS = .33;

    private static final class Layers {
        static final RenderType RED = DimensionMarkRenderType.of(TEXTURES[0], false);
        static final RenderType GOLD = DimensionMarkRenderType.of(TEXTURES[1], false);
        static final RenderType GLOW = DimensionMarkRenderType.of(HALO, true);
        static final RenderType WISP = DimensionMarkRenderType.of(SMOKE, true);
        static final RenderType EMBER = DimensionMarkRenderType.of(SPARK, true);
    }

    record Ribbon(Vec3 axis, Vec3 side) {}

    public AresMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AresCannons.missileType(), AresMissileRenderer::new);
    }

    @Override
    public void render(AresMissile missile, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float pitch = Mth.rotLerp(partialTick, missile.xRotO, missile.getXRot());
        var direction = Vec3.directionFromRotation(pitch, Mth.rotLerp(partialTick, missile.yRotO, missile.getYRot()));
        var camera = entityRenderDispatcher.camera;
        var up = camera.getUpVector();
        var eye = camera.getPosition().subtract(missile.getPosition(partialTick));
        var ribbon = ribbon(direction, eye, new Vec3(up.x(), up.y(), up.z()));
        double distanceSquared = eye.lengthSqr();
        float time = missile.tickCount + partialTick + (missile.getId() & 7) * .71f;
        float pulse = 1 + .055f * Mth.sin(time * .41f);
        int frame = plumeFrame(time);
        boolean advanced = missile.advanced();
        int glow = advanced ? 0xff9e24 : 0xff260e;
        var transform = pose.last();
        float smoke = fade(distanceSquared, 56, 80);
        if (smoke > 0) drawWisps(transform, buffers.getBuffer(Layers.WISP), ribbon, time, glow, smoke);
        draw(transform, buffers.getBuffer(Layers.GLOW), ribbon, .57 * pulse, -2.15, .46,
            0, glow, 116, frame, 4, 2);
        draw(transform, buffers.getBuffer(advanced ? Layers.GOLD : Layers.RED), ribbon, .41 * pulse,
            -2.15, .46, 0, 0xffffff, 255, frame, 4, 2);
        float sparks = fade(distanceSquared, 48, 64);
        if (sparks > 0) drawSparks(transform, buffers.getBuffer(Layers.EMBER), ribbon, time,
            advanced ? 0xffd378 : 0xff6c33, sparks);
    }

    static float fade(double distanceSquared, int start, int end) {
        int endSquared = end * end;
        return Mth.clamp((float)((endSquared - distanceSquared) / (endSquared - start * start)), 0, 1);
    }

    static int plumeFrame(float time) {
        return Mth.floor(time * .8f) & 7;
    }

    static float trailPhase(float time, int index, float rate) {
        float progress = time * rate + index / 3f;
        return progress - Mth.floor(progress);
    }

    private static void drawWisps(PoseStack.Pose pose, VertexConsumer out, Ribbon ribbon, float time,
                                  int color, float opacity) {
        for (int i = 0; i < 3; i++) {
            float phase = trailPhase(time, i, .095f);
            double center = -.17 - 2.12 * phase;
            double sway = Mth.sin(phase * 7 + i * 2.4f) * (.025 + phase * .18);
            double stretch = .32 + .35 * phase;
            int alpha = Math.round(114 * Mth.sin(Mth.PI * phase) * (1 - phase) * opacity);
            draw(pose, out, ribbon, .16 + .13 * phase, center - stretch, center + stretch, sway,
                color, alpha, Math.min(35, Mth.floor(phase * 36)), 6, 6);
        }
    }

    private static void drawSparks(PoseStack.Pose pose, VertexConsumer out, Ribbon ribbon, float time,
                                   int color, float opacity) {
        for (int i = 0; i < 3; i++) {
            float phase = trailPhase(time + i * .43f, i, .15f);
            double center = -.14 - 2.46 * phase;
            double sway = Mth.sin(i * 3.3f + phase * 4) * (.04 + phase * .21);
            int alpha = Math.round(178 * Mth.sin(Mth.PI * phase) * (1 - phase) * opacity);
            draw(pose, out, ribbon, .064, center - .13, center + .13, sway, color, alpha, 0, 1, 1);
        }
    }

    static Ribbon ribbon(Vec3 direction, Vec3 eye, Vec3 cameraUp) {
        var axis = direction.normalize();
        var view = eye.lengthSqr() > 1.0e-6 ? eye.normalize() : new Vec3(0, 0, 1);
        double along = axis.dot(view);
        var projected = axis.subtract(view.scale(along));
        // Keep a narrow teardrop across the screen even when viewed nose- or tail-on.
        if (projected.lengthSqr() < MIN_PROJECTED_AXIS * MIN_PROJECTED_AXIS) {
            if (projected.lengthSqr() < 1.0e-6) {
                projected = cameraUp.subtract(view.scale(cameraUp.dot(view)));
                if (projected.lengthSqr() < 1.0e-6)
                    projected = view.cross(Math.abs(view.x) < .8 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
                if (axis.y < 0) projected = projected.reverse();
            }
            axis = projected.normalize().scale(MIN_PROJECTED_AXIS)
                .add(view.scale(Math.copySign(Math.sqrt(1 - MIN_PROJECTED_AXIS * MIN_PROJECTED_AXIS), along)));
        }
        return new Ribbon(axis, axis.cross(view).normalize());
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer out, Ribbon ribbon, double width,
                             double back, double front, double lateral, int rgb, int alpha,
                             int frame, int columns, int rows) {
        if (alpha == 0) return;
        float u = (float)(frame % columns) / columns;
        float v = (float)(frame / columns) / rows;
        float right = u + 1f / columns - .002f;
        float bottom = v + 1f / rows - .002f;
        u += .002f;
        v += .002f;
        vertex(pose, out, ribbon, back, lateral, width, u, v, rgb, alpha);
        vertex(pose, out, ribbon, front, lateral, width, right, v, rgb, alpha);
        vertex(pose, out, ribbon, front, lateral, -width, right, bottom, rgb, alpha);
        vertex(pose, out, ribbon, back, lateral, -width, u, bottom, rgb, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Ribbon ribbon, double distance,
                               double lateral, double width, float u, float v, int rgb, int alpha) {
        var axis = ribbon.axis();
        var side = ribbon.side();
        float x = (float)(axis.x * distance + side.x * lateral + side.x * width);
        float y = (float)(axis.y * distance + side.y * lateral + side.y * width);
        float z = (float)(axis.z * distance + side.z * lateral + side.z * width);
        var vertex = FastEffectVertices.vertex(out, pose.pose(), x, y, z)
            .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha).uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT);
        FastEffectVertices.normal(vertex, pose.normal(), 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(AresMissile missile) {
        return TEXTURES[missile.advanced() ? 1 : 0];
    }
}
