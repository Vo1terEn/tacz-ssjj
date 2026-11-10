package cn.ssjjdimension.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Source static mesh effects: SrcAlpha/One, linear texture, no depth writes. */
public final class DimensionEffectRenderType extends RenderType {
    private record Material(ResourceLocation texture,boolean additive) {}
    private static final Map<Material,RenderType> CACHE=new HashMap<>();
    private static net.minecraft.client.renderer.ShaderInstance shader;
    private static net.minecraft.client.renderer.ShaderInstance alphaShader;
    public static void shader(net.minecraft.client.renderer.ShaderInstance value){shader=value;CACHE.clear();}
    public static void alphaShader(net.minecraft.client.renderer.ShaderInstance value){alphaShader=value;CACHE.clear();}
    private static final TransparencyStateShard ADDITIVE=new TransparencyStateShard("dimension_source_additive",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(770,1);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private DimensionEffectRenderType(){super("dimension_effect",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    public static RenderType of(ResourceLocation texture) {
        return of(texture,true);
    }
    public static RenderType of(ResourceLocation texture,boolean additive) {
        return CACHE.computeIfAbsent(new Material(texture,additive),t->create("dimension_source_effect",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->t.additive?shader:alphaShader)).setTextureState(new TextureStateShard(t.texture,true,false))
                .setTransparencyState(t.additive?ADDITIVE:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setLayeringState(POLYGON_OFFSET_LAYERING).createCompositeState(false)));
    }
}
