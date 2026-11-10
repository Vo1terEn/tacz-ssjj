package cn.ssjjdimension.client;

import cn.ssjjdimension.DimensionFieldNetwork;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Original lingyu source meshes remain at the authoritative fixed cast point. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionFieldEffects {
    private record Field(DimensionFieldNetwork.Visual message,long start,long until,String skin) {}
    private record Impact(DimensionFieldNetwork.Visual message,long start,String skin) {}
    private static final Map<UUID,Field> FIELDS=new LinkedHashMap<>();
    private static final List<Impact> IMPACTS=new ArrayList<>();
    private static final Map<String,DimensionFieldGeometry.Data> DATA=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    public static final int MAX_FIELDS=128,MAX_IMPACTS=64,FIELD_TICKS=144000;
    private DimensionFieldEffects() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerDimensionFieldEffects(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)DimensionFieldEffects::reload);
        }
    }
    public static void reload(ResourceManager resources) {
        DATA.clear();FIELDS.clear();IMPACTS.clear();
        for(int tier:List.of(1,2))for(String skin:cn.ssjjdimension.DimensionVisualScope.SKINS)for(String kind:List.of("field","impact")) {
            String key=cn.ssjjdimension.DimensionVisualScope.profile(tier,skin)+"_"+kind;
            try(var stream=resources.open(new ResourceLocation("ssjj_dimension","effects/field/"+key+".json"));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var data=new Gson().fromJson(reader,DimensionFieldGeometry.Data.class);DimensionFieldGeometry.validate(data);
                for(var emitter:data.emitters)if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IllegalArgumentException("Missing original field texture "+emitter.texture);
                DATA.put(key,data);
            } catch(Exception error) { LogUtils.getLogger().error("Unable to load original Dimension field effect "+key,error); }
        }
        LogUtils.getLogger().info("DIMENSION_FIELD_EFFECTS source profiles loaded: {}",DATA.size());
    }
    private static void world() {
        var level=Minecraft.getInstance().level;
        if(lastLevel!=level){lastLevel=level;FIELDS.clear();IMPACTS.clear();}
    }
    public static void receive(DimensionFieldNetwork.Visual message) {
        world();var level=lastLevel;
        if(level==null||message==null||message.dimension()==null||!message.dimension().equals(level.dimension().location())||message.field()==null||message.owner()==null||message.center()==null)return;
        Vec3 point=message.center();
        if(!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z)||Math.abs(point.x)>30_000_000||Math.abs(point.z)>30_000_000||!Float.isFinite(message.radius())||message.radius()<=0||message.radius()>64||message.tier()<1||message.tier()>2)return;
        if(message.remove()){FIELDS.remove(message.field());return;}
        long now=level.getGameTime();
        var old=FIELDS.get(message.field());
        // The server captures the skin scope when casting. Cache the explicit
        // packet bit so later item/skin changes cannot alter an existing field.
        String skin=old!=null?old.skin:message.skin();
        if(!cn.ssjjdimension.DimensionVisualScope.isSkin(skin))return;
        if(message.impact()) {
            if(IMPACTS.size()>=MAX_IMPACTS)IMPACTS.remove(0);
            IMPACTS.add(new Impact(message,now,skin));
        }
        int total=message.totalTicks();if(total<1||total>FIELD_TICKS)return;
        int remaining=Math.max(0,Math.min(total,message.remainingTicks()));
        if(remaining==0)return;
        if(!FIELDS.containsKey(message.field())&&FIELDS.size()>=MAX_FIELDS)FIELDS.remove(FIELDS.keySet().iterator().next());
        FIELDS.put(message.field(),new Field(message,now-(total-remaining),now+remaining,skin));
    }
    @SubscribeEvent public static void tickDimensionFieldEffects(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;world();if(lastLevel==null)return;
        long now=lastLevel.getGameTime();FIELDS.values().removeIf(field->now>=field.until);
        IMPACTS.removeIf(impact->now-impact.start>30);
    }
    @SubscribeEvent public static void renderDimensionFieldEffects(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;world();
        if(lastLevel==null||DATA.isEmpty()||(FIELDS.isEmpty()&&IMPACTS.isEmpty()))return;
        var camera=event.getCamera().getPosition();var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        var right=event.getCamera().rotation().transform(new Vector3f(1,0,0));
        var up=event.getCamera().rotation().transform(new Vector3f(0,1,0));
        for(var field:FIELDS.values()) {
            var message=field.message;if(camera.distanceToSqr(message.center())>Math.pow(Math.max(96,cn.ssjjdimension.DimensionFieldRules.range()+message.radius()),2))continue;
            float seconds=(lastLevel.getGameTime()-field.start+event.getPartialTick())/20f;
            if(seconds<0||seconds>=message.totalTicks()/20f)continue;
            draw(DATA.get(cn.ssjjdimension.DimensionVisualScope.profile(message.tier(),field.skin)+"_field"),seconds,message.center(),message.radius(),event.getPoseStack(),camera,right,up,buffers);
        }
        for(var impact:IMPACTS) {
            var message=impact.message;if(camera.distanceToSqr(message.center())>Math.pow(Math.max(96,cn.ssjjdimension.DimensionFieldRules.range()+message.radius()),2))continue;
            float seconds=(lastLevel.getGameTime()-impact.start+event.getPartialTick())/20f;
            draw(DATA.get(cn.ssjjdimension.DimensionVisualScope.profile(message.tier(),impact.skin)+"_impact"),seconds,message.center(),message.radius(),event.getPoseStack(),camera,right,up,buffers);
        }
    }
    private static void draw(DimensionFieldGeometry.Data data,float seconds,Vec3 center,float radius,PoseStack pose,Vec3 camera,Vector3f right,Vector3f up,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) {
        if(data==null)return;
        pose.pushPose();pose.translate(center.x-camera.x,center.y-camera.y+.012,center.z-camera.z);
        var groups=new LinkedHashMap<String,List<DimensionFieldGeometry.Batch>>();
        for(var batch:DimensionFieldGeometry.sample(data,seconds,right,up,radius))groups.computeIfAbsent(batch.texture()+"|"+batch.additive(),key->new ArrayList<>()).add(batch);
        for(var entries:groups.values()) {
            var first=entries.get(0);var type=DimensionMarkRenderType.of(new ResourceLocation(first.texture()),first.additive());var out=buffers.getBuffer(type);
            for(var batch:entries) {
                var mesh=batch.mesh();var color=batch.color();var uv=batch.uv();
                for(var face:mesh.triangles)for(int corner=0;corner<4;corner++) {
                    int index=face[Math.min(corner,2)];var point=mesh.positions[index];var tex=mesh.uv[index];
                    out.vertex(pose.last().pose(),point[0],point[1],point[2]).color(color[0],color[1],color[2],color[3]).uv(tex[0]*uv[0]+uv[2],tex[1]*uv[1]-uv[3]).overlayCoords(0).uv2(15728880).normal(pose.last().normal(),0,1,0).endVertex();
                }
            }
            buffers.endBatch(type);
        }
        pose.popPose();
    }
}
