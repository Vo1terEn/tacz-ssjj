package cn.punishmenteye;

import java.util.*;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Original world particle prefabs, selected by the firing gun's immutable skin snapshot. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SkinWorldVisuals {
 private SkinWorldVisuals(){}
 private static final String[] KEYS={"timetravel","sakura","anime","fire","base"};
 private static final String[] STAGES={"normal","active","ball","cloud"};
 private static final Map<String,MuzzleGeometry.Definition> DEFINITIONS=new HashMap<>();
 private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
 private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(4096));
 public interface LayerSink {VertexConsumer begin(String texture,String blend,int layer);void end();}
 private record Layer(int order,MuzzleGeometry.Pass pass,MuzzleParticles.Emitter emitter){}
 public static String stage(int type){return switch(type){case 0->"normal";case 4->"active";case 1->"ball";case 2->"cloud";default->"";};}
 public static String key(String skin){return skin==null||skin.isEmpty()?"base":skin;}
 public static boolean handles(String skin){return switch(key(skin)){case "base","timetravel","sakura","anime","fire"->true;default->false;};}
 public static MuzzleGeometry.Definition definition(String skin,int type){return DEFINITIONS.get(key(skin)+"_"+stage(type));}
 /** Same stable body contour as server damage; source rendering is not rescaled. */
 public static CloudRanges.Profile cloudProfile(String skin){return CloudRanges.profile(skin);}
 @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)SkinWorldVisuals::reload);}
 public static void reload(ResourceManager resources){
  DEFINITIONS.clear();TEXTURES.clear();var gson=new Gson();
  for(String key:KEYS){var loaded=new HashMap<String,MuzzleGeometry.Definition>();
   try{
    for(String stage:STAGES){var id=new ResourceLocation("eyeofpunishment","rig/world/"+key+"_"+stage+".json");
     try(var reader=resources.openAsReader(id)){
      var definition=gson.fromJson(reader,MuzzleGeometry.Definition.class);
      if(definition==null||definition.passes==null||definition.emitters==null)throw new IllegalArgumentException("Incomplete world particle definition: "+id);
      for(var emitter:definition.emitters)MuzzleParticles.validate(emitter);
      if(stage.equals("cloud")){
       var profile=cloudProfile(key);if(profile==null)throw new IllegalArgumentException("Missing shared cloud body contour: "+key);
       boolean body=false;for(var emitter:definition.emitters)if(emitter.name.equals(profile.bodyEmitter()))body=true;
       if(!body)throw new IllegalArgumentException("Cloud body/profile mismatch: "+key);
      }
      loaded.put(key+"_"+stage,definition);
     }
    }DEFINITIONS.putAll(loaded);
   }catch(Exception error){org.slf4j.LoggerFactory.getLogger("EyeWorldSkins").error("Cannot load original world effects for skin "+key,error);}
  }
 }
 /** This exact drawing path also exports the offline/native verification vertices. */
 public static void draw(MuzzleGeometry.Definition definition,PoseStack pose,float ticks,int overlay,LayerSink sink){
  var layers=new ArrayList<Layer>();
  for(var pass:definition.passes)layers.add(new Layer(pass.layer,pass,null));
  for(var emitter:definition.emitters)layers.add(new Layer(emitter.layer,null,emitter));
  layers.sort(Comparator.comparingInt(Layer::order));
  for(var layer:layers){
   if(layer.pass!=null){var p=layer.pass;MuzzleGeometry.render(p,pose,sink.begin(p.texture,p.blend,p.layer),ticks,overlay);}
   else{var p=layer.emitter;MuzzleParticles.render(p,pose,sink.begin(p.texture,p.blend,p.layer),ticks,overlay);}
   sink.end();
  }
 }
 /** Positions have already been made camera-relative by NofaVisuals; source ages are ticks. */
 public static boolean render(NofaNetwork.Effect effect,PoseStack pose,Vec3 relative,float sceneAge,int duration){
  var definition=definition(effect.skin(),effect.type());
  // Known weapon styles never revive the retired approximate shapes on reload failure.
  if(definition==null)return handles(effect.skin());
  if(sceneAge<0||sceneAge>=duration)return true;
  pose.pushPose();try{
   pose.translate(relative.x,relative.y,relative.z);
   draw(definition,pose,sceneAge,0,new LayerSink(){
    public VertexConsumer begin(String texture,String blend,int layer){var id=TEXTURES.computeIfAbsent(texture,s->new ResourceLocation("eyeofpunishment","textures/fx/"+s+".png"));return BUFFERS.getBuffer(MuzzleRenderTypes.layer(id,blend));}
    public void end(){BUFFERS.endBatch();}
   });
  }finally{pose.popPose();}return true;
 }
}
