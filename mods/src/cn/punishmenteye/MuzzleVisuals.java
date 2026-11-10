package cn.punishmenteye;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
public final class MuzzleVisuals {
 private static MuzzleGeometry.Definition definition;
 private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(4096));
 public static void reload(ResourceManager resources)throws java.io.IOException{
  definition=null;try(var in=resources.open(new ResourceLocation("eyeofpunishment","rig/muzzle_cavity.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){definition=new Gson().fromJson(reader,MuzzleGeometry.Definition.class);}
 }
 public static void render(PoseStack pose,int overlay,WeaponVisuals.Part receiver,int light,float age){
  // Submit the frame depth before its interior. The active receiver is not also
  // queued in TaCZ's deferred consumer, which would repaint far-side bars over it.
  WeaponVisuals.render(receiver,pose,BUFFERS.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation("eyeofpunishment","textures/gun/uv/notdistinguish.png"))),light,overlay);
  BUFFERS.endBatch();
  renderEffects(pose,overlay,age);
 }
 /** Original E014 layers, after a caller has already submitted complete body depth. */
 public static void renderEffects(PoseStack pose,int overlay,float age){
  if(definition==null)return;
  for(int layer=1;layer<=2;layer++){
   for(var p:definition.passes)if(p.layer==layer){
    MuzzleGeometry.render(p,pose,BUFFERS.getBuffer(MuzzleRenderTypes.layer(new ResourceLocation("eyeofpunishment","textures/fx/"+p.texture+".png"),p.blend)),age,overlay);
    BUFFERS.endBatch();
   }
   if(definition.emitters!=null)for(var e:definition.emitters)if(e.layer==layer){
    MuzzleParticles.render(e,pose,BUFFERS.getBuffer(MuzzleRenderTypes.layer(new ResourceLocation("eyeofpunishment","textures/fx/"+e.texture+".png"),e.blend)),age,overlay);
    BUFFERS.endBatch();
   }
  }
 }
}
