package cn.ssjjzeus.client;

import cn.ssjjdimension.client.DimensionParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Static source mesh vertices live on the GPU. Only authored particle state changes per draw. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ZeusParticleMeshes extends RenderType {
    private record Material(String texture,boolean additive) {}
    private static final IdentityHashMap<DimensionParticles.Mesh,VertexBuffer> MESHES=new IdentityHashMap<>();
    private static final IdentityHashMap<DimensionParticles.Emitter,DimensionParticles.Data> SINGLE=new IdentityHashMap<>();
    private static final Map<Material,RenderType> MATERIALS=new HashMap<>();
    private static ShaderInstance additiveShader,alphaShader;
    // BufferBuilder owns malloc-backed memory without a GC cleaner. Reuse one
    // staging allocation across meshes/reloads instead of losing one per bake.
    private static BufferBuilder staging;
    private static final TransparencyStateShard ADDITIVE=new TransparencyStateShard("zeus_source_additive",()->{RenderSystem.enableBlend();RenderSystem.blendFunc(770,1);},()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private ZeusParticleMeshes(){super("zeus_static_particle",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,()->{},()->{});}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void shaders(RegisterShadersEvent e) {
            additiveShader=null;alphaShader=null;clear();
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("ssjj_zeus","static_particle"),DefaultVertexFormat.NEW_ENTITY),s->additiveShader=s);
                e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("ssjj_zeus","static_particle_alpha"),DefaultVertexFormat.NEW_ENTITY),s->alphaShader=s);
            } catch(java.io.IOException failure){com.mojang.logging.LogUtils.getLogger().warn("Zeus static particle shader unavailable; retaining native source effects",failure);}
        }
    }
    public static void clear() {
        Runnable release=()->{for(var buffer:MESHES.values())buffer.close();MESHES.clear();SINGLE.clear();MATERIALS.clear();};
        if(RenderSystem.isOnRenderThread())release.run();else RenderSystem.recordRenderCall(release::run);
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel().isClientSide())clear();}
    public static DimensionParticles.Data single(DimensionParticles.Data data,DimensionParticles.Emitter emitter) {
        return SINGLE.computeIfAbsent(emitter,e->{var d=new DimensionParticles.Data();d.schema=data.schema;d.gun=data.gun;d.bone=data.bone;d.emitters=new DimensionParticles.Emitter[]{e};return d;});
    }
    private static RenderType material(String texture,boolean additive) {
        return MATERIALS.computeIfAbsent(new Material(texture,additive),k->create("zeus_static_particle",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->k.additive?additiveShader:alphaShader)).setTextureState(new TextureStateShard(new ResourceLocation(k.texture),true,false))
                .setTransparencyState(k.additive?ADDITIVE:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).setLayeringState(POLYGON_OFFSET_LAYERING).createCompositeState(false)));
    }
    /** Shaderpack substitutions must use the original sampler/vertex path. */
    public static boolean ready(DimensionParticles.Data data) {
        if(additiveShader==null||alphaShader==null)return false;
        for(var emitter:data.emitters)if("mesh".equals(emitter.mode)) {
            boolean additive="additive".equals(emitter.render);var type=material(emitter.texture,additive);type.setupRenderState();
            boolean nativeShader=RenderSystem.getShader()==(additive?additiveShader:alphaShader);type.clearRenderState();if(!nativeShader)return false;
        }
        return true;
    }
    private static VertexBuffer mesh(DimensionParticles.Mesh source) {
        return MESHES.computeIfAbsent(source,m->{
            if(staging==null)staging=new BufferBuilder(65536);
            var builder=staging;builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            for(int[] face:m.triangles)for(int corner=0;corner<4;corner++){int i=face[Math.min(corner,2)];var p=m.positions[i];var uv=m.uv[i];builder.vertex(p[0],p[1],p[2],1,1,1,1,uv[0],uv[1],0,15728880,0,0,1);}
            var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(builder.end());VertexBuffer.unbind();return buffer;
        });
    }
    // Match native VertexConsumer.color(float): truncate to an unsigned byte.
    private static float byteColor(float c){return ((int)(Math.min(1,c)*255)&255)/255f;}
    public static void draw(DimensionParticles.Emitter emitter,float seconds,PoseStack pose) {
        boolean additive="additive".equals(emitter.render);var type=material(emitter.texture,additive);var shader=additive?additiveShader:alphaShader;
        VertexBuffer buffer=null;type.setupRenderState();
        try {
            shader.safeGetUniform("EffectMat").set(pose.last().pose());
            for(var particle:emitter.particles) {
                int first=Math.max(0,(int)Math.floor((seconds-particle.birth-particle.life)/emitter.duration)+1),last=(int)Math.floor((seconds-particle.birth)/emitter.duration);
                for(int cycle=first;cycle<=last;cycle++) {
                    float age=seconds-(particle.birth+cycle*emitter.duration);if(age<0||age>=particle.life)continue;
                    var s=DimensionParticles.state(particle,age/particle.life);if(s[13]<=.0001f)continue;
                    if(buffer==null){buffer=mesh(emitter.geometry);buffer.bind();}
                    shader.safeGetUniform("ParticleMat").set(new Matrix4f().translation(s[0],s[1],s[2]).rotate(new Quaternionf(s[6],s[7],s[8],s[9])).scale(s[3],s[4],s[5]));
                    shader.safeGetUniform("ParticleColor").set(byteColor(s[10]),byteColor(s[11]),byteColor(s[12]),byteColor(s[13]));
                    shader.safeGetUniform("ParticleUV").set(s[14],s[15],s[16],s[17]);
                    buffer.drawWithShader(RenderSystem.getModelViewMatrix(),RenderSystem.getProjectionMatrix(),shader);
                }
            }
        } finally {VertexBuffer.unbind();type.clearRenderState();}
    }
}
