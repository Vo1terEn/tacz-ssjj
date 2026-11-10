package cn.judgfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** One closed current around the marked opponent's head, in world space. */
public final class LightningCurrentRing {
  public static final ResourceLocation
      ARC = new ResourceLocation("judgfire", "textures/fx/l_mark_current.png"),
      CORE = new ResourceLocation("judgfire", "textures/fx/l_mark_current_core.png");
  public static final int SEGMENTS = 96;
  private static final int FRAMES = 6, FRAME_RATE = 18;
  private static final double RADIUS = .50, ROTATION_SPEED = .8, VERTICAL_VARIATION = .018;
  private static final float ARC_WIDTH = .040f, CORE_WIDTH = .009f;

  static Vec3 point(double seconds, double progress) {
    double turn = progress * Math.PI * 2, phase = Math.floor(seconds * FRAME_RATE) * .71;
    double radius =
        RADIUS + .014 * Math.sin(turn * 13 - phase) + .012 * Math.sin(turn * 23 + phase * .6);
    double angle = turn + seconds * ROTATION_SPEED;
    return new Vec3(
        Math.cos(angle) * radius,
        VERTICAL_VARIATION * Math.sin(turn * 9 + phase),
        Math.sin(angle) * radius);
  }

  public static void render(
      PoseStack pose, Vec3 centre, double seconds, VertexConsumer out, boolean core) {
    pose.pushPose();
    pose.translate(centre.x, centre.y, centre.z);
    int frame = Math.floorMod((int) Math.floor(seconds * FRAME_RATE), FRAMES);
    float v0 = core ? .5f / 64 : (frame * 64 + .5f) / 384,
        v1 = core ? 63.5f / 64 : (frame * 64 + 63.5f) / 384;
    float width = core ? CORE_WIDTH : ARC_WIDTH, alpha = core ? .90f : .95f;
    for (int i = 0; i < SEGMENTS; i++) {
      double t0 = i / (double) SEGMENTS, t1 = (i + 1) / (double) SEGMENTS;
      Vec3 a = point(seconds, t0), b = point(seconds, t1);
      Vec3 radialA = new Vec3(a.x, 0, a.z).normalize().scale(width),
          radialB = new Vec3(b.x, 0, b.z).normalize().scale(width);
      quad(
          pose,
          out,
          a.subtract(radialA),
          b.subtract(radialB),
          b.add(radialB),
          a.add(radialA),
          (float) t0,
          (float) t1,
          v0,
          v1,
          alpha);
      Vec3 vertical = new Vec3(0, width * .8, 0);
      quad(
          pose,
          out,
          a.subtract(vertical),
          b.subtract(vertical),
          b.add(vertical),
          a.add(vertical),
          (float) t0,
          (float) t1,
          v0,
          v1,
          alpha);
    }
    pose.popPose();
  }

  private static void quad(
      PoseStack pose,
      VertexConsumer out,
      Vec3 a,
      Vec3 b,
      Vec3 c,
      Vec3 d,
      float u0,
      float u1,
      float v0,
      float v1,
      float alpha) {
    // Emit both windings without allocating position/UV arrays for every segment.
    vertex(pose, out, a, u0, v1, alpha, 1);
    vertex(pose, out, b, u1, v1, alpha, 1);
    vertex(pose, out, c, u1, v0, alpha, 1);
    vertex(pose, out, d, u0, v0, alpha, 1);
    vertex(pose, out, d, u0, v0, alpha, -1);
    vertex(pose, out, c, u1, v0, alpha, -1);
    vertex(pose, out, b, u1, v1, alpha, -1);
    vertex(pose, out, a, u0, v1, alpha, -1);
  }

  private static void vertex(
      PoseStack pose, VertexConsumer out, Vec3 p, float u, float v, float alpha, int normalY) {
    out.vertex(pose.last().pose(), (float) p.x, (float) p.y, (float) p.z)
        .color(1f, 1f, 1f, alpha)
        .uv(u, v)
        .overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(15728880)
        .normal(0, normalY, 0)
        .endVertex();
  }
}
