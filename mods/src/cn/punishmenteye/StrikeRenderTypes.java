package cn.punishmenteye;
import java.util.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
/** Explicit depth-tested world effect, never an entity outline or a through-wall pass. */
public final class StrikeRenderTypes extends RenderType {
 private StrikeRenderTypes(){super("eye_strike",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
 private static final Map<ResourceLocation,RenderType> TYPES=new HashMap<>();
 public static RenderType depth(ResourceLocation texture){return TYPES.computeIfAbsent(texture,t->create("eye_strike_depth",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER).setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setOverlayState(OVERLAY).createCompositeState(false)));}
}
