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

/** Original Qinglong S1 projectile / S2 mark meshes, rendered cosmetically. */
public final class QinglongVisuals {
  public static final ResourceLocation DRAGON = tex("q_dragon"),
      FIRE = tex("q_crown_fire"),
      RING = tex("q_crown_ring"),
      TRAIL = tex("q_crown_trail_t285"),
      CROWN = tex("q_crown_t166"),
      IMPACT = tex("q_impact"),
      OUTER = tex("q_laser_t212_outer"),
      CORE = tex("q_laser_t212_core"),
      WAVE = tex("q_burst_wave");

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
    try (var input = manager.open(new ResourceLocation("judgfire", "fx/qinglong_models.json"));
        var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      models = new Gson().fromJson(reader, Models.class);
    } catch (Exception e) {
      models = null;
      LogUtils.getLogger().error("Cannot load Qinglong effect meshes", e);
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
}
