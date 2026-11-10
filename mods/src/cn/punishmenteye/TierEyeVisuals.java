package cn.punishmenteye;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Original tier texture and original tier particles share the native traversal pose. */
public final class TierEyeVisuals {
  private static final MultiBufferSource.BufferSource BUFFERS =
      MultiBufferSource.immediate(new BufferBuilder(4096));
  private static final Set<BedrockAnimatedModel> INSTALLED =
      Collections.newSetFromMap(new WeakHashMap<>());
  private TierEyeVisuals() {}

  public static void reset() {
    INSTALLED.clear();
    TierEyeEffects.reset();
  }

  public static void install() {
    TierEyeEffects.load();
    install("notdistinguish_base", "base");
    install("notdistinguish_tier1", "tier1");
  }

  private static void install(String path, String tier) {
    TimelessAPI.getClientGunIndex(new ResourceLocation("eyeofpunishment", path)).ifPresent(index -> {
      var model = index.getDefaultDisplay().getGunModel();
      if (WeaponVisuals.definition == null || INSTALLED.contains(model)) return;
      var texture = new ResourceLocation("eyeofpunishment", "textures/gun/uv/" + path + ".png");
      // "base" here only requests the common native final-root traversal node.
      // This callback never delegates to the second-stage SkinVisuals BASE sink.
      WeaponVisuals.installSkin(model, WeaponVisuals.definition, "base", new WeaponVisuals.SkinRenderSink() {
        @Override public void body(WeaponVisuals.Part mesh, PoseStack pose, VertexConsumer nativeOut,
            ItemDisplayContext context, int light, int overlay) {
          WeaponVisuals.render(mesh, pose, BUFFERS.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay);
        }
        @Override public void end(PoseStack receiver, PoseStack muzzle, ItemDisplayContext context,
            int light, int overlay) {
          BUFFERS.endBatch();
          if (receiver != null) TierEyeEffects.render(tier, receiver, muzzle, context, overlay);
        }
      });
      INSTALLED.add(model);
    });
  }
}
