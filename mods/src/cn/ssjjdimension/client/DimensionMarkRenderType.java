package cn.ssjjdimension.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Source SrcAlpha/One additive and SrcAlpha/OneMinusSrcAlpha passes; depth tested. */
public final class DimensionMarkRenderType extends RenderType {
    private record Key(ResourceLocation texture,boolean additive) {}
    private static final Map<Key,RenderType> CACHE=new HashMap<>();
    private static final TransparencyStateShard ADDITIVE=new TransparencyStateShard("dimension_mark_additive",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(770,1);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private DimensionMarkRenderType() { super("dimension_mark",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
    public static RenderType of(ResourceLocation texture,boolean additive) {
        return CACHE.computeIfAbsent(new Key(texture,additive),key->create("dimension_target_source",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(key.texture,true,false)).setTransparencyState(key.additive?ADDITIVE:TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false)));
    }
}
