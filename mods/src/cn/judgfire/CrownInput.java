package cn.judgfire;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Retired compatibility entry point; the current production JAR registers no crown key. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID, value = Dist.CLIENT)
public final class CrownInput {
  public static final KeyMapping KEY = null;

  @Mod.EventBusSubscriber(
      modid = JudgmentFlame.MODID,
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Keys {
    @SubscribeEvent
    public static void registerCrownKeys(RegisterKeyMappingsEvent event) {}
  }

  @SubscribeEvent
  public static void key(InputEvent.Key event) {}
}
