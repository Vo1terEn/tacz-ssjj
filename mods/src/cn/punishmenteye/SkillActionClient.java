package cn.punishmenteye;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native first-person action follows the server's successful King's Heart activation. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class SkillActionClient {
 private static SkillActionClock clock=new SkillActionClock();
 private static ClientLevel level;
 private static ResourceLocation requestedGun;
 private static ItemStack requestedStack;
 private static Object requestedPlayer,requestedDisplay;
 private static long requestedAt;
 private static int requestedDuration;
 private static AnimationStateContext context;
 private static AnimationStateMachine<?> machine;
 private static Object display;
 private static ItemStack animatedStack;
 private static ResourceLocation animatedGun;
 private static int track;
 private SkillActionClient(){}
 private static void world(){var next=Minecraft.getInstance().level;if(next!=level){clear();clock=new SkillActionClock();cancelPending();level=next;}}
 public static void requested(){
  world();cancelPending();var mc=Minecraft.getInstance();if(level==null||mc.player==null||!mc.player.isAlive())return;
  var stack=mc.player.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
  if(gun==null||!SkillEligibility.eligible(stack)||UltimateRequirement.installed(stack))return;
  requestedGun=gun.getGunId(stack);requestedStack=stack;requestedPlayer=mc.player;requestedDisplay=TimelessAPI.getGunDisplay(stack).orElse(null);requestedAt=level.getGameTime();requestedDuration=SkillEligibility.duration(stack);clock.request(requestedAt,requestedDuration);
 }
 public static void accept(NofaNetwork.WeaponState state){
  world();var mc=Minecraft.getInstance();
  if(level==null||mc.player==null||!state.valid()||!state.player().equals(mc.player.getUUID())||!state.dimension().equals(level.dimension().location()))return;
  if(!pendingBound()){cancelPending();return;}
  boolean accepted=clock.accept(level.getGameTime(),state.active(),state.permanent(),state.duration());
  var stack=mc.player.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
  if(!accepted||gun==null||requestedGun==null||!requestedGun.equals(gun.getGunId(stack))||!SkillEligibility.eligible(stack)||UltimateRequirement.installed(stack))return;
  cancelPending();
  TimelessAPI.getGunDisplay(stack).ifPresent(current->{
   var next=current.getAnimationStateMachine();if(next==null||!next.isInitialized())return;
   var value=next.getContext();if(value==null||value.getStateMachine()!=next||!value.hasAnimationPrototype("punishment_skill"))return;
   clear();int index=value.getTrack(0,15);value.runAnimation("punishment_skill",index,false,1,.05f);
   context=value;machine=next;track=index;display=current;animatedStack=stack;animatedGun=gun.getGunId(stack);
  });
 }
 private static void cancelPending(){clock.cancel();requestedGun=null;requestedStack=null;requestedPlayer=null;requestedDisplay=null;requestedAt=0;requestedDuration=0;}
 private static boolean pendingBound(){
  var mc=Minecraft.getInstance();if(level==null||mc.player==null||mc.player!=requestedPlayer||!mc.player.isAlive()||level.getGameTime()<requestedAt||level.getGameTime()-requestedAt>20)return false;
  var stack=mc.player.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
  return requestedDuration==SkillEligibility.duration(stack)&&requestedStack==stack&&gun!=null&&requestedGun!=null&&requestedGun.equals(gun.getGunId(stack))&&SkillEligibility.eligible(stack)&&!UltimateRequirement.installed(stack)&&TimelessAPI.getGunDisplay(stack).orElse(null)==requestedDisplay;
 }
 private static boolean bound(){return context!=null&&machine!=null&&context.getStateMachine()==machine&&machine.getContext()==context&&machine.isInitialized();}
 private static void clear(){var old=context;int index=track;boolean stop=bound();context=null;machine=null;display=null;animatedStack=null;animatedGun=null;track=0;if(stop)old.stopAnimation(index);}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  if(event.phase!=TickEvent.Phase.END)return;world();if(requestedStack!=null&&!pendingBound())cancelPending();if(context==null)return;var mc=Minecraft.getInstance();
  if(mc.player==null||!mc.player.isAlive()||!bound()){clear();return;}
  var stack=mc.player.getMainHandItem();var gun=IGun.getIGunOrNull(stack);
  if(stack!=animatedStack||gun==null||!animatedGun.equals(gun.getGunId(stack))||!SkillEligibility.eligible(stack)||TimelessAPI.getGunDisplay(stack).orElse(null)!=display||context.isStopped(track))clear();
 }
}
