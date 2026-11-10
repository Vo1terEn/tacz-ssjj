package cn.judgfire;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.*;
import static java.lang.Math.*;

/** Independent non-original world FX candidate. No event subscribers or gameplay hooks.
 * World mount is actor feet or packet endpoint, never the observer's reticle. All custom
 * arithmetic is delegated to the shared body/world CustomParticleTransform implementation.
 */
public final class SkinWorldFlames {
  private SkinWorldFlames(){}
  public static final String LIMITS="Offline candidate: native PS seeded randomness reconstructed; custom engine policy is explicit inference. Native NoiseModule/ClampVelocityModule/particle TrailModule remain preserved raw and pending implementation; no video supplied for these three skins.";
  private static final Gson GSON=new Gson();
  private static Data current;
  private static boolean attempted;
  private static final List<LiveInstance> LIVE_INSTANCES=new ArrayList<>();
  private static Object liveLevel;
  private static Map<Layer,BirthState> runtimeBirths;
  private static final class LiveInstance {String id;Vec3 mount;double born,seen;long frame=-1;final Map<Layer,BirthState> births=new IdentityHashMap<>();}
  private static LiveInstance liveInstance(String id,Vec3 mount){
    Object level=Minecraft.getInstance().level;if(level!=liveLevel){LIVE_INSTANCES.clear();liveLevel=level;}
    double now=System.nanoTime()/1e9;LIVE_INSTANCES.removeIf(i->now-i.seen>1);LiveInstance found=null;double near=1;
    for(LiveInstance i:LIVE_INSTANCES)if(i.frame!=WorldFlameRenderType.frame()&&i.id.equals(id)&&i.mount.distanceToSqr(mount)<near){near=i.mount.distanceToSqr(mount);found=i;}
    if(found==null){found=new LiveInstance();found.id=id;found.born=now;LIVE_INSTANCES.add(found);}found.mount=mount;found.seen=now;found.frame=WorldFlameRenderType.frame();return found;
  }
  private static final class BirthState {double last=Double.NEGATIVE_INFINITY;long ordinal;final List<Birth> alive=new ArrayList<>();
    List<Birth> sample(Layer l,double time){
      if(time<last){last=Double.NEGATIVE_INFINITY;ordinal=0;alive.clear();}
      ArrayList<Birth> fresh=new ArrayList<>();double first=(l.prewarm&&l.loop?-l.duration:0)+l.delay;
      if(l.rate>0)for(double born=first+ordinal/l.rate;born<=time;born=first+ordinal/l.rate){
        if(!l.loop&&born>=l.delay+l.duration)break;long n=ordinal++,seed=NativeEmitterClock.mix(l.name.hashCode()+n*0x9e3779b97f4a7c15L);
        float life=range(l.lifeRange,NativeEmitterClock.unit(seed),l.life);fresh.add(new Birth(n,born,time-born,life,seed));
      }
      if(l.bursts!=null){int loops=l.loop&&l.duration>0?max(1,(int)floor((time-l.delay)/l.duration)+1):1;
        double tail=0;for(Burst b:l.bursts)tail=max(tail,b.time+max(0,b.cycles-1)*b.repeat);
        int start=l.loop&&l.duration>0&&Double.isFinite(last)?max(0,(int)floor((last-l.delay-tail)/l.duration)):0;
        for(int loop=start;loop<loops;loop++)for(int bi=0;bi<l.bursts.length;bi++){Burst b=l.bursts[bi];for(int cycle=0;cycle<max(1,b.cycles);cycle++){
          double born=l.delay+loop*l.duration+b.time+cycle*b.repeat;if(born<=last||born>time)continue;
          long seed=NativeEmitterClock.mix(l.name.hashCode()+bi*5813L+loop*11887L+cycle*941L);if(NativeEmitterClock.unit(seed)>b.probability)continue;
          int count=min(l.max,max(0,(int)round(range(b.countRange,NativeEmitterClock.unit(seed+1),1))));
          for(int n=0;n<count;n++){long key=NativeEmitterClock.mix(seed+n*89);float life=range(l.lifeRange,NativeEmitterClock.unit(key),l.life);fresh.add(new Birth(0x10000000L+bi*65536L+n,born,time-born,life,key));}
        }}
      }
      fresh.sort(Comparator.comparingDouble(Birth::seconds).thenComparingLong(Birth::index));
      for(Birth p:fresh){alive.removeIf(a->p.seconds-a.seconds>=a.life);if(alive.size()<l.max)alive.add(p);}
      alive.removeIf(p->time-p.seconds>=p.life||time<p.seconds);last=time;
      ArrayList<Birth> result=new ArrayList<>();for(Birth p:alive)result.add(new Birth(p.index,p.seconds,time-p.seconds,p.life,p.seed));return result;
    }
  }
  private static final Map<JudgmentFlame.CrownStrike,Vec3> CROWN_STARTS=new IdentityHashMap<>();
  private static final Map<JudgmentFlame.CrownStrike,Integer> CROWN_OWNERS=new IdentityHashMap<>();
  private static final String[] NAMES={"","qinglong","frozen","lightning"};
  private static final float[] WHITE={1,1,1,1};
  private static final float[][] QUAD={{-.5f,-.5f,0},{.5f,-.5f,0},{.5f,.5f,0},{-.5f,.5f,0}};
  private static final float[][] QUAD_UV={{0,0},{1,0},{1,1},{0,1}};
  private static final int[][] QUAD_FACES={{0,1,2,3}};
  static final class SamplerState {int wrapU,wrapV,filter;}
  static final class Material {
    String name,shaderName;String[] textures;SamplerState[] samplers;
    float[] mainST,maskST,noiseST,modelST,modelMaskST,scroll,tint,params,force;
    float heatTime;boolean additive,premultiply;int cull,zTest,zWrite;int[] sourceBlend;
    Map<String,Float> source_floats;JsonObject source_colors;
  }
  static final class Animation {double duration;boolean loop;float[][] eulerSamples,parent,initialInverse;float[] localPosition,localScale;}
  static final class Burst {float time,repeat,probability;float[] countRange;int cycles;}
  static final class Layer {
    String name;int material,max,renderMode,renderAlignment,billboardType,sourceIsWorldParticle,simulationSpace;
    int customConeAxis=2; // Preserve +Z unless this world layer has source bounds proving the +Y family.
    boolean enabled,customParticle,staticMesh,loop,prewarm,builtinQuad,vertexColorsUsedByShader;
    float life,rate,duration,delay,sortFudge,lengthScale,velocityScale,billboardShader;
    float[] lifeRange,speedRange,initialEuler,velocity,position;
    float[][] localToWorld,nativeLocalVertices,uv,vertexColors,startSizeAxesRange,startColorRange,colorsRangeFlat;
    float[][][] colorsRange;float[][] colors,sizeAxes,angularVelocity,custom;
    int[][] faces;int[] sheet;Burst[] bursts;Animation animation;
    float[][] sheetFrameOverTime,sheetStartFrame;float sheetCycles=1;
    JsonObject shape,customParticleRaw,sourceParticleRaw,sourceRenderer;
    transient CustomParticleSchedule.Spec spec;transient CustomParticleTransform.Compiled customModel;
    transient CustomParticleSchedule schedule;
    transient Matrix4f matrix;
    transient NativeParticleShape.Spec nativeShape;
    transient boolean[] angularMotion;
  }
  static final class Effect {String card,skin,role;Layer[] groups,emitters,trails;transient List<Layer> layers;}
  static final class Data {Material[] materials;Map<String,Effect> effects;}
  /** Snapshot vertices are mount-relative Minecraft Y-up meters; camera translation is absent. */
  public record Mesh(int material,float[][] positions,float[][] uv,float[][] colors,float[] tint,float[] custom,String sourceLayer){}
  private record Birth(long index,double seconds,double age,double life,long seed){float random(int channel){return NativeEmitterClock.unit(seed+0x9e3779b97f4a7c15L*(channel+1L));}}
  private record State(float[] center,float[] velocity,float[] size,float[] euler,float[] color,float[] uv,float[] custom){}
  private static final class Buffers {static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(262144));}

  static void resetWorld(){LIVE_INSTANCES.clear();runtimeBirths=null;liveLevel=null;CROWN_STARTS.clear();CROWN_OWNERS.clear();}
  public static void reload(ResourceManager resources){
    current=null;attempted=true;CROWN_STARTS.clear();CROWN_OWNERS.clear();LIVE_INSTANCES.clear();runtimeBirths=null;
    try(var in=resources.open(new ResourceLocation("judgfire","fx/skin_world_native.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
      current=parse(reader);
      com.mojang.logging.LogUtils.getLogger().info("Independent skin world FX loaded: {} materials / {} effects",current.materials.length,current.effects.size());
    }catch(Exception e){com.mojang.logging.LogUtils.getLogger().error("Cannot load independent skin world FX",e);}
  }
  private static void ensure(){if(!attempted)reload(Minecraft.getInstance().getResourceManager());}
  public static boolean handles(int skin){return skin>=1&&skin<=3;}
  /** Only the graphics for three explicit source skins are replaced. */
  public static boolean routeShot(RenderLevelStageEvent event,PoseStack pose,JudgmentFlame.Shot shot,double age){
    if(!handles(shot.skin()))return false;
    // Crown impact is owned by routeStrike, never an ordinary bullet/laser hit.
    return true; // Native weapon_mouth/line groups are in gun space; no invented ray.
  }
  public static boolean routeMark(RenderLevelStageEvent event,PoseStack pose,int skin,Entity actor){
    if(!handles(skin))return false;
    var level=Minecraft.getInstance().level;if(level!=null)mark(skin,pose,actor.getPosition(event.getPartialTick()),event.getCamera().rotation(),(level.getGameTime()+event.getPartialTick())/20.0);
    return true;
  }
  public static boolean routeCrown(RenderLevelStageEvent event,PoseStack pose,LivingEntity actor,double charge,double seconds){
    if(!JudgmentFlame.ours(actor.getMainHandItem()))return false;int skin=WeaponSkins.skin(actor.getMainHandItem());if(!handles(skin))return false;
    crown(skin,charge>=67?3:charge>=34?2:1,pose,actor.getPosition(event.getPartialTick()),event.getCamera().rotation(),seconds);return true;
  }
  public static boolean routeStrike(RenderLevelStageEvent event,PoseStack pose,JudgmentFlame.CrownStrike packet,double age){
    int skin=packet.skin();if(!handles(skin))return false;double duration=CrownHoming.duration(packet.start(),packet.end());
    if(age<duration)projectile(skin,pose,crownStart(packet),packet.end(),event.getCamera().rotation(),age,duration);
    else impact(skin,pose,packet.end(),event.getCamera().rotation(),age-duration);
    return true;
  }
  public static boolean routeBurst(int skin){return handles(skin);} // No E010 event in these skin source graphs.
  public static long shotRetention(long original,int skin){return handles(skin)?java.lang.Math.max(original,(long)(impactWindow(skin)*1e9)):original;}
  public static double strikeRetention(double original,int skin){return handles(skin)?java.lang.Math.max(original,impactWindow(skin)):original;}
  private static double impactWindow(int skin){
    ensure();if(current==null)return 7;
    Effect e=current.effects.get(NAMES[skin]+":crown_projectile_impact");if(e==null)return 0;
    double end=0;
    for(Layer l:e.layers){double life=l.lifeRange==null?l.life:max(l.lifeRange[0],l.lifeRange[1]);
      if(l.customParticle){var s=l.spec.customSchedule;double born=s==null?0:s.playTime;
        if(born<=0&&l.spec.sourceStartTimeSamplesRange!=null)for(float[] row:l.spec.sourceStartTimeSamplesRange)for(float x:row)born=max(born,x);
        end=max(end,born+life);
      }else {if(l.rate>0)end=max(end,l.delay+l.duration+life);if(l.bursts!=null)for(Burst b:l.bursts)end=max(end,l.delay+b.time+max(0,b.cycles-1)*b.repeat+life);}
    }return end+.01;
  }
  /** Packet has no owner id: a unique same-skin carrier may correct its native crown center. */
  public static int crownOwner(JudgmentFlame.CrownStrike packet){crownStart(packet);return CROWN_OWNERS.getOrDefault(packet,-1);}
  private static Vec3 crownStart(JudgmentFlame.CrownStrike packet){
    if(CROWN_STARTS.size()>=512){CROWN_STARTS.clear();CROWN_OWNERS.clear();}
    return CROWN_STARTS.computeIfAbsent(packet,p->{CROWN_OWNERS.put(p,-1);var mc=Minecraft.getInstance();if(mc.level==null)return p.start();
      net.minecraft.world.entity.player.Player best=null;double closest=Double.POSITIVE_INFINITY,second=Double.POSITIVE_INFINITY;
      for(var entity:mc.level.entitiesForRendering()){
        if(!(entity instanceof net.minecraft.world.entity.player.Player player)||!player.isAlive()||!JudgmentFlame.crown(player.getMainHandItem())||!JudgmentFlame.ours(player.getMainHandItem())||WeaponSkins.skin(player.getMainHandItem())!=p.skin())continue;
        double d=player.position().add(0,player.getBbHeight()+.63,0).distanceToSqr(p.start());if(d>.64)continue;
        if(d<closest){second=closest;closest=d;best=player;}else second=min(second,d);
      }
      if(best==null||sqrt(second)-sqrt(closest)<.2)return p.start();CROWN_OWNERS.put(p,best.getId());return p.start().add(0,2.8-(best.getBbHeight()+.63),0);
    });
  }
  /** Pure loading entry point for offline Java/JOML snapshots; does not initialize GL buffers. */
  static Data parse(Reader reader){
    JsonObject raw=JsonParser.parseReader(reader).getAsJsonObject();Data d=GSON.fromJson(raw,Data.class);
    if(d.materials==null||d.effects==null)throw new IllegalArgumentException("Skin world config");
    for(var entry:d.effects.entrySet()){
      var rawEffect=raw.getAsJsonObject("effects").getAsJsonObject(entry.getKey());Effect e=entry.getValue();
      for(String kind:List.of("groups","emitters","trails")){
        Layer[] layers=kind.equals("groups")?e.groups:kind.equals("emitters")?e.emitters:e.trails;
        JsonArray rawLayers=rawEffect.getAsJsonArray(kind);
        for(int j=0;j<layers.length;j++){
          Layer l=layers[j];l.matrix=matrix(l.localToWorld);l.nativeShape=shape(l.shape);
          l.angularMotion=new boolean[3];if(l.angularVelocity!=null)for(float[] row:l.angularVelocity)for(int k=0;k<3;k++)if(row[k*2]!=0||row[k*2+1]!=0)l.angularMotion[k]=true;
          if(l.material<0||l.material>=d.materials.length)throw new IllegalArgumentException("Material index");
          if(l.customParticle){l.spec=GSON.fromJson(rawLayers.get(j),CustomParticleSchedule.Spec.class);l.customModel=CustomParticleTransform.compile(l.customParticleRaw,l.customConeAxis);l.schedule=new CustomParticleSchedule(l.spec,l.name.hashCode());}
          if(l.nativeLocalVertices!=null){
            if(l.uv==null||l.uv.length!=l.nativeLocalVertices.length)throw new IllegalArgumentException("UV mismatch "+l.name);
            for(int[] f:l.faces)for(int i:f)if(i<0||i>=l.nativeLocalVertices.length)throw new IllegalArgumentException("Face "+l.name);
          }
        }
      }
      ArrayList<Layer> ordered=new ArrayList<>();Collections.addAll(ordered,e.groups);Collections.addAll(ordered,e.emitters);
      ordered.sort(Comparator.comparingInt((Layer l)->d.materials[l.material].params[0]==5?0:1).thenComparing(Comparator.comparingDouble((Layer l)->l.sortFudge).reversed()));e.layers=ordered;
    }return d;
  }
  public static void crown(int skin,int crownLevel,PoseStack pose,Vec3 actorFeet,Quaternionf camera,double age){draw(skin,"crown_level_"+max(1,min(3,crownLevel)),pose,actorFeet,camera,age,true,null);}
  public static void impact(int skin,PoseStack pose,Vec3 endpoint,Quaternionf camera,double age){draw(skin,"crown_projectile_impact",pose,endpoint,camera,age,false,null);}
  public static void mark(int skin,PoseStack pose,Vec3 actorFeet,Quaternionf camera,double age){draw(skin,skin==2?"skill_arctic_mark":"skill_red_lotus_mark",pose,actorFeet,camera,age,true,null);}
  /** No supplied three-skin event resolves to original E010. Do not invent a ground-wave. */
  public static void burst(int skin,PoseStack pose,Vec3 actorFeet,Quaternionf camera,double age){}
  public static void projectile(int skin,PoseStack pose,Vec3 start,Vec3 end,Quaternionf camera,double age,double duration){
    if(duration<=0||start.distanceToSqr(end)<1e-10)return;Flight f=new Flight(start,end,duration);draw(skin,"crown_projectile",pose,f.point(age),camera,age,false,f);
  }
  /** Additional exported source roles are exposed for coordinated gun-space integration only. */
  public static void drawRole(int skin,String role,PoseStack pose,Vec3 mount,Quaternionf camera,double age,boolean continuous){draw(skin,role,pose,mount,camera,age,continuous,null);}
  private static void draw(int skin,String role,PoseStack pose,Vec3 mount,Quaternionf camera,double age,boolean continuous,Flight flight){
    ensure();
    if(current==null||SkinWorldRenderType.shader==null||SkinWorldRenderType.hdrShader==null||skin<1||skin>3||age<0)return;
    String id=NAMES[skin]+":"+role;
    Vector3f right=new Vector3f(1,0,0).rotate(camera),up=new Vector3f(0,1,0).rotate(camera);
    WorldFlameRenderType.beginWorldPass();
    LiveInstance instance=continuous?liveInstance(id,mount):null;if(instance!=null)age=instance.seen-instance.born;
    List<Mesh> meshes;runtimeBirths=instance==null?null:instance.births;
    try{meshes=sample(current,id,age,continuous,right,up,flight);}finally{runtimeBirths=null;}
    pose.pushPose();pose.translate(mount.x,mount.y,mount.z);
    for(int index=0;index<meshes.size();){Mesh mesh=meshes.get(index);
      Material material=current.materials[mesh.material];
      // Preserve the old per-mesh face sorting for meshes containing multiple
      // quads. Adjacent single-quad particles can share one unsorted HDR batch.
      if(mesh.positions.length!=4){SkinWorldRenderType.prepare(material,age,mesh.tint,mesh.custom);RenderType type=SkinWorldRenderType.get(material);VertexConsumer out=Buffers.BUFFER.getBuffer(type);
        for(int i=0;i<mesh.positions.length;i++){float[] v=mesh.positions[i],c=mesh.colors[i],uv=mesh.uv[i];out.vertex(pose.last().pose(),v[0],v[1],v[2]).color(c[0],c[1],c[2],max(0,min(1,c[3]))).uv(uv[0],uv[1]).endVertex();}
        Buffers.BUFFER.endBatch(type);index++;continue;
      }
      int end=index+1;while(end<meshes.size()&&hdrCompatible(mesh,meshes.get(end)))end++;
      SkinWorldRenderType.prepareHdr(material,age,WHITE,mesh.custom);RenderType type=SkinWorldRenderType.getHdr(material);VertexConsumer out=Buffers.BUFFER.getBuffer(type);float[] rgba=new float[4];
      for(int mi=index;mi<end;mi++){Mesh particle=meshes.get(mi);for(int i=0;i<4;i++){float[] v=particle.positions[i],c=particle.colors[i],uv=particle.uv[i];
        // Only the original vertex color was byte encoded. ParticleTint always
        // was a float uniform, so retain its full HDR range when merging it.
        for(int k=0;k<4;k++)rgba[k]=((int)(max(0,min(1,c[k]))*255))/255f*particle.tint[k];
        FlameRenderType.vertexHdr(out,pose.last().pose(),v[0],v[1],v[2],rgba,uv[0],uv[1]);
      }}Buffers.BUFFER.endBatch(type);index=end;
    }pose.popPose();
  }
  static boolean hdrCompatible(Mesh a,Mesh b){return b.positions.length==4&&a.material==b.material&&Arrays.equals(a.custom,b.custom);}
  static final class Flight {
    final Vec3 start,end;final double duration;
    Flight(Vec3 start,Vec3 end,double duration){this.start=start;this.end=end;this.duration=duration;}
    Vec3 point(double age){return CrownHoming.point(start,end,max(0,min(1,age/duration)));}
    Quaternionf rotation(double age){Vec3 d=point(age+.001).subtract(point(max(0,age-.001))).normalize();return new Quaternionf().rotationTo(new Vector3f(0,0,1),new Vector3f((float)d.x,(float)d.y,(float)d.z));}
  }
  /** Shared by actual renderer and offline snapshot harness; no Minecraft instance/GL calls. */
  static List<Mesh> sample(Data data,String id,double time,boolean continuous,Vector3f right,Vector3f up,Flight flight){
    Effect effect=data.effects.get(id);if(effect==null)return List.of();ArrayList<Mesh> result=new ArrayList<>();
    for(Layer l:effect.layers){
      if(!l.enabled||!l.customParticle&&l.nativeLocalVertices==null&&l.renderMode==4)continue;
      Matrix4f world=l.animation==null?l.matrix:animation(l.animation,time).mul(l.matrix);
      float[] axisScale={world.getColumn(0,new Vector3f()).length(),world.getColumn(1,new Vector3f()).length(),world.getColumn(2,new Vector3f()).length()};
      if(l.customParticle){
        l.schedule.forEach(time,p->{
          var s=l.customModel.sample(p);State state=new State(s.localCenter(),s.localVelocity(),s.sizeXYZ(),s.eulerRadians(),s.rgba(),s.uvScaleOffset(),new float[4]);
          add(result,data,l,state,time,p.birthSeconds(),right,up,flight,world,axisScale);
        });
      }else for(Birth p:births(l,time,continuous))add(result,data,l,nativeState(l,p),time,p.seconds,right,up,flight,world,axisScale);
    }
    if(flight!=null)for(Layer trail:effect.trails)if(trail.enabled)addTrail(result,trail,time,flight,right,up);
    return result;
  }
  private static void add(List<Mesh> result,Data data,Layer l,State s,double time,double birth,Vector3f right,Vector3f up,Flight flight,Matrix4f world,float[] axisScale){
    if(s.color[3]<=0||s.size[0]==0||s.size[1]==0)return;
    float[][] vertices=l.nativeLocalVertices==null?QUAD:l.nativeLocalVertices,uv=l.nativeLocalVertices==null?QUAD_UV:l.uv;int[][] faces=l.faces==null?QUAD_FACES:l.faces;
    boolean billboard=l.customParticle?(l.billboardShader>0||l.billboardType!=4):l.nativeLocalVertices==null&&l.renderAlignment!=2;
    Vector3f center=world.transformPosition(new Vector3f(s.center[0],s.center[1],s.center[2]));center.z=-center.z;
    Vector3f velocity=world.transformDirection(new Vector3f(s.velocity[0],s.velocity[1],s.velocity[2]));velocity.z=-velocity.z;
    Quaternionf place=flight==null?null:flight.rotation((l.customParticle?l.sourceIsWorldParticle==1:l.simulationSpace==1)?birth:time);
    if(place!=null){center.rotate(place);velocity.rotate(place);}
    if(flight!=null&&(l.customParticle?l.sourceIsWorldParticle==1:l.simulationSpace==1)){
      Vec3 delta=flight.point(birth).subtract(flight.point(time));center.add((float)delta.x,(float)delta.y,(float)delta.z);
    }
    Vector3f rr=new Vector3f(right),uu=new Vector3f(up);
    if(!l.customParticle&&l.renderMode==2){rr.set(1,0,0);uu.set(0,0,-1);}
    else if(!l.customParticle&&l.renderMode==3){rr.y=0;if(rr.lengthSquared()>1e-10)rr.normalize();uu.set(0,1,0);}
    if((l.customParticle&&l.billboardType==1||!l.customParticle&&l.renderMode==1)&&velocity.lengthSquared()>1e-10){
      uu.set(velocity).normalize();Vector3f look=new Vector3f(right).cross(up);rr.set(uu).cross(look);if(rr.lengthSquared()<1e-10)rr.set(right);else rr.normalize();
    }
    Quaternionf rotation=new Quaternionf().rotateY(s.euler[1]).rotateX(s.euler[0]).rotateZ(s.euler[2]);
    float[][] points=new float[vertices.length][];
    for(int i=0;i<vertices.length;i++){
      Vector3f p=new Vector3f(vertices[i]).mul(s.size[0],s.size[1],s.size[2]).rotate(rotation);
      if(billboard){
        float height=p.y;if(!l.customParticle&&l.renderMode==1)height*=max(1,l.lengthScale);
        p=new Vector3f(center).fma(p.x*axisScale[0],rr).fma(height*axisScale[1],uu);
      }else{
        p.add(s.center[0],s.center[1],s.center[2]);world.transformPosition(p);p.z=-p.z;if(place!=null)p.rotate(place);
        if(flight!=null&&(l.customParticle?l.sourceIsWorldParticle==1:l.simulationSpace==1)){Vec3 delta=flight.point(birth).subtract(flight.point(time));p.add((float)delta.x,(float)delta.y,(float)delta.z);}
      }points[i]=new float[]{p.x,p.y,p.z};
    }
    ArrayList<float[]> vv=new ArrayList<>(),tt=new ArrayList<>(),cc=new ArrayList<>();
    for(int[] face:faces)for(int j=0;j<4;j++){
      int i=face[min(j,face.length-1)];vv.add(points[i]);tt.add(new float[]{uv[i][0]*s.uv[0]+s.uv[2],uv[i][1]*s.uv[1]+s.uv[3]});
      cc.add(!l.customParticle&&l.vertexColorsUsedByShader&&l.vertexColors!=null?l.vertexColors[i]:WHITE);
    }
    result.add(new Mesh(l.material,vv.toArray(float[][]::new),tt.toArray(float[][]::new),cc.toArray(float[][]::new),s.color,s.custom,l.name));
  }
  private static List<Birth> births(Layer l,double time,boolean continuous){
    if(runtimeBirths!=null&&!l.staticMesh){if(l.max<=0)return List.of();return runtimeBirths.computeIfAbsent(l,k->new BirthState()).sample(l,time);}
    if(l.staticMesh)return List.of(new Birth(0,0,0,1,l.name.hashCode()));
    double first=(l.prewarm&&l.loop?-l.duration:0)+l.delay,lifeMax=l.lifeRange==null?l.life:max(l.lifeRange[0],l.lifeRange[1]);
    if(time<first||l.max<=0)return List.of();ArrayList<Birth> candidates=new ArrayList<>();
    // Replay from the authored start so rejected births remain rejected until a live slot dies.
    if(l.rate>0){
      long count=(long)floor((time-first)*l.rate)+1;for(long n=0;n<count;n++){
        double born=first+n/l.rate;if(!l.loop&&born>=l.delay+l.duration)break;long seed=NativeEmitterClock.mix(l.name.hashCode()+n*0x9e3779b97f4a7c15L);float life=range(l.lifeRange,NativeEmitterClock.unit(seed),l.life);
        candidates.add(new Birth(n,born,time-born,life,seed));
      }
    }
    if(l.bursts!=null){
      int loops=l.loop&&l.duration>0?max(1,(int)floor((time-l.delay)/l.duration)+1):1;
      for(int loop=0;loop<loops;loop++)for(int bi=0;bi<l.bursts.length;bi++){
        Burst b=l.bursts[bi];for(int cycle=0;cycle<max(1,b.cycles);cycle++){
          double born=l.delay+loop*l.duration+b.time+cycle*b.repeat;if(born>time)continue;
          long seed=NativeEmitterClock.mix(l.name.hashCode()+bi*5813L+loop*11887L+cycle*941L);if(NativeEmitterClock.unit(seed)>b.probability)continue;
          int count=max(0,(int)round(range(b.countRange,NativeEmitterClock.unit(seed+1),1)));
          for(int n=0;n<count;n++){long key=NativeEmitterClock.mix(seed+n*89);float life=range(l.lifeRange,NativeEmitterClock.unit(key),l.life);candidates.add(new Birth(0x10000000L+bi*65536L+n,born,time-born,life,key));}
        }
      }
    }
    candidates.sort(Comparator.comparingDouble(Birth::seconds).thenComparingLong(Birth::index));ArrayList<Birth> alive=new ArrayList<>();
    for(Birth p:candidates){alive.removeIf(a->p.seconds-a.seconds>=a.life);if(alive.size()<l.max)alive.add(p);}
    alive.removeIf(p->time-p.seconds>=p.life||time<p.seconds);return alive;
  }
  private static State nativeState(Layer l,Birth p){
    double t=p.age/p.life;float[] color=new float[4],size=new float[3],euler=new float[3];
    for(int i=0;i<4;i++)color[i]=sampleRange4(l.colorsRange,t,i,p.random(20))*(l.startColorRange==null?1:lerp(l.startColorRange[0][i],l.startColorRange[1][i],p.random(21)));
    for(int k=0;k<3;k++){
      size[k]=range(l.startSizeAxesRange==null?null:l.startSizeAxesRange[k],p.random(30),1)*sampleRange(l.sizeAxes,t,k,p.random(31),1);
      euler[k]=l.initialEuler==null?0:lerp(l.initialEuler[k*2],l.initialEuler[k*2+1],p.random(40));
      if(l.angularMotion[k])euler[k]+=integral(l.angularVelocity,t,k,p.random(41),p.life);
    }
    var spawn=NativeParticleShape.sample(l.nativeShape,p.random(61),p.random(62),p.random(64),p.random(65));float[] center=spawn.position(),direction=spawn.direction();float speed=range(l.speedRange,p.random(50),0);float[] velocity={direction[0]*speed,direction[1]*speed,direction[2]*speed};
    for(int i=0;i<3;i++){velocity[i]+=l.velocity==null?0:l.velocity[i];center[i]+=velocity[i]*p.age;}
    int cols=l.sheet==null?1:max(1,l.sheet[0]),rows=l.sheet==null?1:max(1,l.sheet[1]),frame=NativeTextureSheet.frame(l.sheet,l.sheetFrameOverTime,l.sheetStartFrame,l.sheetCycles,t,p.random(42));
    float[] custom=new float[4];for(int i=0;i<4;i++)custom[i]=sample(l.custom,t,i,0);
    return new State(center,velocity,size,euler,color,new float[]{1f/cols,1f/rows,(frame%cols)/(float)cols,(rows-1-frame/cols)/(float)rows},custom);
  }
  private static void addTrail(List<Mesh> out,Layer l,double age,Flight f,Vector3f right,Vector3f up){
    JsonObject raw=l.sourceRenderer,params=obj(raw,"m_Parameters");float life=number(raw,"m_Time",0),width=number(params,"widthMultiplier",0)*.01f;if(life<=0||width<=0)return;
    double oldest=max(0,age-life);int count=max(1,(int)ceil((age-oldest)*120));ArrayList<float[]> v=new ArrayList<>(),uv=new ArrayList<>(),colors=new ArrayList<>();
    Vector3f eye=new Vector3f(right).cross(up),offset=flip(new Vector3f(l.position));
    for(int i=0;i<count;i++){
      double ta=oldest+(age-oldest)*i/count,tb=oldest+(age-oldest)*(i+1)/count;Vec3 aa=f.point(ta).subtract(f.point(age)),bb=f.point(tb).subtract(f.point(age));Vector3f a=new Vector3f((float)aa.x,(float)aa.y,(float)aa.z).add(new Vector3f(offset).rotate(f.rotation(ta))),b=new Vector3f((float)bb.x,(float)bb.y,(float)bb.z).add(new Vector3f(offset).rotate(f.rotation(tb)));
      Vector3f side=new Vector3f(b).sub(a).cross(eye);if(side.lengthSquared()<1e-12)continue;side.normalize();float pa=(float)((age-ta)/life),pb=(float)((age-tb)/life);Vector3f sa=new Vector3f(side).mul(width*.5f),sb=new Vector3f(side).mul(width*.5f);
      for(int j=0;j<4;j++){Vector3f p=j==0?new Vector3f(a).sub(sa):j==1?new Vector3f(b).sub(sb):j==2?new Vector3f(b).add(sb):new Vector3f(a).add(sa);v.add(new float[]{p.x,p.y,p.z});uv.add(new float[]{j==0||j==3?1-pa:1-pb,j<2?0:1});colors.add(new float[]{1,1,1,j==0||j==3?1-pa:1-pb});}
    }if(!v.isEmpty())out.add(new Mesh(l.material,v.toArray(float[][]::new),uv.toArray(float[][]::new),colors.toArray(float[][]::new),WHITE,new float[4],l.name));
  }
  private static Matrix4f animation(Animation a,double time){
    double phase=a.duration>0?(a.loop?time%a.duration:min(time,a.duration))/a.duration:0;
    float x=sample(a.eulerSamples,phase,0,0),y=sample(a.eulerSamples,phase,1,0),z=sample(a.eulerSamples,phase,2,0);
    return matrix(a.parent).translate(a.localPosition[0],a.localPosition[1],a.localPosition[2]).rotateY((float)toRadians(y)).rotateX((float)toRadians(x)).rotateZ((float)toRadians(z)).scale(a.localScale[0],a.localScale[1],a.localScale[2]).mul(matrix(a.initialInverse));
  }
  private static Matrix4f matrix(float[][] a){float[] flat=new float[16];for(int r=0;r<4;r++)for(int c=0;c<4;c++)flat[c*4+r]=a[r][c];return new Matrix4f().set(flat);}
  private static Vector3f flip(Vector3f p){p.z=-p.z;return p;}
  private static float sample(float[][] a,double t,int c,float fallback){return CustomParticleSchedule.sample(a,t,c,fallback);}
  private static float sampleRange(float[][] a,double t,int c,float random,float fallback){return CustomParticleSchedule.sampleRange(a,t,c,random,fallback);}
  private static float sampleRange4(float[][][] a,double t,int c,float r){if(a==null)return 1;double p=max(0,min(1,t))*(a.length-1);int i=(int)p,j=min(i+1,a.length-1);return lerp(lerp(a[i][0][c],a[i][1][c],r),lerp(a[j][0][c],a[j][1][c],r),(float)(p-i));}
  private static float integral(float[][] a,double t,int axis,float r,double life){if(a==null)return 0;double n=t*(a.length-1),sum=0;for(int i=0;i<(int)ceil(n);i++){double u=i/(double)(a.length-1),v=min(t,(i+1)/(double)(a.length-1));sum+=(v-u)*(sampleRange(a,u,axis,r,0)+sampleRange(a,v,axis,r,0))*.5*life;}return(float)sum;}
  private static float range(float[] p,float r,float fallback){return p==null?fallback:lerp(p[0],p[1],r);}
  private static float lerp(float a,float b,float t){return a+(b-a)*t;}
  private static NativeParticleShape.Spec shape(JsonObject raw){
    var s=new NativeParticleShape.Spec();s.enabled=raw!=null&&bool(raw,"enabled");s.type=(int)number(raw,"type",0);s.radius=number(obj(raw,"radius"),"value",0);s.angle=number(raw,"angle",0);s.length=number(raw,"length",0);s.radiusThickness=number(raw,"radiusThickness",1);s.arc=number(obj(raw,"arc"),"value",360);s.box=new float[3];s.position=new float[3];s.rotation=new float[3];
    for(int k=0;k<3;k++){String key="xyz".substring(k,k+1);s.box[k]=number(obj(raw,"m_Scale"),key,1);s.position[k]=number(obj(raw,"m_Position"),key,0);s.rotation[k]=number(obj(raw,"m_Rotation"),key,0);}return s;
  }
  private static JsonObject obj(JsonObject a,String name){return a!=null&&a.has(name)&&a.get(name).isJsonObject()?a.getAsJsonObject(name):new JsonObject();}
  private static float number(JsonObject a,String name,float fallback){return a!=null&&a.has(name)&&a.get(name).isJsonPrimitive()?a.get(name).getAsFloat():fallback;}
  private static boolean bool(JsonObject a,String name){return a!=null&&a.has(name)&&a.get(name).getAsBoolean();}
}
