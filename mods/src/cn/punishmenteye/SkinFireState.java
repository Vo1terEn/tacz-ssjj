package cn.punishmenteye;

import java.util.*;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Actual TaCZ local and remote fire events start the skin's short source group. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID,value=Dist.CLIENT)
public final class SkinFireState {
 private record Shot(String skin,double start,double last){}
 private static final Map<UUID,Shot> SHOTS=new HashMap<>();
 private static net.minecraft.client.multiplayer.ClientLevel level;
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void fired(GunFireEvent event){
  if(event.isCanceled()||event.getLogicalSide()!=LogicalSide.CLIENT)return;
  var mc=Minecraft.getInstance();if(mc.level==null)return;
  var stack=event.getGunItemStack();var gun=IGun.getIGunOrNull(stack);if(gun==null)return;
  String key=SkinRules.key(gun.getGunId(stack),gun.getAttachmentId(stack,AttachmentType.SCOPE));if(key.isEmpty())return;
  if(level!=mc.level){SHOTS.clear();level=mc.level;}
  UUID who=event.getShooter().getUUID();double now=level.getGameTime()+mc.getFrameTime();Shot previous=SHOTS.get(who);
  // Keep source looping particles running during a burst; each successful shot
  // extends the project's short stop window instead of restarting delayed emitters.
  float window=SkinVisuals.fireDuration(key)*20;
  double start=previous!=null&&key.equals(previous.skin)&&now-previous.last<window?previous.start:now;
  SHOTS.put(who,new Shot(key,start,now));
 }
 public static float age(String skin,ItemDisplayContext context,float durationSeconds){
  var mc=Minecraft.getInstance();var entity=SkillVisualState.entityFor(context);
  if(level==null||level!=mc.level||entity==null||!entity.isAlive())return -1;
  Shot shot=SHOTS.get(entity.getUUID());if(shot==null||!skin.equals(shot.skin))return -1;
  double now=level.getGameTime()+mc.getFrameTime();float age=(float)(now-shot.start);
  return age>=0&&now-shot.last<durationSeconds*20?age:-1;
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  if(event.phase!=TickEvent.Phase.END)return;
  var mc=Minecraft.getInstance();if(level!=mc.level){SHOTS.clear();level=mc.level;}
  if(level!=null)SHOTS.entrySet().removeIf(x->level.getGameTime()-x.getValue().last>20);
 }
}
