package cn.judgfire;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** Keep the skill key from also running TaCZ melee/zoom for this weapon. */
public final class JudgmentSkillKey {
  public static boolean reserved(InputEvent.Key event) {
    var mc = Minecraft.getInstance();
    return mc.player != null && mc.screen == null && event.getAction() == GLFW.GLFW_PRESS
        && JudgmentFlame.burst(mc.player.getMainHandItem())
        && BurstClient.KEY.matches(event.getKey(), event.getScanCode());
  }
  private JudgmentSkillKey() {}
}
