package cn.ssjjdimension.client;

import cn.ssjjdimension.DimensionMarkNetwork;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Source biaoji effects remain attached to the interpolated living target. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionMarkEffects {
    private record Mark(DimensionMarkNetwork.Visual message,long start,long until) {}
    private record Burst(DimensionMarkNetwork.Visual message,long start) {}
    private static final Map<UUID,Mark> MARKS=new LinkedHashMap<>();
    private static final List<Burst> BURSTS=new ArrayList<>();
    private static final Map<String,DimensionMarkGeometry.Data> DATA=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    public static final int MAX_TARGETS=128,MAX_BURSTS=48,STALE_TICKS=70;
    public static final int VISIBLE_TARGETS=24,VISIBLE_BURSTS=24,TRIANGLE_BUDGET=49152;
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource EFFECT_BUFFERS=DimensionSkillBatcher.buffers();
    private DimensionMarkEffects() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerDimensionTargetEffects(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener)DimensionMarkEffects::reload);
        }
    }
    public static void reload(ResourceManager resources) {
        DATA.clear();MARKS.clear();BURSTS.clear();
        for(String profile:DimensionMarkNetwork.profiles())for(String kind:List.of("mark","burst")) {
            String key=profile+"_"+kind;
            try(var stream=resources.open(new ResourceLocation("ssjj_dimension","effects/marks/"+key+".json"));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var d=new Gson().fromJson(reader,DimensionMarkGeometry.Data.class);DimensionMarkGeometry.validate(d);
                for(var emitter:d.emitters)if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IllegalArgumentException("Missing source texture "+emitter.texture);
                DATA.put(key,d);
            } catch(Exception error) { LogUtils.getLogger().error("Unable to load source Dimension target effect "+key,error); }
        }
        LogUtils.getLogger().info("DIMENSION_TARGET_EFFECTS source profiles loaded: {}",DATA.size());
    }
    private static void world() {
        var level=Minecraft.getInstance().level;if(level!=lastLevel){lastLevel=level;MARKS.clear();BURSTS.clear();}
    }
    public static void receive(DimensionMarkNetwork.Visual message) {
        world();var level=lastLevel;
        if(level==null||!message.valid()||!message.dimension().equals(level.dimension().location()))return;
        var entity=level.getEntity(message.target());
        if(!(entity instanceof LivingEntity)||!entity.getUUID().equals(message.uuid()))return;
        long now=level.getGameTime();
        if(message.burst()) {
            // Packets repeated for the same target/server tick describe the same visual event.
            for(var burst:BURSTS)if(burst.message.uuid().equals(message.uuid())&&burst.message.tick()==message.tick())return;
            if(BURSTS.size()>=MAX_BURSTS)BURSTS.remove(0);BURSTS.add(new Burst(message,now));return;
        }
        var old=MARKS.get(message.uuid());if(old!=null&&old.message.tick()>message.tick())return;
        if(message.count()==0){MARKS.remove(message.uuid());return;}
        if(!MARKS.containsKey(message.uuid())&&MARKS.size()>=MAX_TARGETS)MARKS.remove(MARKS.keySet().iterator().next());
        MARKS.put(message.uuid(),new Mark(message,old==null?now:old.start,now+STALE_TICKS));
    }
    private static LivingEntity target(DimensionMarkNetwork.Visual message) {
        var level=lastLevel;if(level==null||!message.dimension().equals(level.dimension().location()))return null;
        var entity=level.getEntity(message.target());
        // A confirmed lethal burst may finish on the native death-animation entity.
        return entity instanceof LivingEntity t&&t.getUUID().equals(message.uuid())&&(message.burst()||t.isAlive())&&!t.isRemoved()?t:null;
    }
    @SubscribeEvent public static void tickDimensionTargetEffects(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;world();if(lastLevel==null)return;
        long now=lastLevel.getGameTime();
        MARKS.entrySet().removeIf(entry->target(entry.getValue().message)==null||now>=entry.getValue().until);
        BURSTS.removeIf(b->target(b.message)==null||now-b.start>16);
    }
    public static Vec3 surface(AABB body,Vec3 camera) {
        var center=body.getCenter();var direction=camera.subtract(center).normalize();
        double scale=Math.max(Math.abs(direction.x)/Math.max(.01,body.getXsize()*.5),Math.max(Math.abs(direction.y)/Math.max(.01,body.getYsize()*.5),Math.abs(direction.z)/Math.max(.01,body.getZsize()*.5)));
        return center.add(direction.scale((scale<1e-8?0:1/scale)+.045));
    }
    private static Vec3 anchor(LivingEntity target,float partial,Vec3 camera) {
        double x=Mth.lerp(partial,target.xOld,target.getX()),y=Mth.lerp(partial,target.yOld,target.getY()),z=Mth.lerp(partial,target.zOld,target.getZ());
        return surface(target.getBoundingBox().move(x-target.getX(),y-target.getY(),z-target.getZ()),camera);
    }
    @SubscribeEvent public static void renderDimensionTargetEffects(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;world();
        if(lastLevel==null||DATA.isEmpty()||(MARKS.isEmpty()&&BURSTS.isEmpty()))return;
        var camera=e.getCamera().getPosition();var samples=new DimensionSkillSamples();
        var batcher=new DimensionSkillBatcher(TRIANGLE_BUDGET*2/3);
        var burstBatcher=new DimensionSkillBatcher(TRIANGLE_BUDGET/3);
        var right=e.getCamera().rotation().transform(new Vector3f(1,0,0));
        var up=e.getCamera().rotation().transform(new Vector3f(0,1,0));
        // A bounded visible list is selected nearest-first, independently of server gameplay state.
        var marks=new ArrayList<>(MARKS.values());marks.removeIf(m->target(m.message)==null);
        marks.sort(Comparator.comparingDouble(m->camera.distanceToSqr(target(m.message).position())));
        int visible=0;
        for(var mark:marks) {
            var t=target(mark.message);double distance=camera.distanceToSqr(t.position());
            if(distance>80*80||visible++>=VISIBLE_TARGETS)break;
            float seconds=(lastLevel.getGameTime()-mark.start+e.getPartialTick())/20f+1.25f;
            Vec3 center=anchor(t,e.getPartialTick(),camera);
            for(int i=0;i<mark.message.count();i++) {
                var d=DATA.get(mark.message.layers().get(i)+"_mark");if(d==null)continue;
                double angle=2*Math.PI*i/5-Math.PI*.5,radius=mark.message.count()==1?0:.24;
                Vec3 p=center.add(right.x*Math.cos(angle)*radius+up.x*Math.sin(angle)*radius,right.y*Math.cos(angle)*radius+up.y*Math.sin(angle)*radius,right.z*Math.cos(angle)*radius+up.z*Math.sin(angle)*radius);
                // Layer placement remains distinct; identical profiles share the exact animated mesh.
                draw(d,seconds,p,.46f,e.getPoseStack(),e.getCamera().rotation(),camera,batcher,samples);
            }
        }
        var bursts=new ArrayList<>(BURSTS);bursts.removeIf(b->target(b.message)==null);
        bursts.sort(Comparator.comparingDouble(b->camera.distanceToSqr(target(b.message).position())));
        visible=0;
        for(var burst:bursts) {
            var t=target(burst.message);double distance=camera.distanceToSqr(t.position());
            if(distance>80*80||visible++>=VISIBLE_BURSTS)break;
            float seconds=(lastLevel.getGameTime()-burst.start+e.getPartialTick())/20f;
            Vec3 center=anchor(t,e.getPartialTick(),camera);
            for(int i=0;i<burst.message.count();i++) {
                var d=DATA.get(burst.message.layers().get(i)+"_burst");if(d==null)continue;
                double angle=2*Math.PI*i/5-Math.PI*.5,radius=burst.message.count()==1?0:.24;
                Vec3 p=center.add(right.x*Math.cos(angle)*radius+up.x*Math.sin(angle)*radius,right.y*Math.cos(angle)*radius+up.y*Math.sin(angle)*radius,right.z*Math.cos(angle)*radius+up.z*Math.sin(angle)*radius);
                draw(d,seconds,p,.45f,e.getPoseStack(),e.getCamera().rotation(),camera,burstBatcher,samples);
            }
        }
        batcher.flush(EFFECT_BUFFERS);burstBatcher.flush(EFFECT_BUFFERS);samples.clear();
    }
    private static void draw(DimensionMarkGeometry.Data data,float seconds,Vec3 anchor,float scale,PoseStack pose,org.joml.Quaternionf facing,Vec3 camera,DimensionSkillBatcher batcher,DimensionSkillSamples samples) {
        if(!batcher.hasBudget())return;
        pose.pushPose();
        try {
            pose.translate(anchor.x-camera.x,anchor.y-camera.y,anchor.z-camera.z);pose.mulPose(facing);pose.scale(scale,scale,scale);
            var matrix=new org.joml.Matrix4f(pose.last().pose());var normal=new org.joml.Matrix3f(pose.last().normal());
            var frames=samples.get(data,seconds,1,()->DimensionMarkGeometry.sample(data,seconds,new Vector3f(1,0,0),new Vector3f(0,1,0)));
            batcher.beginObject();
            for(var b:frames){var mesh=b.mesh();batcher.add(b.texture(),b.additive(),mesh.positions,mesh.uv,mesh.triangles,b.color(),b.uv(),matrix,normal,data.loop?.05f:1,true);}
            batcher.endObject();
        } finally {pose.popPose();}
    }
}
