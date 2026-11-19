package cn.ssjjhyper.client;

import cn.ssjjhyper.HyperChargeNetwork;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Original appearance-specific fire rings, head marks and detonation particles. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperChargeEffects {
    public static final class Ref {
        public String resource,anchor;
        public float duration;
        @SerializedName("source_radius") public float sourceRadius;
        @SerializedName("radial_normalize") public boolean radialNormalize;
    }
    public static final class Profile {public Ref ring,mark,detonation;}
    public static final class Atlas {public int columns,rows,width,height,cell_width,cell_height,gutter;}
    public static final class Profiles {public Map<String,Profile> profiles;public Map<String,Atlas> texture_atlas;}
    private record Asset(DimensionParticles.Data data,Ref ref) {}
    private record Visible(HyperChargeNetwork.Visual effect,long born) {}
    private record Frame(DimensionParticles.Batch batch,Vec3 center) {}
    private record SampleKey(String resource,int seconds,int radius) {}
    private static final Map<SampleKey,List<DimensionParticles.Batch>> SAMPLES=new HashMap<>();
    private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
    private static float[] DRAW_VERTICES=new float[0];
    private static long sampledTick=Long.MIN_VALUE;
    private static float sampledPartial;
    private static final Vector3f sampledRight=new Vector3f(),sampledUp=new Vector3f();
    private static volatile Map<String,Profile> profiles=Map.of();
    private static volatile Map<String,Atlas> atlases=Map.of();
    private static volatile Map<String,Asset> assets=Map.of();
    private static final Map<UUID,Visible> RINGS=new LinkedHashMap<>(),MARKS=new LinkedHashMap<>(),HITS=new LinkedHashMap<>();
    private static ClientLevel level;
    private static boolean fallbackAttempted;
    private static final class Drawing {
        static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));
        static final BufferBuilder PASS=new BufferBuilder(65536);
    }
    private HyperChargeEffects() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerHyperChargeResources(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)HyperChargeEffects::read);}
    }
    public static void read(ResourceManager resources) {
        profiles=Map.of();assets=Map.of();atlases=Map.of();fallbackAttempted=false;SAMPLES.clear();TEXTURES.clear();sampledTick=Long.MIN_VALUE;
        try(var input=resources.open(new ResourceLocation("ssjj_hyper","effects/energy/profiles.json"));var text=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            var source=new Gson().fromJson(text,Profiles.class);
            if(source==null||source.profiles==null||source.profiles.size()!=8)throw new IllegalArgumentException("Invalid Hyper charge profiles");
            var loaded=new LinkedHashMap<String,Asset>();
            if(source.texture_atlas!=null)for(var a:source.texture_atlas.values())
                if(a.columns<1||a.rows<1||a.columns*a.rows>256||a.gutter<2||a.cell_width<2||a.cell_height<2
                    ||a.width!=a.columns*(a.cell_width+2*a.gutter)||a.height!=a.rows*(a.cell_height+2*a.gutter)||a.width>8192||a.height>8192)
                    throw new IllegalArgumentException("Invalid charge atlas metadata");
            for(var entry:source.profiles.entrySet()) {
                var profile=entry.getValue();if(profile==null)throw new IllegalArgumentException("Missing Hyper charge appearance");
                for(var kind:List.of("ring","mark","detonation")) {
                    Ref ref=switch(kind){case "ring"->profile.ring;case "mark"->profile.mark;default->profile.detonation;};
                    if(ref==null||ref.resource==null||!ref.resource.equals("ssjj_hyper:effects/energy/"+entry.getKey()+"_"+kind+".json")
                        ||!Float.isFinite(ref.duration)||ref.duration<=0||ref.duration>60)throw new IllegalArgumentException("Invalid Hyper charge source reference");
                    if(kind.equals("ring")&&(!Float.isFinite(ref.sourceRadius)||ref.sourceRadius<=0||!ref.radialNormalize))throw new IllegalArgumentException("Missing authored ring radius");
                    try(var stream=resources.open(new ResourceLocation(ref.resource));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                        var data=new Gson().fromJson(reader,DimensionParticles.Data.class);
                        DimensionParticles.validate(data,data.gun,"world","ssjj_hyper:textures/effects/");
                        for(var emitter:data.emitters){var texture=new ResourceLocation(emitter.texture);if(resources.getResource(texture).isEmpty())throw new IllegalArgumentException("Missing Hyper charge texture "+emitter.texture);TEXTURES.put(emitter.texture,texture);}
                        loaded.put(ref.resource,new Asset(data,ref));
                    }
                }
            }
            profiles=Map.copyOf(source.profiles);assets=Map.copyOf(loaded);atlases=source.texture_atlas==null?Map.of():Map.copyOf(source.texture_atlas);
            LogUtils.getLogger().info("HYPER_CHARGE loaded {} original effects for {} appearances",assets.size(),profiles.size());
        }catch(Exception error){LogUtils.getLogger().error("Cannot load original Hyper charge effects",error);}
    }
    private static boolean ready() {
        if(profiles.isEmpty()&&!fallbackAttempted){read(Minecraft.getInstance().getResourceManager());fallbackAttempted=true;}
        return !profiles.isEmpty();
    }
    private static void world(){var next=Minecraft.getInstance().level;if(level!=next){level=next;RINGS.clear();MARKS.clear();HITS.clear();SAMPLES.clear();sampledTick=Long.MIN_VALUE;}}
    private static <T> void cap(Map<UUID,T> values,int limit){while(values.size()>limit){var it=values.keySet().iterator();it.next();it.remove();}}
    public static void accept(HyperChargeNetwork.Visual effect) {
        world();if(effect==null||!effect.valid()||level==null||!effect.dimension().equals(level.dimension().location()))return;
        if(effect.type()==4){clearPersistent(effect.owner());return;}
        if(effect.type()==3){MARKS.remove(effect.id());return;}
        long now=level.getGameTime();if(effect.tick()>now+10||effect.life()==0||now>=effect.tick()+effect.life())return;
        Map<UUID,Visible> group=switch(effect.type()){case 0->RINGS;case 1->MARKS;case 2->HITS;default->null;};if(group==null)return;
        var old=group.get(effect.id());
        // Mark refresh packets extend expiry without restarting their source loop.
        long born=old!=null&&effect.type()==1&&old.effect.owner().equals(effect.owner())&&old.effect.targetUuid().equals(effect.targetUuid())?old.born:effect.tick();
        group.put(effect.id(),new Visible(effect,born));cap(group,effect.type()==1?512:128);
    }
    private static void clearPersistent(UUID owner){for(var group:List.of(RINGS,MARKS))group.values().removeIf(v->v.effect.owner().equals(owner));}
    private static Asset asset(HyperChargeNetwork.Visual effect) {
        var p=profiles.get(effect.profile());if(p==null)return null;
        Ref ref=switch(effect.type()){case 0->p.ring;case 1->p.mark;case 2->p.detonation;default->null;};
        return ref==null?null:assets.get(ref.resource);
    }
    public static float ringRadius(float maximum,float age,int life){return maximum*Math.max(0,Math.min(1,age/Math.max(1,life)));}
    private static Vec3 center(Visible visible,float partial,Ref ref) {
        var e=visible.effect;if(e.type()!=1)return e.center();
        var target=level.getEntity(e.target());
        if(target==null||!target.getUUID().equals(e.targetUuid())||target.isRemoved()||!target.isAlive())return null;
        double top="targetTop".equals(ref.anchor)?target.getBbHeight():0;
        return new Vec3(Mth.lerp(partial,target.xOld,target.getX()),Mth.lerp(partial,target.yOld,target.getY())+top,Mth.lerp(partial,target.zOld,target.getZ()));
    }
    private static float[] atlasUV(DimensionParticles.Batch batch) {
        float[] st=batch.uvST().clone();var a=atlases.get(batch.texture());if(a==null)return st;
        // Single-frame ring sheets use authored continuous UV scrolling.
        // They have no neighbouring animation cell to snap to; preserve their flow.
        if(a.columns*a.rows==1)return st;
        // Atlas offsets are frame indices; shared LUT interpolation must not slide across cells.
        double sx=(double)(a.cell_width+2*a.gutter)/a.width,sy=(double)(a.cell_height+2*a.gutter)/a.height;
        double col=(st[2]-(a.gutter+.5)/a.width)/sx,row=(-st[3]-(a.gutter+.5)/a.height)/sy;
        int frame=Math.max(0,Math.min(a.columns*a.rows-1,(int)Math.floor(row*a.columns+col+1e-4)));
        st[2]=(float)(((frame%a.columns)*(a.cell_width+2*a.gutter)+a.gutter+.5)/a.width);
        st[3]=(float)(-((frame/a.columns)*(a.cell_height+2*a.gutter)+a.gutter+.5)/a.height);return st;
    }
    private static List<Frame> frames(Visible visible,long now,float partial,Vector3f right,Vector3f up) {
        var e=visible.effect;var a=asset(e);if(a==null)return List.of();float ticks=now-visible.born+partial;
        if(ticks<0||now+partial>=e.tick()+e.life())return List.of();
        var center=center(visible,partial,a.ref);if(center==null)return List.of();
        float seconds=e.type()==0?Math.min(a.ref.duration-.00001f,ticks/e.life()*a.ref.duration):ticks/20;
        if(e.type()==2&&seconds>=a.ref.duration)return List.of();
        float radius=e.type()==0?ringRadius(e.radius(),ticks,e.life()):-1;
        var sampled=samples(a,seconds,radius,now,partial,right,up);
        var out=new ArrayList<Frame>(sampled.size());for(var batch:sampled)out.add(new Frame(batch,center));return out;
    }
    /** Same frame, source age and camera yield identical local vertices for every target. */
    private static List<DimensionParticles.Batch> samples(Asset a,float seconds,float radius,long now,float partial,Vector3f right,Vector3f up){
        if(sampledTick!=now||Float.floatToIntBits(sampledPartial)!=Float.floatToIntBits(partial)||!sampledRight.equals(right)||!sampledUp.equals(up)){
            SAMPLES.clear();sampledTick=now;sampledPartial=partial;sampledRight.set(right);sampledUp.set(up);
        }
        var key=new SampleKey(a.ref.resource,Float.floatToIntBits(seconds),Float.floatToIntBits(radius));var old=SAMPLES.get(key);if(old!=null)return old;
        var sampled=DimensionParticles.sample(a.data,seconds,right,up);float scale=1;
        if(radius>=0) {
            // FIRE_RING, rather than the source particle asset, supplies radial movement.
            // Scale all source axes together: the authored ring is a cylinder, not a flat ground strip.
            float source=0;for(var b:sampled)for(var p:b.mesh().positions)source=Math.max(source,(float)Math.hypot(p[0],p[2]));
            scale=radius/(source>1e-6f?source:a.ref.sourceRadius);
        }
        var out=new ArrayList<DimensionParticles.Batch>(sampled.size());for(var batch:sampled) {
            var mesh=batch.mesh();
            // Marks and detonations already have the exact source coordinates. Never clone their meshes per target.
            if(radius>=0){var transformed=new DimensionParticles.Mesh();transformed.uv=mesh.uv;transformed.triangles=mesh.triangles;transformed.positions=new float[mesh.positions.length][3];
                for(int i=0;i<mesh.positions.length;i++){var p=mesh.positions[i];transformed.positions[i]=new float[]{p[0]*scale,p[1]*scale,p[2]*scale};}mesh=transformed;}
            out.add(new DimensionParticles.Batch(batch.texture(),batch.render(),mesh,batch.color(),atlasUV(batch)));
        }
        // At most the visible effect cap can contribute entries; do not retain previous frame geometry.
        if(SAMPLES.size()<768)SAMPLES.put(key,out);return out;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;world();if(level==null)return;long now=level.getGameTime();
        for(var group:List.of(RINGS,MARKS,HITS))group.values().removeIf(v->now>=v.effect.tick()+v.effect.life());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;world();
        HyperChargeMeshRenderer.beginFrame();
        try {
            if(level==null||RINGS.isEmpty()&&MARKS.isEmpty()&&HITS.isEmpty()||!ready())return;
            var camera=event.getCamera().getPosition();var rotation=event.getCamera().rotation();
            var right=rotation.transform(new Vector3f(1,0,0));var up=rotation.transform(new Vector3f(0,1,0));
            boolean shared=HyperChargeMeshRenderer.ready();
            for(var group:List.of(RINGS,MARKS,HITS))for(var visible:group.values()){
                var a=asset(visible.effect);if(a==null)continue;var center=center(visible,event.getPartialTick(),a.ref);
                if(center==null||center.distanceToSqr(camera)>160*160)continue;
                var frames=frames(visible,level.getGameTime(),event.getPartialTick(),right,up);
                if(shared){
                    var batches=new ArrayList<DimensionParticles.Batch>(frames.size());for(var frame:frames)batches.add(frame.batch);
                    if(!HyperChargeMeshRenderer.draw(batches,event.getPoseStack(),center,camera))for(var frame:frames)draw(frame,event.getPoseStack(),camera);
                }else for(var frame:frames)draw(frame,event.getPoseStack(),camera);
            }
            Drawing.BUFFERS.endBatch();
        } finally {HyperChargeMeshRenderer.endFrame();}
    }
    private static void draw(Frame frame,PoseStack pose,Vec3 camera) {
        if(frame.center.distanceToSqr(camera)>160*160)return;var batch=frame.batch;
        pose.pushPose();pose.translate(frame.center.x-camera.x,frame.center.y-camera.y,frame.center.z-camera.z);
        boolean additive="additive".equals(batch.render());var mesh=batch.mesh();float[] c=batch.color(),st=batch.uvST();
        var out=Drawing.BUFFERS.getBuffer(DimensionEffectRenderType.of(TEXTURES.computeIfAbsent(batch.texture(),ResourceLocation::new),additive));
        float rgb=Math.max(1,Math.max(c[0],Math.max(c[1],c[2]))),energy=additive?Math.min(16,rgb*c[3]):Math.min(1,c[3]);
        float red=Math.min(1,c[0]/(additive?rgb:1)),green=Math.min(1,c[1]/(additive?rgb:1)),blue=Math.min(1,c[2]/(additive?rgb:1));
        var matrix=pose.last().pose();var normal=pose.last().normal().transform(new Vector3f(0,0,1));var vertex=new Vector4f();
        int length=mesh.positions.length*5;if(DRAW_VERTICES.length<length)DRAW_VERTICES=new float[length];
        // Shared mesh vertices and HDR passes all use the same transform and atlas state in this draw.
        for(int i=0;i<mesh.positions.length;i++){var p=mesh.positions[i];var uv=mesh.uv[i];matrix.transform(vertex.set(p[0],p[1],p[2],1));int base=i*5;
            DRAW_VERTICES[base]=vertex.x;DRAW_VERTICES[base+1]=vertex.y;DRAW_VERTICES[base+2]=vertex.z;DRAW_VERTICES[base+3]=uv[0]*st[0]+st[2];DRAW_VERTICES[base+4]=uv[1]*st[1]-st[3];}
        if(additive&&energy>1&&out instanceof BufferBuilder buffer){
            // HDR full-alpha passes are byte-identical. Encode once with the native writer, then copy the complete pass.
            var scratch=Drawing.PASS;scratch.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);writePass(scratch,mesh,red,green,blue,1,normal);
            var encoded=scratch.end();try{
                var bytes=encoded.vertexBuffer();int full=(int)energy;
                for(int pass=0;pass<full;pass++){bytes.position(0);buffer.putBulkData(bytes);}
                float fraction=energy-full;
                if(fraction>0){byte alpha=(byte)(int)(fraction*255);for(int offset=15;offset<bytes.limit();offset+=36)bytes.put(offset,alpha);bytes.position(0);buffer.putBulkData(bytes);}
            }finally{encoded.release();}
        }else for(int pass=0;pass<(additive?(int)Math.ceil(energy):1);pass++)writePass(out,mesh,red,green,blue,additive?Math.min(1,energy-pass):energy,normal);
        pose.popPose();
    }
    private static void writePass(VertexConsumer out,DimensionParticles.Mesh mesh,float red,float green,float blue,float alpha,Vector3f normal){
        for(int[] face:mesh.triangles)for(int corner=0;corner<4;corner++){
            int base=face[Math.min(corner,2)]*5;
            // Minecraft's complete-vertex overload writes NEW_ENTITY directly; source order and byte quantization stay native.
            out.vertex(DRAW_VERTICES[base],DRAW_VERTICES[base+1],DRAW_VERTICES[base+2],red,green,blue,alpha,DRAW_VERTICES[base+3],DRAW_VERTICES[base+4],0,15728880,normal.x,normal.y,normal.z);
        }
    }
}
