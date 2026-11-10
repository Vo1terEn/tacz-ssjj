package cn.ssjjdimension.client;

import com.google.gson.Gson;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Actual clipped source triangles appear only when the attachment is a standalone item. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionAttachmentRenderer {
 private static final List<String> PARTS=List.of("dimension_muzzle","dimension_grip","dimension_stock");
 private static final Map<String,DimensionMeshRenderer.Geometry> DATA=new LinkedHashMap<>();
 private static final Map<BedrockAttachmentModel,DimensionMeshRenderer.Geometry> BOUND=new WeakHashMap<>();
 private static final Field CURRENT;
 static {try{CURRENT=BedrockAttachmentModel.class.getDeclaredField("currentGunItem");CURRENT.setAccessible(true);}catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}}
 private DimensionAttachmentRenderer(){}
 @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Resources {
  @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)DimensionAttachmentRenderer::reload);}
 }
 public static void reload(ResourceManager resources){
  DATA.clear();BOUND.clear();
  for(String name:PARTS)try(var input=resources.open(new ResourceLocation("ssjj_dimension","source_attachment/"+name+".json"));var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
   var data=new Gson().fromJson(reader,DimensionMeshRenderer.Geometry.class);DimensionMeshRenderer.validate(data);DATA.put(name,data);
  }catch(Exception error){com.mojang.logging.LogUtils.getLogger().error("Unable to load original Dimension attachment "+name,error);}
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
  if(event.phase!=TickEvent.Phase.END)return;
  for(var entry:DATA.entrySet())TimelessAPI.getClientAttachmentIndex(new ResourceLocation("ssjj_dimension",entry.getKey())).ifPresent(index->{
   var model=index.getAttachmentModel();if(model!=null&&BOUND.get(model)!=entry.getValue()){attach(model,entry.getValue());BOUND.put(model,entry.getValue());}
  });
 }
 public static void attach(BedrockAttachmentModel model,DimensionMeshRenderer.Geometry data){
  DimensionMeshRenderer.validate(data);
  if(model.getNode("item_extract")==null)throw new IllegalArgumentException("Missing extracted attachment anchor");
  model.setFunctionalRenderer("item_extract",node->(pose,consumer,context,light,overlay)->{
   try{if(CURRENT.get(model)!=null)return;}catch(IllegalAccessException error){throw new IllegalStateException(error);}
   for(var part:data.parts)for(var face:part.triangles)for(int corner=0;corner<4;corner++){
    int index=face[Math.min(corner,2)];var p=part.positions[index];var uv=part.uv[index];var n=part.normals[index];
    consumer.vertex(pose.last().pose(),p[0],p[1],p[2]).color(1f,1f,1f,1f).uv(uv[0],uv[1]).overlayCoords(overlay).uv2(light).normal(pose.last().normal(),n[0],n[1],n[2]).endVertex();
   }
  });
 }
}
