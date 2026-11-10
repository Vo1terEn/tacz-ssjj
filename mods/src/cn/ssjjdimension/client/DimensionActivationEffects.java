package cn.ssjjdimension.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Continuous tier-II default layers, wrapped around the native gun renderer. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionActivationEffects {
    private static final ResourceLocation GUN=new ResourceLocation("ssjj_dimension","dimension_star2");
    private static final ResourceLocation DISPLAY=new ResourceLocation("ssjj_dimension","dimension_star2_display");
    private static volatile Map<String,DimensionActivationGeometry.Data> profiles=Map.of();
    private static LivingEntity renderingEntity;
    private DimensionActivationEffects() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerDimensionActivationResources(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)DimensionActivationEffects::reload);
        }
    }
    public static void reload(ResourceManager resources) {
        var fresh=new LinkedHashMap<String,DimensionActivationGeometry.Data>();
        for(int tier:List.of(1,2))for(String skin:cn.ssjjdimension.DimensionVisualScope.SKINS)for(String kind:List.of("active","active_p3")) {
            String profile=cn.ssjjdimension.DimensionVisualScope.profile(tier,skin);String key=profile+"_"+kind;
            try(var stream=resources.open(new ResourceLocation("ssjj_dimension","effects/activation/"+key+".json"));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var data=new Gson().fromJson(reader,DimensionActivationGeometry.Data.class);DimensionActivationGeometry.validate(data);
                if(!kind.equals(data.kind))throw new IllegalArgumentException("Activation kind differs");
                for(var emitter:data.emitters)if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IllegalArgumentException("Missing original activation texture "+emitter.texture);
                fresh.put(key,data);
            } catch(Exception error){LogUtils.getLogger().error("Cannot load tier-II default activation "+kind,error);}
        }
        profiles=Map.copyOf(fresh);renderingEntity=null;
        LogUtils.getLogger().info("DIMENSION_ACTIVATION source profiles loaded: {}",fresh.size());
    }
    /** Uses the same common identity gate as the scoped server field. */
    public static boolean defaultTier2(ItemStack stack) {return cn.ssjjdimension.DimensionVisualScope.defaultTier2(stack);}
    public static boolean accepts(ItemStack stack){return cn.ssjjdimension.DimensionVisualScope.accepts(stack)&&cn.ssjjdimension.DimensionVisualScope.tier(stack)>=1;}
    private static String profile(ItemStack stack){return cn.ssjjdimension.DimensionVisualScope.profile(cn.ssjjdimension.DimensionVisualScope.tier(stack),cn.ssjjdimension.DimensionVisualScope.skin(stack));}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void bindDimensionActivation(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||profiles.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){renderingEntity=null;return;}
        // The ordinary source renderer binds at NORMAL priority. Wrap only its particle
        // node afterwards; source triangles, normal particles and native hands stay intact.
        for(int tier:List.of(1,2))for(String skin:cn.ssjjdimension.DimensionVisualScope.SKINS) {
            String gun="dimension_star"+tier,profile=cn.ssjjdimension.DimensionVisualScope.profile(tier,skin);
            var display=new ResourceLocation("ssjj_dimension",gun+(skin.isEmpty()?"":"_"+skin)+"_display");
            TimelessAPI.getGunDisplay(display,new ResourceLocation("ssjj_dimension",gun)).ifPresent(index->{
                var model=index.getGunModel();if(model==null)return;
                if(model.getNode(DimensionParticleEffects.BONE) instanceof FunctionalBedrockPart node
                    &&node.functionalRenderer!=null&&!(node.functionalRenderer instanceof ActivationFactory))
                    node.functionalRenderer=new ActivationFactory(node.functionalRenderer,model,profile);
            });
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void beforeActivationLiving(RenderLivingEvent.Pre<?,?> event){renderingEntity=event.getEntity();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void afterActivationLiving(RenderLivingEvent.Post<?,?> event){renderingEntity=null;}
    private static boolean visible(ItemDisplayContext context) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!mc.player.isAlive()||DimensionFieldClient.activeTicks()<=0)return false;
        if(context.firstPerson()) {
            var renderer=KeepingItemRenderer.getRenderer();return renderer!=null&&accepts(renderer.getCurrentItem())&&accepts(mc.player.getMainHandItem());
        }
        return (context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
            &&renderingEntity==mc.player&&accepts(mc.player.getMainHandItem());
    }
    private static final class ActivationFactory implements Function<BedrockPart,IFunctionalRenderer> {
        private final Function<BedrockPart,IFunctionalRenderer> original;
        private final BedrockAnimatedModel model;private final String profile;
        ActivationFactory(Function<BedrockPart,IFunctionalRenderer> original,BedrockAnimatedModel model,String profile){this.original=original;this.model=model;this.profile=profile;}
        @Override public IFunctionalRenderer apply(BedrockPart node) {
            var nativeRenderer=original.apply(node);
            return (pose,consumer,context,light,overlay)->{
                nativeRenderer.render(pose,consumer,context,light,overlay);
                if(visible(context)&&profile.equals(profile(Minecraft.getInstance().player.getMainHandItem())))defer(model,pose,context.firstPerson(),profile);
            };
        }
    }
    private record Material(String texture,String render) {}
    private static List<DimensionParticles.Batch> sampleBody(Map<String,DimensionActivationGeometry.Data> selected,boolean firstPerson,float seconds,Vector3f right,Vector3f up) {
        return sampleBody(selected,firstPerson,seconds,right,up,"tier2");
    }
    private static List<DimensionParticles.Batch> sampleBody(Map<String,DimensionActivationGeometry.Data> selected,boolean firstPerson,float seconds,Vector3f right,Vector3f up,String profile) {
        return DimensionActivationGeometry.sample(selected.get(profile+(firstPerson?"_active":"_active_p3")),seconds,right,up);
    }
    private static void defer(BedrockAnimatedModel model,PoseStack pose,boolean firstPerson,String profile) {
        var captured=new Matrix4f(pose.last().pose());var normal=new Matrix3f(pose.last().normal());
        float seconds=DimensionFieldClient.activationSeconds(Minecraft.getInstance().getFrameTime());
        if(seconds<0)return;
        var selected=profiles;
        model.delegateRender((unused,consumer,context,light,overlay)->{
            // Re-check the authoritative local presentation state at deferred execution.
            if(DimensionFieldClient.activeTicks()<=0)return;
            var inverse=new Matrix3f(captured).invert();
            var right=inverse.transform(new Vector3f(1,0,0)).normalize();var up=inverse.transform(new Vector3f(0,1,0)).normalize();
            var batches=sampleBody(selected,firstPerson,seconds,right,up,profile);
            var groups=new LinkedHashMap<Material,List<DimensionParticles.Batch>>();
            for(var batch:batches)groups.computeIfAbsent(new Material(batch.texture(),batch.render()),key->new ArrayList<>()).add(batch);
            PoseStack local=new PoseStack();local.last().pose().set(captured);local.last().normal().set(normal);
            var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
            for(var entry:groups.entrySet()) {
                var type=DimensionEffectRenderType.of(new ResourceLocation(entry.getKey().texture()),"additive".equals(entry.getKey().render()));var out=buffers.getBuffer(type);
                for(var batch:entry.getValue()) {
                    var mesh=batch.mesh();float[] color=batch.color(),uv=batch.uvST();
                    for(var face:mesh.triangles)for(int corner=0;corner<4;corner++) {
                        int i=face[Math.min(corner,2)];var point=mesh.positions[i];var tex=mesh.uv[i];
                        out.vertex(local.last().pose(),point[0],point[1],point[2]).color(color[0],color[1],color[2],color[3])
                            .uv(tex[0]*uv[0]+uv[2],tex[1]*uv[1]-uv[3]).overlayCoords(0).uv2(15728880).normal(local.last().normal(),0,0,1).endVertex();
                    }
                }
                buffers.endBatch(type);
            }
        });
    }
}
