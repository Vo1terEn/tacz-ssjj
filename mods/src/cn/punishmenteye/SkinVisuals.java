package cn.punishmenteye;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;

/** Each native display owns its body texture and matching source particle definitions. */
public final class SkinVisuals {
 public static final String BASE="base";
 private record Effect(MuzzleGeometry.Definition data,String anchor){}
 private record Style(WeaponVisuals.Definition body,ResourceLocation texture,Effect normal,Effect active,Effect fire,float fireDuration){}
 private static final Map<String,Style> STYLES=new LinkedHashMap<>();
 private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
 private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(4096));
 private static <T>T read(ResourceManager resources,String path,Class<T> type)throws java.io.IOException{
  try(var in=resources.open(new ResourceLocation("eyeofpunishment",path));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){return new Gson().fromJson(reader,type);}
 }
 private static Effect effect(ResourceManager resources,String path)throws java.io.IOException{
  var json=read(resources,path,JsonObject.class);
  String anchor=json.get("anchor").getAsString();
  if(!anchor.equals("receiver")&&!anchor.equals("muzzle_flash"))throw new IllegalArgumentException("Unsupported effect anchor: "+path+"/"+anchor);
  return new Effect(new Gson().fromJson(json,MuzzleGeometry.Definition.class),anchor);
 }
 public static void reload(ResourceManager resources)throws java.io.IOException{
  STYLES.clear();TEXTURES.clear();
  STYLES.put(BASE,new Style(read(resources,"rig/weapon_mesh.json",WeaponVisuals.Definition.class),
   new ResourceLocation("eyeofpunishment","textures/gun/uv/notdistinguish.png"),
   effect(resources,"rig/skins/base_normal.json"),effect(resources,"rig/skins/base_active.json"),null,0));
  for(String key:SkinRules.KEYS){
   var body=read(resources,"rig/notdistinguish_"+key+".json",WeaponVisuals.Definition.class);
   var normal=effect(resources,"rig/skins/"+key+"_normal.json");
   var active=effect(resources,"rig/skins/"+key+"_active.json");
   var fire=read(resources,"rig/skins/"+key+"_fire.json",JsonObject.class);
   float duration=fire.get("durationSeconds").getAsFloat();
   if(!(duration>0&&duration<1))throw new IllegalArgumentException("Invalid source fire duration: "+key);
   var firing=effect(resources,"rig/skins/"+key+"_fire.json");
   if(!normal.anchor.equals("receiver")||!active.anchor.equals("receiver")||!firing.anchor.equals("muzzle_flash"))throw new IllegalArgumentException("Invalid source skin anchors: "+key);
   STYLES.put(key,new Style(body,new ResourceLocation("eyeofpunishment","textures/gun/uv/notdistinguish_"+key+".png"),normal,active,firing,duration));
  }
 }
 public static void install(){
  for(var entry:STYLES.entrySet()){
   String key=entry.getKey();
   if(key.equals(BASE))continue;
   TimelessAPI.getGunDisplay(new ResourceLocation("eyeofpunishment","notdistinguish_"+key+"_display"),GripRequirement.WEAPON)
    .ifPresent(display->WeaponVisuals.installSkin(display.getGunModel(),entry.getValue().body,key));
  }
 }
 public static void installBase(BedrockAnimatedModel model,WeaponVisuals.Definition body){
  if(STYLES.containsKey(BASE))WeaponVisuals.installSkin(model,body,BASE,sink(BASE));
  else WeaponVisuals.install(model,body);
 }
 public static float fireDuration(String key){Style style=STYLES.get(key);return style==null?0:style.fireDuration;}
 /** Every animated body part reaches this buffer before the native final-root callback. */
 public static WeaponVisuals.SkinRenderSink sink(String key){
  Style style=STYLES.get(key);
  if(style==null)throw new IllegalArgumentException("Unknown skin style: "+key);
  return new WeaponVisuals.SkinRenderSink(){
   @Override public void body(WeaponVisuals.Part mesh,PoseStack pose,VertexConsumer nativeOut,ItemDisplayContext context,int light,int overlay){
    WeaponVisuals.render(mesh,pose,BUFFERS.getBuffer(RenderType.entityCutoutNoCull(style.texture)),light,overlay);
   }
   @Override public void end(PoseStack receiverPose,PoseStack muzzlePose,ItemDisplayContext context,int light,int overlay){
    BUFFERS.endBatch();
    if(receiverPose!=null)render(style,key,receiverPose,muzzlePose,context,overlay);
   }
  };
 }
 /** Opaque body depth is complete; FX retain the exact animated receiver pose and normal. */
 private static void render(Style style,String key,PoseStack receiver,PoseStack muzzle,ItemDisplayContext context,int overlay){
  float skillAge=SkillVisualState.activeAgeFor(context);
  var mc=Minecraft.getInstance();float age=skillAge>=0?skillAge:mc.level==null?0:(float)(mc.level.getGameTime()%24000)+mc.getFrameTime();
  renderEffect(skillAge>=0?style.active:style.normal,receiver,muzzle,overlay,age);
  if(key.equals(BASE)) {
   if(skillAge>=0)MuzzleVisuals.renderEffects(receiver,overlay,skillAge);
   return;
  }
  float fireAge=SkinFireState.age(key,context,style.fireDuration);
  if(fireAge>=0)renderEffect(style.fire,receiver,muzzle,overlay,fireAge);
 }
 private static void renderEffect(Effect effect,PoseStack receiver,PoseStack muzzle,int overlay,float age){
  if(effect==null)return;
  PoseStack pose=WeaponVisuals.effectPose(effect.anchor,receiver,muzzle);
  if(pose!=null)renderEffects(effect.data,pose,overlay,age);
 }
 private static ResourceLocation texture(String name){return TEXTURES.computeIfAbsent(name,n->new ResourceLocation("eyeofpunishment","textures/fx/"+n+".png"));}
 private static void renderEffects(MuzzleGeometry.Definition definition,PoseStack pose,int overlay,float age){
  if(definition==null)return;
  var layers=new TreeSet<Integer>();
  if(definition.passes!=null)for(var pass:definition.passes)layers.add(pass.layer);
  if(definition.emitters!=null)for(var emitter:definition.emitters)layers.add(emitter.layer);
  for(int layer:layers){
   if(definition.passes!=null)for(var pass:definition.passes)if(pass.layer==layer){
    MuzzleGeometry.render(pass,pose,BUFFERS.getBuffer(MuzzleRenderTypes.layer(texture(pass.texture),pass.blend)),age,overlay);BUFFERS.endBatch();
   }
   if(definition.emitters!=null)for(var emitter:definition.emitters)if(emitter.layer==layer){
    MuzzleParticles.render(emitter,pose,BUFFERS.getBuffer(MuzzleRenderTypes.layer(texture(emitter.texture),emitter.blend)),age,overlay);BUFFERS.endBatch();
   }
  }
 }
}
