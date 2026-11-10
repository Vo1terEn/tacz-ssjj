package cn.judgfire;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.*;

/** Isolated source material state; never changes original world/body shader programs. */
public final class SkinWorldRenderType extends RenderType {
  static MaterialBlendShader shader;
  static MaterialBlendShader hdrShader;
  private static TextureTarget scene;
  private static final IdentityHashMap<SkinWorldFlames.Material,RenderType> TYPES=new IdentityHashMap<>();
  private static final IdentityHashMap<SkinWorldFlames.Material,RenderType> HDR_TYPES=new IdentityHashMap<>();
  private static final Map<Integer,Long> WRAPS=new HashMap<>();
  private static SkinWorldFlames.Material prepared,preparedHdr;
  private static float preparedTime=Float.NaN,preparedHdrTime=Float.NaN;
  private static final float[] IDENTITY={1,1,0,0},ZERO={0,0,0,0};
  private SkinWorldRenderType(){super("skin_world_unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
  static RenderType get(SkinWorldFlames.Material material){
    return get(material,false);
  }
  static RenderType getHdr(SkinWorldFlames.Material material){return get(material,true);}
  private static RenderType get(SkinWorldFlames.Material material,boolean hdr){
    return (hdr?HDR_TYPES:TYPES).computeIfAbsent(material,m->{
      var textures=MultiTextureStateShard.builder();for(int i=0;i<m.textures.length;i++)textures.add(new ResourceLocation(m.textures[i]),m.samplers[i].filter!=0,false);
      var blend=new TransparencyStateShard("skin_source_blend",()->{
        RenderSystem.enableBlend();int src=blend(m.sourceBlend[0]),dst=blend(m.sourceBlend[1]);RenderSystem.blendFuncSeparate(src,dst,src,dst);
      },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
      var samplers=new LayeringStateShard("skin_source_samplers",()->{
        for(int i=0;i<m.textures.length;i++){
          var texture=Minecraft.getInstance().getTextureManager().getTexture(new ResourceLocation(m.textures[i]));
          SkinWorldFlames.SamplerState s=m.samplers[i];long desired=((long)s.wrapU<<32)|(s.wrapV&0xffffffffL);int id=texture.getId();
          if(!Objects.equals(WRAPS.get(id),desired)){texture.bind();GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,wrap(s.wrapU));GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,wrap(s.wrapV));WRAPS.put(id,desired);}
        }
      },()->{});
      CullStateShard front=new CullStateShard(true){public void setupRenderState(){RenderSystem.enableCull();GL11.glCullFace(GL11.GL_FRONT);}public void clearRenderState(){GL11.glCullFace(GL11.GL_BACK);RenderSystem.disableCull();}};
      return create(hdr?"judgment_skin_world_hdr":"judgment_skin_world_native",hdr?FlameRenderType.HDR_FORMAT:DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,!hdr,
        CompositeState.builder().setShaderState(new ShaderStateShard(()->hdr?hdrShader:shader)).setTextureState(textures.build()).setLayeringState(samplers)
          .setTransparencyState(blend).setCullState(m.cull==1?front:m.cull==2?CULL:NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    });
  }
  private static int blend(int b){return switch(b){case 0->0;case 1->1;case 2->774;case 3->768;case 4->775;case 5->770;case 6->769;case 7->772;case 8->773;case 9->776;case 10->771;default->throw new IllegalArgumentException("Unity blend "+b);};}
  private static int wrap(int w){return w==1?GL12.GL_CLAMP_TO_EDGE:w==2?GL14.GL_MIRRORED_REPEAT:w==3?0x8743:GL11.GL_REPEAT;}
  static void prepare(SkinWorldFlames.Material m,double time,float[] tint,float[] custom){
    prepare(m,time,tint,custom,false);
  }
  static void prepareHdr(SkinWorldFlames.Material m,double time,float[] tint,float[] custom){prepare(m,time,tint,custom,true);}
  private static void prepare(SkinWorldFlames.Material m,double time,float[] tint,float[] custom,boolean hdr){
    var s=hdr?hdrShader:shader;if(m.params[0]==5)WorldFlameRenderType.bindScene(s);
    // The original refraction uses opaque GrabPass replacement. Shared scene
    // coverage needs alpha composition instead, preserving all other materials.
    if(m.params[0]==5)s.setBlend(770,771,1,771);
    else {int src=blend(m.sourceBlend[0]),dst=blend(m.sourceBlend[1]);s.setBlend(src,dst,src,dst);}
    s.safeGetUniform("ParticleTint").set(tint);s.safeGetUniform("Custom").set(custom);
    float oldTime=hdr?preparedHdrTime:preparedTime;if(oldTime!=(float)time){s.safeGetUniform("EffectTime").set((float)time);if(hdr)preparedHdrTime=(float)time;else preparedTime=(float)time;}
    if((hdr?preparedHdr:prepared)==m)return;if(hdr)preparedHdr=m;else prepared=m;
    s.safeGetUniform("AdditiveEnergy").set(m.additive&&m.sourceBlend[0]==5?1f:0f);
    s.safeGetUniform("MainST").set(m.mainST);s.safeGetUniform("MaskST").set(m.maskST);s.safeGetUniform("NoiseST").set(m.noiseST);s.safeGetUniform("Scroll").set(m.scroll);
    s.safeGetUniform("Tint").set(m.tint);s.safeGetUniform("Params").set(m.params);s.safeGetUniform("Force").set(m.force);s.safeGetUniform("HeatTime").set(m.heatTime);
    s.safeGetUniform("Dis").set(number(m,"_Dis",1));s.safeGetUniform("ModelST").set(m.modelST);s.safeGetUniform("ModelMaskST").set(m.modelMaskST);
    s.safeGetUniform("ModelPan").set(number(m,"_ModelTex_X",0),number(m,"_ModelTex_Y",0),number(m,"_Modelmask_Y",0),number(m,"_Modelmask_X",0));
    JsonColor model=new JsonColor(m.source_colors,"_Model_Color");s.safeGetUniform("ModelColor").set(model.r,model.g,model.b,1f);
    s.safeGetUniform("Refraction").set(number(m,"_noise",0),number(m,"_refraction_power",0),number(m,"_opacity",0),0f);
    s.safeGetUniform("LegacyMode").set(m.shaderName.equals("Legacy Shaders/Particles/Alpha Blended Premultiply")?1f:0f);
  }
  private static final class JsonColor {float r=1,g=1,b=1;JsonColor(com.google.gson.JsonObject source,String name){if(source!=null&&source.has(name)){var c=source.getAsJsonObject(name);r=c.get("r").getAsFloat();g=c.get("g").getAsFloat();b=c.get("b").getAsFloat();}}}
  private static float number(SkinWorldFlames.Material m,String key,float fallback){return m.source_floats==null?fallback:m.source_floats.getOrDefault(key,fallback);}
  private static void copyScene(){WorldFlameRenderType.bindScene(shader);}
  /** Unique simple class and method names avoid Forge's simple-name wrapper collision. */
  @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
  public static final class ThreeSkinWorldShaderRegistration {
    @SubscribeEvent public static void registerThreeSkinWorldFlameShader(RegisterShadersEvent event)throws IOException{
      event.registerShader(new MaterialBlendShader(event.getResourceProvider(),new ResourceLocation("judgfire","skin_world_native"),DefaultVertexFormat.POSITION_COLOR_TEX),s->{shader=(MaterialBlendShader)s;TYPES.clear();WRAPS.clear();prepared=null;preparedTime=Float.NaN;com.mojang.logging.LogUtils.getLogger().info("Three-skin world native shader registered");});
      event.registerShader(new MaterialBlendShader(event.getResourceProvider(),new ResourceLocation("judgfire","skin_world_hdr"),FlameRenderType.HDR_FORMAT),s->{hdrShader=(MaterialBlendShader)s;HDR_TYPES.clear();WRAPS.clear();preparedHdr=null;preparedHdrTime=Float.NaN;});
    }
  }
}
