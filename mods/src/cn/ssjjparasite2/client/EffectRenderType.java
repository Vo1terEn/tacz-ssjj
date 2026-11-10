package cn.ssjjparasite2.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Source SrcAlpha/One, double-sided, depth-tested, color-only local pass. */
public final class EffectRenderType extends RenderType {
    private static final Map<ResourceLocation,RenderType> CACHE=new HashMap<>();
    private static final Map<ResourceLocation,RenderType> MARKER_CACHE=new HashMap<>();
    private static net.minecraft.client.renderer.ShaderInstance shader;
    public static void shader(net.minecraft.client.renderer.ShaderInstance value){shader=value;}
    private static final TransparencyStateShard SOURCE_ADDITIVE=new TransparencyStateShard("parasite_source_additive",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(770,1);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private EffectRenderType(){super("parasite_effect",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    // Vanilla NO_DEPTH_TEST is a no-op shard. Markers must explicitly override
    // the world depth state, then restore the normal world pass on completion.
    private static final DepthTestStateShard MARKER_DEPTH=new DepthTestStateShard("parasite_marker_depth",519) {
        @Override public void setupRenderState(){RenderSystem.disableDepthTest();}
        @Override public void clearRenderState(){RenderSystem.enableDepthTest();RenderSystem.depthFunc(515);}
    };
    public static RenderType marker(ResourceLocation texture) {
        return MARKER_CACHE.computeIfAbsent(texture,t->create("parasite_destruction_marker",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTextureState(new TextureStateShard(t,true,false))
                .setTransparencyState(SOURCE_ADDITIVE).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                .setDepthTestState(MARKER_DEPTH).createCompositeState(false)));
    }
    public static RenderType of(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture,t->create("parasite_source_effect",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,
            // Original soft glow textures need linear interpolation when enlarged.
            // Mipmaps remain disabled for these standalone animated effect textures.
            CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTextureState(new TextureStateShard(t,true,false))
                .setTransparencyState(SOURCE_ADDITIVE).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setLayeringState(POLYGON_OFFSET_LAYERING).createCompositeState(false)));
    }
}
