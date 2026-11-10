package cn.judgfire;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** T038 ready / T239 cooldown HUD, anchored left of TaCZ's weapon icon. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID, value = Dist.CLIENT)
public final class BurstHud {
  private static final ResourceLocation
      READY = new ResourceLocation("judgfire", "textures/hud/burst_t038_r.png"),
      COOLDOWN = new ResourceLocation("judgfire", "textures/hud/burst_t239_r.png");
  private static final BurstHudState STATE = new BurstHudState();
  private static Object level;
  private static int playerId = Integer.MIN_VALUE;

  private static boolean context() {
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) {
      STATE.reset();
      level = null;
      playerId = Integer.MIN_VALUE;
      return false;
    }
    if (level != mc.level || playerId != mc.player.getId()) {
      STATE.reset();
      level = mc.level;
      playerId = mc.player.getId();
    }
    return true;
  }

  public static void state(JudgmentFlame.BurstState packet) {
    if (!context() || packet.shooter() != playerId) return;
    STATE.sync(packet.cooldown(), RedLotus.BURST_COOLDOWN.get(), packet.started());
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase == TickEvent.Phase.END && context() && !Minecraft.getInstance().isPaused())
      STATE.tick();
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void render(RenderGuiEvent.Post event) {
    if (!context()) return;
    var mc = Minecraft.getInstance();
    var player = mc.player;
    if (mc.options.hideGui
        || !RenderConfig.GUN_HUD_ENABLE.get()
        || !player.isAlive()
        || player.isSpectator()) return;
    var stack = player.getMainHandItem();
    if (!JudgmentFlame.burst(stack)) return;
    boolean ready = STATE.ready(BurstRuntime.loaded(stack), BurstStun.stunned(player));
    float progress = ready ? 1 : STATE.remaining() > 0 ? STATE.progress(event.getPartialTick()) : 0;
    var layout =
        BurstHudState.layout(
            event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    var g = event.getGuiGraphics();
    g.pose().pushPose();
    g.pose().translate(layout.x(), layout.y(), 500);
    g.pose().scale(layout.scale(), layout.scale(), 1);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.setShaderColor(1, 1, 1, 1);
    g.blit(SkinHud.burstIcon(WeaponSkins.skin(stack), ready), 0, 0, 0, 0, 210, 90, 210, 90);
    g.fill(92, 64, 167, 68, 0xB520242A);
    int fill = Math.round(75 * progress);
    if (fill > 0)
      g.fill(
          92, 64, 92 + fill, 68, ready ? SkinHud.readyColour(WeaponSkins.skin(stack)) : 0xFFB4BBC5);
    if (STATE.remaining() > 0)
      g.drawString(mc.font, ((STATE.remaining() + 19) / 20) + "s", 173, 61, 0xFFCDD2DA, false);
    g.pose().popPose();
    RenderSystem.setShaderColor(1, 1, 1, 1);
  }
}
