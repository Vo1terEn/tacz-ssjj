package cn.judgfire;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original world nodes, native meshes and particle curves. Actor mount translation is supplied once. */
final class NativeWorldFlames {
  static final class Material {
    String name;String[] textures;float[] mainST,maskST,noiseST,scroll,tint,params,force;
    float heatTime,flowOffset;boolean additive;Map<String,String> extraTextures;Map<String,float[]> extraST;
    Map<String,Float> source_floats;JsonObject source_colors;
    transient RenderType cachedType;
  }
  static class Particle {
    String name;int material,max,renderMode,renderAlignment,simulationSpace;float life,rate,duration,delay,initialRotation,sortFudge,alpha=1;
    boolean loop,staticMesh,nativeParticle,prewarm;float[] center,startColor,initialEuler,lifeRange,initialSizeBaked;float[][] colors,sizes,sizeAxes,custom,angularVelocity,initialSizeRange,startColorRange;float[][][] colorRanges;Burst[] bursts;int[] sheet;
    float[][] sheetFrameOverTime,sheetStartFrame;float sheetCycles=1;
    transient NativeEmitterClock clock;
  }
  static final class Burst {float time,count,repeat,probability=1;float[] countRange;int cycles;}
  static final class Group extends Particle {float[][] vertices,uv,particleBasis,vertexColors;int[][] faces;transient Matrix3f nativeBasis,nativeBasisInverse;}
  static final class Shape extends NativeParticleShape.Spec {}
  static final class Emitter extends Particle {
    float[] position,axisX,axisY,axisZ,size,velocity,speedRange;float[][] velocityRanges;float speed,rotation,lengthScale,velocityScale;Shape shape;
  }
  static final class Trail {String name;int material;float[] position;JsonObject source_renderer;}
  static final class Effect {Group[] groups;Emitter[] emitters;Trail[] trails;transient List<Particle> layers;}
  static final class Animation {float[][] initial,parent;float duration;float[] euler;}
  static final class Data {Material[] materials;Map<String,Effect> effects;Animation animation;}
  private static Data data;
  private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new BufferBuilder(262144));
  private static final List<Track> TRACKS=new ArrayList<>();
  private static Object level;
  private static final List<Instance> INSTANCES=new ArrayList<>();
  private static final class Instance {String id;Vec3 mount;double born,seen;long frame=-1;final Map<Particle,NativeEmitterClock> clocks=new IdentityHashMap<>();}
  private static Instance instance(String id,Vec3 mount){
    double now=System.nanoTime()/1e9;INSTANCES.removeIf(i->now-i.seen>1);Instance found=null;double nearest=1;
    for(Instance i:INSTANCES)if(i.frame!=WorldFlameRenderType.frame()&&i.id.equals(id)&&i.mount.distanceToSqr(mount)<nearest){nearest=i.mount.distanceToSqr(mount);found=i;}
    if(found==null){found=new Instance();found.id=id;found.born=now;INSTANCES.add(found);}
    found.mount=mount;found.seen=now;found.frame=WorldFlameRenderType.frame();return found;
  }
  private static final float[] WHITE={1,1,1,1},IDENTITY={1,1,0,0};
  private static final class Knot {double time;Vector3f[] points;Knot(double t,Vector3f[] p){time=t;points=p;}}
  private static final class Track {Vec3 last;double seen,born;long frame=-1;Deque<Knot> knots=new ArrayDeque<>();}
  private record ParticleSample(double phase,double age,double life,double birth,long seed){float random(int channel){return NativeEmitterClock.unit(seed+0x9e3779b97f4a7c15L*(channel+1L));}}
  static void resetWorld(){TRACKS.clear();INSTANCES.clear();level=null;WorldFlameRenderType.invalidateScene();}
  static void reload(ResourceManager resources){
    data=null;TRACKS.clear();INSTANCES.clear();WorldFlameRenderType.invalidateScene();
    try(var input=resources.open(new ResourceLocation("judgfire","fx/world_native.json"));var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
      Data d=new Gson().fromJson(reader,Data.class);
      if(d==null||d.materials==null||d.effects==null)throw new IllegalArgumentException("Native world config");
      for(var effect:d.effects.values())for(var g:effect.groups){
        if(g.vertexColors!=null&&g.vertexColors.length!=g.vertices.length)throw new IllegalArgumentException("World vertex-color mismatch "+g.name);
        if(g.vertices.length!=g.uv.length)throw new IllegalArgumentException("World UV mismatch "+g.name);
        for(int[] face:g.faces)for(int i:face)if(i<0||i>=g.vertices.length)throw new IllegalArgumentException("World face index");
        g.nativeBasis=basis(g.particleBasis);g.nativeBasisInverse=g.nativeBasis==null?null:new Matrix3f(g.nativeBasis).invert();
      }
      data=d;
      com.mojang.logging.LogUtils.getLogger().info("Native world flame nodes loaded: {} materials / {} effects",d.materials.length,d.effects.size());
    }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("Cannot load native world flame nodes",ex);}
  }
  static void draw(String id,PoseStack pose,Vec3 mount,Quaternionf camera,double time,boolean continuous){
    draw(id,pose,mount,camera,time,continuous,null);
  }
  private static final class Flight {
    final Vec3 start,end;final double duration;final Quaternionf rotation;
    Flight(Vec3 start,Vec3 end,double age,double duration){
      this.start=start;this.end=end;this.duration=duration;
      rotation=rotationAt(age);
    }
    Quaternionf rotationAt(double age){
      Vec3 delta=point(age+.001).subtract(point(Math.max(0,age-.001))).normalize();
      // Source wrapper E029 has Unity local Euler (0,-90,0); reflect Z once into Minecraft.
      return new Quaternionf().rotationTo(new Vector3f(0,0,1),new Vector3f((float)delta.x,(float)delta.y,(float)delta.z)).rotateY((float)(Math.PI/2));
    }
    Vec3 point(double age){return CrownHoming.point(start,end,Math.max(0,Math.min(1,age/duration)));}
  }
  static void projectile(PoseStack pose,Vec3 start,Vec3 end,Quaternionf camera,double age,double duration){
    if(start.distanceToSqr(end)<1e-10)return;
    Flight flight=new Flight(start,end,age,duration);
    draw("E016",pose,flight.point(age),camera,age,false,flight);
  }
  static void crown(String id,PoseStack pose,Vec3 mount,Quaternionf camera,double age){
    draw(id,pose,mount,camera,age,true,null,new Quaternionf().rotationY((float)(Math.PI/2)));
  }
  static void mark(PoseStack pose,Vec3 mount,Quaternionf camera,double age){
    draw("E017",pose,mount,camera,age,true,null,new Quaternionf().rotationY((float)(Math.PI/2)));
  }
  static void impact(PoseStack pose,Vec3 mount,Quaternionf camera,double age){
    draw("E014",pose,mount,camera,age,false,null,new Quaternionf().rotationY((float)(Math.PI/2)));
  }
  private static void draw(String id,PoseStack pose,Vec3 mount,Quaternionf camera,double time,boolean continuous,Flight flight){
    draw(id,pose,mount,camera,time,continuous,flight,flight==null?null:flight.rotation);
  }
  private static void draw(String id,PoseStack pose,Vec3 mount,Quaternionf camera,double time,boolean continuous,Flight flight,Quaternionf placement){
    if(data==null||WorldFlameRenderType.shader==null)return;
    Effect effect=data.effects.get(id);if(effect==null)return;
    if(Minecraft.getInstance().level!=level){level=Minecraft.getInstance().level;TRACKS.clear();INSTANCES.clear();}
    Instance instance=continuous?instance(id,mount):null;
    if(instance!=null)time=instance.seen-instance.born;
    WorldFlameRenderType.beginWorldPass();
    Track track=id.equals("E017")?track(mount):null;
    Matrix4f animated=track==null?null:animation(track.seen-track.born);
    Matrix4f reflected=animated==null?null:new Matrix4f().scaling(1,1,-1).mul(animated).scale(1,1,-1);
    pose.pushPose();pose.translate(mount.x,mount.y,mount.z);
    Matrix4f matrix=pose.last().pose();
    Vector3f right=new Vector3f(1,0,0).rotate(camera),up=new Vector3f(0,1,0).rotate(camera);
    if(effect.layers==null){effect.layers=new ArrayList<>();Collections.addAll(effect.layers,effect.groups);Collections.addAll(effect.layers,effect.emitters);
      // Refraction samples the shared pre-FX scene. Draw it before this effect's
      // luminous layers so it cannot erase fire/core layers from that snapshot.
      effect.layers.sort(Comparator.comparingInt((Particle p)->data.materials[p.material].params[0]==5?0:1)
          .thenComparing(Comparator.comparingDouble((Particle p)->p.sortFudge).reversed()));}
    for(Particle layer:effect.layers){
     if(layer instanceof Emitter missingMesh&&missingMesh.renderMode==4)continue;
     // The core is one luminous volume; ten identical coplanar native glow cards obscure it.
     // Sample the authored steady midpoint once, independent of rate phase/owner/frame count.
     boolean singleCore=continuous&&(id.equals("E011")||id.equals("E012")||id.equals("E013"))&&layer.name.contains("_glow_B_");
     List<ParticleSample> layerSamples=singleCore?crownCore(layer):particles(layer,time,continuous,instance);if(layerSamples.isEmpty())continue;
     Material layerMaterial=data.materials[layer.material];boolean batch=layerMaterial.params[0]!=4||constant(layer.custom);
     float[] layerScale=batch?colorScale(layer,layerSamples):WHITE;
     RenderType layerType=type(layerMaterial);
     if(batch){prepare(layerMaterial,time,layer.custom==null?null:sample4(layer.custom,layerSamples.get(0).phase),1);particleTint(layerScale);}
     if(layer instanceof Group g){
      Matrix4f meshMatrix=new Matrix4f(matrix);if(placement!=null)meshMatrix.rotate(placement);if(reflected!=null)meshMatrix.mul(reflected);
      int groupOrdinal=0;
      for(ParticleSample sample:layerSamples){
        double phase=sample.phase;Material mat=data.materials[g.material];float[] rgba=color(g,phase,sample.random(2));
        float birthRandom=g.nativeParticle?sample.random(3):random(g.name.hashCode()+groupOrdinal*149);groupOrdinal++;float sx=size(g,phase,birthRandom,0)*initialSize(g,sample,0,true),sy=size(g,phase,birthRandom,1)*initialSize(g,sample,1,true),sz=size(g,phase,birthRandom,2)*initialSize(g,sample,2,true);
        if(sx<=0||sy<=0||rgba[3]<=0)continue;
        if(!batch){prepare(mat,time,g.custom==null?null:sample4(g.custom,phase),1);particleTint(rgba);}
        RenderType type=type(mat);VertexConsumer out=BUFFER.getBuffer(type);
        Matrix3f nativeBasis=g.nativeBasis,particle=new Matrix3f().rotateY(initial(g,birthRandom,1)+angular(g,phase,birthRandom,1,sample.life)).rotateX(initial(g,birthRandom,0)+angular(g,phase,birthRandom,0,sample.life)).rotateZ(initial(g,birthRandom,2)+angular(g,phase,birthRandom,2,sample.life)).scale(sx,sy,sz);
        if(nativeBasis!=null)particle=new Matrix3f(nativeBasis).mul(particle).mul(g.nativeBasisInverse);
        int cols=g.sheet==null?1:g.sheet[0],rows=g.sheet==null?1:g.sheet[1],frame=NativeTextureSheet.frame(g.sheet,g.sheetFrameOverTime,g.sheetStartFrame,g.sheetCycles,phase,sample.random(42));
        float[] uniformColor=batch&&g.vertexColors==null?packedColor(WHITE,rgba,layerScale):null;
        for(int[] face:g.faces)for(int j=0;j<4;j++){
          int ix=face[j<face.length?j:face.length-1];float[] v=g.vertices[ix],uv=g.uv[ix];
          Vector3f point=new Vector3f(v[0]-g.center[0],v[1]-g.center[1],v[2]-g.center[2]);
          particle.transform(point);point.add(g.center[0],g.center[1],g.center[2]);point.z=-point.z;
          vertex(out,meshMatrix,point,(uv[0]+frame%cols)/cols,(uv[1]+rows-1-frame/cols)/rows,batch?(uniformColor==null?packedColor(g.vertexColors[ix],rgba,layerScale):uniformColor):g.vertexColors==null?WHITE:g.vertexColors[ix]);
        }
        if(!batch)BUFFER.endBatch(type);
      }
     } else {
      Emitter e=(Emitter)layer;int ei=Arrays.asList(effect.emitters).indexOf(e);
      // A renderer in mesh mode with no assigned native mesh renders nothing in Unity.
      if(e.renderMode==4)continue;
      List<ParticleSample> samples=layerSamples;int ordinal=0;
      for(ParticleSample sample:samples){
        double phase=sample.phase;float[] rgba=color(e,phase,sample.random(2));if(rgba[3]<=0){ordinal++;continue;}
        float birthRandom=e.nativeParticle?sample.random(3):random(ei*9749+ordinal*149+1);float age=(float)sample.age,sx=size(e,phase,birthRandom,0),sy=size(e,phase,birthRandom,1);
        if(sx<=0||sy<=0){ordinal++;continue;}
        Vector3f axisX=reflect(e.axisX),axisY=reflect(e.axisY),axisZ=reflect(e.axisZ);
        var spawn=NativeParticleShape.sample(e.shape,sample.random(4),sample.random(5),sample.random(6),sample.random(7));float[] local=spawn.position(),direction=spawn.direction();
        float speed=e.speedRange==null?e.speed:e.speedRange[0]+(e.speedRange[1]-e.speedRange[0])*sample.random(8);
        Vector3f center=reflect(e.position).fma(local[0],axisX).fma(local[1],axisY).fma(local[2],axisZ),velocity=new Vector3f().fma(direction[0]*speed,axisX).fma(direction[1]*speed,axisY).fma(direction[2]*speed,axisZ);
        center.fma(age,velocity);
        if(e.velocityRanges!=null){for(int k=0;k<3;k++){Vector3f axis=k==0?axisX:k==1?axisY:axisZ;center.fma(velocityIntegral(e,phase,k,sample.random(9+k),sample.life),axis);velocity.fma(rangeSample(e.velocityRanges,phase,k,sample.random(9+k)),axis);}}
        else if(e.velocity!=null){Vector3f extra=new Vector3f().fma(e.velocity[0],axisX).fma(e.velocity[1],axisY).fma(e.velocity[2],axisZ);center.fma(age,extra);velocity.add(extra);}
        if(reflected!=null){reflected.transformPosition(center);reflected.transformDirection(velocity);}
        if(flight!=null){
          Quaternionf rotation=e.simulationSpace==1?flight.rotationAt(time-age):flight.rotation;
          center.rotate(rotation);velocity.rotate(rotation);
          if(e.simulationSpace==1){Vec3 born=flight.point(time-age).subtract(mount);center.add((float)born.x,(float)born.y,(float)born.z);}
        } else if(placement!=null){center.rotate(placement);velocity.rotate(placement);}

        float width=new Vector3f(axisX).length()*(e.nativeParticle?initialSize(e,sample,0,false):e.size[0])*sx,height=new Vector3f(axisY).length()*(e.nativeParticle?initialSize(e,sample,1,false):e.size[1])*sy;
        Vector3f rr=new Vector3f(right).mul(width*.5f),uu=new Vector3f(up).mul(height*.5f);
        if(e.renderMode==2){rr.set(width*.5f,0,0);uu.set(0,0,-height*.5f);}
        else if(e.renderMode==3){rr.y=0;if(rr.lengthSquared()>1e-8)rr.normalize().mul(width*.5f);uu.set(0,height*.5f,0);}
        else if(e.renderMode==0&&e.renderAlignment==2){
          rr.set(axisX).normalize().mul(width*.5f);uu.set(axisY).normalize().mul(height*.5f);
          if(placement!=null){rr.rotate(placement);uu.rotate(placement);}
        }
        if(e.renderMode==1&&velocity.lengthSquared()>1e-8){
          uu.set(velocity).normalize();Vector3f eye=new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector()).negate();
          rr.set(uu).cross(eye).normalize().mul(width*.5f);
          uu.mul(Math.max(height,Math.max(1,e.lengthScale)*height+e.velocityScale*velocity.length())*.5f);
        }
        float angleZ=initial(e,birthRandom,2)+angular(e,phase,birthRandom,2,sample.life);float cs=(float)Math.cos(angleZ),sn=(float)Math.sin(angleZ);Vector3f r0=new Vector3f(rr),u0=new Vector3f(uu);
        rr.set(r0).mul(cs).fma(sn,u0);uu.set(u0).mul(cs).fma(-sn,r0);
        Material mat=data.materials[e.material];if(!batch){prepare(mat,time,e.custom==null?null:sample4(e.custom,phase),1);particleTint(rgba);}
        RenderType type=type(mat);VertexConsumer out=BUFFER.getBuffer(type);
        int cols=e.sheet==null?1:e.sheet[0],rows=e.sheet==null?1:e.sheet[1];int f=NativeTextureSheet.frame(e.sheet,e.sheetFrameOverTime,e.sheetStartFrame,e.sheetCycles,phase,sample.random(42));
        float u=(f%cols)/(float)cols,v=1-(f/cols+1f)/rows;
        quad(out,matrix,center,rr,uu,u,u+1f/cols,v,v+1f/rows,batch?packedColor(WHITE,rgba,layerScale):WHITE);
        if(!batch)BUFFER.endBatch(type);ordinal++;
      }
    }
    if(batch)BUFFER.endBatch(layerType);
    }
    pose.popPose();
    if(effect.trails.length>0){
      if(flight==null)trails(effect,pose,mount,time,track,reflected,placement);
      else projectileTrails(effect,pose,time,flight);
    }
  }
  private static List<Double> phases(Particle p,double t,boolean continuous){
    if(p.staticMesh)return List.of(0.0);
    List<Double> phases=new ArrayList<>();double life=Math.max(.001,p.life);
    if(continuous&&p.loop){
      int count=Math.min(p.max,Math.max(1,(int)Math.ceil(life*p.rate)));
      if(p.rate>0)for(int n=0;n<count;n++)phases.add(fract(t/life+n/(p.rate*life)));
      return phases;
    }
    if(t<p.delay)return phases;t-=p.delay;
    if(p.bursts!=null)for(Burst burst:p.bursts)for(int cycle=0;cycle<Math.max(1,burst.cycles);cycle++){
      double age=t-burst.time-cycle*burst.repeat;
      if(age>=0&&age<life)for(int n=0;n<Math.min(p.max,(int)burst.count);n++)phases.add(age/life);
    }
    if(p.rate>0)for(int n=0;n<Math.min(p.max,(int)Math.ceil(p.rate*Math.min(t,Math.max(0,p.duration))));n++){
      double age=t-n/p.rate;if(age>=0&&age<life)phases.add(age/life);
    }
    return phases;
  }
  private static List<ParticleSample> particles(Particle p,double time,boolean continuous,Instance instance){
    ArrayList<ParticleSample> out=new ArrayList<>();
    if(!p.nativeParticle){int i=0;for(double phase:phases(p,time,continuous))out.add(new ParticleSample(phase,phase*p.life,p.life,time-phase*p.life,NativeEmitterClock.mix(p.name.hashCode()+i++*149L)));return out;}
    if(p.staticMesh){out.add(new ParticleSample(0,0,1,0,p.name.hashCode()));return out;}
    NativeEmitterClock clock=instance==null?p.clock:instance.clocks.get(p);
    if(clock==null){NativeEmitterClock.Spec spec=new NativeEmitterClock.Spec();spec.max=p.max;spec.life=p.life;spec.lifeRange=p.lifeRange;spec.rate=p.rate;spec.duration=p.duration;spec.delay=p.delay;spec.loop=p.loop;spec.prewarm=p.prewarm;clock=new NativeEmitterClock(spec,p.name.hashCode());if(instance==null)p.clock=clock;else instance.clocks.put(p,clock);}
    for(var birth:clock.sample(Math.max(0,time))){double age=time-birth.seconds();out.add(new ParticleSample(age/birth.life(),age,birth.life(),birth.seconds(),birth.seed()));}
    if(p.bursts!=null){
      int loops=p.loop&&p.duration>0?Math.max(1,(int)Math.floor((time-p.delay)/p.duration)+1):1;
      double lifeMax=p.lifeRange==null?p.life:Math.max(p.lifeRange[0],p.lifeRange[1]),lastBurst=0;
      for(Burst burst:p.bursts)lastBurst=Math.max(lastBurst,burst.time+Math.max(0,burst.cycles-1)*burst.repeat);
      int firstLoop=p.loop&&p.duration>0?Math.max(0,(int)Math.floor((time-p.delay-lifeMax-lastBurst)/p.duration)):0;
      for(int loop=firstLoop;loop<loops;loop++)for(int bi=0;bi<p.bursts.length;bi++){
        Burst b=p.bursts[bi];for(int cycle=0;cycle<Math.max(1,b.cycles);cycle++){
          double born=p.delay+loop*p.duration+b.time+cycle*b.repeat;if(born>time)continue;
          long seed=NativeEmitterClock.mix(p.name.hashCode()+bi*5813L+loop*11887L+cycle*941L);if(NativeEmitterClock.unit(seed)>b.probability)continue;
          int count=Math.max(0,Math.round(b.countRange==null?b.count:b.countRange[0]+(b.countRange[1]-b.countRange[0])*NativeEmitterClock.unit(seed+1)));
          for(int n=0;n<Math.min(p.max,count);n++){
            long key=NativeEmitterClock.mix(seed+n*89);float life=p.lifeRange==null?p.life:p.lifeRange[0]+(p.lifeRange[1]-p.lifeRange[0])*NativeEmitterClock.unit(key);double age=time-born;
            if(age>=0&&age<life)out.add(new ParticleSample(age/life,age,life,born,key));
          }
        }
      }
    }
    out.sort(Comparator.comparingDouble(ParticleSample::birth));if(out.size()>p.max)out.subList(p.max,out.size()).clear();return out;
  }
  private static List<ParticleSample> crownCore(Particle p){double life=p.lifeRange==null?p.life:(p.lifeRange[0]+p.lifeRange[1])*.5;return List.of(new ParticleSample(.5,life*.5,life,0,NativeEmitterClock.mix(p.name.hashCode())));}
  private static boolean constant(float[][] values){if(values==null||values.length==0)return true;for(float[] row:values)if(!Arrays.equals(row,values[0]))return false;return true;}
  private static float[] colorScale(Particle p,List<ParticleSample> samples){float[] scale=WHITE.clone();for(var sample:samples){float[] c=color(p,sample.phase,sample.random(2));for(int k=0;k<4;k++)scale[k]=Math.max(scale[k],c[k]);}return scale;}
  private static float[] packedColor(float[] nativeColor,float[] particle,float[] scale){float[] c=new float[4];for(int k=0;k<4;k++)c[k]=Math.max(0,Math.min(1,nativeColor[k]*particle[k]/scale[k]));return c;}
  private static float initialSize(Particle p,ParticleSample s,int axis,boolean ratio){
    if(!p.nativeParticle||p.initialSizeRange==null)return 1;
    float[] range=p.initialSizeRange[axis];float value=range[0]+(range[1]-range[0])*s.random(3);
    return ratio&&p.initialSizeBaked!=null&&p.initialSizeBaked[axis]!=0?value/p.initialSizeBaked[axis]:value;
  }
  private static float[] color(Particle p,double phase,float random){
    float[] c=p.colors==null?WHITE.clone():sample4(p.colors,phase);
    if(p.colorRanges!=null){double position=Math.max(0,Math.min(1,phase))*(p.colorRanges.length-1);int i=(int)position,j=Math.min(i+1,p.colorRanges.length-1);for(int k=0;k<4;k++){float a=p.colorRanges[i][0][k]+(p.colorRanges[i][1][k]-p.colorRanges[i][0][k])*random,b=p.colorRanges[j][0][k]+(p.colorRanges[j][1][k]-p.colorRanges[j][0][k])*random;c[k]=(float)(a+(b-a)*(position-i));}}
    if(p.startColorRange!=null){for(int k=0;k<4;k++)c[k]*=p.startColorRange[0][k]+(p.startColorRange[1][k]-p.startColorRange[0][k])*random;}
    else if(p.startColor!=null)for(int k=0;k<4;k++)c[k]*=p.startColor[k];return c;
  }
  private static float rangeSample(float[][] table,double phase,int axis,float r){float low=sample(table,phase,axis*2,0),high=sample(table,phase,axis*2+1,low);return low+(high-low)*r;}
  private static float velocityIntegral(Emitter e,double phase,int axis,float random,double life){
    double steps=phase*(e.velocityRanges.length-1),integral=0;
    for(int i=0;i<(int)Math.ceil(steps);i++){double a=i/(double)(e.velocityRanges.length-1),b=Math.min(phase,(i+1)/(double)(e.velocityRanges.length-1));integral+=(b-a)*(rangeSample(e.velocityRanges,a,axis,random)+rangeSample(e.velocityRanges,b,axis,random))*.5*life;}
    return(float)integral;
  }
  private static RenderType type(Material m){
    if(m.cachedType!=null)return m.cachedType;
    String[] tex=Arrays.copyOf(m.textures,4);tex[3]=m.extraTextures!=null&&m.extraTextures.containsKey("_ModelTex")?m.extraTextures.get("_ModelTex"):m.textures[1];
    return m.cachedType=WorldFlameRenderType.get(tex,m.additive,(int)number(m,"_Cull",0));
  }
  private static void prepare(Material m,double time,float[] custom,float alpha){
    var s=WorldFlameRenderType.shader;particleTint(WHITE);if(m.params[0]==5)WorldFlameRenderType.copyScene();
    if(m.additive)s.setBlend(770,1,1,1);else s.setBlend(770,771,1,771);
    s.safeGetUniform("LuminousCoverage").set("beam_spiritlink_combo_orangedots 1".equals(m.name)?1f:0f);
    s.safeGetUniform("AdditiveEnergy").set(m.additive?1f:0f);s.safeGetUniform("EffectTime").set((float)time);s.safeGetUniform("MainST").set(m.mainST);s.safeGetUniform("MaskST").set(m.maskST);s.safeGetUniform("NoiseST").set(m.noiseST);
    float[] scroll=m.scroll.clone();if(m.params[0]==2){scroll[0]=number(m,"_MoveX",scroll[0]);scroll[1]=number(m,"_MoveY",scroll[1]);}
    s.safeGetUniform("Scroll").set(scroll);s.safeGetUniform("Params").set(m.params);s.safeGetUniform("Force").set(m.force==null?new float[]{0,0}:m.force);
    s.safeGetUniform("HeatTime").set(m.heatTime);s.safeGetUniform("FlowOffset").set(m.flowOffset);s.safeGetUniform("Custom").set(custom==null?new float[]{0,0,0,0}:custom);
    s.safeGetUniform("Tint").set(m.tint[0],m.tint[1],m.tint[2],m.tint[3]*alpha);s.safeGetUniform("Dis").set(number(m,"_Dis",1));
    s.safeGetUniform("ModelST").set(m.extraST==null?IDENTITY:m.extraST.getOrDefault("_ModelTex",IDENTITY));s.safeGetUniform("ModelMaskST").set(m.extraST==null?IDENTITY:m.extraST.getOrDefault("_Model_mask",IDENTITY));
    s.safeGetUniform("ModelPan").set(number(m,"_ModelTex_X",0),number(m,"_ModelTex_Y",0),number(m,"_Modelmask_Y",0),number(m,"_Modelmask_X",0));
    var model=m.source_colors==null?null:m.source_colors.getAsJsonObject("_Model_Color");
    s.safeGetUniform("ModelColor").set(model==null?1:model.get("r").getAsFloat(),model==null?1:model.get("g").getAsFloat(),model==null?1:model.get("b").getAsFloat(),1f);
    s.safeGetUniform("Refraction").set(number(m,"_noise",0),number(m,"_refraction_power",0),number(m,"_opacity",0),0f);
  }
  private static void particleTint(float[] rgba){WorldFlameRenderType.shader.safeGetUniform("ParticleTint").set(rgba);}
  private static float number(Material m,String key,float fallback){return m.source_floats==null?fallback:m.source_floats.getOrDefault(key,fallback);}
  private static Track track(Vec3 mount){
    Object current=Minecraft.getInstance().level;if(current!=level){TRACKS.clear();level=current;}
    double now=System.nanoTime()/1e9;TRACKS.removeIf(t->now-t.seen>1);
    Track track=null;double distance=.25;
    for(Track t:TRACKS){double d=t.last.distanceToSqr(mount);if(t.frame!=WorldFlameRenderType.frame()&&d<distance){distance=d;track=t;}}
    if(track==null){track=new Track();track.born=now;TRACKS.add(track);}track.last=mount;track.seen=now;track.frame=WorldFlameRenderType.frame();return track;
  }
  private static Matrix4f animation(double age){
    if(data.animation==null)return null;Animation a=data.animation;
    Matrix4f initial=matrix(a.initial),parent=matrix(a.parent);
    Matrix4f animated=parent.translate(0,1,0).rotateX((float)Math.toRadians(a.euler[0])).rotateZ((float)Math.toRadians(a.euler[2]*(age%a.duration)));
    return animated.mul(initial.invert());
  }
  private static Matrix4f matrix(float[][] a){float[] flat=new float[16];for(int row=0;row<4;row++)for(int col=0;col<4;col++)flat[col*4+row]=a[row][col];return new Matrix4f().set(flat);}
  private static void trails(Effect effect,PoseStack pose,Vec3 mount,double shaderTime,Track track,Matrix4f reflected,Quaternionf placement){
    if(track==null)return;double now=track.seen;
    Vector3f[] points=new Vector3f[effect.trails.length];
    for(int i=0;i<points.length;i++){points[i]=reflect(effect.trails[i].position);if(reflected!=null)reflected.transformPosition(points[i]);if(placement!=null)points[i].rotate(placement);points[i].add((float)mount.x,(float)mount.y,(float)mount.z);}
    if(track.knots.isEmpty()||now-track.knots.getLast().time>.015)track.knots.addLast(new Knot(now,points));
    while(!track.knots.isEmpty()&&now-track.knots.getFirst().time>1)track.knots.removeFirst();
    renderTrails(effect,pose,shaderTime,track);
  }
  private static void projectileTrails(Effect effect,PoseStack pose,double age,Flight flight){
    Track track=new Track();track.seen=age;
    double longest=0;for(Trail trail:effect.trails)longest=Math.max(longest,trail.source_renderer.get("m_Time").getAsDouble());
    double oldest=Math.max(0,age-longest);
    int count=Math.max(1,(int)Math.ceil((age-oldest)*120));
    for(int n=0;n<=count;n++){
      double t=oldest+(age-oldest)*n/count;Vec3 root=flight.point(t);Vector3f[] points=new Vector3f[effect.trails.length];
      for(int i=0;i<points.length;i++)points[i]=reflect(effect.trails[i].position).rotate(flight.rotation).add((float)root.x,(float)root.y,(float)root.z);
      track.knots.addLast(new Knot(t,points));
    }
    renderTrails(effect,pose,age,track);
  }
  private static void renderTrails(Effect effect,PoseStack pose,double shaderTime,Track track){
    if(track.knots.size()<2)return;double now=track.seen;
    for(int ti=0;ti<effect.trails.length;ti++){
      Trail trail=effect.trails[ti];JsonObject raw=trail.source_renderer,params=raw.getAsJsonObject("m_Parameters");
      double life=raw.get("m_Time").getAsDouble();float width=params.get("widthMultiplier").getAsFloat()*.01f;Material mat=data.materials[trail.material];prepare(mat,shaderTime,null,1);
      RenderType type=type(mat);VertexConsumer out=BUFFER.getBuffer(type);Knot previous=null;
      for(Knot knot:track.knots){
        if(now-knot.time>life)continue;
        if(previous!=null){
          Vector3f a=previous.points[ti],b=knot.points[ti];if(a.distanceSquared(b)>1e-8){
            float pa=(float)((now-previous.time)/life),pb=(float)((now-knot.time)/life);
            Vector3f tangent=new Vector3f(b).sub(a),eye=new Vector3f((float)Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().x,(float)Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y,(float)Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().z).sub(a);
            Vector3f side=tangent.cross(eye).normalize();if(!side.isFinite())continue;
            Vector3f sa=new Vector3f(side).mul(width*curve(params.getAsJsonObject("widthCurve"),pa)*.5f),sb=new Vector3f(side).mul(width*curve(params.getAsJsonObject("widthCurve"),pb)*.5f);
            float[] ca=gradient(params.getAsJsonObject("colorGradient"),pa),cb=gradient(params.getAsJsonObject("colorGradient"),pb);
            vertex(out,pose.last().pose(),new Vector3f(a).sub(sa),1-pa,0,ca);vertex(out,pose.last().pose(),new Vector3f(b).sub(sb),1-pb,0,cb);
            vertex(out,pose.last().pose(),new Vector3f(b).add(sb),1-pb,1,cb);vertex(out,pose.last().pose(),new Vector3f(a).add(sa),1-pa,1,ca);
          }
        }previous=knot;
      }BUFFER.endBatch(type);
    }
  }
  private static float curve(JsonObject c,float t){
    var keys=c.getAsJsonArray("m_Curve");if(keys.size()==0)return 1;
    JsonObject a=keys.get(0).getAsJsonObject();if(t<=a.get("time").getAsFloat())return a.get("value").getAsFloat();
    for(int i=1;i<keys.size();i++){JsonObject b=keys.get(i).getAsJsonObject();float ta=a.get("time").getAsFloat(),tb=b.get("time").getAsFloat();
      if(t<=tb){float x=(t-ta)/(tb-ta),x2=x*x,x3=x2*x;return (2*x3-3*x2+1)*a.get("value").getAsFloat()+(x3-2*x2+x)*(tb-ta)*a.get("outSlope").getAsFloat()+(-2*x3+3*x2)*b.get("value").getAsFloat()+(x3-x2)*(tb-ta)*b.get("inSlope").getAsFloat();}a=b;
    }return a.get("value").getAsFloat();
  }
  private static float[] gradient(JsonObject g,float t){
    float[] result=new float[4];String[] channels={"r","g","b","a"};
    for(int c=0;c<4;c++){int count=g.get(c==3?"m_NumAlphaKeys":"m_NumColorKeys").getAsInt();String prefix=c==3?"atime":"ctime";int j=0;
      while(j+1<count&&g.get(prefix+(j+1)).getAsFloat()/65535f<t)j++;int k=Math.min(count-1,j+1);float a=g.get(prefix+j).getAsFloat()/65535f,b=g.get(prefix+k).getAsFloat()/65535f;
      float f=b>a?Math.max(0,Math.min(1,(t-a)/(b-a))):0;result[c]=g.getAsJsonObject("key"+j).get(channels[c]).getAsFloat()*(1-f)+g.getAsJsonObject("key"+k).get(channels[c]).getAsFloat()*f;
    }return result;
  }
  private static void quad(VertexConsumer out,Matrix4f m,Vector3f p,Vector3f r,Vector3f u,float u0,float u1,float v0,float v1,float[] rgba){
    vertex(out,m,new Vector3f(p).sub(r).sub(u),u0,v0,rgba);vertex(out,m,new Vector3f(p).add(r).sub(u),u1,v0,rgba);
    vertex(out,m,new Vector3f(p).add(r).add(u),u1,v1,rgba);vertex(out,m,new Vector3f(p).sub(r).add(u),u0,v1,rgba);
  }
  private static void vertex(VertexConsumer out,Matrix4f m,Vector3f p,float u,float v,float[] c){out.vertex(m,p.x,p.y,p.z).color(c[0],c[1],c[2],Math.max(0,Math.min(1,c[3]))).uv(u,v).endVertex();}
  private static Vector3f reflect(float[] p){return new Vector3f(p[0],p[1],-p[2]);}
  private static double fract(double v){return v-Math.floor(v);}
  private static float random(int seed){return (float)fract(Math.sin(seed*93.3)*41413.1);}
  private static Matrix3f basis(float[][] b){
    return b==null?null:new Matrix3f().set(new float[]{b[0][0],b[1][0],b[2][0],b[0][1],b[1][1],b[2][1],b[0][2],b[1][2],b[2][2]});
  }
  private static float initial(Particle p,float birthRandom,int axis){if(p.initialEuler==null)return axis==2?p.initialRotation:0;float low=p.initialEuler[axis*2],high=p.initialEuler[axis*2+1];return low+(high-low)*birthRandom;}
  private static float angular(Particle p,double phase,float random,int axis,double actualLife){
    if(p.angularVelocity==null)return 0;double steps=phase*(p.angularVelocity.length-1),integral=0;
    for(int i=0;i<(int)Math.ceil(steps);i++){
      double a=i/(double)(p.angularVelocity.length-1),b=Math.min(phase,(i+1)/(double)(p.angularVelocity.length-1));
      float al=sample(p.angularVelocity,a,axis*2,0),ah=sample(p.angularVelocity,a,axis*2+1,al),bl=sample(p.angularVelocity,b,axis*2,0),bh=sample(p.angularVelocity,b,axis*2+1,bl);
      integral+=(b-a)*((al+(ah-al)*random)+(bl+(bh-bl)*random))*.5*actualLife;
    }return (float)integral;
  }
  // Exported two-column lifetime sizes are min/max curves, not independent X/Y scales.
  private static float size(Particle p,double phase,float birthRandom,int axis){
    float[][] values=p.sizeAxes==null?p.sizes:p.sizeAxes;int component=p.sizeAxes==null?0:axis*2;
    float low=sample(values,phase,component,1),high=sample(values,phase,component+1,low);
    return low+(high-low)*birthRandom;
  }
  private static float sample(float[][] v,double t,int c,float fallback){if(v==null||v.length==0)return fallback;double p=Math.max(0,Math.min(v.length-1,t*(v.length-1)));int i=(int)p,j=Math.min(i+1,v.length-1);return (float)(v[i][c]*(1-(p-i))+v[j][c]*(p-i));}
  private static float[] sample4(float[][] v,double t){return new float[]{sample(v,t,0,1),sample(v,t,1,1),sample(v,t,2,1),sample(v,t,3,1)};}
}
