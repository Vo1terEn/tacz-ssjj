package cn.ssjjdark.client;

import cn.ssjjdark.DarkDragonNetwork;
import cn.ssjjdark.DarkDragonRules;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
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
import org.joml.Quaternionf;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/** Original longyinzhan particles follow server-authored, wall-clipped travel. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkDragonClient {
    private static final Map<UUID,Trail> TRAILS=new LinkedHashMap<>();
    public static final float SCALE=.4375f,CENTER_Y=-1.09f;
    private static volatile Map<String,DarkSkinParticles.Data> assets=Map.of();
    private static ClientLevel lastLevel;
    private static final DarkSkinParticles.Frame FRAME=new DarkSkinParticles.Frame();
    private static final class Drawing {static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));}
    private static final class Trail {
        final NavigableMap<Long,DarkDragonNetwork.Effect> history=new TreeMap<>();
        long launch,finish=Long.MAX_VALUE;
        int tier;
        String profile;
    }
    private DarkDragonClient(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerDragonEffects(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)DarkDragonClient::read);}
    }
    public static void read(ResourceManager resources){
        assets=Map.of();TRAILS.clear();var loaded=new LinkedHashMap<String,DarkSkinParticles.Data>();
        try {
            for(int tier=1;tier<=2;tier++)for(String suffix:new String[]{"","_mix","_nightguardian","_pure"}) {
                String profile="darkdiamond"+tier+suffix;
                var name=new ResourceLocation("ssjj_dark","effects/skins/dragon/"+profile+".json");
                try(var input=resources.open(name);var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
                    var data=new Gson().fromJson(reader,DarkSkinParticles.Data.class);
                    DarkSkinParticles.validateDragon(data,"ssjj_dark:"+profile);
                    for(var emitter:data.emitters) {
                        if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IllegalArgumentException("Missing dragon texture "+emitter.texture);
                        DarkSkinEffects.type(emitter);
                    }
                    loaded.put(profile,data);
                }
            }
            assets=Map.copyOf(loaded);
            LogUtils.getLogger().info("DARK_DRAGON loaded original longyinzhan effects for {} tiers",assets.size());
        } catch(Exception failure){LogUtils.getLogger().error("Cannot load original Dragon Seal Slash effects",failure);}
    }
    private static void world(ClientLevel level){if(level!=lastLevel){TRAILS.clear();lastLevel=level;}}
    public static void accept(DarkDragonNetwork.Effect effect){
        var mc=Minecraft.getInstance();world(mc.level);
        if(effect==null||!effect.valid()||mc.level==null||!mc.level.dimension().location().equals(effect.dimension()))return;
        long now=mc.level.getGameTime();if(effect.tick()<now-40||effect.tick()>now+10)return;
        if(effect.type()==1){var trail=TRAILS.get(effect.id());if(trail!=null)trail.finish=Math.min(trail.finish,effect.tick());return;}
        var trail=TRAILS.computeIfAbsent(effect.id(),id->{var t=new Trail();t.launch=effect.tick()-1;t.tier=effect.tier();t.profile=effect.profile();return t;});
        if(trail.tier!=effect.tier()||!trail.profile.equals(effect.profile())||effect.tick()>trail.finish)return;
        trail.history.put(effect.tick(),effect);while(trail.history.size()>DarkDragonRules.MAX_AGE+4)trail.history.pollFirstEntry();
        while(TRAILS.size()>256){var it=TRAILS.keySet().iterator();it.next();it.remove();}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();world(mc.level);if(mc.level==null)return;
        long now=mc.level.getGameTime();TRAILS.values().removeIf(t->t.history.isEmpty()||now-t.history.lastKey()>5||now-t.launch>DarkDragonRules.MAX_AGE+6||t.finish!=Long.MAX_VALUE&&now>t.finish+2);
    }
    public static Vec3 interpolate(Vec3 from,Vec3 to,double phase){return from.lerp(to,Math.max(0,Math.min(1,phase)));}
    /** Source dragon mouth faces +X; retain an upright +Y while aiming in any direction. */
    public static Quaternionf rotation(Vec3 direction){
        var forward=new Vector3f((float)direction.x,(float)direction.y,(float)direction.z).normalize();
        var reference=Math.abs(forward.y)>.999f?new Vector3f(0,0,1):new Vector3f(0,1,0);
        var side=new Vector3f(forward).cross(reference).normalize();var up=new Vector3f(side).cross(forward).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,forward).setColumn(1,up).setColumn(2,side));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();world(mc.level);if(mc.level==null||TRAILS.isEmpty())return;
        double time=mc.level.getGameTime()+event.getPartialTick()-1;
        var camera=event.getCamera().getPosition();var right=event.getCamera().rotation().transform(new Vector3f(1,0,0));var up=event.getCamera().rotation().transform(new Vector3f(0,1,0));
        try {
            for(var trail:TRAILS.values()) {
                var data=assets.get(trail.profile);if(data==null||time<trail.launch||time>trail.finish)continue;
                var entry=trail.history.ceilingEntry((long)Math.ceil(time));if(entry==null)entry=trail.history.lastEntry();if(entry==null)continue;
                var segment=entry.getValue();var direction=segment.to().subtract(segment.from());if(direction.lengthSqr()<1e-12)continue;
                var rotation=rotation(direction);
                var point=interpolate(segment.from(),segment.to(),time-(segment.tick()-1));
                draw(data,(float)((time-trail.launch)/20),point,rotation,event.getPoseStack(),camera,right,up);
            }
        } finally {Drawing.BUFFERS.endBatch();}
    }
    private static void draw(DarkSkinParticles.Data data,float age,Vec3 center,Quaternionf rotation,PoseStack pose,Vec3 camera,Vector3f right,Vector3f up){
        if(age<0||center.distanceToSqr(camera)>96*96)return;
        var inverse=new Quaternionf(rotation).conjugate();var r=inverse.transform(new Vector3f(right));var u=inverse.transform(new Vector3f(up));
        var localCamera=inverse.transform(new Vector3f((float)(camera.x-center.x),(float)(camera.y-center.y),(float)(camera.z-center.z))).div(SCALE).add(0,-CENTER_Y,0);
        DarkSkinParticles.fill(data,age,r,u,localCamera,FRAME);
        pose.pushPose();
        try {
            pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);pose.mulPose(rotation);pose.scale(SCALE,SCALE,SCALE);pose.translate(0,CENTER_Y,0);
            net.minecraft.client.renderer.RenderType active=null;com.mojang.blaze3d.vertex.VertexConsumer out=null;
            for(int i=0;i<FRAME.count;i++) {
                var draw=FRAME.get(i);var type=DarkSkinEffects.type(draw.emitter);
                if(type!=active){if(active!=null)Drawing.BUFFERS.endBatch(active);active=type;out=Drawing.BUFFERS.getBuffer(type);}
                DarkSkinParticles.emit(draw,pose.last().pose(),pose.last().normal(),out);
            }
            if(active!=null)Drawing.BUFFERS.endBatch(active);
        } finally {pose.popPose();}
    }
}
