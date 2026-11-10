package cn.punishmenteye;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
/** Tracking-entity state drives held-weapon glow for local and remote players. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class SkillVisualState {
 private static final Map<UUID,SkillVisualClock.Entry> UNTIL=new HashMap<>();
 private static net.minecraft.client.multiplayer.ClientLevel level;
 private static LivingEntity rendered;
 public static void accept(NofaNetwork.WeaponState s){SkillActionClient.accept(s);var mc=Minecraft.getInstance();if(mc.level==null||!s.valid()||!mc.level.dimension().location().equals(s.dimension()))return;if(level!=mc.level){UNTIL.clear();level=mc.level;}var state=SkillVisualClock.accept(UNTIL.get(s.player()),s.active(),s.permanent(),level.getGameTime(),s.duration());if(state==null)UNTIL.remove(s.player());else UNTIL.put(s.player(),state);}
 public static boolean activeFor(ItemDisplayContext context){return activeAgeFor(context)>=0;}
 public static LivingEntity entityFor(ItemDisplayContext context){
  if(context.firstPerson())return Minecraft.getInstance().player;
  return context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND?rendered:null;
 }
 public static float activeAgeFor(ItemDisplayContext context){
  if(UNTIL.isEmpty())return -1;
  if(!context.firstPerson()&&context!=ItemDisplayContext.THIRD_PERSON_LEFT_HAND&&context!=ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)return -1;
  var mc=Minecraft.getInstance();if(mc==null||mc.level==null||level!=mc.level)return -1;
  var entity=entityFor(context);if(entity==null||!entity.isAlive()||entity.isSpectator()||!SkillEligibility.eligible(entity.getMainHandItem()))return -1;
  return SkillVisualClock.age(UNTIL.get(entity.getUUID()),level.getGameTime(),mc.getFrameTime(),true,UltimateRequirement.installed(entity.getMainHandItem()));
 }
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void pre(RenderLivingEvent.Pre<?,?> e){if(!e.isCanceled())rendered=e.getEntity();}
 @SubscribeEvent public static void post(RenderLivingEvent.Post<?,?> e){rendered=null;}
 @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(level!=mc.level){UNTIL.clear();level=mc.level;rendered=null;}if(level!=null)UNTIL.entrySet().removeIf(x->SkillVisualClock.expired(x.getValue(),level.getGameTime()));}
}
