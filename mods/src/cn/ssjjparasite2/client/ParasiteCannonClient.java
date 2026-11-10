package cn.ssjjparasite2.client;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ParasiteCannonClient {
 private static final KeyMapping KEY=new KeyMapping("key.ssjjparasite2.cannon",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.eyeofpunishment");
 private static ParasiteCannon.State state;
 private static net.minecraft.client.multiplayer.ClientLevel level;
 private static long start,requestAt=-100;
 @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Registration {@SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(KEY);}}
 public static boolean busy(){var mc=Minecraft.getInstance();return state!=null&&state.remaining()>0&&mc.level==level&&mc.player!=null&&mc.player.getInventory().selected==state.slot()&&ParasiteCannon.eligible(mc.player.getMainHandItem());}
 public static boolean blocks(net.minecraft.world.entity.LivingEntity entity){return entity!=null&&entity==Minecraft.getInstance().player&&busy();}
 @SubscribeEvent public static void key(InputEvent.Key e){
  var mc=Minecraft.getInstance();if(e.getAction()!=GLFW.GLFW_PRESS||!KEY.matches(e.getKey(),e.getScanCode())||mc.screen!=null||mc.player==null||mc.level==null||!mc.player.isAlive()||mc.player.isSpectator()||!ParasiteCannon.eligible(mc.player.getMainHandItem())||busy())return;
  var op=IClientPlayerGunOperator.fromLocalPlayer(mc.player);if(op.isAim()||op.getClientAimingProgress(mc.getFrameTime())>.01f||mc.level.getGameTime()==requestAt)return;
  requestAt=mc.level.getGameTime();ParasiteCannon.request(mc.player.getInventory().selected);
 }
 public static void accept(ParasiteCannon.State next){
  var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!next.valid()||mc.player.getInventory().selected!=next.slot())return;
  var stack=mc.player.getMainHandItem();if(!ParasiteCannon.eligible(stack))return;
  if(next.remaining()==0){if(state==null||!next.token().equals(state.token()))return;stack.getOrCreateTag().putBoolean(ParasiteCannon.ACTIVE,next.active());state=null;return;}
  state=next;level=mc.level;start=level.getGameTime();
  var gun=IGun.getIGunOrNull(stack);
  TimelessAPI.getGunDisplay(stack).ifPresent(display->{var machine=display.getAnimationStateMachine();if(machine!=null&&machine.isInitialized()){machine.trigger("inspect_break_out");machine.trigger(toggleInput(next.remaining()));}});
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
  if(mc.level!=level||mc.player==null||!mc.player.isAlive()||state!=null&&(!ParasiteCannon.eligible(mc.player.getMainHandItem())||mc.player.getInventory().selected!=state.slot())){state=null;level=mc.level;requestAt=-100;}
  // Keep authority with the server; timeout releases a stale visual lock only.
  if(state!=null&&level.getGameTime()-start>state.remaining()+40)state=null;
  if(busy()){mc.player.setSprinting(false);IClientPlayerGunOperator.fromLocalPlayer(mc.player).aim(false);}
 }
 public static String toggleInput(int duration){return duration<ParasiteCannon.SWITCH_TICKS?"parasite_cannon_toggle_ultimate":"parasite_cannon_toggle";}
 public static float switchProgress(float elapsed,int duration){return duration<=0?1:Math.max(0,Math.min(1,elapsed/duration));}
 public static float progress(){var mc=Minecraft.getInstance();return busy()?switchProgress(mc.level.getGameTime()-start+mc.getFrameTime(),state.remaining()):1;}
 @SubscribeEvent public static void draw(RenderGuiEvent.Post e){
  var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()||!ParasiteCannon.eligible(mc.player.getMainHandItem())||mc.options.hideGui||mc.screen!=null)return;
  RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
  var sprites=ParasiteCannonHud.sprites(e.getWindow().getGuiScaledWidth(),e.getWindow().getGuiScaledHeight(),ParasiteCannon.active(mc.player.getMainHandItem()),progress());
  String skin="";
  for(var s:sprites)e.getGuiGraphics().blit(new ResourceLocation("ssjjparasite2","textures/gui/cannon_"+skin+s.texture()+".png"),s.x(),s.y(),s.w(),s.h(),0,0,s.sourceW(),s.sourceH(),s.textureW(),s.textureH());
  // Original artwork says E. Replace only that prompt glyph at draw time with
  // the user's configured skill key; all supplied sprite bytes stay original.
  var card=sprites.get(0);float scale=card.w()/210f;var pose=e.getGuiGraphics().pose();pose.pushPose();pose.translate(card.x()+94*scale,card.y()+45*scale,1);pose.scale(scale,scale,1);
  e.getGuiGraphics().fill(-1,-1,8,11,0xff07131e);e.getGuiGraphics().drawString(mc.font,KEY.getTranslatedKeyMessage(),0,1,0xff00ffff,false);pose.popPose();
  RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();
 }
}
