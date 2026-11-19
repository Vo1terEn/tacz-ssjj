package cn.ssjjzeus.client;

import cn.ssjjzeus.ZeusThunderNetwork;
import cn.ssjjdimension.client.DimensionParticles;
import cn.ssjjdimension.client.DimensionEffectRenderType;
import cn.fusionarsenal.compat.TaCZClientCompat;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.sound.SoundManager;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original exported SSJJ textures, geometry, curves and materials in world space. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ZeusThunderClient {
    public static final class Ref {public String resource;public float duration;}
    public static final class Profile {public Ref orb,impact,chain,anchor;public String skill;}
    public static final class Profiles {public Map<String,Profile> profiles;}
    private record Asset(DimensionParticles.Data data,float duration) {}
    private record Visible(ZeusThunderNetwork.Effect effect,long born) {}
    private static volatile Map<String,Profile> profiles=Map.of();
    private static volatile Map<String,Asset> assets=Map.of();
    private static final Map<UUID,Visible> BALLS=new LinkedHashMap<>(),SHOTS=new LinkedHashMap<>();
    private static final Map<UUID,Long> FINISHED=new LinkedHashMap<>(),SOUNDED=new LinkedHashMap<>();
    private static final Set<GunDisplayInstance> MUTED=Collections.newSetFromMap(new WeakHashMap<>());
    private static ClientLevel lastLevel;
    private static final class Drawing {static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));}
    private ZeusThunderClient() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerZeusThunderResources(RegisterClientReloadListenersEvent event) {event.registerReloadListener((ResourceManagerReloadListener)ZeusThunderClient::read);}
    }
    public static void read(ResourceManager resources) {
        ZeusParticleMeshes.clear();
        // The native gun-model resource reload also runs after TaCZ mounts its
        // gunpacks. Keep target effects on the same proven resource lifecycle.
        ZeusFocusClient.read(resources);ZeusShockClient.read(resources);
        profiles=Map.of();assets=Map.of();BALLS.clear();SHOTS.clear();MUTED.clear();
        try(var stream=resources.open(new ResourceLocation("ssjj_zeus","effects/thunder/profiles.json"));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            Profiles source=new Gson().fromJson(reader,Profiles.class);Map<String,Asset> loaded=new LinkedHashMap<>();
            if(source==null||source.profiles==null||source.profiles.size()!=6)throw new IllegalArgumentException("Invalid Zeus source effect profiles");
            for(var entry:source.profiles.entrySet()) {
                Profile p=entry.getValue();if(p==null||p.skill==null||!p.skill.startsWith("ssjj_zeus:thunder/"))throw new IllegalArgumentException("Invalid Zeus source sound");
                for(Ref ref:List.of(p.orb,p.impact,p.chain,p.anchor)) {
                    if(ref==null||ref.resource==null||!ref.resource.startsWith("ssjj_zeus:effects/thunder/")||!Float.isFinite(ref.duration)||ref.duration<=0||ref.duration>60)
                        throw new IllegalArgumentException("Invalid Zeus source effect reference");
                    if(loaded.containsKey(ref.resource))continue;
                    try(var input=resources.open(new ResourceLocation(ref.resource));var text=new InputStreamReader(input,StandardCharsets.UTF_8)) {
                        var data=new Gson().fromJson(text,DimensionParticles.Data.class);
                        if(data==null||data.gun==null||!data.gun.startsWith("ssjj_zeus:"))throw new IllegalArgumentException("Invalid Zeus source data");
                        DimensionParticles.validate(data,data.gun,"world","ssjj_zeus:textures/effects/");
                        for(var emitter:data.emitters)if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IllegalArgumentException("Missing source texture "+emitter.texture);
                        loaded.put(ref.resource,new Asset(data,ref.duration));
                    }
                }
            }
            assets=Map.copyOf(loaded);profiles=Map.copyOf(source.profiles);
            LogUtils.getLogger().info("ZEUS_THUNDER loaded {} original source effects for {} profiles",loaded.size(),profiles.size());
        } catch(Exception failure) {LogUtils.getLogger().error("Cannot load original Zeus thunder source effects",failure);}
    }
    private static void world(ClientLevel current) {if(current!=lastLevel){BALLS.clear();SHOTS.clear();FINISHED.clear();SOUNDED.clear();lastLevel=current;}}
    public static void accept(ZeusThunderNetwork.Effect effect) {
        var mc=Minecraft.getInstance();world(mc.level);
        if(effect==null||!effect.valid()||mc.level==null||!mc.level.dimension().location().equals(effect.dimension()))return;
        long now=mc.level.getGameTime();
        if(effect.type()==5) {
            // The reference summon video's waveform identifies skill.wav as
            // summon audio. A legacy chain-release packet must remain silent.
            return;
        }
        if(effect.type()==1||effect.type()==2) {
            BALLS.remove(effect.id());FINISHED.put(effect.id(),now+100);cap(FINISHED,512);
            if(effect.type()==1){SHOTS.put(effect.id(),new Visible(effect,now));cap(SHOTS,256);}return;
        }
        if(effect.type()==0&&FINISHED.containsKey(effect.id()))return;
        long transit=Math.max(0,Math.min(effect.life(),now-effect.tick()));if(transit>=effect.life())return;
        Map<UUID,Visible> target=effect.type()==0?BALLS:SHOTS;target.put(effect.id(),new Visible(effect,now-transit));cap(target,256);
    }
    /** Only the V-summon network may invoke the video-confirmed summon sound. */
    public static void playSummon(Vec3 center,String profile) {
        if(profile==null||!profile.startsWith("hand_of_zeuss2"))return;
        Profile p=profiles.get(profile);if(p!=null)playSource(center,new ResourceLocation(p.skill));
    }
    /** Autonomous phantom fire uses the fire.wav confirmed by the firing video. */
    public static void playShot(Vec3 center,String profile) {
        if(profile==null||!profile.startsWith("hand_of_zeuss2"))return;
        playSource(center,new ResourceLocation("ssjj_zeus","advent/hand_of_zeuss2_fire"));
    }
    private static void playSource(Vec3 center,ResourceLocation sound) {
        var mc=Minecraft.getInstance();if(mc.level==null||center==null)return;
        mc.level.playLocalSound(center.x,center.y,center.z,SoundEvent.createVariableRangeEvent(sound),SoundSource.PLAYERS,1,1,false);
    }
    private static <T> void cap(Map<UUID,T> map,int maximum) {while(map.size()>maximum){Iterator<UUID> i=map.keySet().iterator();i.next();i.remove();}}
    private static Asset asset(ZeusThunderNetwork.Effect effect) {
        Profile p=profiles.get(effect.profile());if(p==null)return null;
        Ref ref=switch(effect.type()){case 0->p.orb;case 1->p.impact;case 3->p.chain;case 4->p.anchor;default->null;};
        return ref==null?null:assets.get(ref.resource);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(String key:profiles.keySet()) {
            String gun=key.startsWith("hand_of_zeuss1")?"hand_of_zeuss1":"hand_of_zeuss2";
            TaCZClientCompat.forEachGunDisplay(new ResourceLocation("ssjj_zeus",key+"_display"),new ResourceLocation("ssjj_zeus",gun),display->{
                if(MUTED.contains(display))return;
                try {silenceStockFeedback(display);MUTED.add(display);}
                catch(ReflectiveOperationException failure) {throw new IllegalStateException("Cannot disable unsupplied Zeus feedback sounds",failure);}
            });
        }
        var mc=Minecraft.getInstance();world(mc.level);if(mc.level==null)return;long now=mc.level.getGameTime();
        BALLS.values().removeIf(v->now-v.born>=v.effect.life());
        SHOTS.values().removeIf(v->{Asset a=asset(v.effect);return a==null||now-v.born>=Math.min(v.effect.life(),Math.ceil(a.duration*20));});
        FINISHED.values().removeIf(until->now>=until);SOUNDED.values().removeIf(until->now>=until);
    }
    /** TaCZ installs eight fallback sounds during index creation. Its playback
     * checks a null route before any audio lookup; removing these entries
     * preserves the native hurt/kill events and HUD without creating silent audio.
     */
    @SuppressWarnings("unchecked")
    public static void silenceStockFeedback(GunDisplayInstance display)throws ReflectiveOperationException {
        var field=GunDisplayInstance.class.getDeclaredField("sounds");field.setAccessible(true);
        Map<String,ResourceLocation> sounds=(Map<String,ResourceLocation>)field.get(display);
        if(sounds==null)return;
        for(String key:List.of(SoundManager.FLESH_HIT_SOUND,SoundManager.HEAD_HIT_SOUND,SoundManager.KILL_SOUND,
            SoundManager.DRY_FIRE_SOUND,SoundManager.FIRE_SELECT,SoundManager.MELEE_BAYONET,SoundManager.MELEE_STOCK,SoundManager.MELEE_PUSH))sounds.remove(key);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();world(mc.level);
        if(mc.level==null||BALLS.isEmpty()&&SHOTS.isEmpty())return;
        Vec3 camera=event.getCamera().getPosition();Quaternionf cameraRotation=event.getCamera().rotation();
        Vector3f cameraRight=cameraRotation.transform(new Vector3f(1,0,0)),cameraUp=cameraRotation.transform(new Vector3f(0,1,0));
        long now=mc.level.getGameTime();
        for(var group:List.of(BALLS,SHOTS))for(Visible visible:group.values())draw(visible,event.getPoseStack(),camera,cameraRight,cameraUp,now,event.getPartialTick());
        Drawing.BUFFERS.endBatch();
    }
    private static void draw(Visible v,PoseStack pose,Vec3 camera,Vector3f cameraRight,Vector3f cameraUp,long tick,float partial) {
        float age=tick-v.born+partial;Asset a=asset(v.effect);if(a==null||age<0||age>=v.effect.life()||v.effect.type()!=0&&age/20>=a.duration)return;
        Vec3 center=v.effect.type()==0?v.effect.position().add(v.effect.velocity().scale(age)):v.effect.position();if(center.distanceToSqr(camera)>160*160)return;
        Quaternionf rotation=new Quaternionf();boolean chain=v.effect.type()==3;Vector3f right,up;
        if(chain) {
            Vector3f direction=new Vector3f((float)v.effect.velocity().x,(float)v.effect.velocity().y,(float)v.effect.velocity().z).normalize();
            Vector3f towardCamera=new Vector3f((float)(camera.x-center.x),(float)(camera.y-center.y),(float)(camera.z-center.z));
            Vector3f side=new Vector3f(direction).cross(towardCamera);
            if(side.lengthSquared()<1e-8f)side.set(direction).cross(Math.abs(direction.y)>.9f?new Vector3f(1,0,0):new Vector3f(0,1,0));
            side.normalize();Vector3f normal=new Vector3f(side).cross(direction).normalize();
            rotation.setFromNormalized(new Matrix3f(normal,side,direction));
            // Source linelink prefab is authored along +Z after its -90deg Y config.
            // Affine length adaptation only: original meshes/atlas/curves supply all arcs.
            right=new Vector3f(0,0,1);up=new Vector3f(0,1,0);
        } else {
            // Sampled original tuowei mesh extends behind -Z in configured space.
            if(v.effect.type()==0)rotation.rotationTo(new Vector3f(0,0,1),new Vector3f((float)v.effect.velocity().x,(float)v.effect.velocity().y,(float)v.effect.velocity().z).normalize());
            Quaternionf inverse=new Quaternionf(rotation).conjugate();right=inverse.transform(new Vector3f(cameraRight));up=inverse.transform(new Vector3f(cameraUp));
        }
        pose.pushPose();pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);pose.mulPose(rotation);
        if(chain)pose.scale(1,1,(float)v.effect.velocity().length());else if(v.effect.type()==1)pose.scale((float)v.effect.size(),(float)v.effect.size(),(float)v.effect.size());
        if(ZeusParticleMeshes.ready(a.data)) {
            // Preserve authored emitter order, alpha ordering and pending
            // billboard geometry before an immediate static-mesh draw.
            for(var emitter:a.data.emitters) {
                if("mesh".equals(emitter.mode)){Drawing.BUFFERS.endBatch();ZeusParticleMeshes.draw(emitter,age/20,pose);}
                else batches(DimensionParticles.sample(ZeusParticleMeshes.single(a.data,emitter),age/20,right,up),pose);
            }
        } else batches(DimensionParticles.sample(a.data,age/20,right,up),pose);
        pose.popPose();
    }
    private static void batches(List<DimensionParticles.Batch> batches,PoseStack pose) {
        for(var batch:batches) {
            var type=DimensionEffectRenderType.of(new ResourceLocation(batch.texture()),"additive".equals(batch.render()));var out=Drawing.BUFFERS.getBuffer(type);var mesh=batch.mesh();float[] c=batch.color(),uvST=batch.uvST();
            for(int[] face:mesh.triangles)for(int corner=0;corner<4;corner++) {
                int i=face[Math.min(corner,2)];float[] p=mesh.positions[i],uv=mesh.uv[i];
                out.vertex(pose.last().pose(),p[0],p[1],p[2]).color(Math.min(1,c[0]),Math.min(1,c[1]),Math.min(1,c[2]),Math.min(1,c[3]))
                    .uv(uv[0]*uvST[0]+uvST[2],uv[1]*uvST[1]-uvST[3]).overlayCoords(0).uv2(15728880).normal(pose.last().normal(),0,0,1).endVertex();
            }
        }
    }
}
