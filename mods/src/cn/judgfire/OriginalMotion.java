package cn.judgfire;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.compat.oculus.OculusCompat;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

/** Runs original continuous skinning through TaCZ's existing texture/lighting pass. */
public final class OriginalMotion {
  private static final ResourceLocation
      DATA = new ResourceLocation("judgfire", "rig/original_skin.json"),
      PIXEL = new ResourceLocation("judgfire", "flame_crown_scope");
  private static volatile MotionSkin rig;
  private static volatile MotionSkin.Mesh qinglong, frozen, lightning;
  private static final ResourceLocation QINGLONG_DATA =
      new ResourceLocation("judgfire", "rig/qinglong_skin.json");
  private static final float[][] IDENTITY = identity();

  private static float[][] identity() {
    float[][] m = new float[11][12];
    for (var b : m) {
      b[0] = b[5] = b[10] = 1;
    }
    return m;
  }

  private static boolean frozen(BedrockGunModel model) {
    return model.getNode("frozen_skin_anchor") != null;
  }

  private static boolean lightning(BedrockGunModel model) {
    return model.getNode("lightning_skin_anchor") != null;
  }

  private static boolean custom(BedrockGunModel model) {
    return qinglong(model) || frozen(model) || lightning(model);
  }

  private static boolean qinglong(BedrockGunModel model) {
    return model.getNode("qinglong_skin_anchor") != null;
  }

  private static final Map<BedrockGunModel, Frame> FRAMES = new WeakHashMap<>();

  public record Frame(MotionSkin rig, float[][] matrices) {
    public float[] deform(float x, float y, float z) {
      var w = rig.weights(x, y, z);
      float[] result = new float[3];
      MotionSkin.deform(matrices, w.indices(), w.values(), x, y, z, result);
      return result;
    }
  }

  public static void reload(ResourceManager manager) {
    FRAMES.clear();
    BaseVideoMotion.reload(manager);
    try (var stream = manager.open(DATA);
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      MotionSkin next = new Gson().fromJson(reader, MotionSkin.class);
      next.validate();
      rig = next;
    } catch (Exception error) {
      rig = null;
      LogUtils.getLogger().error("Cannot load original weapon motion", error);
    }
    try (var stream = manager.open(QINGLONG_DATA);
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      qinglong = new Gson().fromJson(reader, MotionSkin.Mesh.class);
    } catch (Exception error) {
      qinglong = null;
      LogUtils.getLogger().error("Cannot load Qinglong skin", error);
    }
    try (var stream = manager.open(new ResourceLocation("judgfire", "rig/frozen_skin.json"));
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      frozen = new Gson().fromJson(reader, MotionSkin.Mesh.class);
    } catch (Exception error) {
      frozen = null;
      LogUtils.getLogger().error("Cannot load Frozen skin", error);
    }
    try (var stream = manager.open(new ResourceLocation("judgfire", "rig/lightning_skin.json"));
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      lightning = new Gson().fromJson(reader, MotionSkin.Mesh.class);
    } catch (Exception error) {
      lightning = null;
      LogUtils.getLogger().error("Cannot load Lightning skin", error);
    }
  }

  public static void install(BedrockGunModel model) {
    if (model.getNode("source_skin_anchor") == null) return;
    model.setFunctionalRenderer(
        "source_skin_anchor",
        part ->
            (pose, out, context, light, overlay) -> {
              Frame frame = FRAMES.get(model);
              if (frame == null || OculusCompat.isRenderShadow() && !custom(model)) return;
              if (custom(model)) {
                MotionSkin.Mesh mesh =
                    lightning(model) ? lightning : frozen(model) ? frozen : qinglong;
                if (mesh != null) render(mesh, frame.matrices, pose, out, light, overlay);
                return;
              }
              if (!context.firstPerson()) return;
              var stack = model.getCurrentGunItem();
              var gun = IGun.getIGunOrNull(stack);
              boolean pixel =
                  gun != null && PIXEL.equals(gun.getAttachmentId(stack, AttachmentType.SCOPE));
              render(frame, pixel, pose, out, light, overlay);
            });
  }

  public static Frame frame(BedrockGunModel model) {
    return FRAMES.get(model);
  }

  static void validate(BodyEffectBinding binding, int vertices) {
    MotionSkin data = rig;
    if (data == null) throw new IllegalStateException("Gun animation rig unavailable");
    binding.validate(vertices, data.smooth, data.bone_names.length);
  }

  public static boolean replacing(BedrockGunModel model) {
    return FRAMES.containsKey(model);
  }

  public static void prepare(BedrockGunModel model, boolean visible) {
    FRAMES.remove(model);
    MotionSkin data = BaseVideoMotion.select(model, rig);
    var clock = model.getNode("source_anim_clock");
    var mc = Minecraft.getInstance();
    if (data == null) return;
    if (BaseVideoMotion.uses(model)) FRAMES.put(model, new Frame(data, IDENTITY));
    if ((qinglong(model) && qinglong != null)
        || (frozen(model) && frozen != null)
        || (lightning(model) && lightning != null)) FRAMES.put(model, new Frame(data, IDENTITY));
    if (OculusCompat.isRenderShadow()) return;
    if (!visible
        || clock == null
        || mc.player == null
        || !mc.options.getCameraType().isFirstPerson()) return;
    // Native track blending scales every clock component together. Decode the
    // identifier and time after dividing out its weight, never guess an event time.
    float weight = clock.offsetZ;
    if (!Float.isFinite(weight) || weight < .0001f) return;
    int id = Math.round(clock.offsetX / weight);
    float time = clock.offsetY / weight;
    float[][] matrices = data.sample(id, time, weight);
    if (matrices != null) FRAMES.put(model, new Frame(data, matrices));
  }

  static void render(
      Frame frame, boolean pixel, PoseStack pose, VertexConsumer out, int light, int overlay) {
    render(pixel ? frame.rig.pixel : frame.rig.smooth, frame.matrices, pose, out, light, overlay);
  }

  static void render(
      MotionSkin.Mesh mesh,
      float[][] matrices,
      PoseStack pose,
      VertexConsumer out,
      int light,
      int overlay) {
    float[][] vertices = MotionSkin.deform(mesh, matrices);
    for (int i = 0; i < mesh.faces.length; i++) {
      int[] face = mesh.faces[i];
      float[][] uv = mesh.uv[i];
      float[] a = vertices[face[0]], b = vertices[face[1]], c = vertices[face[2]];
      Vector3f normal =
          new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2])
              .cross(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
      if (normal.lengthSquared() < 1e-12) continue;
      normal.normalize().mul(pose.last().normal());
      for (int side = 0; side < 2; side++)
        for (int j = 0; j < 4; j++) {
          int index = face.length == 3 ? Math.min(j, 2) : j;
          if (side == 1) index = face.length - 1 - index;
          float[] p = vertices[face[index]], tex = uv[index];
          float sign = side == 0 ? 1 : -1;
          out.vertex(pose.last().pose(), p[0] / 16, p[1] / 16, p[2] / 16)
              .color(1f, 1f, 1f, 1f)
              .uv(tex[0], tex[1])
              .overlayCoords(overlay)
              .uv2(light)
              .normal(normal.x * sign, normal.y * sign, normal.z * sign)
              .endVertex();
        }
    }
  }
}
