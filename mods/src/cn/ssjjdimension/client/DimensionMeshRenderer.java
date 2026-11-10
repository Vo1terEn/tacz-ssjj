package cn.ssjjdimension.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Source triangles remain joint-local; the native TaCZ model owns view and animation. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class DimensionMeshRenderer {
    public static final ResourceLocation GUN=new ResourceLocation("ssjj_dimension", "dimension_star2");
    public static final ResourceLocation DISPLAY=new ResourceLocation("ssjj_dimension", "dimension_star2_display");
    private static final ResourceLocation RESOURCE=new ResourceLocation("ssjj_dimension", "source_mesh.json");
    public static final class Part {
        public String bone, texture, render;
        public float[] color;
        public FxAnimation fx_animation;
        public float[][] positions, normals, uv;
        public int[][] triangles;
    }
    public static final class Geometry { public String texture; public Part[] parts; }
    public static final class FxAnimation { public float duration;public float[] uv_times,u,v; }
    private static final long START=System.nanoTime();
    private static volatile Geometry geometry;
    private static volatile Map<String,Geometry> profiles=Map.of();
    private record Material(String texture,boolean additive) {}
    private static final Map<BedrockAnimatedModel,Geometry> bound=new WeakHashMap<>();
    private static final Map<BedrockAnimatedModel,Geometry> rejected=new WeakHashMap<>();
    private DimensionMeshRenderer() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientResources {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws java.io.IOException {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("ssjj_dimension","dimension_effect"),com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),DimensionEffectRenderType::shader);
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("ssjj_dimension","dimension_effect_alpha"),com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),DimensionEffectRenderType::alphaShader);
        }
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)DimensionMeshRenderer::read);
        }
    }
    public static String[] profileKeys() {
        var keys=new ArrayList<String>();
        for(String gun:cn.ssjjdimension.DimensionScopes.GUNS) {
            keys.add(gun);for(String skin:cn.ssjjdimension.DimensionScopes.SKINS)keys.add(gun+"_"+skin);
        }
        return keys.toArray(String[]::new);
    }
    public static void read(ResourceManager resources) {
        geometry=null;profiles=Map.of();bound.clear();rejected.clear();
        Map<String,Geometry> fresh=new LinkedHashMap<>();
        for(String key:profileKeys()) {
            String file=key.equals("dimension_star2")?"source_mesh.json":"source_mesh_"+key+".json";
            try(var stream=resources.open(new ResourceLocation("ssjj_dimension",file));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                Geometry selected=new Gson().fromJson(reader,Geometry.class);validate(selected);
                for(Part part:selected.parts)if(effect(part) && resources.getResource(new ResourceLocation(part.texture)).isEmpty())
                    throw new IllegalArgumentException("Missing source effect texture "+part.texture);
                fresh.put(key,selected);
            } catch(Exception failure) {LogUtils.getLogger().error("Cannot load Dimension Star profile "+key,failure);}
        }
        profiles=Map.copyOf(fresh);geometry=fresh.get("dimension_star2");
        LogUtils.getLogger().info("DIMENSION_FAMILY source mesh profiles loaded: {}",fresh.size());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var entry:profiles.entrySet()) {
            String key=entry.getKey(),gun=key.split("_frozen|_neon|_royal")[0];Geometry selected=entry.getValue();
            TimelessAPI.getGunDisplay(new ResourceLocation("ssjj_dimension",key+"_display"),new ResourceLocation("ssjj_dimension",gun)).ifPresent(index->{
                BedrockAnimatedModel model=index.getGunModel();
                if(model==null || bound.get(model)==selected || rejected.get(model)==selected)return;
                try {attach(model,selected);DimensionParticleEffects.bind(model,key);bound.put(model,selected);}
                catch(RuntimeException failure){rejected.put(model,selected);LogUtils.getLogger().error("Cannot attach Dimension Star profile "+key,failure);}
            });
        }
    }
    public static void validate(Geometry data) {
        if(data==null || data.parts==null || data.parts.length==0)throw new IllegalArgumentException("No source mesh parts");
        String texture=data.texture;
        for(Part part:data.parts) {
            if(part==null || part.bone==null || part.bone.isBlank() || part.positions==null || part.normals==null || part.uv==null || part.triangles==null
                || part.positions.length==0 || part.triangles.length==0 || part.positions.length!=part.normals.length || part.positions.length!=part.uv.length)
                throw new IllegalArgumentException("Incomplete source mesh streams");
            // TaCZ supplies one display texture consumer. Explicit per-part names must identify the same atlas.
            if(part.render!=null && !part.render.equals("additive") && !part.render.equals("alpha"))throw new IllegalArgumentException("Unsupported source render mode");
            if(effect(part)) {
                if(part.texture==null || part.texture.isBlank())throw new IllegalArgumentException("Source effect has no texture");
                new ResourceLocation(part.texture);
            } else if(part.texture!=null && !part.texture.isBlank()) {
                if(texture==null)texture=part.texture;
                else if(!texture.equals(part.texture))throw new IllegalArgumentException("Bake multiple source textures into the display atlas");
            }
            if(part.color!=null) {
                vector(part.color,4);
                for(float value:part.color)if(value<0 || value>1)throw new IllegalArgumentException("Source effect color outside [0,1]");
            }
            if(part.fx_animation!=null) {
                var a=part.fx_animation;
                if(!Float.isFinite(a.duration) || a.duration<=0 || a.uv_times==null || a.u==null || a.v==null
                    || a.uv_times.length<2 || a.u.length!=a.uv_times.length || a.v.length!=a.u.length)
                    throw new IllegalArgumentException("Incomplete source UV animation");
                float previous=-1;
                for(int i=0;i<a.u.length;i++) {
                    float time=a.uv_times[i];
                    if(!Float.isFinite(time) || time<0 || time>1 || time<previous || !Float.isFinite(a.u[i]) || !Float.isFinite(a.v[i]))
                        throw new IllegalArgumentException("Invalid normalized source UV key");
                    previous=time;
                }
            }
            for(int i=0;i<part.positions.length;i++) {
                vector(part.positions[i],3);vector(part.normals[i],3);vector(part.uv[i],2);
            }
            for(int[] face:part.triangles) {
                if(face==null || face.length!=3)throw new IllegalArgumentException("Non-triangle source face");
                for(int index:face)if(index<0 || index>=part.positions.length)throw new IllegalArgumentException("Invalid source face index");
            }
        }
    }
    private static void vector(float[] values,int width) {
        if(values==null || values.length!=width)throw new IllegalArgumentException("Wrong source vector width");
        for(float value:values)if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite source vector");
    }
    public static void attach(BedrockAnimatedModel model,Geometry data) {
        validate(data);
        Map<String,List<Part>> groups=new LinkedHashMap<>();
        for(Part part:data.parts) {
            if(model.getNode(part.bone)==null)throw new IllegalArgumentException("Missing source joint "+part.bone);
            groups.computeIfAbsent(part.bone,key->new ArrayList<>()).add(part);
        }
        if(model.getNode(DimensionParticleEffects.BONE)==null)throw new IllegalArgumentException("Missing source particle anchor "+DimensionParticleEffects.BONE);
        groups.computeIfAbsent(DimensionParticleEffects.BONE,key->new ArrayList<>());
        lockRefitView(model);
        for(var entry:groups.entrySet()) {
            List<Part> parts=List.copyOf(entry.getValue());
            model.setFunctionalRenderer(entry.getKey(),node->(pose,consumer,context,light,overlay)->{
                for(Part part:parts)if(!effect(part))emit(part,pose,consumer,light,overlay);
                deferEffects(model,parts,pose);
                DimensionParticleEffects.defer(model,entry.getKey(),pose);
                for(var child:node.children)child.render(pose,context,consumer,light,overlay);
            });
        }
        DimensionNativeHands.install(model);
    }
    public static boolean additive(Part part) {return "additive".equals(part.render);}
    public static boolean effect(Part part) {return part.render!=null;}
    private static void deferEffects(BedrockAnimatedModel model,List<Part> parts,PoseStack pose) {
        Map<Material,List<Part>> groups=new LinkedHashMap<>();
        for(Part part:parts)if(effect(part))groups.computeIfAbsent(new Material(part.texture,additive(part)),key->new ArrayList<>()).add(part);
        if(groups.isEmpty())return;
        var captured=new org.joml.Matrix4f(pose.last().pose());
        var normal=new org.joml.Matrix3f(pose.last().normal());
        float time=(System.nanoTime()-START)/1e9f;
        // TaCZ submits delegates only after its main texture consumer is flushed.
        model.delegateRender((unused,consumer,context,light,overlay)->{
            PoseStack local=new PoseStack();local.last().pose().set(captured);local.last().normal().set(normal);
            var buffers=net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
            for(var entry:groups.entrySet()) {
                var type=DimensionEffectRenderType.of(new ResourceLocation(entry.getKey().texture()),entry.getKey().additive());var out=buffers.getBuffer(type);
                for(Part part:entry.getValue())emitTimed(part,local,out,15728880,0,time);
                buffers.endBatch(type);
            }
        });
    }
    public static float[] uvOffset(Part part,float seconds) {
        var a=part.fx_animation;if(a==null)return new float[]{0,0};
        float phase=(seconds%a.duration+a.duration)%a.duration/a.duration;
        int at=0;
        while(at+1<a.uv_times.length && phase>=a.uv_times[at+1])at++;
        if(at+1==a.uv_times.length)return new float[]{a.u[at],a.v[at]};
        float width=a.uv_times[at+1]-a.uv_times[at];
        float mix=width<=0?1:Math.max(0,Math.min(1,(phase-a.uv_times[at])/width));
        return new float[]{a.u[at]+mix*(a.u[at+1]-a.u[at]),a.v[at]+mix*(a.v[at+1]-a.v[at])};
    }
    /** Keep the complete new gun visible while selecting every refit slot. */
    public static void lockRefitView(BedrockAnimatedModel model) {
        if(!(model instanceof BedrockGunModel gun))return;
        try {
            var field=BedrockGunModel.class.getDeclaredField("refitAttachmentViewPath");field.setAccessible(true);
            @SuppressWarnings("unchecked") var paths=(Map<AttachmentType,List<BedrockPart>>)field.get(gun);
            var full=gun.getRefitAttachmentViewPath(AttachmentType.NONE);
            if(full==null || full.isEmpty())throw new IllegalArgumentException("Missing full-gun refit camera path");
            for(var type:AttachmentType.values())paths.put(type,full);
        } catch(ReflectiveOperationException failure) {throw new IllegalStateException("Cannot establish Dimension Star refit view",failure);}
    }
    public static void emit(Part part,PoseStack pose,VertexConsumer consumer,int light,int overlay) {
        emitTimed(part,pose,consumer,light,overlay,(System.nanoTime()-START)/1e9f);
    }
    public static void emitTimed(Part part,PoseStack pose,VertexConsumer consumer,int light,int overlay,float seconds) {
        float[] c=part.color==null?new float[]{1,1,1,1}:part.color,offset=uvOffset(part,seconds);
        for(int[] face:part.triangles)for(int corner=0;corner<4;corner++) {
            int i=face[Math.min(corner,2)];float[] p=part.positions[i],n=part.normals[i],uv=part.uv[i];
            consumer.vertex(pose.last().pose(),p[0],p[1],p[2]).color(c[0],c[1],c[2],c[3]).uv(uv[0]+offset[0],uv[1]-offset[1])
                .overlayCoords(overlay).uv2(light).normal(pose.last().normal(),n[0],n[1],n[2]).endVertex();
        }
    }
}
