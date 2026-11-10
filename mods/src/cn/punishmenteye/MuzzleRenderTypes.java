package cn.punishmenteye;
import java.util.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
/** Native alpha and SRC_ALPHA/ONE layers, with soft edges and normal weapon occlusion. */
public final class MuzzleRenderTypes extends RenderType {
 private MuzzleRenderTypes(){super("eye_cavity",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
 private static final Map<String,RenderType> TYPES=new HashMap<>();
 private static final ShaderStateShard ALPHA_SHADER=new ShaderStateShard(()->MuzzleShaders.alpha),ADDITIVE_SHADER=new ShaderStateShard(()->MuzzleShaders.additive);
 private static final LayeringStateShard SOURCE_OFFSET=new LayeringStateShard("eye_source_offset",()->{RenderSystem.polygonOffset(-1,-1);RenderSystem.enablePolygonOffset();RenderSystem.colorMask(true,true,true,false);},()->{RenderSystem.disablePolygonOffset();RenderSystem.polygonOffset(0,0);RenderSystem.colorMask(true,true,true,true);});
 public static RenderType layer(ResourceLocation texture,String blend){return TYPES.computeIfAbsent(texture+"/"+blend,k->create("eye_cavity_"+blend,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,CompositeState.builder().setShaderState("additive".equals(blend)?ADDITIVE_SHADER:ALPHA_SHADER).setTextureState(new TextureStateShard(texture,true,false)).setTransparencyState("additive".equals(blend)?LIGHTNING_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setLayeringState(SOURCE_OFFSET).createCompositeState(false)));}
}
