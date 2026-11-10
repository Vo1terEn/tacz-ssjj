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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Native world materials. Refraction reads an independent scene snapshot, never its draw target. */
public final class WorldFlameRenderType extends RenderType {
  static MaterialBlendShader shader;
  private static TextureTarget scene;
  private static boolean sceneCaptured;
  private static long frame;
  static long frame(){return frame;}
  static void invalidateScene(){sceneCaptured=false;}
  private static final Map<String,RenderType> TYPES=new HashMap<>();
  private static final TransparencyStateShard ADD=new TransparencyStateShard("native_world_add",()->{
    RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(770,1,1,1);
  },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
  private WorldFlameRenderType(){super("native_world_unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
  private static final CullStateShard FRONT=new CullStateShard(true){public void setupRenderState(){RenderSystem.enableCull();GL11.glCullFace(GL11.GL_FRONT);}public void clearRenderState(){GL11.glCullFace(GL11.GL_BACK);RenderSystem.disableCull();}};
  static RenderType get(String[] textures,boolean additive,int cull){
    String key=Arrays.toString(textures)+additive+cull;
    return TYPES.computeIfAbsent(key,k->{
      var state=MultiTextureStateShard.builder();
      for(String texture:textures)state.add(new ResourceLocation(texture),true,false);
      return create("judgment_world_native",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,true,
        CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTextureState(state.build())
          .setTransparencyState(additive?ADD:TRANSLUCENT_TRANSPARENCY).setCullState(cull==1?FRONT:cull==2?CULL:NO_CULL)
          .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    });
  }
  static void beginWorldPass(){copyScene();}
  static void bindScene(ShaderInstance targetShader){copyScene();targetShader.setSampler("SceneSampler",scene.getColorTextureId());targetShader.safeGetUniform("ScreenSize").set((float)scene.width,(float)scene.height);}
  static void copyScene(){
    if(sceneCaptured){if(shader!=null){shader.setSampler("SceneSampler",scene.getColorTextureId());shader.safeGetUniform("ScreenSize").set((float)scene.width,(float)scene.height);}return;}
    var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();
    if(scene==null||scene.width!=target.width||scene.height!=target.height){
      if(scene!=null)scene.destroyBuffers();
      scene=new TextureTarget(target.width,target.height,false,Minecraft.ON_OSX);
      target.bindWrite(false);
    }
    int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    GlStateManager._activeTexture(GL13.GL_TEXTURE0+7);
    int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    GlStateManager._bindTexture(scene.getColorTextureId());
    GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,target.width,target.height);
    GlStateManager._bindTexture(binding);GlStateManager._activeTexture(active);
    sceneCaptured=true;
    if(shader!=null){shader.setSampler("SceneSampler",scene.getColorTextureId());shader.safeGetUniform("ScreenSize").set((float)target.width,(float)target.height);}
  }
  @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
  public static final class WorldFrameBoundary {
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void beginNativeWorldFrame(net.minecraftforge.client.event.RenderLevelStageEvent event){
      if(event.getStage()==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES){sceneCaptured=false;frame++;}
    }
  }
  @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
  public static final class Resources {
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws IOException{
      event.registerShader(new MaterialBlendShader(event.getResourceProvider(),new ResourceLocation("judgfire","world_native"),DefaultVertexFormat.POSITION_COLOR_TEX),s->{shader=(MaterialBlendShader)s;invalidateScene();});
    }
  }
}
