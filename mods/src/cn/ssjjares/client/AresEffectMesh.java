package cn.ssjjares.client;

import cn.ssjjares.AresArmorItems;
import cn.ssjjcompat.client.FastEffectVertices;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AresEffectMesh {
    static volatile AresEffectMesh wall;
    private final float[] vertices;
    private final AABB bounds;

    private AresEffectMesh(float[] vertices) {
        this.vertices = vertices;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int i = 0; i < vertices.length; i += 5) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }
        bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)AresEffectMesh::reload);
    }

    private static void reload(ResourceManager manager) {
        wall = read(manager, "field");
    }

    private static AresEffectMesh read(ResourceManager manager, String name) {
        try (var input = new DataInputStream(manager.open(AresArmorItems.id("effects/ares/" + name + ".mesh")))) {
            if (input.readInt() != 0x41524553) throw new IOException("Invalid Ares mesh");
            int count = input.readInt();
            if (count <= 0 || count > 4096 || count % 3 != 0) throw new IOException("Invalid Ares triangle count");
            float[] vertices = new float[count * 5];
            for (int i = 0; i < vertices.length; i++) {
                vertices[i] = input.readFloat();
                if (!Float.isFinite(vertices[i])) throw new IOException("Invalid Ares vertex");
            }
            return new AresEffectMesh(vertices);
        } catch (IOException error) {
            LogUtils.getLogger().error("Unable to load Ares effect mesh {}", name, error);
            return null;
        }
    }

    boolean visible(Frustum frustum, double x, double y, double z, float radius, int height) {
        if (frustum == null) return true;
        return frustum.isVisible(new AABB(x + bounds.minX * radius, y + bounds.minY * height, z + bounds.minZ * radius,
                                          x + bounds.maxX * radius, y + bounds.maxY * height, z + bounds.maxZ * radius));
    }

    void draw(PoseStack.Pose pose, VertexConsumer out, int rgb, float alpha) {
        int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
        int opacity = Math.round(255 * alpha);
        var transform = pose.pose();
        var normals = pose.normal();
        for (int triangle = 0; triangle < vertices.length; triangle += 15) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int i = triangle + Math.min(vertex, 2) * 5;
                var next = FastEffectVertices.vertex(out, transform, vertices[i], vertices[i + 1], vertices[i + 2])
                    .color(red, green, blue, opacity).uv(vertices[i + 3], vertices[i + 4])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT);
                FastEffectVertices.normal(next, normals, 0, 1, 0).endVertex();
            }
        }
    }
}
