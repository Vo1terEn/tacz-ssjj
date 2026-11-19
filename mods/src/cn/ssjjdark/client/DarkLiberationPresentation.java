package cn.ssjjdark.client;

import cn.ssjjdark.DarkSkillPresentationNetwork.Cast;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The original normal cast and a wholly separate stationary ultimate actor. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkLiberationPresentation {
    public static final class Index {public int schema;public Map<String,Profile> profiles;}
    public static final class Profile {public String burst,buff,clockMode;public float scale=.6f,burstOffsetY=1.06f,buffOffsetY=1.06f,groundOffsetY=.025f;public transient DarkSkinParticles.Data burstData,buffData;}
    public record Clock(float elapsed,float sourceSeconds,float recovery,float particleClock,float ringClock,boolean body,boolean camera) {}
    public record Actor(Cast cast,ResourceLocation skin,boolean slim) {}
    public record Motion(Cast cast,Clock clock) {}
    public record Anchors(Vec3 burst,Vec3 buff) {}
    private static final LinkedHashMap<UUID,Actor> ACTORS=new LinkedHashMap<>();
    private static volatile Map<String,Profile> profiles=Map.of();
    private static final LinkedHashMap<String,Profile> LOADED=new LinkedHashMap<>(4,.75f,true);
    private static final Set<String> FAILED=new HashSet<>();
    private static ResourceManager resourceManager;
    private static volatile DarkLiberationAvatar.Data avatar;
    private static ClientLevel level;
    private static CameraType savedView;
    private static UUID cameraCast;
    private static boolean cameraOwned;
    private static Method setCameraPosition;
    private static final DarkSkinParticles.Frame FRAME=new DarkSkinParticles.Frame();
    private static final class Drawing {static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));}
    private DarkLiberationPresentation(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)DarkLiberationPresentation::read);}
    }
    public static void read(ResourceManager resources){
        profiles=Map.of();avatar=null;LOADED.clear();FAILED.clear();DarkLiberationMaterial.reset();DarkYsmLiberation.reset();resourceManager=resources;
        try {
            var model=DarkLiberationAvatar.read(resources);Index index;
            try(var in=resources.open(new ResourceLocation("ssjj_dark","effects/liberation/profiles.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){index=new Gson().fromJson(reader,Index.class);}
            if(index==null||index.schema!=1||index.profiles==null||index.profiles.size()!=4)throw new IOException("Expected four original liberation profiles");
            for(var entry:index.profiles.entrySet()){
                if(!cn.ssjjdark.DarkSkillPresentationNetwork.PROFILES.contains(entry.getKey()))throw new IOException("Foreign liberation profile");
                var profile=entry.getValue();if(!Float.isFinite(profile.scale)||profile.scale<=0||profile.scale>4||!Float.isFinite(profile.burstOffsetY)||!Float.isFinite(profile.buffOffsetY)||!Float.isFinite(profile.groundOffsetY)||Math.abs(profile.burstOffsetY)>4||Math.abs(profile.buffOffsetY)>4||Math.abs(profile.groundOffsetY)>4||!effectPath(profile.burst)||!effectPath(profile.buff)||!List.of("native_remap","source_elapsed").contains(profile.clockMode))throw new IOException("Invalid original effect profile");
            }
            avatar=model;profiles=Map.copyOf(index.profiles);LogUtils.getLogger().info("DARK_LIBERATION loaded original avatar and four source skill profile references");
        }catch(Exception failure){LogUtils.getLogger().error("Cannot load original Dark Crystal skill presentation",failure);}
    }
    private static boolean effectPath(String name){return name!=null&&name.startsWith("ssjj_dark:effects/liberation/");}
    private static Profile profile(String name){
        var cached=LOADED.get(name);if(cached!=null)return cached;
        var selected=profiles.get(name);if(selected==null||FAILED.contains(name)||resourceManager==null)return null;
        try{
            selected.burstData=effect(resourceManager,selected.burst,name);selected.buffData=effect(resourceManager,selected.buff,name);LOADED.put(name,selected);
            trimProfiles();
            return selected;
        }catch(Exception failure){DarkLiberationMaterial.unbind(selected.burstData);DarkLiberationMaterial.unbind(selected.buffData);selected.burstData=selected.buffData=null;FAILED.add(name);LogUtils.getLogger().error("Cannot load original skill profile "+name,failure);return null;}
    }
    /** Two idle profiles; the at most four authored profiles can remain pinned by active casts. */
    private static void trimProfiles(){
        var active=new HashSet<String>();for(var actor:ACTORS.values())active.add(actor.cast.profile());
        var iterator=LOADED.entrySet().iterator();while(LOADED.size()>2&&iterator.hasNext()){
            var entry=iterator.next();if(active.contains(entry.getKey()))continue;var old=entry.getValue();iterator.remove();DarkLiberationMaterial.unbind(old.burstData);DarkLiberationMaterial.unbind(old.buffData);old.burstData=old.buffData=null;
        }
    }
    private static DarkSkinParticles.Data effect(ResourceManager resources,String name,String owner)throws IOException {
        if(!effectPath(name))throw new IOException("Foreign liberation effect");
        try(var original=resources.open(new ResourceLocation(name));var in=name.endsWith(".gz")?new GZIPInputStream(original):original;var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var data=new Gson().fromJson(json,DarkSkinParticles.Data.class);DarkSkinParticles.validateDragon(data,"ssjj_dark:"+owner);
            for(var emitter:data.emitters)if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IOException("Missing original liberation texture "+emitter.texture);
            DarkLiberationMaterial.bind(data,json,resources);return data;
        }
    }
    public static Clock clock(Cast cast,long now,float partial){
        float elapsed=(now-cast.startTick()+partial)/20f,charge=cast.chargeTicks()/20f;
        float source=Math.min(3,Math.max(0,elapsed/charge*3));float recovery=smooth((elapsed-charge-.35f)/.55f);
        float particles=elapsed<=charge?elapsed/charge*2.3f:2.3f+elapsed-charge;
        float ring=particles<4?particles:2+(particles-4)%2;
        return new Clock(elapsed,source,recovery,particles,ring,elapsed>=0&&elapsed<=charge+1.2f,elapsed>=0&&elapsed<=charge+.95f);
    }
    public static float smooth(float value){float v=Math.max(0,Math.min(1,value));return v*v*(3-2*v);}
    public static boolean controlsView(Cast cast,UUID local,Clock time){return !cast.phantom()&&cast.owner().equals(local)&&time.camera();}
    /** Phantom anchors are packet values, regardless of where the owner subsequently moves. */
    public static Vec3 anchor(Cast cast,Vec3 owner){return cast.phantom()||owner==null?cast.origin():owner;}
    /** The stationary ultimate actor owns the burst; all status auras belong to the living owner. */
    public static Anchors anchors(Cast cast,Vec3 owner){return new Anchors(anchor(cast,owner),owner);}
    public static Anchors resolveAnchors(Cast cast,float partial){
        world();if(cast==null||level==null||!level.dimension().location().equals(cast.dimension()))return null;
        var owner=level.getPlayerByUUID(cast.owner());boolean present=owner!=null&&owner.isAlive()&&owner.getId()==cast.entityId();
        return anchors(cast,present?owner.getPosition(partial):null);
    }
    private static void world(){var next=Minecraft.getInstance().level;if(next!=level){restoreCamera();ACTORS.clear();DarkYsmLiberation.reset();trimProfiles();level=next;}}
    public static void accept(Cast cast){
        world();var mc=Minecraft.getInstance();if(cast==null||!cast.valid()||level==null||!level.dimension().location().equals(cast.dimension()))return;
        long now=level.getGameTime();if(cast.stopped()){ACTORS.remove(cast.id());DarkYsmLiberation.forget(cast.id());trimProfiles();return;}if(cast.startTick()>now+10||cast.activeUntil()<=now)return;
        if(ACTORS.containsKey(cast.id()))return;
        var owner=level.getPlayerByUUID(cast.owner());var player=owner instanceof AbstractClientPlayer p?p:null;
        var texture=player==null?DefaultPlayerSkin.getDefaultSkin(cast.owner()):player.getSkinTextureLocation();
        ACTORS.put(cast.id(),new Actor(cast,texture,player!=null&&"slim".equals(player.getModelName())));
        if(player!=null)DarkYsmLiberation.capture(cast,player);
        profile(cast.profile());
        while(ACTORS.size()>128){var it=ACTORS.keySet().iterator();var old=it.next();it.remove();DarkYsmLiberation.forget(old);}
        // A tracking snapshot joins the current clock silently; it never restarts the skill sound.
        if(now-cast.startTick()<=10)level.playLocalSound(cast.origin().x,cast.origin().y,cast.origin().z,SoundEvent.createVariableRangeEvent(new ResourceLocation("ssjj_dark","darkdiamond2_skill")),SoundSource.PLAYERS,1,1,false);
    }
    private static Actor normal(float partial){
        var mc=Minecraft.getInstance();if(level==null||mc.player==null||!mc.player.isAlive())return null;
        for(var actor:ACTORS.values())if(controlsView(actor.cast,mc.player.getUUID(),clock(actor.cast,level.getGameTime(),partial)))return actor;return null;
    }
    public static boolean normalViewActive(){world();return normal(0)!=null;}
    /** Read-only source motion for a model bridge. A phantom never animates the actual player. */
    public static Motion motion(UUID owner,long now,float partial){
        world();if(owner==null||level==null)return null;
        for(var actor:ACTORS.values())if(!actor.cast.phantom()&&actor.cast.owner().equals(owner)){
            var player=level.getPlayerByUUID(owner);if(player==null||!player.isAlive()||player.getId()!=actor.cast.entityId())continue;
            var time=clock(actor.cast,now,partial);if(time.body()&&now<actor.cast.activeUntil())return new Motion(actor.cast,time);
        }return null;
    }
    public static Motion phantomMotion(UUID castId,long now,float partial){
        world();var actor=ACTORS.get(castId);if(actor==null||!actor.cast.phantom()||level==null)return null;
        var time=clock(actor.cast,now,partial);return time.body()&&now<actor.cast.activeUntil()?new Motion(actor.cast,time):null;
    }
    private static void restoreCamera(){
        var mc=Minecraft.getInstance();if(cameraOwned&&savedView!=null&&mc.options!=null&&mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK)mc.options.setCameraType(savedView);
        cameraOwned=false;savedView=null;cameraCast=null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;world();var mc=Minecraft.getInstance();if(level==null)return;
        long now=level.getGameTime();ACTORS.values().removeIf(actor->{var owner=level.getPlayerByUUID(actor.cast.owner());boolean remove=actor.cast.activeUntil()<=now||!actor.cast.phantom()&&owner!=null&&!owner.isAlive();if(remove)DarkYsmLiberation.forget(actor.cast.id());return remove;});trimProfiles();
        var actor=normal(0);if(actor==null){if(cameraCast!=null)restoreCamera();return;}
        if(!actor.cast.id().equals(cameraCast)){
            restoreCamera();cameraCast=actor.cast.id();savedView=mc.options.getCameraType();cameraOwned=savedView==CameraType.FIRST_PERSON;
            if(cameraOwned)mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if(cameraOwned&&mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK)cameraOwned=false;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void camera(ViewportEvent.ComputeCameraAngles event){
        world();var actor=normal((float)event.getPartialTick());if(actor==null||!cameraOwned)return;
        var mc=Minecraft.getInstance();var time=clock(actor.cast,level.getGameTime(),(float)event.getPartialTick());float charge=actor.cast.chargeTicks()/20f;
        float distance=4.2f*smooth(time.elapsed/.5f)*(1-smooth((time.elapsed-charge-.45f)/.5f));
        float yaw=actor.cast.yaw(),pitch=event.getPitch();event.setYaw(yaw);
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);var forward=new Vec3(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));
        var eye=mc.player.getEyePosition((float)event.getPartialTick());var point=eye.subtract(forward.scale(distance));
        var hit=level.clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));if(hit.getType()!=HitResult.Type.MISS)point=hit.getLocation().add(forward.scale(.12));
        try {
            if(setCameraPosition==null){try{setCameraPosition=Camera.class.getDeclaredMethod("m_90581_",Vec3.class);}catch(NoSuchMethodException named){setCameraPosition=Camera.class.getDeclaredMethod("setPosition",Vec3.class);}setCameraPosition.setAccessible(true);}
            setCameraPosition.invoke(event.getCamera(),point);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Original Dark Crystal camera cannot bind",failure);}
        float after=time.elapsed-charge;if(after>=0&&after<.5f)event.setRoll(event.getRoll()+(float)Math.sin(after*56)*1.6f*(1-after/.5f));
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event){
        world();var actor=normal((float)event.getPartialTick());if(actor==null)return;var time=clock(actor.cast,level.getGameTime(),(float)event.getPartialTick());float after=time.elapsed-actor.cast.chargeTicks()/20f;
        if(after>=0&&after<.8f)event.setFOV(event.getFOV()+9*Math.sin(Math.PI*after/.8));
    }
    @SubscribeEvent public static void player(RenderPlayerEvent.Pre event){
        world();if(level==null||avatar==null)return;
        for(var actor:ACTORS.values()){
            if(actor.cast.phantom()||!actor.cast.owner().equals(event.getEntity().getUUID())||actor.cast.entityId()!=event.getEntity().getId())continue;
            var time=clock(actor.cast,level.getGameTime(),event.getPartialTick());if(!time.body())continue;
            if(event.getEntity() instanceof AbstractClientPlayer player&&DarkYsmLiberation.handles(player))return;
            event.setCanceled(true);var pose=event.getPoseStack();pose.pushPose();
            try{pose.mulPose(Axis.YP.rotationDegrees(-actor.cast.yaw()));drawAvatar(actor,time,pose,event.getMultiBufferSource(),1,event.getPackedLight());}finally{pose.popPose();}return;
        }
    }
    private static void drawAvatar(Actor actor,Clock time,PoseStack pose,MultiBufferSource buffers,float alpha,int light){
        var data=avatar;if(data==null)return;var sample=DarkLiberationAvatar.pose(data,time.sourceSeconds,time.recovery);
        DarkLiberationAvatar.body(data,sample,pose.last().pose(),buffers.getBuffer(RenderType.entityTranslucent(actor.skin)),alpha,light,actor.slim);
        var texture=new ResourceLocation("ssjj_dark","textures/thirdperson/"+actor.cast.profile()+".png");
        DarkLiberationAvatar.weapon(data,sample,pose.last().pose(),buffers.getBuffer(RenderType.entityTranslucent(texture)),alpha,light);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;world();if(level==null||ACTORS.isEmpty())return;
        var camera=event.getCamera().getPosition();var right=event.getCamera().rotation().transform(new Vector3f(1,0,0));var up=event.getCamera().rotation().transform(new Vector3f(0,1,0));
        try{for(var actor:ACTORS.values()){
            var cast=actor.cast;var time=clock(cast,level.getGameTime(),event.getPartialTick());if(time.elapsed<0||level.getGameTime()>=cast.activeUntil())continue;
            var anchors=resolveAnchors(cast,event.getPartialTick());if(anchors==null)continue;
            boolean burstVisible=time.body()&&anchors.burst.distanceToSqr(camera)<=128*128;
            boolean buffVisible=anchors.buff!=null&&anchors.buff.distanceToSqr(camera)<=128*128;
            if(!burstVisible&&!buffVisible)continue;
            var profile=profile(cast.profile());var inverse=Axis.YP.rotationDegrees(cast.yaw());var r=inverse.transform(new Vector3f(right));var u=inverse.transform(new Vector3f(up));
            float effectClock=profile!=null&&"source_elapsed".equals(profile.clockMode)?time.elapsed:time.particleClock;
            var pose=event.getPoseStack();
            if(burstVisible){
                var point=anchors.burst;pose.pushPose();
                try{
                    pose.translate(point.x-camera.x,point.y-camera.y,point.z-camera.z);pose.mulPose(Axis.YP.rotationDegrees(-cast.yaw()));
                    if(cast.phantom()&&!DarkYsmLiberation.renderPhantom(cast,time,pose,Drawing.BUFFERS,15728880))drawAvatar(actor,time,pose,Drawing.BUFFERS,.65f,15728880);
                    if(profile!=null){var localCamera=inverse.transform(new Vector3f((float)(camera.x-point.x),(float)(camera.y-point.y),(float)(camera.z-point.z)));drawEffectAt(profile.burstData,effectClock,pose,r,u,localCamera,profile.scale,profile.burstOffsetY,profile.groundOffsetY);}
                }finally{pose.popPose();}
            }
            if(buffVisible&&profile!=null){
                var point=anchors.buff;pose.pushPose();
                try{
                    pose.translate(point.x-camera.x,point.y-camera.y,point.z-camera.z);pose.mulPose(Axis.YP.rotationDegrees(-cast.yaw()));
                    var localCamera=inverse.transform(new Vector3f((float)(camera.x-point.x),(float)(camera.y-point.y),(float)(camera.z-point.z)));float ringClock=effectClock<4?effectClock:2+(effectClock-4)%2;
                    drawEffectAt(profile.buffData,ringClock,pose,r,u,localCamera,profile.scale,profile.buffOffsetY,profile.groundOffsetY);
                }finally{pose.popPose();}
            }
        }}finally{Drawing.BUFFERS.endBatch();}
    }
    /** The legacy Minecraft height offset is in world units; apply it before the source .6 scale. */
    public static void effectPose(PoseStack pose,float scale,float offsetY){pose.translate(0,offsetY,0);pose.scale(scale,scale,scale);}
    private static void drawEffectAt(DarkSkinParticles.Data data,float clock,PoseStack pose,Vector3f right,Vector3f up,Vector3f camera,float scale,float offsetY,float groundY){
        pose.pushPose();try{effectPose(pose,scale,offsetY);drawEffect(data,clock,pose,right,up,new Vector3f(camera).add(0,-offsetY,0).div(scale),(groundY-offsetY)/scale);}finally{pose.popPose();}
    }
    private static void drawEffect(DarkSkinParticles.Data data,float clock,PoseStack pose,Vector3f right,Vector3f up,Vector3f camera,float groundRelativeY){
        DarkLiberationParticles.fill(data,clock,right,up,camera,FRAME);RenderType active=null;VertexConsumer out=null;
        var groundPose=new Matrix4f(pose.last().pose()).translate(0,groundRelativeY,0);var nativePose=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());var nativeGround=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(groundPose);
        try{
            for(int i=0;i<FRAME.count;i++){var draw=FRAME.get(i);boolean source=DarkLiberationMaterial.nativeMaterial(draw.emitter),ground=DarkLiberationMaterial.ground(draw.emitter);var type=DarkLiberationMaterial.type(draw.emitter,clock);if(type!=active){if(active!=null)Drawing.BUFFERS.endBatch(active);active=type;out=Drawing.BUFFERS.getBuffer(type);if(source)out=DarkLiberationMaterial.consumer(out);}DarkSkinParticles.emit(draw,source?(ground?nativeGround:nativePose):(ground?groundPose:pose.last().pose()),pose.last().normal(),out);}
            if(active!=null)Drawing.BUFFERS.endBatch(active);
        }finally{DarkLiberationParticles.release(FRAME);}
    }
}
