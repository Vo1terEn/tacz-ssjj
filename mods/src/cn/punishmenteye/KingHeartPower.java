package cn.punishmenteye;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One outgoing-damage multiplier shared by bullets, lightning, clouds and other player attacks. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID)
public final class KingHeartPower {
 @SubscribeEvent(priority=EventPriority.LOWEST)
 public static void damage(LivingHurtEvent event){
  if(event.isCanceled()||event.getAmount()<=0||event.getEntity().level().isClientSide())return;
  if(event.getSource().getEntity() instanceof ServerPlayer player&&event.getEntity()!=player&&SkillTimer.active(player))
   event.setAmount(UltimateRules.allDamage(event.getAmount(),true,UltimateRequirement.installed(player.getMainHandItem())));
 }
}
