package cn.ssjjhyper.client;

import cn.ssjjhyper.HyperCoreNetwork;
import cn.ssjjdimension.client.DimensionParticles;
import cn.ssjjdimension.client.DimensionEffectRenderType;
import com.google.gson.Gson;
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

/** Authored XML-selected blue/red core spheres and ground detonations, with shared native frame preparation. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperCoreEffects {
    public static final class Ref {public String resource,anchor;public float duration;}
    public static final class Profile {public Ref blue_orb,red_orb,blue_burst,red_burst;}
    public static final class Atlas {public int columns,rows,width,height,cell_width,cell_height,gutter;}
    public static final class Profiles {public Map<String,Profile> profiles;public Map<String,Atlas> texture_atlas;}
    private record Asset(DimensionParticles.Data data,Ref ref){}
    private record Key(UUID owner,UUID target){}
    private record BurstKey(UUID owner,UUID target,long tick){}
    private record Visible(HyperCoreNetwork.Orb effect,long born){}
    private record Frame(DimensionParticles.Batch batch,Vec3 center){}
    private record SampleKey(String resource,int seconds){}
    private static final Map<Key,Visible> ORBS=new LinkedHashMap<>();
    private static final Map<BurstKey,Visible> BURSTS=new LinkedHashMap<>();
    private static final Map<SampleKey,List<DimensionParticles.Batch>> SAMPLES=new HashMap<>();
    private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
    private static float[] DRAW_VERTICES=new float[0];
    private static long sampledTick=Long.MIN_VALUE;
    private static float sampledPartial;
    private static final Vector3f sampledRight=new Vector3f(),sampledUp=new Vector3f();
    private static volatile Map<String,Profile> profiles=Map.of();
    private static volatile Map<String,Atlas> atlases=Map.of();
    private static volatile Map<String,Asset> assets=Map.of();
    private static ClientLevel level;
    private static boolean fallbackAttempted;
    private static final class Drawing {
        static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));
        static final BufferBuilder PASS=new BufferBuilder(65536);
    }
    private HyperCoreEffects(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerCoreResources(RegisterClientReloadListenersEvent e){e.registerReloadListener((ResourceManagerReloadListener)HyperCoreEffects::read);}
    }
    public static void read(ResourceManager resources){
        profiles=Map.of();atlases=Map.of();assets=Map.of();fallbackAttempted=false;SAMPLES.clear();TEXTURES.clear();sampledTick=Long.MIN_VALUE;
        try(var stream=resources.open(new ResourceLocation("ssjj_hyper","effects/core/profiles.json"));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            var source=new Gson().fromJson(reader,Profiles.class);
            if(source==null||source.profiles==null||source.profiles.size()!=8)throw new IllegalArgumentException("Missing eight core appearances");
            if(source.texture_atlas!=null)for(var a:source.texture_atlas.values())
                if(a.columns<1||a.rows<1||a.columns*a.rows>256||a.gutter<2||a.cell_width<2||a.cell_height<2||a.width!=a.columns*(a.cell_width+2*a.gutter)||a.height!=a.rows*(a.cell_height+2*a.gutter)||a.width>8192||a.height>8192)throw new IllegalArgumentException("Invalid core texture atlas");
            var loaded=new LinkedHashMap<String,Asset>();
            for(var entry:source.profiles.entrySet())for(var kind:List.of("blue_orb","red_orb","blue_burst","red_burst")){
                var p=entry.getValue();var ref=switch(kind){case "blue_orb"->p.blue_orb;case "red_orb"->p.red_orb;case "blue_burst"->p.blue_burst;default->p.red_burst;};
                String expected=kind.startsWith("blue")?"ssjj_hyper:effects/core/"+entry.getKey()+"_"+kind+".json":"ssjj_hyper:effects/energy/"+entry.getKey()+"_"+(kind.endsWith("orb")?"mark":"detonation")+".json";
                if(ref==null||!expected.equals(ref.resource)||!Float.isFinite(ref.duration)||ref.duration<=0||ref.duration>60||!(kind.endsWith("orb")?"targetTop":"ground").equals(ref.anchor))throw new IllegalArgumentException("Invalid authored core reference "+entry.getKey()+"/"+kind);
                try(var input=resources.open(new ResourceLocation(ref.resource));var text=new InputStreamReader(input,StandardCharsets.UTF_8)){
                    var data=new Gson().fromJson(text,DimensionParticles.Data.class);DimensionParticles.validate(data,"ssjj_hyper:"+entry.getKey(),"world","ssjj_hyper:textures/effects/");
                    for(var emitter:data.emitters){var texture=new ResourceLocation(emitter.texture);if(resources.getResource(texture).isEmpty())throw new IllegalArgumentException("Missing source core texture "+emitter.texture);TEXTURES.put(emitter.texture,texture);}
                    loaded.put(ref.resource,new Asset(data,ref));
                }
            }
            profiles=Map.copyOf(source.profiles);assets=Map.copyOf(loaded);atlases=source.texture_atlas==null?Map.of():Map.copyOf(source.texture_atlas);
            LogUtils.getLogger().info("HYPER_CORE loaded {} original sphere/detonation assets for {} appearances",assets.size(),profiles.size());
        }catch(Exception error){LogUtils.getLogger().error("Cannot load authored core effects",error);}
    }
    private static boolean ready(){if(profiles.isEmpty()&&!fallbackAttempted){read(Minecraft.getInstance().getResourceManager());fallbackAttempted=true;}return !profiles.isEmpty();}
    private static void world(){var next=Minecraft.getInstance().level;if(next!=level){level=next;ORBS.clear();BURSTS.clear();SAMPLES.clear();sampledTick=Long.MIN_VALUE;}}
    private static <K,V> void cap(Map<K,V> group,int count){while(group.size()>count){var it=group.keySet().iterator();it.next();it.remove();}}
    public static void accept(HyperCoreNetwork.Orb m){
        world();if(m==null||!m.valid()||level==null||!m.dimension().equals(level.dimension().location()))return;
        if(m.action()==3){ORBS.keySet().removeIf(k->k.owner().equals(m.owner()));return;}
        var key=new Key(m.owner(),m.target());if(m.action()==1){ORBS.remove(key);return;}
        long now=level.getGameTime();if(m.tick()>now+10||now-m.tick()>100)return;
        if(m.action()==2){ORBS.remove(key);BURSTS.put(new BurstKey(m.owner(),m.target(),m.tick()),new Visible(m,m.tick()));cap(BURSTS,128);return;}
        var old=ORBS.get(key);long born=old!=null&&old.effect.profile().equals(m.profile())&&old.effect.red()==m.red()?old.born:m.tick();
        ORBS.put(key,new Visible(m,born));cap(ORBS,512);
    }
    private static Asset asset(HyperCoreNetwork.Orb m){var p=profiles.get(m.profile());if(p==null)return null;Ref ref=m.action()==2?(m.red()?p.red_burst:p.blue_burst):(m.red()?p.red_orb:p.blue_orb);return assets.get(ref.resource);}
    private static Vec3 center(Visible v,float partial){
        var m=v.effect;if(m.action()==2)return m.center();var target=level.getEntity(m.entityId());
        if(target==null||!target.getUUID().equals(m.target())||!target.isAlive()||target.isRemoved())return null;
        return new Vec3(Mth.lerp(partial,target.xOld,target.getX()),Mth.lerp(partial,target.yOld,target.getY())+target.getBbHeight(),Mth.lerp(partial,target.zOld,target.getZ()));
    }
    private static float[] atlasUV(DimensionParticles.Batch b){
        float[] st=b.uvST().clone();var a=atlases.get(b.texture());if(a==null||a.columns*a.rows==1)return st;
        double sx=(double)(a.cell_width+2*a.gutter)/a.width,sy=(double)(a.cell_height+2*a.gutter)/a.height;
        double col=(st[2]-(a.gutter+.5)/a.width)/sx,row=(-st[3]-(a.gutter+.5)/a.height)/sy;
        int frame=Math.max(0,Math.min(a.columns*a.rows-1,(int)Math.floor(row*a.columns+col+1e-4)));
        st[2]=(float)(((frame%a.columns)*(a.cell_width+2*a.gutter)+a.gutter+.5)/a.width);st[3]=(float)(-((frame/a.columns)*(a.cell_height+2*a.gutter)+a.gutter+.5)/a.height);return st;
    }
    private static List<DimensionParticles.Batch> samples(Asset a,float seconds,long now,float partial,Vector3f right,Vector3f up){
        if(sampledTick!=now||Float.floatToIntBits(sampledPartial)!=Float.floatToIntBits(partial)||!sampledRight.equals(right)||!sampledUp.equals(up)){
            SAMPLES.clear();sampledTick=now;sampledPartial=partial;sampledRight.set(right);sampledUp.set(up);
        }
        var key=new SampleKey(a.ref.resource,Float.floatToIntBits(seconds));var old=SAMPLES.get(key);if(old!=null)return old;
        var sampled=DimensionParticles.sample(a.data,seconds,right,up);var out=new ArrayList<DimensionParticles.Batch>(sampled.size());
        for(var batch:sampled)out.add(new DimensionParticles.Batch(batch.texture(),batch.render(),batch.mesh(),batch.color(),atlasUV(batch)));
        if(SAMPLES.size()<640)SAMPLES.put(key,out);return out;
    }
    private static List<Frame> frames(Visible v,long now,float partial,Vector3f right,Vector3f up){
        var a=asset(v.effect);if(a==null)return List.of();float seconds=(now-v.born+partial)/20;
        if(seconds<0||v.effect.action()==2&&seconds>=a.ref.duration)return List.of();var center=center(v,partial);if(center==null)return List.of();
        var batches=samples(a,seconds,now,partial,right,up);var out=new ArrayList<Frame>(batches.size());for(var b:batches)out.add(new Frame(b,center));return out;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;world();if(level==null)return;long now=level.getGameTime();
        ORBS.values().removeIf(v->now-v.effect.tick()>100);BURSTS.values().removeIf(v->{var a=asset(v.effect);return a!=null&&now-v.born>=Math.ceil(a.ref.duration*20)||now-v.born>1200;});
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;world();if(level==null||ORBS.isEmpty()&&BURSTS.isEmpty()||!ready())return;
        var camera=e.getCamera().getPosition();var rotation=e.getCamera().rotation();var right=rotation.transform(new Vector3f(1,0,0));var up=rotation.transform(new Vector3f(0,1,0));
        for(var group:List.of(ORBS.values(),BURSTS.values()))for(var v:group){var c=center(v,e.getPartialTick());if(c==null||c.distanceToSqr(camera)>160*160)continue;for(var frame:frames(v,level.getGameTime(),e.getPartialTick(),right,up))draw(frame,e.getPoseStack(),camera);}
        Drawing.BUFFERS.endBatch();
    }
    private static void draw(Frame frame,PoseStack pose,Vec3 camera){
        if(frame.center.distanceToSqr(camera)>160*160)return;var batch=frame.batch;pose.pushPose();pose.translate(frame.center.x-camera.x,frame.center.y-camera.y,frame.center.z-camera.z);
        boolean additive="additive".equals(batch.render());var mesh=batch.mesh();float[] c=batch.color(),st=batch.uvST();
        var out=Drawing.BUFFERS.getBuffer(DimensionEffectRenderType.of(TEXTURES.computeIfAbsent(batch.texture(),ResourceLocation::new),additive));
        float rgb=Math.max(1,Math.max(c[0],Math.max(c[1],c[2]))),energy=additive?Math.min(16,rgb*c[3]):Math.min(1,c[3]);
        float red=Math.min(1,c[0]/(additive?rgb:1)),green=Math.min(1,c[1]/(additive?rgb:1)),blue=Math.min(1,c[2]/(additive?rgb:1));
        var matrix=pose.last().pose();var normal=pose.last().normal().transform(new Vector3f(0,0,1));var vertex=new Vector4f();
        int length=mesh.positions.length*5;if(DRAW_VERTICES.length<length)DRAW_VERTICES=new float[length];
        for(int i=0;i<mesh.positions.length;i++){var p=mesh.positions[i];var uv=mesh.uv[i];matrix.transform(vertex.set(p[0],p[1],p[2],1));int base=i*5;
            DRAW_VERTICES[base]=vertex.x;DRAW_VERTICES[base+1]=vertex.y;DRAW_VERTICES[base+2]=vertex.z;DRAW_VERTICES[base+3]=uv[0]*st[0]+st[2];DRAW_VERTICES[base+4]=uv[1]*st[1]-st[3];}
        if(additive&&energy>1&&out instanceof BufferBuilder buffer){
            var scratch=Drawing.PASS;scratch.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);writePass(scratch,mesh,red,green,blue,1,normal);
            var encoded=scratch.end();try{
                var bytes=encoded.vertexBuffer();int full=(int)energy;for(int pass=0;pass<full;pass++){bytes.position(0);buffer.putBulkData(bytes);}
                float fraction=energy-full;if(fraction>0){byte alpha=(byte)(int)(fraction*255);for(int offset=15;offset<bytes.limit();offset+=36)bytes.put(offset,alpha);bytes.position(0);buffer.putBulkData(bytes);}
            }finally{encoded.release();}
        }else for(int pass=0;pass<(additive?(int)Math.ceil(energy):1);pass++)writePass(out,mesh,red,green,blue,additive?Math.min(1,energy-pass):energy,normal);
        pose.popPose();
    }
    private static void writePass(VertexConsumer out,DimensionParticles.Mesh mesh,float red,float green,float blue,float alpha,Vector3f normal){
        for(int[] face:mesh.triangles)for(int corner=0;corner<4;corner++){int base=face[Math.min(corner,2)]*5;
            out.vertex(DRAW_VERTICES[base],DRAW_VERTICES[base+1],DRAW_VERTICES[base+2],red,green,blue,alpha,DRAW_VERTICES[base+3],DRAW_VERTICES[base+4],0,15728880,normal.x,normal.y,normal.z);}
    }
}
