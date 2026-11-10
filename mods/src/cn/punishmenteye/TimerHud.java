package cn.punishmenteye;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Only displays authoritative remaining durations. Switching hides the bar without stopping time. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class TimerHud {
 private static SkillTimer.State state=new SkillTimer.State(0,0);
 private static net.minecraft.client.multiplayer.ClientLevel level;
 private static long received;
 private static boolean initialized;
 public static void accept(SkillTimer.State s){var mc=Minecraft.getInstance();if(mc.level==null||!s.valid())return;level=mc.level;state=s;received=level.getGameTime();initialized=true;}
 @SubscribeEvent public static void draw(RenderGuiEvent.Post e){
  var mc=Minecraft.getInstance();if(level!=mc.level){level=mc.level;state=new SkillTimer.State(0,0);received=level==null?0:level.getGameTime();initialized=false;}
  if(!initialized||mc.player==null||!SkillEligibility.eligible(mc.player.getMainHandItem())||!mc.player.isAlive()||mc.options.hideGui||mc.screen!=null)return;
  long elapsed=Math.max(0,level.getGameTime()-received);
  var remaining=TimerHudModel.remaining(state.active(),state.cooldown(),state.permanent()&&UltimateRequirement.installed(mc.player.getMainHandItem()),elapsed,state.duration(),state.cooldownTotal());
  RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
  for(var s:TimerHudModel.sprites(e.getWindow().getGuiScaledWidth(),e.getWindow().getGuiScaledHeight(),remaining.active(),remaining.cooldown(),state.duration(),state.cooldownTotal())){
   RenderSystem.setShaderColor(1,1,1,s.alpha());e.getGuiGraphics().blit(new ResourceLocation("eyeofpunishment","textures/gui/"+s.texture()+".png"),s.x(),s.y(),s.w(),s.h(),0,0,s.sourceWidth(),s.sourceHeight(),s.textureWidth(),s.textureHeight());
  }
  RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();
 }
}
