package cn.ssjjdimension.client;

import cn.ssjjdimension.DimensionDecoyNetwork;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Server-fed original dash HUD, source overload particles and native player snapshots. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionDecoyClient {
 private static ClientLevel world;
 private static DimensionDecoyNetwork.Status state;
 private static long received,lastRequest=-100;
 private record Ghost(DimensionDecoyNetwork.Visual message,SnapshotPlayer player,long until){}
 private static final Map<UUID,Ghost> GHOSTS=new HashMap<>();
 private static final Map<UUID,DimensionDecoyNetwork.Visual> OVERLOAD=new HashMap<>();
 private static DimensionDecoyGeometry.Data effect;
 private static final Map<UUID,Long> CLOAKS=new HashMap<>();
 private static final Map<net.minecraft.world.entity.Entity,Boolean> FIRE=new IdentityHashMap<>();
 private DimensionDecoyClient(){}
 @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static class Resources {
  @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((ResourceManagerReloadListener)DimensionDecoyClient::load);}
 }
 private static void load(ResourceManager manager){
  effect=null;
  try(var reader=new InputStreamReader(manager.open(new ResourceLocation("ssjj_dimension","effects/decoy/overload.json")),StandardCharsets.UTF_8)){
   var fresh=new Gson().fromJson(reader,DimensionDecoyGeometry.Data.class);DimensionDecoyGeometry.validate(fresh);effect=fresh;
  }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot load original Dimension Overload profile",ex);}
  GHOSTS.clear();
 }
 private static void world(){var level=Minecraft.getInstance().level;if(world!=level){world=level;state=null;GHOSTS.clear();OVERLOAD.clear();CLOAKS.clear();FIRE.clear();lastRequest=-100;}}
 public static int cooldown(){world();return state==null||world==null?0:Math.max(0,state.cooldown()-(int)(world.getGameTime()-received));}
 public static int dashTicks(){world();return state==null||world==null?0:Math.max(0,state.dash()-(int)(world.getGameTime()-received));}
 public static void status(DimensionDecoyNetwork.Status next){world();if(world==null||!next.valid()||!world.dimension().location().equals(next.dimension()))return;if(state!=null&&state.cooldown()>0&&next.cooldown()==0)lastRequest=-100;state=next;received=world.getGameTime();}
 public static void visual(DimensionDecoyNetwork.Visual m){
  world();if(world==null||!m.valid()||!m.dimension().equals(world.dimension().location()))return;
  if(m.kind()==5){CLOAKS.put(m.owner(),world.getGameTime()+m.ticks());return;}
  if(m.kind()==6){CLOAKS.remove(m.owner());return;}
  if(m.kind()==1){GHOSTS.remove(m.id());return;}
  if(m.kind()==3){OVERLOAD.put(m.owner(),m);return;}
  if(m.kind()==4){OVERLOAD.remove(m.owner());return;}
  if(m.kind()!=0||!(world.getEntity(m.entity()) instanceof AbstractClientPlayer source)||!source.getUUID().equals(m.owner()))return;
  var ghost=new SnapshotPlayer(world,source,m.id());ghost.setPos(m.position().x,m.position().y,m.position().z);standing(ghost,m.yaw());ghost.setMainArm(source.getMainArm());
  for(var slot:EquipmentSlot.values())ghost.setItemSlot(slot,source.getItemBySlot(slot).copy());
  ghost.setOldPosAndRot();ghost.xCloak=ghost.xCloakO=ghost.getX();ghost.yCloak=ghost.yCloakO=ghost.getY();ghost.zCloak=ghost.zCloakO=ghost.getZ();
  GHOSTS.put(m.id(),new Ghost(m,ghost,world.getGameTime()+m.ticks()));
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(InputEvent.Key e){
  world();var mc=Minecraft.getInstance();
  if(world==null||mc.player==null||mc.screen!=null||mc.getOverlay()!=null||mc.isPaused()||!mc.isWindowActive()||!mc.mouseHandler.isMouseGrabbed()||!mc.player.isAlive()||mc.player.isSpectator()||DimensionFieldClient.heldTier()!=2||DimensionFieldClient.activeTicks()<=0||cooldown()>0)return;
  if(e.getAction()!=org.lwjgl.glfw.GLFW.GLFW_PRESS||!mc.options.keyShift.matches(e.getKey(),e.getScanCode()))return;
  long now=world.getGameTime();if(now-lastRequest<Math.min(4,cn.ssjjdimension.DimensionDecoyRules.cooldownLimit()))return;lastRequest=now;mc.options.keyShift.setDown(false);
  int forward=(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0),left=(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0);
  DimensionDecoyNetwork.request(mc.player.getInventory().selected,forward,left);
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;world();if(world==null)return;
  CLOAKS.values().removeIf(until->world.getGameTime()>=until);
  GHOSTS.values().removeIf(g->world.getGameTime()>=g.until());
  if(dashTicks()>0)Minecraft.getInstance().options.keyShift.setDown(false);
 }
 static void standing(RemotePlayer ghost,float yaw){
  ghost.setYRot(yaw);ghost.yRotO=yaw;ghost.yBodyRot=ghost.yBodyRotO=yaw;ghost.yHeadRot=ghost.yHeadRotO=yaw;ghost.xRotO=0;ghost.setPose(net.minecraft.world.entity.Pose.STANDING);ghost.setXRot(0);ghost.setShiftKeyDown(false);ghost.setSprinting(false);ghost.setDeltaMovement(Vec3.ZERO);
 }
 /** A distinct entity identity keeps the visible clone outside the real player's cloak state. */
 static final class SnapshotPlayer extends RemotePlayer {
  private ResourceLocation skin,cape,elytra;private String model;
  private Set<net.minecraft.world.entity.player.PlayerModelPart> parts;
  SnapshotPlayer(ClientLevel level,AbstractClientPlayer source,UUID id){
   super(level,new com.mojang.authlib.GameProfile(id,source.getGameProfile().getName()));
   skin=source.getSkinTextureLocation();cape=source.getCloakTextureLocation();elytra=source.getElytraTextureLocation();model=source.getModelName();
   parts=EnumSet.noneOf(net.minecraft.world.entity.player.PlayerModelPart.class);for(var part:net.minecraft.world.entity.player.PlayerModelPart.values())if(source.isModelPartShown(part))parts.add(part);
  }
  @Override public ResourceLocation getSkinTextureLocation(){return skin;}
  @Override public String getModelName(){return model;}
  @Override public boolean isSkinLoaded(){return true;}
  @Override public ResourceLocation getCloakTextureLocation(){return cape;}
  @Override public ResourceLocation getElytraTextureLocation(){return elytra;}
  @Override public boolean isCapeLoaded(){return cape!=null;}
  @Override public boolean isElytraLoaded(){return elytra!=null;}
  @Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part){return parts!=null&&parts.contains(part);}
 }
 public static boolean hidden(net.minecraft.world.entity.Entity entity){
  world();return world!=null&&entity!=null&&CLOAKS.getOrDefault(entity.getUUID(),0L)>world.getGameTime();
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hidePlayer(RenderLivingEvent.Pre<?,?> e){if(hidden(e.getEntity()))e.setCanceled(true);}
 // The real hand event remains native; DimensionCloakRender supplies its scoped 50% alpha.
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hideName(RenderNameTagEvent e){if(e.getEntity() instanceof SnapshotPlayer||hidden(e.getEntity()))e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);}
 @SubscribeEvent public static void fire(TickEvent.RenderTickEvent e){
  if(e.phase==TickEvent.Phase.START){
   world();if(world==null)return;
   for(var entity:world.entitiesForRendering())if(hidden(entity)&&entity.isOnFire()){FIRE.put(entity,true);entity.setSharedFlagOnFire(false);}
  }else{for(var entity:FIRE.keySet())entity.setSharedFlagOnFire(true);FIRE.clear();}
 }
 private static ResourceLocation hud(String name){return new ResourceLocation("ssjj_dimension","textures/gui/decoy/"+name+".png");}
 @SubscribeEvent public static void hud(RenderGuiEvent.Post e){
  world();var mc=Minecraft.getInstance();if(world==null||mc.player==null||mc.options.hideGui||!mc.player.isAlive()||mc.player.isSpectator())return;
  var gui=e.getGuiGraphics();
  if(DimensionDecoyHud.visible(DimensionFieldClient.heldTier(),DimensionFieldClient.activeTicks())&&com.tacz.guns.config.client.RenderConfig.GUN_HUD_ENABLE.get()){
   var layout=DimensionDecoyHud.layout(e.getWindow().getGuiScaledWidth(),e.getWindow().getGuiScaledHeight());
   gui.pose().pushPose();gui.pose().translate(layout.x(),layout.y(),500);gui.pose().scale(layout.scale(),layout.scale(),1);
   RenderSystem.enableBlend();RenderSystem.setShaderColor(1,1,1,1);
   gui.blit(hud("background"),0,0,0,0,157,29,157,29);gui.blit(hud("text"),42,-3,0,0,91,15,91,15);
   int width=Math.round(126*(1-cooldown()/(float)cn.ssjjdimension.DimensionDecoyRules.cooldownLimit()));if(width>0)gui.blit(hud("progress"),27,15,0,0,width,7,126,7);
   gui.pose().popPose();RenderSystem.setShaderColor(1,1,1,1);
  }
  if(state!=null&&state.overloaded()&&!state.recovering()&&state.damageFactor()<=cn.ssjjdimension.DimensionDecoyRules.minimumDamage()*1.01){
   float scale=Math.min(.5f,(e.getWindow().getGuiScaledWidth()-20)/560f);
   gui.pose().pushPose();gui.pose().translate((e.getWindow().getGuiScaledWidth()-560*scale)/2,e.getWindow().getGuiScaledHeight()*.22,500);gui.pose().scale(scale,scale,1);
   RenderSystem.enableBlend();gui.blit(hud("overload"),0,0,0,0,560,115,560,115);gui.pose().popPose();
  }
 }
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;world();if(world==null)return;
  var mc=Minecraft.getInstance();var camera=e.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
  var pose=e.getPoseStack();boolean renderedGhost=false;
  for(var g:GHOSTS.values()){
   Vec3 p=g.message().position();if(camera.distanceToSqr(p)>96*96)continue;
   // Native PlayerRenderer supplies the skin, clothes, armor and TaCZ held-gun pose together.
   // No manual translucent model or separate renderStatic weapon transform.
   mc.getEntityRenderDispatcher().render(g.player(),p.x-camera.x,p.y-camera.y,p.z-camera.z,g.message().yaw(),0,pose,buffers,mc.getEntityRenderDispatcher().getPackedLightCoords(g.player(),0));renderedGhost=true;
  }
  if(renderedGhost)buffers.endBatch();
  if(effect==null)return;
  var right=e.getCamera().rotation().transform(new Vector3f(1,0,0));var up=e.getCamera().rotation().transform(new Vector3f(0,1,0));
  for(var v:OVERLOAD.values()){
   var target=world.getEntity(v.entity());if(target==null||!target.getUUID().equals(v.owner())||!target.isAlive()||camera.distanceToSqr(target.position())>96*96)continue;
   if(target==mc.player&&mc.options.getCameraType().isFirstPerson())continue;
   pose.pushPose();pose.translate(target.getX()-camera.x,target.getY()-camera.y,target.getZ()-camera.z);
   float seconds=(world.getGameTime()+e.getPartialTick())/20f;
   for(var batch:DimensionDecoyGeometry.sample(effect,seconds,right,up)){
    var type=DimensionEffectRenderType.of(new ResourceLocation(batch.texture()),"additive".equals(batch.render()));var out=buffers.getBuffer(type);var mesh=batch.mesh();var color=batch.color();var uv=batch.uvST();
    for(var face:mesh.triangles)for(int corner=0;corner<4;corner++){int i=face[Math.min(corner,2)];var p=mesh.positions[i];var t=mesh.uv[i];out.vertex(pose.last().pose(),p[0],p[1],p[2]).color(color[0],color[1],color[2],color[3]).uv(t[0]*uv[0]+uv[2],t[1]*uv[1]-uv[3]).overlayCoords(0).uv2(15728880).normal(pose.last().normal(),0,1,0).endVertex();}
    buffers.endBatch(type);
   }
   pose.popPose();
  }
 }
}
