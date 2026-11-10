package cn.punishmenteye;
import java.util.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Original qiu sequence for the ball, original blue lightning/cloud texture references for effects. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID,value=Dist.CLIENT)
public final class NofaVisuals {
 private record Visible(NofaNetwork.Effect effect,long born,net.minecraft.world.entity.LivingEntity target){}
 private static final Map<UUID,Visible> FX=new LinkedHashMap<>();
 private static net.minecraft.client.multiplayer.ClientLevel level;
 private static long lastViewTick=Long.MIN_VALUE;
 private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
 private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(4096));
 public static void accept(NofaNetwork.Effect m){var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(m.dimension()))return;
  if(level!=mc.level){FX.clear();level=mc.level;}if(m.type()==3){FX.remove(m.id());return;}
  if(m.ticks()<1||m.ticks()>200||m.type()<0||m.type()>4)return;
  // Lightning UUIDs are separate from persistent target IDs so multiple rapid shots animate independently.
  net.minecraft.world.entity.LivingEntity target=null;if(m.type()==0||m.type()==4)for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.LivingEntity living&&living.getUUID().equals(m.id())){target=living;break;}
  FX.put((m.type()==0||m.type()==4)?UUID.randomUUID():m.id(),new Visible(m,mc.level.getGameTime(),target));
 }
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  var mc=Minecraft.getInstance();if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
  if(level!=mc.level){FX.clear();level=mc.level;}if(mc.level==null)return;
  if(mc.player!=null&&NofaPower.installed(mc.player.getMainHandItem())&&mc.level.getGameTime()!=lastViewTick&&mc.player.tickCount%5==0){
   lastViewTick=mc.level.getGameTime();
   // Outer diamond is 32 GUI pixels wide. Actual 3D projection accounts for aspect/FOV.
   float slope=32f/mc.getWindow().getGuiScaledHeight()/e.getProjectionMatrix().m11();
   if(Float.isFinite(slope))NofaNetwork.view(slope);
  }
  long now=mc.level.getGameTime();FX.values().removeIf(v->now-v.born>=v.effect.ticks());
  Vec3 camera=e.getCamera().getPosition();
  for(var v:FX.values()){
   var m=v.effect;float age=now-v.born+e.getPartialTick();boolean strike=m.type()==0||m.type()==4;Vec3 world=m.pos().add(m.velocity().scale(age));
   if(strike){
    if(v.target!=null){if(v.target.isRemoved())continue;world=v.target.getBoundingBox().getCenter().add(v.target.getPosition(e.getPartialTick()).subtract(v.target.position()));}
    if(mc.player==null||mc.level.clip(new net.minecraft.world.level.ClipContext(camera,world,net.minecraft.world.level.ClipContext.Block.VISUAL,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
   }
   Vec3 p=world.subtract(camera);
   float sceneAge=age+(m.type()==2?NofaRules.DURATION-m.ticks():m.type()==1?NofaTuning.LIFETIME-m.ticks():0);
   int duration=m.type()==2?NofaRules.DURATION:m.type()==1?NofaTuning.LIFETIME:m.ticks();
   if(SkinWorldVisuals.render(m,e.getPoseStack(),p,sceneAge,duration))continue;
   if(m.type()==2){
    var pose=e.getPoseStack();pose.pushPose();pose.translate(p.x,p.y,p.z);
    for(var q:CloudScene.quads(sceneAge,duration,m.id().hashCode(),mc.getEntityRenderDispatcher().cameraOrientation())){
     var texture=TEXTURES.computeIfAbsent(q.texture(),s->new ResourceLocation("eyeofpunishment","textures/fx/"+s+".png"));
     NofaGeometry.quad(pose,BUFFERS.getBuffer(RenderType.entityTranslucentEmissive(texture)),q.p(),q.uv(),q.alpha());
    }pose.popPose();continue;
   }
   for(var layer:NofaScene.layers(m.type(),sceneAge,duration,m.id().hashCode(),mc.getEntityRenderDispatcher().cameraOrientation())){
    var texture=TEXTURES.computeIfAbsent(layer.texture(),s->new ResourceLocation("eyeofpunishment","textures/fx/"+s+".png"));
    NofaGeometry.render(e.getPoseStack(),BUFFERS.getBuffer(strike?StrikeRenderTypes.depth(texture):RenderType.entityTranslucentEmissive(texture)),p.add(layer.offset()),layer.w(),layer.h(),layer.rotation(),layer.u0(),layer.v0(),layer.u1(),layer.v1(),layer.alpha());
   }
  }
  BUFFERS.endBatch();
 }
}
