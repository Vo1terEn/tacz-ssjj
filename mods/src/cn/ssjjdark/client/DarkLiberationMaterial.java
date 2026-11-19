package cn.ssjjdark.client;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.shaders.Uniform;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** The old native shader, with its original three textures and time-dependent mask/noise uniforms. */
public final class DarkLiberationMaterial {
    public static final class Source {
        public String[] textures;public float[][] st;public float[] scroll,noiseScroll;
        public float brightness=1,alpha=1,contrast=1,warp;public boolean masked,nativeLinearLut;
    }
    private static final Map<DarkSkinParticles.Emitter,Material> MATERIALS=new IdentityHashMap<>();
    private static final java.util.Set<DarkSkinParticles.Emitter> GROUND=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private record BasicKey(String texture,String blend,boolean depthWrite) {}
    private static final Map<BasicKey,RenderType> BASICS=new HashMap<>();
    private static ShaderInstance shader;
    private static final class Material {
        final Source source;final DarkSkinParticles.Emitter emitter;final RenderType type;final ResourceLocation[] textures;float clock;
        Material(Source source,DarkSkinParticles.Emitter emitter){this.source=source;this.emitter=emitter;textures=java.util.Arrays.stream(source.textures).map(ResourceLocation::new).toArray(ResourceLocation[]::new);type=new NativeType(this);}
    }
    private static final class NativeType extends RenderType {
        NativeType(Material material){super("dark_liberation_source_material",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.TRIANGLES,65536,false,false,()->setup(material),DarkLiberationMaterial::clearState);}
    }
    /** Texture/state keys retain no emitter or large particle lifetime tables after LRU eviction. */
    private static final class BasicType extends RenderType {
        BasicType(BasicKey key,RenderType shared){super("dark_liberation_source_basic",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.TRIANGLES,65536,false,false,()->{
            shared.setupRenderState();RenderSystem.depthMask(key.depthWrite);RenderSystem.colorMask(true,true,true,false);RenderSystem.polygonOffset(-1,-1);
        },()->{try{shared.clearRenderState();}finally{RenderSystem.depthMask(true);RenderSystem.colorMask(true,true,true,true);}});}
    }
    private DarkLiberationMaterial(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent public static void register(RegisterShadersEvent event)throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("ssjj_dark","native_skill"),DefaultVertexFormat.POSITION_TEX_COLOR),value->shader=value);
        }
    }
    public static void reset(){MATERIALS.clear();BASICS.clear();GROUND.clear();}
    public static void unbind(DarkSkinParticles.Data data){if(data!=null)for(var emitter:data.emitters){MATERIALS.remove(emitter);GROUND.remove(emitter);}}
    public static void bind(DarkSkinParticles.Data data,JsonObject json,ResourceManager resources)throws IOException {
        var emitters=json.getAsJsonArray("emitters");if(emitters.size()!=data.emitters.length)throw new IOException("Material emitter count mismatch");
        var loaded=new IdentityHashMap<DarkSkinParticles.Emitter,Material>();
        var ground=java.util.Collections.newSetFromMap(new IdentityHashMap<DarkSkinParticles.Emitter,Boolean>());
        for(int i=0;i<data.emitters.length;i++){
            var metadata=emitters.get(i).getAsJsonObject();if(metadata.has("presentationGround")&&metadata.get("presentationGround").getAsBoolean()){if(!"mesh".equals(data.emitters[i].mode))throw new IOException("Ground presentation must retain original mesh");ground.add(data.emitters[i]);}
            var value=metadata.get("sourceMaterial");if(value==null||value.isJsonNull())continue;
            var source=new Gson().fromJson(value,Source.class);if(source.textures==null||source.textures.length!=3||source.st==null||source.st.length!=3||source.scroll==null||source.scroll.length!=2||source.noiseScroll==null||source.noiseScroll.length!=2)throw new IOException("Invalid source skill material");
            for(var vector:source.st)if(vector.length!=4)throw new IOException("Invalid source texture transform");
            for(var texture:source.textures)if(resources.getResource(new ResourceLocation(texture)).isEmpty())throw new IOException("Missing original skill material texture "+texture);
            loaded.put(data.emitters[i],new Material(source,data.emitters[i]));
        }
        MATERIALS.putAll(loaded);
        GROUND.addAll(ground);
    }
    public static boolean nativeMaterial(DarkSkinParticles.Emitter emitter){return MATERIALS.containsKey(emitter);}
    public static boolean nativeLinear(DarkSkinParticles.Emitter emitter){var material=MATERIALS.get(emitter);return material!=null&&material.source.nativeLinearLut;}
    public static boolean ground(DarkSkinParticles.Emitter emitter){return GROUND.contains(emitter);}
    public static RenderType type(DarkSkinParticles.Emitter emitter,float clock){var material=MATERIALS.get(emitter);if(material==null)return BASICS.computeIfAbsent(new BasicKey(emitter.texture,emitter.render,emitter.depthWrite),key->new BasicType(key,cn.ssjjdimension.client.DimensionEffectRenderType.of(new ResourceLocation(key.texture),"additive".equals(key.blend))));material.clock=clock;return material.type;}
    private static void vec(String name,float[] values){Uniform uniform=shader.getUniform(name);if(uniform==null)return;if(values.length==2)uniform.set(values[0],values[1]);else uniform.set(values[0],values[1],values[2],values[3]);}
    private static void scalar(String name,float value){var uniform=shader.getUniform(name);if(uniform!=null)uniform.set(value);}
    private static void setup(Material material){
        if(shader==null)throw new IllegalStateException("Original liberation source shader unavailable");
        var source=material.source;RenderSystem.setShader(()->shader);
        for(int i=0;i<3;i++)RenderSystem.setShaderTexture(i,material.textures[i]);
        vec("MainST",source.st[0]);vec("MaskST",source.st[1]);vec("NoiseST",source.st[2]);vec("Scroll",source.scroll);vec("NoiseScroll",source.noiseScroll);
        vec("Tiles",new float[]{1,1});vec("Cell",new float[]{0,0});scalar("Clock",material.clock);scalar("Brightness",source.brightness);scalar("Alpha",source.alpha);scalar("Contrast",source.contrast);scalar("Warp",source.warp);scalar("Masked",source.masked?1:0);
        RenderSystem.enableBlend();RenderSystem.blendFunc(GL11.GL_SRC_ALPHA,"additive".equals(material.emitter.render)?GL11.GL_ONE:GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(material.emitter.depthWrite);RenderSystem.disableCull();RenderSystem.colorMask(true,true,true,false);
    }
    private static void clearState(){RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.colorMask(true,true,true,true);}
    /** Native shader takes source UVs and fully transformed camera-space vertices. */
    public static VertexConsumer consumer(VertexConsumer target){return new SourceConsumer(target);}
    private static final class SourceConsumer implements VertexConsumer {
        final VertexConsumer target;SourceConsumer(VertexConsumer target){this.target=target;}
        @Override public void vertex(float x,float y,float z,float red,float green,float blue,float alpha,float u,float v,int overlay,int light,float nx,float ny,float nz){target.vertex(x,y,z).uv(u,1-v).color(red,green,blue,alpha).endVertex();}
        @Override public VertexConsumer vertex(double x,double y,double z){target.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){target.color(r,g,b,a);return this;}
        @Override public VertexConsumer uv(float u,float v){target.uv(u,1-v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){return this;}
        @Override public VertexConsumer uv2(int u,int v){return this;}
        @Override public VertexConsumer normal(float x,float y,float z){return this;}
        @Override public void endVertex(){target.endVertex();}
        @Override public void defaultColor(int r,int g,int b,int a){target.defaultColor(r,g,b,a);}
        @Override public void unsetDefaultColor(){target.unsetDefaultColor();}
    }
}
