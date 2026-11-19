package cn.ssjjares.client;

import cn.ssjjares.AresArmorItems;
import cn.ssjjares.AresRage;
import cn.ssjjares.AresRageNetwork.Boundary;
import cn.ssjjdimension.client.DimensionMarkRenderType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", value = Dist.CLIENT)
public final class AresRageBoundary {
    private static final int BAND_HEIGHT = 24;
    private static final float RADIUS = (float)AresRage.RADIUS;
    private static final int OWN_COLOR = 0x81ceff;
    private static final int OTHER_COLOR = 0xff514e;
    private static final Map<UUID, Wall> WALLS = new HashMap<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(65536));
    private static ClientLevel level;

    private AresRageBoundary() {}

    private static final class Layers {
        static final RenderType WALL = DimensionMarkRenderType.of(AresArmorItems.id("textures/effects/ares/field.png"), false);
    }

    private record Wall(Boundary message, Vec3 previous, Vec3 target, long received, long until) {
        Vec3 center(ClientLevel world, float partialTick) {
            if (world.getEntity(message.id()) instanceof Player owner && owner.getUUID().equals(message.owner()))
                return owner.getPosition(partialTick);
            double progress = Math.min(1, (world.getGameTime() + partialTick - received) / 5);
            return progress >= 1 ? target : previous.lerp(target, progress);
        }
    }

    private static void checkLevel(Minecraft client) {
        if (level != client.level) {
            level = client.level;
            WALLS.clear();
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (level != null && event.getLevel() == level) {
            WALLS.clear();
            level = null;
        }
    }

    public static void receive(Boundary message) {
        checkLevel(Minecraft.getInstance());
        if (level == null || !level.dimension().location().equals(message.dimension())) return;
        if (message.visibleTicks() == 0) {
            WALLS.remove(message.owner());
            return;
        }
        var target = new Vec3(message.x(), 0, message.z());
        var old = WALLS.get(message.owner());
        var previous = old == null ? target : old.center(level, 0);
        long now = level.getGameTime();
        WALLS.put(message.owner(), new Wall(message, previous, target, now, now + message.visibleTicks()));
    }

    static int color(UUID viewer, UUID owner) {
        return viewer.equals(owner) ? OWN_COLOR : OTHER_COLOR;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var client = Minecraft.getInstance();
        checkLevel(client);
        if (level == null || client.player == null || WALLS.isEmpty()) return;
        long now = level.getGameTime();
        WALLS.values().removeIf(wall -> now >= wall.until);
        var mesh = AresEffectMesh.wall;
        if (mesh == null || WALLS.isEmpty()) return;
        VertexConsumer out = null;
        var eye = event.getCamera().getPosition();
        var frustum = event.getFrustum();
        var pose = event.getPoseStack();
        int band = (int)Math.floor(eye.y / BAND_HEIGHT);
        for (var wall : WALLS.values()) {
            var center = wall.center(level, event.getPartialTick());
            int rgb = color(client.player.getUUID(), wall.message.owner());
            for (int offset = -6; offset <= 6; offset++) {
                int height = (band + offset) * BAND_HEIGHT;
                if (!mesh.visible(frustum, center.x, height, center.z, RADIUS, BAND_HEIGHT)) continue;
                if (out == null) out = BUFFERS.getBuffer(Layers.WALL);
                float alpha = Math.abs(offset) == 6 ? 0.19f : 0.39f;
                pose.pushPose();
                pose.translate(center.x - eye.x, height - eye.y, center.z - eye.z);
                pose.scale(RADIUS, BAND_HEIGHT, RADIUS);
                mesh.draw(pose.last(), out, rgb, alpha);
                pose.popPose();
            }
        }
        if (out != null) BUFFERS.endBatch(Layers.WALL);
    }
}
