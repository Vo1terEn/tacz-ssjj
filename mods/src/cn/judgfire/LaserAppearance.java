package cn.judgfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Shared visual widths only; collision, range and the pulse clock are independent. */
public final class LaserAppearance {
  public static final float WIDTH_SCALE = 1.6f;
  public static final float OUTER_WIDTH = .085f * WIDTH_SCALE, CORE_WIDTH = .028f * WIDTH_SCALE;
  public static final float PLASMA_WIDTH = .060f * WIDTH_SCALE;
  public static final ResourceLocation PLASMA =
      new ResourceLocation("judgfire", "textures/fx/l_laser_t055_plasma.png");

  public static void ribbon(
      VertexConsumer out, PoseStack pose, Vec3 a, Vec3 b, Vec3 camera, float width, float alpha) {
    Vec3 direction = b.subtract(a).normalize();
    Vec3 side = direction.cross(camera.subtract(a)).normalize();
    if (side.lengthSqr() < 1e-8) side = direction.cross(new Vec3(0, 1, 0)).normalize();
    if (side.lengthSqr() < 1e-8) side = new Vec3(1, 0, 0);
    Vec3 up = direction.cross(side).normalize();
    for (int i = 0; i < 3; i++) {
      double angle = i * Math.PI / 3;
      Vec3 radial = side.scale(Math.cos(angle) * width).add(up.scale(Math.sin(angle) * width));
      Matrix4f matrix = pose.last().pose();
      vertex(out, matrix, a.subtract(radial), 0, 1, alpha);
      vertex(out, matrix, b.subtract(radial), 1, 1, alpha);
      vertex(out, matrix, b.add(radial), 1, 0, alpha);
      vertex(out, matrix, a.add(radial), 0, 0, alpha);
    }
  }

  private static void vertex(
      VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, float alpha) {
    out.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
        .color(1f, 1f, 1f, alpha)
        .uv(u, v)
        .overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(15728880)
        .normal(0, 1, 0)
        .endVertex();
  }
}
