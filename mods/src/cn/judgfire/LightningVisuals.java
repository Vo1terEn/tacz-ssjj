package cn.judgfire;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Original Lightning S1 projectile / S2 mark meshes, rendered cosmetically. */
public final class LightningVisuals {
  public static final ResourceLocation DRAGON = tex("l_mark"),
      FIRE = tex("l_crown_fire"),
      RING = tex("l_crown_ring"),
      TRAIL = tex("l_crown_trail"),
      CROWN = tex("l_crown"),
      IMPACT = tex("l_impact"),
      OUTER = tex("l_laser_outer"),
      CORE = tex("l_laser_core"),
      WAVE = tex("l_wave");
  public static final ResourceLocation PROJECTILE = tex("l_projectile"), BULLET = tex("l_bullet");

  public static ResourceLocation choose(
      int skin,
      ResourceLocation original,
      ResourceLocation qinglong,
      ResourceLocation frozen,
      ResourceLocation lightning) {
    return skin == 3 ? lightning : skin == 2 ? frozen : skin == 1 ? qinglong : original;
  }

  private static ResourceLocation tex(String n) {
    return new ResourceLocation("judgfire", "textures/fx/" + n + ".png");
  }

  private static final class Mesh {
    float[][] vertices, uv;
    int[][] faces;
  }

  private static final class Models {
    Mesh projectile, mark;
  }

  private static volatile Models models;

  public static void reload(ResourceManager manager) {
    try (var input = manager.open(new ResourceLocation("judgfire", "fx/lightning_models.json"));
        var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      models = new Gson().fromJson(reader, Models.class);
    } catch (Exception e) {
      models = null;
      LogUtils.getLogger().error("Cannot load Lightning effect meshes", e);
    }
  }

  public static void dragon(
      PoseStack pose,
      Vec3 p,
      Quaternionf rotation,
      float size,
      float alpha,
      VertexConsumer out,
      boolean mark) {
    Models data = models;
    if (data == null) return;
    Mesh mesh = mark ? data.mark : data.projectile;
    pose.pushPose();
    pose.translate(p.x, p.y, p.z);
    pose.mulPose(rotation);
    pose.scale(size, size, size);
    var matrix = pose.last().pose();
    for (int[] face : mesh.faces)
      for (int side = 0; side < 2; side++)
        for (int j = 0; j < 4; j++) {
          int k = Math.min(j, 2);
          if (side == 1) k = 2 - k;
          int id = face[k];
          var v = mesh.vertices[id];
          var uv = mesh.uv[id];
          out.vertex(matrix, v[0], v[1], v[2])
              .color(1f, 1f, 1f, alpha)
              .uv(uv[0], uv[1])
              .overlayCoords(OverlayTexture.NO_OVERLAY)
              .uv2(15728880)
              .normal(0, 0, side == 0 ? 1 : -1)
              .endVertex();
        }
    pose.popPose();
  }

  /** The source mark lies in XY; lay it onto XZ, then orbit about world up. */
  static Quaternionf markRotation(double seconds) {
    return new Quaternionf()
        .rotationY((float) (seconds % (Math.PI * 2)))
        .rotateX((float) (Math.PI / 2));
  }

  static void bullet(
      PoseStack pose,
      net.minecraft.client.renderer.MultiBufferSource buffers,
      int frame,
      Quaternionf rotation) {
    pose.pushPose();
    pose.mulPose(rotation);
    var matrix = pose.last().pose();
    var out =
        buffers.getBuffer(
            net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(BULLET));
    float u = (frame % 4) / 4f, v = (frame / 4) / 4f;
    for (int i = 0; i < 4; i++) {
      float x = (i == 1 || i == 2 ? 1 : -1) * .32f, y = (i >= 2 ? 1 : -1) * .14f;
      out.vertex(matrix, x, y, 0)
          .color(1f, 1f, 1f, 1f)
          .uv(u + (i == 1 || i == 2 ? .25f : 0), v + (i >= 2 ? 0 : .25f))
          .overlayCoords(OverlayTexture.NO_OVERLAY)
          .uv2(15728880)
          .normal(0, 0, 1)
          .endVertex();
    }
    pose.popPose();
  }
}
