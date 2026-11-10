package cn.judgfire;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.input.ZoomKey;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Original weapon UI; preserves its circular aperture at every window aspect. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID, value = Dist.CLIENT)
public final class ScopeOverlay {
  private static final ResourceLocation SCOPE =
      new ResourceLocation("judgfire", "textures/fx/fireofjustice2_scope.png");

  @SubscribeEvent
  public static void render(RenderGuiEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson())
      return;
    var stack = mc.player.getMainHandItem();
    if (!JudgmentFlame.ours(stack)) return;
    float progress =
        IClientPlayerGunOperator.fromLocalPlayer(mc.player)
            .getClientAimingProgress(event.getPartialTick());
    if (progress <= .8f) return;
    float alpha = Math.min(1, (progress - .8f) * 5);
    int width = event.getWindow().getGuiScaledWidth(),
        height = event.getWindow().getGuiScaledHeight();
    int imageWidth = Math.round(height * 960f / 540), x = (width - imageWidth) / 2;
    var graphics = event.getGuiGraphics();
    graphics.pose().pushPose();
    graphics.pose().translate(0, 0, 400);
    int shade = Math.round(alpha * 255) << 24;
    if (x > 0) {
      graphics.fill(0, 0, x, height, shade);
      graphics.fill(x + imageWidth, 0, width, height, shade);
    }
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.setShaderColor(1, 1, 1, alpha);
    graphics.blit(SCOPE, x, 0, imageWidth, height, 0f, 0f, 960, 540, 960, 540);
    RenderSystem.setShaderColor(1, 1, 1, 1);
    var gun = IGun.getIGunOrNull(stack);
    String label = Math.round(gun.getAimingZoom(stack)) + "×";
    if (!DefaultAssets.isEmptyAttachmentId(gun.getAttachmentId(stack, AttachmentType.SCOPE)))
      label += "  [" + ZoomKey.ZOOM_KEY.getTranslatedKeyMessage().getString() + "]";
    graphics.drawString(
        mc.font,
        label,
        width / 2 - mc.font.width(label) / 2,
        height - 32,
        ((int) (alpha * 255) << 24) | 0xFFD6B0,
        false);
    graphics.pose().popPose();
  }
}
