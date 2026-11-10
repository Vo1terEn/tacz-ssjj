package cn.judgfire;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID, value = Dist.CLIENT)
public final class CrownInput {
  public static final KeyMapping KEY =
      new KeyMapping(
          "key.judgfire.crown_toggle",
          KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_V,
          "key.categories.judgfire");

  @Mod.EventBusSubscriber(
      modid = JudgmentFlame.MODID,
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Keys {
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
      event.register(KEY);
    }
  }

  @SubscribeEvent
  public static void key(InputEvent.Key event) {
    var mc = Minecraft.getInstance();
    if (event.getAction() != GLFW.GLFW_PRESS
        || mc.screen != null
        || mc.player == null
        || !mc.isWindowActive()
        || mc.player.isSpectator()
        || !KEY.matches(event.getKey(), event.getScanCode())) return;
    var stack = mc.player.getMainHandItem();
    if (!JudgmentFlame.crown(stack) || BurstStun.stunned(mc.player)) return;
    var operator = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
    if (operator.isAim() || operator.getClientAimingProgress(1) > 0) return;
    var tag = stack.getTag();
    if (tag != null && tag.hasUUID(EmperorCrown.ID))
      JudgmentFlame.CHANNEL.sendToServer(
          new JudgmentFlame.CrownToggleRequest(tag.getUUID(EmperorCrown.ID)));
  }
}
