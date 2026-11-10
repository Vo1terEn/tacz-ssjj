package cn.judgfire;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.*;
import com.google.common.collect.ImmutableMap;
import org.joml.Matrix4f;

/** Fullbright source fire; tests scene depth, never writes transparent depth. */
public final class FlameRenderType extends RenderType {
  static MaterialBlendShader shader;
  static MaterialBlendShader hdrShader;
  /** Float colors retain source HDR values while allowing one ordered particle batch. */
  static final VertexFormat HDR_FORMAT = new VertexFormat(ImmutableMap.of(
    "Position", DefaultVertexFormat.ELEMENT_POSITION,
    "Color", new VertexFormatElement(0,VertexFormatElement.Type.FLOAT,VertexFormatElement.Usage.COLOR,4),
    "UV0", DefaultVertexFormat.ELEMENT_UV0));
  // Exported material arrays are immutable. Identity lookup avoids formatting a
  // string key and cloning filter arrays for every particle of every frame.
  private static final IdentityHashMap<String[],RenderType[]> TYPES = new IdentityHashMap<>();
  private static final TransparencyStateShard ADD = new TransparencyStateShard("native_fire_add", () -> {
    RenderSystem.enableBlend(); RenderSystem.blendFuncSeparate(770,1,1,1);
  }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
  private static final LayeringStateShard CUSTOM_POLYGON_OFFSET = new LayeringStateShard("ssjj_custom_source_offset", () -> {
    RenderSystem.polygonOffset(-1f,-1f);RenderSystem.enablePolygonOffset();
  }, () -> {RenderSystem.disablePolygonOffset();RenderSystem.polygonOffset(0f,0f);});
  private FlameRenderType() { super("native_fire",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
  static RenderType get(String[] textures, boolean additive) {
    return get(textures,additive,null,false);
  }
  /** Unity filter0=point,1=bilinear,2=trilinear. Exported PNGs have only level0, so filter2
   * degenerates to bilinear until a real mip chain is supplied; never enable incomplete mips.
   * Address modes are per-draw shader uniforms, not part of this cached state. */
  static RenderType get(String[] textures,boolean additive,int[] sourceFilters,boolean customPolygonOffset) {
    return cached(textures,additive,sourceFilters,customPolygonOffset,false);
  }
  static RenderType getHdr(String[] textures,boolean additive,int[] sourceFilters,boolean customPolygonOffset) {
    return cached(textures,additive,sourceFilters,customPolygonOffset,true);
  }
  private static RenderType cached(String[] textures,boolean additive,int[] sourceFilters,boolean customPolygonOffset,boolean hdr) {
    if(textures==null||textures.length!=3)throw new IllegalArgumentException("Expected 3 native textures");
    if(sourceFilters!=null&&sourceFilters.length!=3)throw new IllegalArgumentException("Expected 3 native filters");
    int f0=sourceFilters==null?1:sourceFilters[0],f1=sourceFilters==null?1:sourceFilters[1],f2=sourceFilters==null?1:sourceFilters[2];
    if(f0<0||f0>2||f1<0||f1>2||f2<0||f2>2)throw new IllegalArgumentException("Invalid Unity filters");
    int index=f0+3*f1+9*f2+(additive?27:0)+(customPolygonOffset?54:0)+(hdr?108:0);
    RenderType[] variants=TYPES.get(textures);
    if(variants==null){variants=new RenderType[216];TYPES.put(textures,variants);}
    RenderType result=variants[index];
    if(result==null){
      var state=CompositeState.builder().setShaderState(new ShaderStateShard(()->{
            // BufferUploader invokes ShaderInstance.apply after this RenderType's
            // transparency state. Restore this material after the shader JSON blend.
            MaterialBlendShader selected=hdr?hdrShader:shader;
            if(selected!=null)selected.setBlend(770,additive?1:771,1,additive?1:771);
            return selected;
          }))
          .setTextureState(MultiTextureStateShard.builder()
            .add(new ResourceLocation(textures[0]),f0!=0,false)
            .add(new ResourceLocation(textures[1]),f1!=0,false)
            .add(new ResourceLocation(textures[2]),f2!=0,false).build())
          .setTransparencyState(additive?ADD:TRANSLUCENT_TRANSPARENCY)
          .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE);
      if(customPolygonOffset)state.setLayeringState(CUSTOM_POLYGON_OFFSET);
      result=create(hdr?"judgment_native_fire_hdr":"judgment_native_fire",hdr?HDR_FORMAT:DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,65536,false,!hdr,state.createCompositeState(false));
      variants[index]=result;
    }
    return result;
  }
  static void vertexHdr(VertexConsumer v,Matrix4f pose,float x,float y,float z,float[] rgba,float u,float vv){
    v.vertex(pose,x,y,z);
    // VertexConsumer.color(float...) encodes bytes, even with a float format.
    // Write all four source floats directly to avoid clipping HDR alpha/color.
    BufferVertexConsumer b=(BufferVertexConsumer)v;
    b.putFloat(0,rgba[0]);b.putFloat(4,rgba[1]);b.putFloat(8,rgba[2]);b.putFloat(12,rgba[3]);b.nextElement();
    v.uv(u,vv).endVertex();
  }
  @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
  public static final class BodyShaderRegistration {
    @SubscribeEvent public static void registerBodyFlameShader(RegisterShadersEvent event) throws IOException {
      event.registerShader(new MaterialBlendShader(event.getResourceProvider(),new ResourceLocation("judgfire","flame_native"),DefaultVertexFormat.POSITION_COLOR_TEX),s->{shader=(MaterialBlendShader)s;com.mojang.logging.LogUtils.getLogger().info("Native body flame shader registered: {}",s.getName());});
      event.registerShader(new MaterialBlendShader(event.getResourceProvider(),new ResourceLocation("judgfire","flame_hdr"),HDR_FORMAT),s->{hdrShader=(MaterialBlendShader)s;TYPES.clear();});
    }
  }
}
