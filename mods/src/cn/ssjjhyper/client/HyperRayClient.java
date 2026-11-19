package cn.ssjjhyper.client;

import cn.ssjjhyper.HyperRayNetwork;
import cn.ssjjhyper.HyperRays;
import cn.ssjjdimension.client.DimensionParticles;
import cn.ssjjdimension.client.DimensionEffectRenderType;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original source effects follow only authoritative, scene-clipped segments. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperRayClient {
    public static final class Ref {public String resource;public float duration;public int[] world;public float[] prewarm;}
    public static final class Profile {public Ref flight;@SerializedName("return")public Ref returning;}
    public static final class Atlas {public int columns,rows,width,height,cell_width,cell_height,gutter;}
    public static final class Profiles {public Map<String,Profile> profiles;public Map<String,Atlas> texture_atlas;}
    private record Unit(DimensionParticles.Data data,float birth,float life,float period,float prewarm){}
    private record Layer(DimensionParticles.Data data,float prewarm){}
    private record Asset(List<Layer> local,List<Unit> world,float duration){}
    private static final class Trail {final NavigableMap<Long,HyperRayNetwork.Effect> history=new TreeMap<>();long launch,finish=Long.MAX_VALUE;String profile;UUID burst;int slot;Vec3 origin;}
    private static volatile Map<String,Profile> profiles=Map.of();
    private static volatile Map<String,Atlas> atlases=Map.of();
    private static volatile Map<String,Asset> assets=Map.of();
    private static final Map<UUID,Trail> TRAILS=new LinkedHashMap<>();
    private static ClientLevel lastLevel;
    private static final class Drawing {static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));}
    private HyperRayClient(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerHyperRayEffects(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)HyperRayClient::read);}
    }
    private static DimensionParticles.Data subset(DimensionParticles.Data data,List<DimensionParticles.Emitter> emitters){var out=new DimensionParticles.Data();out.schema=data.schema;out.gun=data.gun;out.bone=data.bone;out.emitters=emitters.toArray(DimensionParticles.Emitter[]::new);return out;}
    public static void read(ResourceManager resources){
        profiles=Map.of();assets=Map.of();atlases=Map.of();TRAILS.clear();
        try(var input=resources.open(new ResourceLocation("ssjj_hyper","effects/ray/profiles.json"));var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var source=new Gson().fromJson(reader,Profiles.class);if(source==null||source.profiles==null||source.profiles.size()!=8)throw new IllegalArgumentException("Invalid Hyper ray profiles");
            if(source.texture_atlas==null||source.texture_atlas.isEmpty()||source.texture_atlas.size()>32)throw new IllegalArgumentException("Missing transparent ray atlas metadata");
            for(var a:source.texture_atlas.values())if(a.columns<1||a.rows<1||a.columns*a.rows>256||a.gutter<2||a.cell_width<2||a.cell_height<2||a.width!=a.columns*(a.cell_width+2*a.gutter)||a.height!=a.rows*(a.cell_height+2*a.gutter)||a.width>8192||a.height>8192)throw new IllegalArgumentException("Invalid ray atlas metadata");
            Map<String,Asset> loaded=new LinkedHashMap<>();
            for(var entry:source.profiles.entrySet()){
                for(boolean returning:new boolean[]{false,true}){
                var ref=returning?entry.getValue().returning:entry.getValue().flight;
                String kind=returning?"return":"flight";
                if(ref==null||!ref.resource.equals("ssjj_hyper:effects/ray/"+entry.getKey()+"_"+kind+".json"))throw new IllegalArgumentException("Mismatched Hyper flight/return appearance");
                if(ref.resource==null||!ref.resource.startsWith("ssjj_hyper:effects/ray/")||!Float.isFinite(ref.duration)||ref.duration<=0||ref.duration>60||ref.world==null)throw new IllegalArgumentException("Invalid Hyper source reference");
                if(loaded.containsKey(ref.resource))continue;
                try(var stream=resources.open(new ResourceLocation(ref.resource));var text=new InputStreamReader(stream,StandardCharsets.UTF_8)){
                    var data=new Gson().fromJson(text,DimensionParticles.Data.class);DimensionParticles.validate(data,data.gun,"world","ssjj_hyper:textures/effects/");
                    var world=new HashSet<Integer>();for(int i:ref.world){if(i<0||i>=data.emitters.length)throw new IllegalArgumentException("Invalid world emitter");world.add(i);}
                    if(ref.prewarm==null||ref.prewarm.length!=data.emitters.length)throw new IllegalArgumentException("Missing source prewarm");
                    List<Layer> local=new ArrayList<>();List<Unit> units=new ArrayList<>();
                    for(int i=0;i<data.emitters.length;i++){
                        var e=data.emitters[i];if(!source.texture_atlas.containsKey(e.texture))throw new IllegalArgumentException("Missing ray layer atlas "+e.texture);if(resources.getResource(new ResourceLocation(e.texture)).isEmpty())throw new IllegalArgumentException("Missing Hyper source texture "+e.texture);
                        float prewarm=ref.prewarm[i];if(!Float.isFinite(prewarm)||prewarm<0||prewarm>10)throw new IllegalArgumentException("Invalid source prewarm");
                        if(!world.contains(i)){local.add(new Layer(subset(data,List.of(e)),prewarm));continue;}
                        for(var particle:e.particles){var one=new DimensionParticles.Emitter();one.name=e.name;one.texture=e.texture;one.mode=e.mode;one.render=e.render;one.duration=60;one.billboard_axis=e.billboard_axis;one.geometry=e.geometry;one.particles=new DimensionParticles.Particle[]{particle};units.add(new Unit(subset(data,List.of(one)),particle.birth,particle.life,e.duration,prewarm));}
                    }
                    loaded.put(ref.resource,new Asset(List.copyOf(local),List.copyOf(units),ref.duration));
                }
            }
            }
            assets=Map.copyOf(loaded);atlases=Map.copyOf(source.texture_atlas);profiles=Map.copyOf(source.profiles);
            LogUtils.getLogger().info("HYPER_RAY loaded {} original effects for {} appearances",assets.size(),profiles.size());
        }catch(Exception error){LogUtils.getLogger().error("Cannot load original Hyper ray effects",error);}
    }
    private static void world(ClientLevel level){if(level!=lastLevel){TRAILS.clear();lastLevel=level;}}
    private static <T> void cap(Map<UUID,T> values,int size){while(values.size()>size){var i=values.keySet().iterator();i.next();i.remove();}}
    public static void accept(HyperRayNetwork.Effect effect){
        var mc=Minecraft.getInstance();world(mc.level);if(effect==null||!effect.valid()||mc.level==null||!mc.level.dimension().location().equals(effect.dimension()))return;
        long now=mc.level.getGameTime();if(effect.tick()<now-80||effect.tick()>now+10)return;
        if(effect.type()==2){var trail=TRAILS.get(effect.id());if(trail!=null&&effect.burst().equals(trail.burst)&&effect.slot()==trail.slot)trail.finish=Math.min(trail.finish,effect.tick());return;}
        // Temporarily render flight only; impact and stun/buff packets retain server semantics.
        if(effect.type()!=0)return;
        var trail=TRAILS.computeIfAbsent(effect.id(),id->{var fresh=new Trail();fresh.launch=effect.tick()-1;fresh.profile=effect.profile();fresh.burst=effect.burst();fresh.slot=effect.slot();fresh.origin=effect.origin();return fresh;});
        if(!effect.profile().equals(trail.profile)||!effect.burst().equals(trail.burst)||effect.slot()!=trail.slot||!effect.origin().equals(trail.origin)||effect.tick()>trail.finish)return;
        trail.history.put(effect.tick(),effect);while(trail.history.size()>HyperRays.MAX_AGE)trail.history.pollFirstEntry();cap(TRAILS,256);
    }
    public static Vec3 interpolate(Vec3 from,Vec3 to,double phase){return from.lerp(to,Math.max(0,Math.min(1,phase)));}
    private record Point(Vec3 center,Quaternionf rotation){}
    private static Point point(Trail trail,double time){
        var entry=trail.history.ceilingEntry((long)Math.ceil(time));if(entry==null)entry=trail.history.lastEntry();if(entry==null)return null;
        var e=entry.getValue();var direction=e.to().subtract(e.from());var rotation=new Quaternionf();
        // Converted projectile emitters trail along -Z in every supplied skin.
        if(direction.lengthSqr()>1e-10)rotation.rotationTo(new Vector3f(0,0,1),new Vector3f((float)direction.x,(float)direction.y,(float)direction.z).normalize());
        return new Point(interpolate(e.from(),e.to(),time-(e.tick()-1)),rotation);
    }
    private static Asset asset(String name,boolean returning){var p=profiles.get(name);if(p==null)return null;var ref=returning?p.returning:p.flight;return assets.get(ref.resource);}
    private static HyperRayNetwork.Effect segment(Trail trail,double time){var entry=trail.history.ceilingEntry((long)Math.ceil(time));if(entry==null)entry=trail.history.lastEntry();return entry==null?null:entry.getValue();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();world(mc.level);if(mc.level==null)return;long now=mc.level.getGameTime();
        TRAILS.values().removeIf(t->t.history.isEmpty()||t.finish!=Long.MAX_VALUE&&now>t.finish+5||now-t.history.lastKey()>6||now-t.launch>HyperRays.MAX_AGE+6);
    }
    private record Frame(DimensionParticles.Batch batch,Point point,float fade){}
    // Authored world particles remain at their actual emission points. The
    // flight fire cloud is distinct from the compact returning light-ball.
    private static final float SPRITE_SCALE=5;
    private static DimensionParticles.Batch visibleSize(DimensionParticles.Batch batch){
        // Match the reference fire-body footprint at Minecraft ray distances.
        // Expand each original sprite about its own centre; preserve emitter
        // positions, authored drift, skin art, atlas animation and trajectory.
        var mesh=batch.mesh();var large=new DimensionParticles.Mesh();large.uv=mesh.uv;large.triangles=mesh.triangles;large.positions=new float[mesh.positions.length][3];
        float[] center=new float[3];for(var p:mesh.positions)for(int i=0;i<3;i++)center[i]+=p[i]/mesh.positions.length;
        for(int n=0;n<mesh.positions.length;n++)for(int i=0;i<3;i++)large.positions[n][i]=center[i]+(mesh.positions[n][i]-center[i])*SPRITE_SCALE;
        var a=atlases.get(batch.texture());float[] st=batch.uvST().clone();
        if(a!=null){
            // The shared sampler interpolates all LUT floats. UV atlas offsets
            // are discrete frame indices, so reconstruct and snap the combined
            // index before drawing; never slide through neighbouring cells.
            double sx=(double)(a.cell_width+2*a.gutter)/a.width,sy=(double)(a.cell_height+2*a.gutter)/a.height;
            double col=(st[2]-(a.gutter+.5)/a.width)/sx,row=(-st[3]-(a.gutter+.5)/a.height)/sy;
            int frame=Math.max(0,Math.min(a.columns*a.rows-1,(int)Math.floor(row*a.columns+col+1e-4)));
            st[2]=(float)(((frame%a.columns)*(a.cell_width+2*a.gutter)+a.gutter+.5)/a.width);
            st[3]=(float)(-((frame/a.columns)*(a.cell_height+2*a.gutter)+a.gutter+.5)/a.height);
        }
        return new DimensionParticles.Batch(batch.texture(),batch.render(),large,batch.color(),st);
    }
    private static void sample(List<Frame> frames,DimensionParticles.Data data,float age,Point point,float fade,Vector3f right,Vector3f up){
        if(age<0||fade<=0)return;var inverse=new Quaternionf(point.rotation).conjugate();
        var r=inverse.transform(new Vector3f(right));var u=inverse.transform(new Vector3f(up));
        for(var batch:DimensionParticles.sample(data,age,r,u))frames.add(new Frame(visibleSize(batch),point,fade));
    }
    private record Visual(UUID burst,int slot,String profile,Point head,float age,float fade,boolean returning){}
    private static float fade(Trail trail,double time){return trail.finish==Long.MAX_VALUE?1:(float)Math.max(0,1-(time-trail.finish)/4);}
    private static boolean visible(Trail trail,double time){return time>=trail.launch&&(trail.finish==Long.MAX_VALUE||time<trail.finish+4)&&!trail.history.isEmpty();}
    /** One visual for each outbound vertex, one shared orb for each returning burst. */
    private static List<Visual> visuals(Collection<Trail> trails,double time){
        var out=new ArrayList<Visual>();Map<UUID,List<Trail>> groups=new LinkedHashMap<>();
        for(var trail:trails){
            if(!visible(trail,time))continue;var e=segment(trail,time);if(e==null)continue;
            if(e.returning())groups.computeIfAbsent(trail.burst,k->new ArrayList<>()).add(trail);
            else{Point p=point(trail,time);if(p!=null)out.add(new Visual(trail.burst,trail.slot,trail.profile,p,(float)((time-trail.launch)/20),fade(trail,time),false));}
        }
        for(var entry:groups.entrySet()){
            var group=entry.getValue();Vec3 center=Vec3.ZERO;float opacity=0;double totalWeight=0,began=time;var first=group.get(0);
            for(var trail:group){Point p=point(trail,time);float weight=fade(trail,time);center=center.add(p.center.scale(weight));totalWeight+=weight;opacity=Math.max(opacity,weight);for(var e:trail.history.values())if(e.returning()){began=Math.min(began,e.tick()-1);break;}}
            if(totalWeight<=0)continue;center=center.scale(1.0/totalWeight);Vec3 direction=first.origin.subtract(center);var rotation=new Quaternionf();
            if(direction.lengthSqr()>1e-10)rotation.rotationTo(new Vector3f(0,0,1),new Vector3f((float)direction.x,(float)direction.y,(float)direction.z).normalize());
            out.add(new Visual(entry.getKey(),-1,first.profile,new Point(center,rotation),(float)((time-began)/20),opacity,true));
        }
        return out;
    }
    /** The drawing and offline visual QA use this complete sampling path. */
    private static List<Frame> frames(Collection<Trail> trails,double time,Vector3f right,Vector3f up){
        var out=new ArrayList<Frame>();
        for(var visual:visuals(trails,time)){
            Asset a=asset(visual.profile,visual.returning);if(a==null)continue;
            for(var layer:a.local){
                float phase=visual.age+layer.prewarm;
                var emitter=layer.data.emitters[0];
                // A 0.1-second source flash loop would extinguish the moving
                // head repeatedly. Keep the two authored head cores visible;
                // the flame atlas and world-space fire cloud still animate.
                if(visual.returning||"huan_shan".equals(emitter.name)||"Particle".equals(emitter.name)){var particle=emitter.particles[0];phase=particle.birth+particle.life*.5f;}
                sample(out,layer.data,phase,visual.head,visual.fade,right,up);
            }
            if(visual.returning)continue;
            Trail trail=null;for(var t:trails)if(visual.burst.equals(t.burst)&&visual.slot==t.slot){trail=t;break;}if(trail==null)continue;
            for(var unit:a.world){float phase=visual.age+unit.prewarm;
                int first=Math.max(0,(int)Math.floor((phase-unit.birth-unit.life)/unit.period)+1),last=(int)Math.floor((phase-unit.birth)/unit.period);
                for(int cycle=first;cycle<=last;cycle++){
                    float born=unit.birth+cycle*unit.period-unit.prewarm;
                    // No world-space particles existed before the trigger. Do
                    // not stack prewarmed clouds at a fabricated muzzle anchor.
                    if(born<0||trail.launch+born*20>trail.finish)continue;
                    Point emitted=point(trail,trail.launch+Math.max(0,born)*20);
                    if(emitted!=null)sample(out,unit.data,phase-cycle*unit.period,emitted,visual.fade,right,up);
                }
            }
        }return out;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();world(mc.level);if(mc.level==null||TRAILS.isEmpty())return;
        Vec3 camera=event.getCamera().getPosition();var cr=event.getCamera().rotation();var right=cr.transform(new Vector3f(1,0,0));var up=cr.transform(new Vector3f(0,1,0));double time=mc.level.getGameTime()+event.getPartialTick()-1;
        for(var frame:frames(TRAILS.values(),time,right,up))draw(frame,event.getPoseStack(),camera);
        Drawing.BUFFERS.endBatch();
    }
    private static void draw(Frame frame,PoseStack pose,Vec3 camera){
        var point=frame.point;if(point.center.distanceToSqr(camera)>160*160)return;var batch=frame.batch;
        pose.pushPose();pose.translate(point.center.x-camera.x,point.center.y-camera.y,point.center.z-camera.z);pose.mulPose(point.rotation);
        boolean additive="additive".equals(batch.render());
        var out=Drawing.BUFFERS.getBuffer(DimensionEffectRenderType.of(new ResourceLocation(batch.texture()),additive));var mesh=batch.mesh();float[] c=batch.color(),st=batch.uvST();
        // Preserve the source shader's HDR additive energy without overflowing
        // Minecraft's unsigned-byte vertex colours. Each pass stays in [0,1].
        float rgb=Math.max(1,Math.max(c[0],Math.max(c[1],c[2]))),energy=additive?Math.min(16,rgb*c[3]*frame.fade):Math.min(1,c[3]*frame.fade);
        int passes=additive?(int)Math.ceil(energy):1;
        for(int pass=0;pass<passes;pass++){
            float alpha=additive?Math.min(1,energy-pass):energy;
            for(int[] face:mesh.triangles)for(int corner=0;corner<4;corner++){
                int i=face[Math.min(corner,2)];float[] p=mesh.positions[i],uv=mesh.uv[i];
                out.vertex(pose.last().pose(),p[0],p[1],p[2]).color(Math.min(1,c[0]/(additive?rgb:1)),Math.min(1,c[1]/(additive?rgb:1)),Math.min(1,c[2]/(additive?rgb:1)),alpha).uv(uv[0]*st[0]+st[2],uv[1]*st[1]-st[3]).overlayCoords(0).uv2(15728880).normal(pose.last().normal(),0,0,1).endVertex();
            }
        }pose.popPose();
    }
}
