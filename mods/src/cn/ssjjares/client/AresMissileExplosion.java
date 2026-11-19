package cn.ssjjares.client;

import cn.ssjjares.AresArmorItems;
import cn.ssjjares.AresRageNetwork.Impact;
import cn.ssjjcompat.client.FastEffectVertices;
import cn.ssjjdimension.client.DimensionMarkRenderType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", value = Dist.CLIENT)
public final class AresMissileExplosion {
    private static final ArrayDeque<Burst> BURSTS = new ArrayDeque<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(8192));
    private static final int LIFETIME = 30;
    private static ClientLevel level;

    private AresMissileExplosion() {}

    private record Burst(Impact impact, long start) {}

    private enum Layer {
        RING("impact_ring", true), FLASH("impact_flash", true),
        FIRE("impact_fire", true), SMOKE("impact_smoke", false);

        final RenderType type;

        Layer(String texture, boolean additive) {
            type = DimensionMarkRenderType.of(AresArmorItems.id("textures/effects/ares/" + texture + ".png"), additive);
        }
    }

    private static void currentLevel() {
        var current = Minecraft.getInstance().level;
        if (current == level) return;
        level = current;
        BURSTS.clear();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (level != null && event.getLevel() == level) {
            BURSTS.clear();
            level = null;
        }
    }

    public static void receive(Impact message) {
        currentLevel();
        if (level == null || !level.dimension().location().equals(message.dimension())) return;
        if (BURSTS.size() == 48) BURSTS.removeFirst();
        BURSTS.addLast(new Burst(message, level.getGameTime()));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        currentLevel();
        if (level == null || BURSTS.isEmpty()) return;
        long time = level.getGameTime();
        BURSTS.removeIf(pulse -> time - pulse.start >= LIFETIME);
        var eye = event.getCamera().getPosition();
        var frustum = event.getFrustum();
        var pose = event.getPoseStack();
        for (var burst : BURSTS) {
            var point = burst.impact.point();
            float age = time - burst.start + event.getPartialTick();
            double distanceSquared = point.distanceToSqr(eye);
            if (age < 0 || distanceSquared > 144 * 144) continue;
            // The largest billboard is under six blocks across and drifts less than two blocks upward.
            if (frustum != null && !frustum.isVisible(new AABB(point.x - 5, point.y - 5, point.z - 5,
                                                               point.x + 5, point.y + 5, point.z + 5))) continue;
            boolean advanced = burst.impact.advanced();
            double x = point.x - eye.x;
            double y = point.y - eye.y;
            double z = point.z - eye.z;
            if (age < 14) {
                float p = age / 14;
                pose.pushPose();
                pose.translate(x, y + 0.04, z);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                draw(pose, Layer.RING, 0.9f + 4.7f * p, 0.72f * (1 - p),
                    advanced ? 0xffcc72 : 0xff753f, -1);
                pose.popPose();
            }
            float flash = age < 7 ? AresMissileRenderer.fade(distanceSquared, 64, 96) * (1 - age / 7) : 0;
            float smoke = age >= 3 ? AresMissileRenderer.fade(distanceSquared, 96, 128)
                * 0.78f * (1 - age / LIFETIME) : 0;
            if (age >= 20 && smoke <= 0) continue;
            pose.pushPose();
            pose.translate(x, y + (0.42 + age * 0.036), z);
            pose.mulPose(event.getCamera().rotation());
            if (flash > 0) draw(pose, Layer.FLASH, 2.5f + age * .19f, flash,
                advanced ? 0xffefa7 : 0xff9973, -1);
            if (age < 20) draw(pose, Layer.FIRE, 3.5f, 1, advanced ? 0xffefad : 0xffcf99,
                frame(age, 20));
            if (smoke > 0) draw(pose, Layer.SMOKE, 3.6f + age * .055f, smoke,
                0xb2adb0, frame(age - 3, LIFETIME - 3));
            pose.popPose();
        }
    }

    static int frame(float age, int duration) {
        return Math.max(0, Math.min(15, (int)(age * 16 / duration)));
    }

    private static void draw(PoseStack pose, Layer layer, float size, float alpha, int rgb, int frame) {
        var type = layer.type;
        var out = BUFFERS.getBuffer(type);
        // Insets keep adjacent flipbook tiles from bleeding when the source textures are filtered.
        float u = frame < 0 ? 0 : (frame % 4) * .25f + .004f;
        float v = frame < 0 ? 0 : (frame / 4) * .25f + .004f;
        float tile = frame < 0 ? 1 : .242f;
        float r = size / 2;
        vertex(pose.last(), out, -r, -r, u, v + tile, rgb, alpha);
        vertex(pose.last(), out, r, -r, u + tile, v + tile, rgb, alpha);
        vertex(pose.last(), out, r, r, u + tile, v, rgb, alpha);
        vertex(pose.last(), out, -r, r, u, v, rgb, alpha);
        BUFFERS.endBatch(type);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y,
                               float u, float v, int rgb, float alpha) {
        var vertex = FastEffectVertices.vertex(out, pose.pose(), x, y, 0)
            .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Math.round(alpha * 255))
            .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT);
        FastEffectVertices.normal(vertex, pose.normal(), 0, 0, 1).endVertex();
    }
}
