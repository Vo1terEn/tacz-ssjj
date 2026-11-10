package cn.judgfire;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Extracted geometry is an item preview, never an extra shell on the assembled gun. */
public final class AttachmentAppearance {
  private static final Set<BedrockAttachmentModel> INSTALLED =
      Collections.newSetFromMap(new WeakHashMap<>());
  private static final Field CURRENT_GUN;

  static {
    try {
      CURRENT_GUN = BedrockAttachmentModel.class.getDeclaredField("currentGunItem");
      CURRENT_GUN.setAccessible(true);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static void tick() {
    for (String id :
        new String[] {
          "flame_crown_scope",
          "qinglong_scope",
          "frozen_scope",
          "lightning_scope",
          "default_scope",
          "integrated_scope",
          "dragon_breath_muzzle",
          "red_lotus_grip",
          "emperor_crown_stock",
          "red_lotus_burst_mag",
          "energy_guard",
          "direct_attack",
          "judgment_fusion"
        })
      TimelessAPI.getClientAttachmentIndex(new ResourceLocation("judgfire", id))
          .ifPresent(
              index -> {
                install(index.getAttachmentModel());
                var lod = index.getLodModel();
                if (lod != null) install(lod.getLeft());
              });
  }

  private static void install(BedrockAttachmentModel model) {
    if (model == null || INSTALLED.contains(model)) return;
    model.setFunctionalRenderer(
        "item_extract",
        part -> {
          try {
            part.visible = CURRENT_GUN.get(model) == null;
          } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
          }
          return null;
        });
    // Optical stencil nodes belong to a mounted gun, not the standalone item.
    for (String name : new String[] {"ocular", "division"})
      if (model.getNode(name) != null) {
        model.getNode(name).visible = false;
        model.setFunctionalRenderer(
            name,
            part -> {
              try {
                // Native renderTempPart explicitly enables the optical node for its pass
                // and hides it afterwards. Never re-enable it in the ordinary root pass.
                boolean mounted = CURRENT_GUN.get(model) != null;
                if (!part.visible || !mounted) {
                  part.visible = false;
                  return null;
                }
                var mc = Minecraft.getInstance();
                part.visible =
                    mc != null
                        && mc.player != null
                        && IClientPlayerGunOperator.fromLocalPlayer(mc.player)
                                .getClientAimingProgress(mc.getFrameTime())
                            > .8f;
              } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
              }
              return null;
            });
      }
    INSTALLED.add(model);
  }

  public static void gun(BedrockGunModel model) {
    model.setFunctionalRenderer(
        "root",
        part ->
            (pose, out, context, light, overlay) -> {
              var mc = Minecraft.getInstance();
              // Zoom/NBT synchronization can replace ItemStack instances. The actual
              // render pass, not Java reference identity, determines whether this is the
              // local first-person gun. Gate every child, including the animated skin.
              boolean hidden =
                  hideInScope(
                      context,
                      mc != null && mc.player != null && mc.options.getCameraType().isFirstPerson(),
                      mc == null || mc.player == null
                          ? 0
                          : IClientPlayerGunOperator.fromLocalPlayer(mc.player)
                              .getClientAimingProgress(mc.getFrameTime()));
              OriginalMotion.prepare(model, context.firstPerson() && !hidden);
              if (!hidden)
                for (var child : part.children) child.render(pose, context, out, light, overlay);
            });
    // A built-in optical controller must not activate the pixel skin.
    for (String name : new String[] {"carry", "sight", "sight_folded", "mount"})
      model.setFunctionalRenderer(
          name,
          part -> {
            var stack = model.getCurrentGunItem();
            var gun = stack == null ? null : IGun.getIGunOrNull(stack);
            boolean pixel =
                gun != null
                    && new ResourceLocation("judgfire", "flame_crown_scope")
                        .equals(gun.getAttachmentId(stack, AttachmentType.SCOPE));
            part.visible =
                !OriginalMotion.replacing(model)
                    && ((name.equals("carry") || name.equals("sight")) ? !pixel : pixel);
            return null;
          });
  }

  public static boolean hideInScope(
      ItemDisplayContext context, boolean firstPersonCamera, float progress) {
    return context.firstPerson() && firstPersonCamera && progress > .8f;
  }
}
