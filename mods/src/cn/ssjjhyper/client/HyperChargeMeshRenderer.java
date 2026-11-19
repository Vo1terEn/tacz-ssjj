package cn.ssjjhyper.client;

import cn.ssjjdimension.client.DimensionParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Same-frame source-local VBOs shared by all targets. No target, particle, or pass is discarded. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperChargeMeshRenderer extends RenderType {
    private record Material(String texture,boolean additive) {}
    private record Segment(Material material,VertexBuffer buffer) {}
    private static final Map<Material,RenderType> MATERIALS=new HashMap<>();
    private static final IdentityHashMap<DimensionParticles.Batch,List<Segment>> FRAME=new IdentityHashMap<>();
    private static final ArrayList<VertexBuffer> POOL=new ArrayList<>();
    private static ShaderInstance additiveShader,alphaShader;
    private static int used;
    private static boolean checkedShaders,usableShaders;
    private static final TransparencyStateShard ADDITIVE=new TransparencyStateShard("hyper_source_additive",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(770,1);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private HyperChargeMeshRenderer(){super("hyper_shared_effect",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,()->{},()->{});}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void shaders(RegisterShadersEvent e) {
            additiveShader=null;alphaShader=null;clear();MATERIALS.clear();
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("ssjj_hyper","shared_effect"),DefaultVertexFormat.NEW_ENTITY),s->additiveShader=s);
                e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("ssjj_hyper","shared_effect_alpha"),DefaultVertexFormat.NEW_ENTITY),s->alphaShader=s);
            } catch(java.io.IOException error) {com.mojang.logging.LogUtils.getLogger().warn("Shared Hyper effect shader unavailable; retaining native effect drawing",error);}
        }
    }
    public static boolean ready(){return additiveShader!=null&&alphaShader!=null;}
    public static void beginFrame(){FRAME.clear();used=0;checkedShaders=false;}
    public static void endFrame(){
        VertexBuffer.unbind();FRAME.clear();
        // Retain only buffers used this frame, releasing a previous large burst immediately.
        while(POOL.size()>used)POOL.remove(POOL.size()-1).close();
    }
    private static void clear(){
        if(RenderSystem.isOnRenderThread()){beginFrame();endFrame();}
        else RenderSystem.recordRenderCall(()->{beginFrame();endFrame();});
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel().isClientSide())clear();}
    private static RenderType material(Material m) {
        return MATERIALS.computeIfAbsent(m,k->create("hyper_shared_effect",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->k.additive?additiveShader:alphaShader))
                .setTextureState(new TextureStateShard(new ResourceLocation(k.texture),true,false))
                .setTransparencyState(k.additive?ADDITIVE:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).setLayeringState(POLYGON_OFFSET_LAYERING).createCompositeState(false)));
    }
    private static List<Segment> prepare(List<DimensionParticles.Batch> batches) {
        var key=batches.get(0);var cached=FRAME.get(key);if(cached!=null)return cached;
        var result=new ArrayList<Segment>();
        for(var run:HyperChargeMeshPlan.compile(batches)) {
            if(used==POOL.size())POOL.add(new VertexBuffer(VertexBuffer.Usage.DYNAMIC));
            var buffer=POOL.get(used++);buffer.bind();buffer.upload(run.buffer());
            result.add(new Segment(new Material(run.texture(),run.additive()),buffer));
        }
        FRAME.put(key,result);return result;
    }
    public static boolean draw(List<DimensionParticles.Batch> batches,PoseStack pose,Vec3 center,Vec3 camera) {
        if(batches.isEmpty())return true;
        if(!checkedShaders){
            usableShaders=ready();
            for(boolean additive:new boolean[]{true,false}){
                var type=material(new Material(batches.get(0).texture(),additive));type.setupRenderState();
                usableShaders&=RenderSystem.getShader()==(additive?additiveShader:alphaShader);type.clearRenderState();
            }
            checkedShaders=true;
        }
        if(!usableShaders)return false;
        pose.pushPose();pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        try {
            for(var segment:prepare(batches)) {
                var type=material(segment.material());type.setupRenderState();
                try {
                    var shader=segment.material().additive?additiveShader:alphaShader;
                    // Transform Position before fog as well as projection, matching the original CPU path.
                    shader.safeGetUniform("EffectMat").set(pose.last().pose());
                    segment.buffer().bind();
                    segment.buffer().drawWithShader(RenderSystem.getModelViewMatrix(),RenderSystem.getProjectionMatrix(),shader);
                } finally {type.clearRenderState();}
            }
        } finally {pose.popPose();}
        return true;
    }
}
