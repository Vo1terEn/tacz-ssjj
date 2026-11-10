package cn.ssjjdimension.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Original constant body particles; no skill activation, gameplay or network state. */
public final class DimensionParticleEffects {
    private record Material(String texture,String render) {}
    public static final String BONE="weapon_particle_effect";
    private static final long START=System.nanoTime();
    private static volatile java.util.Map<String,DimensionParticles.Data> profiles=java.util.Map.of();
    private static final java.util.Map<BedrockAnimatedModel,DimensionParticles.Data> bindings=new java.util.WeakHashMap<>();
    public static void bind(BedrockAnimatedModel model,String profile){var chosen=profiles.get(profile);if(chosen==null)bindings.remove(model);else bindings.put(model,chosen);}
    private DimensionParticleEffects() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)DimensionParticleEffects::read);
        }
    }
    public static void read(ResourceManager resources) {
        profiles=java.util.Map.of();bindings.clear();
        var fresh=new java.util.LinkedHashMap<String,DimensionParticles.Data>();
        for(String profile:DimensionMeshRenderer.profileKeys()) {
            String file=profile.equals("dimension_star2")?"effects/particles.json":"effects/particles_"+profile+".json";
            try(var stream=resources.open(new ResourceLocation("ssjj_dimension",file));var text=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var selected=new Gson().fromJson(text,DimensionParticles.Data.class);DimensionParticles.validate(selected);
                for(var e:selected.emitters)if(resources.getResource(new ResourceLocation(e.texture)).isEmpty())throw new IllegalArgumentException("Missing particle texture "+e.texture);
                fresh.put(profile,selected);
            } catch(Exception failure){LogUtils.getLogger().error("Cannot load Dimension Star particles "+profile,failure);}
        }
        profiles=java.util.Map.copyOf(fresh);
        LogUtils.getLogger().info("DIMENSION_FAMILY particle profiles loaded: {}",fresh.size());
    }
    public static void defer(BedrockAnimatedModel model,String bone,PoseStack pose) {
        var selected=bindings.getOrDefault(model,profiles.get("dimension_star2"));if(selected==null || !selected.bone.equals(bone))return;
        Matrix4f captured=new Matrix4f(pose.last().pose());Matrix3f normal=new Matrix3f(pose.last().normal());
        float clock=(System.nanoTime()-START)/1e9f;
        model.delegateRender((unused,consumer,context,light,overlay)->{
            Matrix3f inverse=new Matrix3f(captured).invert();
            Vector3f right=inverse.transform(new Vector3f(1,0,0)).normalize(),up=inverse.transform(new Vector3f(0,1,0)).normalize();
            PoseStack local=new PoseStack();local.last().pose().set(captured);local.last().normal().set(normal);
            var buffers=net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
            // The actual camera origin in this animated bone's local coordinates.
            // Its offset matters for perspective-aligned barrel streaks.
            Vector3f cameraPosition=new Matrix4f(captured).invert().transformPosition(new Vector3f());
            var batches=DimensionParticles.sample(selected,clock,right,up,cameraPosition);
            var groups=new java.util.LinkedHashMap<Material,java.util.List<DimensionParticles.Batch>>();
            for(var batch:batches)groups.computeIfAbsent(new Material(batch.texture(),batch.render()),key->new java.util.ArrayList<>()).add(batch);
            for(var entry:groups.entrySet()) {
                var type=DimensionEffectRenderType.of(new ResourceLocation(entry.getKey().texture()),"additive".equals(entry.getKey().render()));
                var out=buffers.getBuffer(type);
                for(var batch:entry.getValue()) {
                    var mesh=batch.mesh();float[] c=batch.color(),uvST=batch.uvST();
                    for(int[] face:mesh.triangles)for(int corner=0;corner<4;corner++) {
                        int i=face[Math.min(corner,2)];float[] p=mesh.positions[i],uv=mesh.uv[i];
                        out.vertex(local.last().pose(),p[0],p[1],p[2]).color(Math.min(1,c[0]),Math.min(1,c[1]),Math.min(1,c[2]),Math.min(1,c[3]))
                            .uv(uv[0]*uvST[0]+uvST[2],uv[1]*uvST[1]-uvST[3]).overlayCoords(0).uv2(15728880).normal(local.last().normal(),0,0,1).endVertex();
                    }
                }
                buffers.endBatch(type);
            }
        });
    }
}
