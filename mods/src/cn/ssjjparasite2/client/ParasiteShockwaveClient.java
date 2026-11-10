package cn.ssjjparasite2.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Short, server-confirmed wave visuals. Does not predict hits or move entities. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ParasiteShockwaveClient {
    public static final Set<String> KEYS=Set.of("parasitic_sniper2","parasitic_sniper2_frost","parasitic_sniper2_ship");
    public static final class Profile {
        public EffectGeometry.Effect beam,core;
        public float sourceLength,sourceRadius,coreRadius,duration=.65f,timeScale=1;
        public float stretchPivot,rendererPivot;
        public float[] beamOrigin={0,0,0},coreOrigin={0,0,0};
    }
    public static final class Assets { public Map<String,Profile> profiles; }
    private record Flash(ParasiteShockwave.Pulse pulse,float born) {}
    private static volatile Map<String,Profile> profiles=Map.of();
    private static final ArrayDeque<Flash> flashes=new ArrayDeque<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private ParasiteShockwaveClient() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerShockwaveResources(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ParasiteShockwaveClient::read);
        }
    }
    public static void read(ResourceManager resources) {
        flashes.clear();profiles=Map.of();
        try(var stream=resources.open(new ResourceLocation("ssjjparasite2","effects/shockwave.json"));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var fresh=new Gson().fromJson(reader,Assets.class);
            if(fresh==null||fresh.profiles==null||!fresh.profiles.keySet().equals(KEYS))throw new IllegalArgumentException("Missing shockwave profiles");
            for(var profile:fresh.profiles.values()) {
                validate(profile);
                for(var effect:new EffectGeometry.Effect[]{profile.beam,profile.core}) {
                    for(var layer:effect.layers)if(resources.getResource(new ResourceLocation(layer.material.texture)).isEmpty())throw new IllegalArgumentException("Missing shockwave layer texture");
                    for(var emitter:effect.emitters)if(resources.getResource(new ResourceLocation(emitter.material.texture)).isEmpty())throw new IllegalArgumentException("Missing shockwave particle texture");
                }
            }
            profiles=Map.copyOf(fresh.profiles);
            LogUtils.getLogger().info("PARASITE shockwave source effects loaded for {} skins",profiles.size());
        }catch(Exception error) {LogUtils.getLogger().error("Cannot load Parasite cannon shockwave effects",error);}
    }
    public static void validate(Profile p) {
        if(p==null||!Float.isFinite(p.sourceLength)||p.sourceLength<=0||p.sourceLength>2048
            ||!Float.isFinite(p.sourceRadius)||p.sourceRadius<=0||p.sourceRadius>128
            ||!Float.isFinite(p.coreRadius)||p.coreRadius<=0||p.coreRadius>128
            ||!Float.isFinite(p.duration)||p.duration<=0||p.duration>2
            ||!Float.isFinite(p.timeScale)||p.timeScale<=0||p.timeScale>20)throw new IllegalArgumentException("Invalid shockwave dimensions");
        for(float[] origin:new float[][]{p.beamOrigin,p.coreOrigin}) {
            if(origin==null||origin.length!=3)throw new IllegalArgumentException("Invalid shockwave source origin");
            for(float v:origin)if(!Float.isFinite(v)||Math.abs(v)>2048)throw new IllegalArgumentException("Invalid source origin");
        }
        var d=new EffectGeometry.Data();d.schema=1;d.units="blocks";d.gun="ssjjparasite2:parasitic_sniper2";d.bone="WeaponAnchor2";
        d.body=p.beam;d.muzzle=p.core;d.impact=new EffectGeometry.Effect();d.impact.layers=new EffectGeometry.Layer[0];d.impact.emitters=new EffectGeometry.Emitter[0];
        EffectGeometry.validate(d);
    }
    private static float now() {
        var mc=Minecraft.getInstance();return mc.level==null?0:(mc.level.getGameTime()%240000L+mc.getFrameTime())/20f;
    }
    private static float age(float now,float then) {float t=now-then;return t<0?t+12000:t;}
    public static void accept(ParasiteShockwave.Pulse pulse) {
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||pulse==null||!pulse.valid()||!profiles.containsKey(pulse.profile()))return;
        Vec3 midpoint=pulse.start().add(pulse.end()).scale(.5);
        if(mc.player.position().distanceToSqr(midpoint)>192*192)return;
        while(flashes.size()>=32)flashes.removeFirst();
        flashes.addLast(new Flash(pulse,now()));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel) {lastLevel=mc.level;flashes.clear();}
        float clock=now();flashes.removeIf(f->!profiles.containsKey(f.pulse.profile())||age(clock,f.born)>=profiles.get(f.pulse.profile()).duration);
    }

    /** Source +Z is stretched only longitudinally; the radius follows the hit cylinder. */
    public static List<EffectGeometry.Batch> sample(Profile profile,float age,int seed,float length,Vector3f right,Vector3f up) {
        return sample(profile,age,seed,length,right,up,false);
    }
    public static List<EffectGeometry.Batch> sample(Profile profile,float age,int seed,float length,Vector3f right,Vector3f up,boolean firstPerson) {
        return sample(profile,age,seed,length,right,up,firstPerson,(float)ParasiteShockwave.RADIUS);
    }
    public static List<EffectGeometry.Batch> sample(Profile profile,float age,int seed,float length,Vector3f right,Vector3f up,boolean firstPerson,float radius) {
        List<EffectGeometry.Batch> out=new ArrayList<>();
        if(age<0||age>=profile.duration||length<=0)return out;
        float fade=Math.min(1,Math.max(0,(profile.duration-age)/.18f));
        float time=age*profile.timeScale;
        float width=radius/profile.sourceRadius;
        // Treat the original line-link components independently. The source glow
        // disk stays at the muzzle; only its stretched line and flame mesh span
        // the path. A whole-effect AABB must never become the beam's new origin.
        for(int i=0;i<profile.beam.emitters.length;i++) {
            var emitter=profile.beam.emitters[i];
            var component=new EffectGeometry.Effect();component.layers=new EffectGeometry.Layer[0];component.emitters=new EffectGeometry.Emitter[]{emitter};
            var batches=EffectGeometry.sample(component,time,seed+i*7919,false,right,up);
            if(emitter.mode==1) appendLine(out,batches,width,fade,length,profile.stretchPivot);
            else if(emitter.name.equals("huoyan1_0")) append(out,batches,profile.beamOrigin,width,length/profile.sourceLength,fade,length,true);
            else append(out,batches,new float[]{0,0,0},firstPerson?.35f/profile.sourceRadius:width,1,fade,length,true);
        }
        // Keep the muzzle sphere readable without obscuring the player's whole view.
        float coreScale=(firstPerson?.35f:1.2f)/profile.coreRadius;
        append(out,EffectGeometry.sample(profile.core,time,seed+317,false,right,up),profile.coreOrigin,coreScale,coreScale,fade,length,false);
        return out;
    }
    /** The source stretch-single billboard is anchored to the confirmed endpoints.
     * Its source pivot controls the transform anchor; the cylinder center remains
     * the local Z axis for every skin, view direction and clipped path length.
     */
    private static void appendLine(List<EffectGeometry.Batch> out,List<EffectGeometry.Batch> batches,float width,float fade,float length,float pivot) {
        for(var batch:batches) {
            var source=batch.mesh();if(source.positions.length!=4)throw new IllegalArgumentException("Expected source stretch-single quad");
            var mesh=new EffectGeometry.Mesh();mesh.uv=source.uv;mesh.triangles=source.triangles;mesh.positions=new float[4][3];
            // Original quad order is bottom-left, bottom-right, top-right, top-left.
            // Keep its texture, UV animation, width and source color/alpha curves.
            float anchor=(.5f+pivot)*length;
            float centerX=0,centerY=0;
            for(float[] point:source.positions){centerX+=point[0]*.25f;centerY+=point[1]*.25f;}
            for(int i=0;i<4;i++) {
                float side=(i==0||i==1)?-.5f:.5f;
                float depth=anchor+(side-pivot)*length;
                mesh.positions[i]=new float[]{(source.positions[i][0]-centerX)*width,(source.positions[i][1]-centerY)*width,Math.max(0,Math.min(length,depth))};
            }
            float[] color=batch.color().clone();color[3]*=fade;
            out.add(new EffectGeometry.Batch(batch.material(),mesh,color,batch.uvST()));
        }
    }
    private static void append(List<EffectGeometry.Batch> out,List<EffectGeometry.Batch> batches,float[] origin,float xy,float z,float fade,float length,boolean beam) {
        for(var batch:batches) {
            var mesh=new EffectGeometry.Mesh();mesh.uv=batch.mesh().uv;mesh.triangles=batch.mesh().triangles;mesh.positions=new float[batch.mesh().positions.length][3];
            for(int i=0;i<mesh.positions.length;i++) {
                float[] point=batch.mesh().positions[i];float depth=(point[2]-origin[2])*z;
                mesh.positions[i]=new float[]{(point[0]-origin[0])*xy,(point[1]-origin[1])*xy,beam?Math.max(0,Math.min(length,depth)):depth};
            }
            float[] color=batch.color().clone();color[3]*=fade;
            out.add(new EffectGeometry.Batch(batch.material(),mesh,color,batch.uvST()));
        }
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||flashes.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        float clock=now();Vec3 camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        Vector3f worldRight=event.getCamera().getLeftVector().negate(new Vector3f()),worldUp=new Vector3f(event.getCamera().getUpVector());
        for(var flash:flashes) {
            var profile=profiles.get(flash.pulse.profile());if(profile==null)continue;
            float elapsed=age(clock,flash.born);Vec3 vector=flash.pulse.end().subtract(flash.pulse.start());float length=(float)vector.length();
            if(elapsed>=profile.duration||length<.001f)continue;
            boolean firstPerson=mc.player!=null&&flash.pulse.shooter()==mc.player.getId()&&mc.options.getCameraType().isFirstPerson();
            Vec3 start=flash.pulse.start();
            // The native bullet begins near the eye. Keep the local muzzle glow ahead
            // of the camera while retaining the server-confirmed far endpoint.
            if(firstPerson){float advance=Math.min(1,length*.5f);start=start.add(vector.scale(advance/length));length-=advance;}
            var rotation=new Quaternionf().rotationTo(new Vector3f(0,0,1),new Vector3f((float)vector.x,(float)vector.y,(float)vector.z).normalize());
            var inverse=new Quaternionf(rotation).conjugate();
            List<EffectGeometry.Batch> batches=sample(profile,elapsed,flash.pulse.seed(),length,inverse.transform(new Vector3f(worldRight)),inverse.transform(new Vector3f(worldUp)),firstPerson,(float)flash.pulse.radius());
            PoseStack pose=event.getPoseStack();pose.pushPose();
            pose.translate(start.x-camera.x,start.y-camera.y,start.z-camera.z);pose.mulPose(rotation);
            ParasiteEffectsClient.draw(batches,pose,buffers);pose.popPose();
        }
    }
}
