package cn.judgfire;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/** Original red/gold skin VFX. Geometry, mount matrices and curves come from one native cache. */
public final class DefaultFlameEffects {
  private static final float[] WHITE={1,1,1,1};
  private static final float[] ZERO2={0,0};
  static final class Material {
    String name; String[] textures;
    float[] mainST,maskST,noiseST,scroll,tint,params,force;
    float[] wrapMain,wrapMask,wrapNoise;int[] filters;
    float heatTime,flowOffset;
    boolean additive;
  }
  static final class Group extends NativeEmitterClock.Spec {
    String name; float[][] vertices,uv,uv1,vertexColors; int[][] faces;
    int material,bone; float alpha; float[] center; float[][] colors,sizes,custom; BodyEffectBinding binding;
    boolean hitOnly,bodyLoop;float[][] nativeLocalVertices,localToModel,angularVelocity;
    float[] sizeXYZ,sizeXYZMin,initialEuler;com.google.gson.JsonObject sourceShape;
  }
  static final class Emitter extends NativeEmitterClock.Spec {
    String name; float[] position,axisX,axisY,axisZ,size,startColor,velocity;
    int material,bone,renderMode; float speed,rotation,lengthScale; float[][] colors,sizes;
    int[] sheet; boolean mouthOnly,hitOnly; float radius;float[] sizeMin,sizeRange,speedRange,rotationRange;
    float[][] startColorRange,velocityRanges;Boolean localSpace;
    Shape shape;
  }
  static final class Shape extends NativeParticleShape.Spec {float sphericalDirectionAmount,randomDirectionAmount;}
  static final class Definition { Material[] materials; Group[] groups; Emitter[] emitters; }
  private static Definition data;
  private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(262144));
  private static final Map<Integer,Long> SKILLS=new HashMap<>();
  private static final Map<Integer,Long> FIRED=new HashMap<>();
  private static final Map<Integer,ArrayDeque<Long>> FIRE_HISTORY=new HashMap<>();
  private static final Map<Integer,ArrayDeque<Long>> HIT_HISTORY=new HashMap<>();
  private static final Map<JudgmentFlame.CrownStrike,Boolean> CROWN_HITS=new IdentityHashMap<>();
  private static final class LineTrace {
    final long born;final int skin; Vec3 end,forward; boolean confirmed;
    LineTrace(long born,int skin,Vec3 end,Vec3 forward,boolean confirmed){this.born=born;this.skin=skin;this.end=end;this.forward=forward;this.confirmed=confirmed;}
  }
  private record MouthParticle(double age,LineTrace line) {}
  private record HitParticle(double age,long born,int slot) {}
  private static final class OwnerClock {
    final long born;long seen;final Map<Integer,NativeEmitterClock> emitters=new HashMap<>();
    OwnerClock(long now){born=seen=now;}
  }
  private record WorldKey(int owner,int emitter,long birth) {}
  private static final class WorldPin {final Vec3 start,velocity;long seen;WorldPin(Vec3 start,Vec3 velocity,long seen){this.start=start;this.velocity=velocity;this.seen=seen;}}
  private static final Map<Integer,OwnerClock> OWNER_CLOCKS=new HashMap<>();
  private static final Map<WorldKey,WorldPin> WORLD_PINS=new HashMap<>();
  private static final Map<Integer,ArrayDeque<LineTrace>> LINES=new HashMap<>();
  private static final Map<Matrix4f,Integer> BODY_OWNERS=new IdentityHashMap<>();
  private static Object level;
  private static final ResourceLocation PIXEL=new ResourceLocation("judgfire","flame_crown_scope");
  public static boolean isOriginal(ItemStack stack) {
    return JudgmentFlame.ours(stack) && WeaponSkins.skin(stack)==0
      && stack.getItem() instanceof IGun gun && !PIXEL.equals(gun.getAttachmentId(stack,AttachmentType.SCOPE));
  }
  public static boolean isOriginalShooter(int id) {
    var mc=Minecraft.getInstance();
    return mc.level!=null && mc.level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity living && isOriginal(living.getMainHandItem());
  }
  private static void clean() {
    var next=Minecraft.getInstance().level;
    if(next!=level) { SKILLS.clear(); FIRED.clear(); FIRE_HISTORY.clear(); HIT_HISTORY.clear(); CROWN_HITS.clear(); LINES.clear(); BODY_OWNERS.clear(); OWNER_CLOCKS.clear();WORLD_PINS.clear();EndpointProjection.clear();SkinMouthEffects.clear();SkinEndpointLines.clear();NativeSkinAbsorb.clear();DefaultWorldFlames.resetWorld();level=next; }
    long now=System.nanoTime();
    SKILLS.values().removeIf(t->now-t>2_000_000_000L);
    FIRED.values().removeIf(t->now-t>1_200_000_000L);
    FIRE_HISTORY.values().forEach(q->q.removeIf(t->now-t>1_200_000_000L));
    FIRE_HISTORY.values().removeIf(ArrayDeque::isEmpty);
    HIT_HISTORY.values().forEach(q->q.removeIf(t->now-t>4_000_000_000L));
    HIT_HISTORY.values().removeIf(ArrayDeque::isEmpty);
    LINES.values().forEach(q->q.removeIf(t->now-t.born>1_200_000_000L));
    LINES.values().removeIf(ArrayDeque::isEmpty);
    OWNER_CLOCKS.values().removeIf(c->now-c.seen>30_000_000_000L);
    WORLD_PINS.values().removeIf(c->now-c.seen>4_000_000_000L);
  }
  public static void reload(ResourceManager resources) {
    data=null; SKILLS.clear(); FIRED.clear(); FIRE_HISTORY.clear(); HIT_HISTORY.clear(); CROWN_HITS.clear(); LINES.clear(); BODY_OWNERS.clear();OWNER_CLOCKS.clear();WORLD_PINS.clear();EndpointProjection.clear();
    try(var input=resources.open(new ResourceLocation("judgfire","fx/default_native.json"));
        var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
      Definition next=new Gson().fromJson(reader,Definition.class);
      if(next.materials==null||next.groups==null||next.emitters==null)throw new IllegalArgumentException("Missing native fire definitions");
      for(var g:next.groups) {
        if(g.vertices.length!=g.uv.length)throw new IllegalArgumentException("UV/vertex mismatch "+g.name);
        if(g.binding!=null) OriginalMotion.validate(g.binding,g.vertices.length);
      }
      data=next;DefaultWorldFlames.reload(resources);
      LogUtils.getLogger().info("Original native fire loaded: {} mesh layers / {} emitters",next.groups.length,next.emitters.length);
    }catch(Exception e){LogUtils.getLogger().error("Cannot load original native fire",e);}
    NativeSkinBodyBridge.reload(resources);
    SkinMouthEffects.reload(resources);
    SkinEndpointLines.reload(resources);
    NativeSkinAbsorb.reload(resources);
  }
  public static void fired(GunFireEvent event) {
    clean();
    int skin=WeaponSkins.skin(event.getGunItemStack());
    if(event.getLogicalSide().isClient()&&skin>=0&&skin<=3&&isNativeSkin(event.getGunItemStack(),skin)) {
      long now=System.nanoTime();int owner=event.getShooter().getId();
      if(skin!=0)SkinMouthEffects.fired(owner,skin,now);
      var recent=LINES.get(owner);if(recent!=null&&!recent.isEmpty()&&recent.peekLast().skin==skin&&recent.peekLast().confirmed&&now-recent.peekLast().born<50_000_000L)return;
      if(skin==0){FIRED.put(owner,now);var history=FIRE_HISTORY.computeIfAbsent(owner,id->new ArrayDeque<>());history.addLast(now);while(history.size()>24)history.removeFirst();}
      var shooter=event.getShooter();Vec3 forward=shooter.getViewVector(1).normalize();
      Vec3 eye=shooter.getEyePosition(),end=shooter.pick(128,1,false).getLocation();
      // Cosmetic prediction until the existing server trace arrives. Never applies damage.
      double distance=eye.distanceToSqr(end);
      var area=shooter.getBoundingBox().expandTowards(forward.scale(128)).inflate(1);
      for(var entity:shooter.level().getEntities(shooter,area,e->e.isPickable()&&!e.isSpectator())) {
        var hit=entity.getBoundingBox().inflate(entity.getPickRadius()).clip(eye,end);
        if(hit.isPresent()&&eye.distanceToSqr(hit.get())<distance){end=hit.get();distance=eye.distanceToSqr(end);}
      }
      var lines=LINES.computeIfAbsent(owner,id->new ArrayDeque<>());lines.addLast(new LineTrace(now,skin,end,forward,false));while(lines.size()>24)lines.removeFirst();
    }
  }
  /** Resolve the visual line against the existing authoritative hit; no attack logic changes. */
  public static void trace(JudgmentFlame.Shot shot) {
    clean();var mc=Minecraft.getInstance();if(mc.level==null)return;
    var entity=mc.level.getEntity(shot.shooter());
    if(!(entity instanceof net.minecraft.world.entity.LivingEntity living)||shot.skin()<0||shot.skin()>3||!isNativeSkin(living.getMainHandItem(),shot.skin()))return;
    if(shot.skin()!=0)SkinMouthEffects.fired(shot.shooter(),shot.skin(),System.nanoTime());
    if(shot.hit()&&shot.skin()==0)hit(shot.shooter());
    if(shot.hit()&&shot.skin()!=0)NativeSkinAbsorb.hit(shot.shooter(),shot.skin(),System.nanoTime());
    boolean segment=shot.start().distanceToSqr(shot.end())>1e-8;
    if(!segment&&!shot.hit())return;
    long now=System.nanoTime();var lines=LINES.computeIfAbsent(shot.shooter(),id->new ArrayDeque<>());
    LineTrace pending=null;
    for(var line:lines)if(line.skin==shot.skin()&&!line.confirmed&&now-line.born<600_000_000L){pending=line;break;}
    if(pending!=null){pending.end=shot.end();if(segment)pending.forward=shot.end().subtract(shot.start()).normalize();pending.confirmed=true;return;}
    // Remote shot notifications may precede a local GunFireEvent.
    if(!segment)return;
    Vec3 direction=shot.end().subtract(shot.start()).normalize();lines.addLast(new LineTrace(now,shot.skin(),shot.end(),direction,true));
    if(shot.skin()==0){var history=FIRE_HISTORY.computeIfAbsent(shot.shooter(),id->new ArrayDeque<>());history.addLast(now);FIRED.put(shot.shooter(),now);while(history.size()>24)history.removeFirst();}
    while(lines.size()>24)lines.removeFirst();
  }
  private static LineTrace line(int owner,long born){var lines=LINES.get(owner);if(lines!=null)for(var line:lines)if(line.born==born)return line;return null;}
  private static void hit(int owner) {
    if(owner<0)return;
    var history=HIT_HISTORY.computeIfAbsent(owner,id->new ArrayDeque<>());
    history.addLast(System.nanoTime());while(history.size()>24)history.removeFirst();
  }
  private static ArrayList<HitParticle> hitParticles(int owner,float life,float rate,int max,float duration,float delay) {
    return hitParticles(HIT_HISTORY.get(owner),life,rate,max,duration,delay);
  }
  private static ArrayList<HitParticle> hitParticles(java.util.Collection<Long> history,float life,float rate,int max,float duration,float delay) {
    ArrayList<HitParticle> particles=new ArrayList<>();long now=System.nanoTime();
    if(history!=null&&rate>0)for(long born:history)for(int n=0;n<max;n++) {
      double birth=n/(double)rate;if(duration>0&&birth>=duration)break;
      double age=(now-born)/1e9-delay-birth;
      if(age>=0&&age<life)particles.add(new HitParticle(age,born,n));
    }
    particles.sort(java.util.Comparator.comparingDouble(HitParticle::age).reversed());
    if(particles.size()>max)particles.subList(max,particles.size()).clear();return particles;
  }
  public static void skillState(JudgmentFlame.BurstState packet) {
    clean(); if(packet.started()&&packet.skin()==0&&isOriginalShooter(packet.shooter()))SKILLS.put(packet.shooter(),System.nanoTime());
  }
  /** The delegate can run after another model; retain identity on its captured matrix. */
  public static void captureBodyOwner(Matrix4f captured,ItemStack stack,net.minecraft.world.item.ItemDisplayContext context,int skin) {
    if(skin<0||skin>3||!isNativeSkin(stack,skin))return;
    if(!context.firstPerson()&&context!=net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND&&context!=net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)return;
    clean();var mc=Minecraft.getInstance();int owner=-1;
    if(context.firstPerson())owner=mc.player==null?-1:mc.player.getId();
    else if(mc.level!=null) {
      for(var entity:mc.level.entitiesForRendering())
        if(entity instanceof net.minecraft.world.entity.LivingEntity living&&living.getMainHandItem()==stack){owner=living.getId();break;}
      if(owner<0) {
        var tag=stack.getTag();
        if(tag!=null&&tag.hasUUID("JudgfireCrownId")) {
          var weapon=tag.getUUID("JudgfireCrownId");int candidate=-1;
          for(var entity:mc.level.entitiesForRendering())
            if(entity instanceof net.minecraft.world.entity.LivingEntity living) {
              var held=living.getMainHandItem();var heldTag=held.getTag();
              if(isNativeSkin(held,skin)&&heldTag!=null&&heldTag.hasUUID("JudgfireCrownId")&&weapon.equals(heldTag.getUUID("JudgfireCrownId"))) {
                if(candidate>=0){candidate=-1;break;}candidate=living.getId();
              }
            }
          owner=candidate;
        }
      }
    }
    if(BODY_OWNERS.size()>=1024)BODY_OWNERS.clear();
    BODY_OWNERS.put(captured,owner);
    EndpointProjection.captureContext(captured,context.firstPerson());
  }
  private static int bodyOwner(Matrix4f captured) {var owner=BODY_OWNERS.remove(captured);return owner==null?-1:owner;}
  private static boolean isNativeSkin(ItemStack stack,int skin) {
    return JudgmentFlame.ours(stack)&&WeaponSkins.skin(stack)==skin&&stack.getItem() instanceof IGun gun&&!PIXEL.equals(gun.getAttachmentId(stack,AttachmentType.SCOPE));
  }
  public static boolean bodyForSkin(Matrix4f pose,OriginalMotion.Frame motion,int skin) {
    try {
    if(!RenderPoseGuard.usable(pose)){bodyOwner(pose);return true;}
    if(skin==0){body(pose,motion);return true;}
    if(skin<1||skin>3)return false;
    clean();int owner=bodyOwner(pose);boolean rendered=NativeSkinBodyBridge.body(pose,motion,skin,owner);
    if(rendered){
      long now=System.nanoTime();var traces=LINES.get(owner);LineTrace active=null;
      // Reconnecting every historical hit to the moving gun produces a fan of kinked beams.
      // Keep their records for packet matching, but only the latest shot owns this muzzle.
      if(traces!=null)for(var trace:traces)if(trace.skin==skin&&(active==null||trace.born>active.born))active=trace;
      OriginalMotion.Frame beamMotion=active==null?motion:SkinEndpointLines.aimedMotion(skin,pose,motion,active.end);
      SkinMouthEffects.render(owner,skin,pose,beamMotion,now);
      NativeSkinAbsorb.render(owner,skin,pose,motion,now);
      if(active!=null)SkinEndpointLines.render(owner,skin,pose,motion,active.end,active.born,now);
    }
    return rendered;
    } finally {EndpointProjection.finishContext(pose);}
  }
  private static float skill(double age) {
    // Relative to the first visible lift: reference F56 -> F69..81 -> F90.
    if(age<.433||age>1.133)return 0;
    return (float)(smooth((age-.433)/.10)*(1-smooth((age-.733)/.40)));
  }
  private static double smooth(double x) { x=java.lang.Math.max(0,java.lang.Math.min(1,x));return x*x*(3-2*x); }
  public static void body(Matrix4f pose,OriginalMotion.Frame motion) {
    clean();renderDefinition(data,bodyOwner(pose),pose,motion,System.nanoTime(),null);
  }
  static void renderDefinition(Definition d,int owner,Matrix4f pose,OriginalMotion.Frame motion,long now,java.util.Collection<Long> hitBirths) {
    if(d==null||motion==null||FlameRenderType.shader==null)return;
    Matrix4f inverse=RenderPoseGuard.inverse(pose);if(inverse==null)return;
    double time=(now/1e9)%4096;
    // Detached, hit-only skin definitions must not keep another skin's continuous
    // emitter activation alive while that weapon is no longer being rendered.
    OwnerClock clock;
    if(hitBirths!=null)clock=new OwnerClock(now);
    else{
      clock=OWNER_CLOCKS.get(owner);
      if(clock==null||now-clock.seen>300_000_000L){clock=new OwnerClock(now);OWNER_CLOCKS.put(owner,clock);}clock.seen=now;
    }
    double emitterSeconds=(now-clock.born)/1e9;int gi=-1;
    for(Group g:d.groups) {gi++;
      Material mat=d.materials[g.material];var type=FlameRenderType.get(mat.textures,mat.additive,mat.filters,false);
      int count=g.center==null?1:java.lang.Math.min(g.max,java.lang.Math.max(1,(int)java.lang.Math.ceil(g.life*g.rate)));
      var hits=g.hitOnly?hitParticles(hitBirths==null?HIT_HISTORY.get(owner):hitBirths,g.life,g.rate,g.max,g.duration,g.delay):null;
      if(hits!=null)count=hits.size();
      java.util.List<NativeEmitterClock.Birth> meshBirths=null;
      if(g.center!=null&&!g.hitOnly){
        NativeEmitterClock nativeClock=clock.emitters.get(-gi-1);
        if(nativeClock==null){nativeClock=new NativeEmitterClock(g,owner*9749L+g.name.hashCode());clock.emitters.put(-gi-1,nativeClock);}
        meshBirths=nativeClock.sample(emitterSeconds);count=meshBirths.size();
      }
      // All particles in this group share these source material uniforms.
      // Only tint alpha and a possible custom curve vary between particles.
      if(count>0)params(mat,time,1);
      for(int particle=0;particle<count;particle++) {
       double phase=g.hitOnly?hits.get(particle).age()/g.life:meshBirths==null?0:(emitterSeconds-meshBirths.get(particle).seconds())/meshBirths.get(particle).life();
       float rnd=g.hitOnly?random(hits.get(particle).born()+hits.get(particle).slot()*149L+g.name.hashCode())
           :g.bodyLoop&&meshBirths!=null?meshBirths.get(particle).random(2):(float)fract(java.lang.Math.sin((particle+1)*93.3)*41413.1);
       float[] rgba=g.colors==null?WHITE:sampleColor(g.colors,phase);
       FlameRenderType.shader.safeGetUniform("Tint").set(mat.tint[0],mat.tint[1],mat.tint[2],mat.tint[3]*(g.alpha*rgba[3]));
       if(g.custom!=null)FlameRenderType.shader.safeGetUniform("Custom").set(sampleColor(g.custom,phase));
       VertexConsumer v=BUFFERS.getBuffer(type);
       float[][] vertices=g.vertices;
       if(g.nativeLocalVertices!=null&&g.localToModel!=null)vertices=nativeHitVertices(g,phase,rnd);
       else if(g.center!=null&&g.sizes!=null){float size=sample(g.sizes,phase,0);vertices=new float[g.vertices.length][3];for(int i=0;i<vertices.length;i++){
         for(int k=0;k<3;k++)vertices[i][k]=g.center[k]+(g.vertices[i][k]-g.center[k])*size;
       }}
       float[][] points=g.binding!=null?g.binding.deform(vertices,motion):deform(vertices,g.bone,motion);
       for(int[] face:g.faces)for(int j=0;j<4;j++) {
        int ix=face[j<face.length?j:face.length-1];float[] p=points[ix],uv=g.uv[ix];
        float u=uv[0],w=uv[1];
        float cr=rgba[0],cg=rgba[1];
        if(g.uv1!=null) { cr=g.uv1[ix][0];cg=g.uv1[ix][1]; }
        float[] vc=g.vertexColors==null?WHITE:g.vertexColors[ix];
        v.vertex(pose,p[0]/16,-p[1]/16,p[2]/16).color(cr*vc[0],cg*vc[1],rgba[2]*vc[2],vc[3]).uv(u,w).endVertex();
       }
       BUFFERS.endBatch(type);
      }
    }
    Vector3f right=inverse.transformDirection(new Vector3f(1,0,0)).normalize();
    Vector3f up=inverse.transformDirection(new Vector3f(0,1,0)).normalize();
    right.y=-right.y;up.y=-up.y;
    double shotAge=FIRED.containsKey(owner)?(System.nanoTime()-FIRED.get(owner))/1e9:99;
    for(int ei=0;ei<d.emitters.length;ei++) {
      Emitter e=d.emitters[ei]; if(e.mouthOnly&&shotAge>e.life+.1)continue;
      Material mat=d.materials[e.material];var type=FlameRenderType.get(mat.textures,mat.additive,mat.filters,false);
      params(mat,time,1);
      var v=BUFFERS.getBuffer(type);
      int count=java.lang.Math.min(e.max,java.lang.Math.max(1,(int)java.lang.Math.ceil(e.life*e.rate)));
      float maxLife=e.lifeRange==null?e.life:java.lang.Math.max(e.lifeRange[0],e.lifeRange[1]);
      var hitAges=e.hitOnly?hitParticles(hitBirths==null?HIT_HISTORY.get(owner):hitBirths,maxLife,e.rate,e.max,e.duration,e.delay):null;
      if(hitAges!=null)count=hitAges.size();
      ArrayList<NativeEmitterClock.Birth> births=null;
      if(!e.mouthOnly&&!e.hitOnly){
        NativeEmitterClock nativeClock=clock.emitters.get(ei);
        if(nativeClock==null){nativeClock=new NativeEmitterClock(e,owner*9749L+e.name.hashCode());clock.emitters.put(ei,nativeClock);}
        births=new ArrayList<>(nativeClock.sample(emitterSeconds));count=births.size();
      }
      ArrayList<MouthParticle> mouthAges=new ArrayList<>();
      if(e.mouthOnly) {
        var history=FIRE_HISTORY.get(owner);
        if(history!=null)for(long born:history)for(int n=0;n<e.max;n++) {
          double age=(System.nanoTime()-born)/1e9-e.delay-n/(double)e.rate;
          if(age>=0&&age<e.life)mouthAges.add(new MouthParticle(age,line(owner,born)));
        }
        // A native capped emitter keeps its living particles and waits for a free slot.
        mouthAges.sort(java.util.Comparator.comparingDouble(MouthParticle::age).reversed());count=java.lang.Math.min(e.max,mouthAges.size());
      }
      for(int n=0;n<count;n++) {
        long birthKey=e.hitOnly?hitAges.get(n).born()+hitAges.get(n).slot()*149L:e.mouthOnly?0:clock.born+(long)(births.get(n).seconds()*1e9);
        long particleSeed=e.hitOnly?NativeEmitterClock.mix(birthKey+e.name.hashCode()):e.mouthOnly?n+ei*9749L:births.get(n).seed();
        float lifetime=e.lifeRange==null?e.life:e.lifeRange[0]+(e.lifeRange[1]-e.lifeRange[0])*NativeEmitterClock.unit(particleSeed);
        if(births!=null)lifetime=births.get(n).life();
        double ageSeconds=e.hitOnly?hitAges.get(n).age():e.mouthOnly?mouthAges.get(n).age():emitterSeconds-births.get(n).seconds();
        double phase=ageSeconds/lifetime;
        if(phase<0||phase>=1)continue;
        float rnd=NativeEmitterClock.unit(particleSeed+1),sizeRandom=NativeEmitterClock.unit(particleSeed+2);
        float age=(float)ageSeconds,growth=1;
        boolean xyz=e.sizes!=null&&e.sizes[0].length>=3;
        if(e.sizes!=null&&e.sizes.length>1){growth=sample(e.sizes,phase,0);if(!xyz&&e.sizes[0].length==2)growth+=(sample(e.sizes,phase,1)-growth)*rnd;}
        if(growth<=.001)continue;
        float[] color=e.colors==null?new float[]{1,1,1,1}:sampleColor(e.colors,phase);
        if(e.startColor!=null)for(int k=0;k<4;k++)color[k]*=e.startColorRange==null?e.startColor[k]:e.startColorRange[0][k]+(e.startColorRange[1][k]-e.startColorRange[0][k])*NativeEmitterClock.unit(particleSeed+3);
        Vector3f local=new Vector3f(e.position[0],e.position[1],e.position[2]);
        Vector3f birthDirection=new Vector3f(0,0,1);
        float u=NativeEmitterClock.unit(particleSeed+8),w=NativeEmitterClock.unit(particleSeed+9),q=NativeEmitterClock.unit(particleSeed+10);
        if(e.shape!=null&&e.shape.enabled) {
          var sh=e.shape;
          var sampled=NativeParticleShape.sample(sh,u,w,q,NativeEmitterClock.unit(particleSeed+13));
          float[] pos=sampled.position();
          local.fma(pos[0],new Vector3f(e.axisX)).fma(pos[1],new Vector3f(e.axisY)).fma(pos[2],new Vector3f(e.axisZ));
          birthDirection.set(sampled.direction());
          if(sh.sphericalDirectionAmount>0){var radial=new Vector3f(pos);if(radial.lengthSquared()>1e-10f)birthDirection.lerp(radial.normalize(),sh.sphericalDirectionAmount);}
          if(sh.randomDirectionAmount>0){float z=2*NativeEmitterClock.unit(particleSeed+11)-1,phi=(float)(2*java.lang.Math.PI*NativeEmitterClock.unit(particleSeed+12)),r=(float)java.lang.Math.sqrt(java.lang.Math.max(0,1-z*z));birthDirection.lerp(new Vector3f(r*(float)java.lang.Math.cos(phi),r*(float)java.lang.Math.sin(phi),z),sh.randomDirectionAmount);}
          if(birthDirection.lengthSquared()>1e-10f)birthDirection.normalize();else birthDirection.set(0,0,1);
        }
        float nativeSpeed=e.speedRange==null?e.speed:e.speedRange[0]+(e.speedRange[1]-e.speedRange[0])*NativeEmitterClock.unit(particleSeed+4);
        Vector3f velocityVector=new Vector3f(e.axisX).mul(birthDirection.x*nativeSpeed).fma(birthDirection.y*nativeSpeed,new Vector3f(e.axisY)).fma(birthDirection.z*nativeSpeed,new Vector3f(e.axisZ));
        if(e.velocity!=null)for(int k=0;k<3;k++){float speed=e.velocityRanges==null?e.velocity[k]:e.velocityRanges[k][0]+(e.velocityRanges[k][1]-e.velocityRanges[k][0])*NativeEmitterClock.unit(particleSeed+5+k);velocityVector.fma(speed,new Vector3f(k==0?e.axisX:k==1?e.axisY:e.axisZ));}
        local.fma(age,velocityVector);
        float[] attached=point(local.x,local.y,local.z,e.bone,motion);
        if(Boolean.FALSE.equals(e.localSpace)){
          var key=new WorldKey(owner,ei,birthKey);var pin=WORLD_PINS.get(key);
          if(pin==null){
            float[] end=point(local.x+velocityVector.x,local.y+velocityVector.y,local.z+velocityVector.z,e.bone,motion);
            Vec3 worldVelocity=modelDirectionToWorld(pose,new Vector3f(end[0]-attached[0],end[1]-attached[1],end[2]-attached[2]));
            pin=new WorldPin(modelToWorld(pose,new Vector3f(attached)).subtract(worldVelocity.scale(age)),worldVelocity,now);WORLD_PINS.put(key,pin);
          }
          pin.seen=now;Vector3f pinned=worldToModel(inverse,pin.start.add(pin.velocity.scale(age)));attached=new float[]{pinned.x,pinned.y,pinned.z};
        }
        float sx=new Vector3f(e.axisX).length()*startSize(e,0,sizeRandom)*growth;
        float gy=xyz?sample(e.sizes,phase,1):growth;
        float sy=new Vector3f(e.axisY).length()*startSize(e,1,sizeRandom)*gy;
        // Constrain only source placeholder glows; free sparks use their native curves.
        Vector3f center=new Vector3f(attached);Vector3f rr=new Vector3f(right).mul(sx*.5f),uu=new Vector3f(up).mul(sy*.5f);
        if(e.renderMode==4) {
          float[] px=point(local.x+e.axisX[0],local.y+e.axisX[1],local.z+e.axisX[2],e.bone,motion);
          float[] py=point(local.x+e.axisY[0],local.y+e.axisY[1],local.z+e.axisY[2],e.bone,motion);
          rr.set(px[0]-attached[0],px[1]-attached[1],px[2]-attached[2]).normalize().mul(sx*.5f);
          uu.set(py[0]-attached[0],py[1]-attached[1],py[2]-attached[2]).normalize().mul(sy*.5f);
        }
        if(e.renderMode==1) {
          // Stretched billboards follow the particle velocity projected onto the
          // camera plane, rather than camera-up. Keep the authored length sign.
          Vector3f velocity=new Vector3f(velocityVector);
          if(velocity.lengthSquared()<1e-10f)velocity.set(e.axisZ);
          float[] end=point(local.x+velocity.x,local.y+velocity.y,local.z+velocity.z,e.bone,motion);
          Vector3f direction=new Vector3f(end[0]-attached[0],end[1]-attached[1],end[2]-attached[2]);
          Vector3f normal=new Vector3f(right).cross(up).normalize();direction.fma(-direction.dot(normal),normal);
          if(direction.lengthSquared()>1e-10f){direction.normalize();rr.set(direction).cross(normal).normalize().mul(sx*.5f);uu.set(direction).mul(sy*.5f);}
          uu.mul(e.lengthScale==0?1:e.lengthScale);
        }
        float rotation=e.rotationRange==null?e.rotation:e.rotationRange[0]+(e.rotationRange[1]-e.rotationRange[0])*NativeEmitterClock.unit(particleSeed+13);
        if(rotation!=0){float cs=(float)java.lang.Math.cos(rotation),sn=(float)java.lang.Math.sin(rotation);Vector3f r0=new Vector3f(rr),u0v=new Vector3f(uu);rr.set(r0).mul(cs).fma(sn,u0v);uu.set(u0v).mul(cs).fma(-sn,r0);}
        int cols=e.sheet==null?1:e.sheet[0],rows=e.sheet==null?1:e.sheet[1];
        int f=java.lang.Math.min(cols*rows-1,(int)(phase*cols*rows));
        float u0=(f%cols)/(float)cols,u1=u0+1f/cols,v0=1-(f/cols+1f)/rows,v1=v0+1f/rows;
        if(e.mouthOnly&&mouthAges.get(n).line()!=null) {
          var line=mouthAges.get(n).line();var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
          var cameraInverse=new Quaternionf(camera.rotation()).conjugate();Vec3 relative=line.end.subtract(camera.getPosition());
          Vector3f target=new Vector3f((float)relative.x,(float)relative.y,(float)relative.z).rotate(cameraInverse);
          target.x=-target.x;target.z=-target.z;EndpointProjection.adjustViewEndpoint(pose,target);inverse.transformPosition(target).mul(16);target.y=-target.y;
          Vector3f forward=new Vector3f((float)line.forward.x,(float)line.forward.y,(float)line.forward.z).rotate(cameraInverse);
          forward.x=-forward.x;forward.z=-forward.z;inverse.transformDirection(forward).normalize();forward.y=-forward.y;
          Vector3f direction=target.sub(attached[0],attached[1],attached[2]);float distance=direction.length();
          if(distance<1e-4f||direction.dot(forward)<=0)continue;
          direction.div(distance);float length=distance*java.lang.Math.max(0,java.lang.Math.min(1,gy/7f));
          float width=java.lang.Math.min(sx,distance*.5f);if(length<1e-4f||width<1e-4f)continue;
          rr.fma(-rr.dot(direction),direction);
          if(rr.lengthSquared()<1e-10f){rr.set(direction).cross(up);if(rr.lengthSquared()<1e-10f)rr.set(direction).cross(right);}
          rr.normalize().mul(width*.5f);uu.set(direction).mul(length*.5f);center.set(attached).add(uu);
          // Only the source atlas's alpha-bearing positive-Y half. This anchors
          // the visible root at the mouth and bounds every vertex before the hit.
          v0=(v0+v1)*.5f;
        }
        quad(v,pose,center,rr,uu,u0,u1,v0,v1,color);
      }
      BUFFERS.endBatch(type);
    }
  }
  private static double fract(double x){return x-java.lang.Math.floor(x);}
  private static float sample(float[][] values,double t,int component) {
    double f=java.lang.Math.min(values.length-1,t*(values.length-1));int i=(int)f,j=java.lang.Math.min(i+1,values.length-1);
    return (float)(values[i][component]*(1-(f-i))+values[j][component]*(f-i));
  }
  private static float[] sampleColor(float[][] colors,double t){float[] c=new float[4];for(int i=0;i<4;i++)c[i]=sample(colors,t,i);return c;}
  private static float random(long key) {
    key=(key^(key>>>30))*0xbf58476d1ce4e5b9L;key=(key^(key>>>27))*0x94d049bb133111ebL;key^=key>>>31;
    return (float)((key>>>40)*0x1.0p-24);
  }
  private static float startSize(Emitter e,int axis,float rnd) {
    if(e.sizeMin!=null)return e.sizeMin[axis]+(e.size[axis]-e.sizeMin[axis])*rnd;
    if(e.sizeRange!=null){float average=(e.sizeRange[0]+e.sizeRange[1])*.5f;if(java.lang.Math.abs(average)>1e-10f)return e.size[axis]*(e.sizeRange[0]+(e.sizeRange[1]-e.sizeRange[0])*rnd)/average;}
    return e.size[axis];
  }
  private static Vec3 modelToWorld(Matrix4f pose,Vector3f p) {
    var camera=Minecraft.getInstance().gameRenderer.getMainCamera();p.set(p.x/16,-p.y/16,p.z/16);pose.transformPosition(p);
    p.x=-p.x;p.z=-p.z;p.rotate(camera.rotation());return camera.getPosition().add(p.x,p.y,p.z);
  }
  private static Vec3 modelDirectionToWorld(Matrix4f pose,Vector3f p) {
    p.set(p.x/16,-p.y/16,p.z/16);pose.transformDirection(p);p.x=-p.x;p.z=-p.z;p.rotate(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());return new Vec3(p.x,p.y,p.z);
  }
  private static Vector3f worldToModel(Matrix4f inverse,Vec3 point) {
    var camera=Minecraft.getInstance().gameRenderer.getMainCamera();Vec3 relative=point.subtract(camera.getPosition());
    Vector3f p=new Vector3f((float)relative.x,(float)relative.y,(float)relative.z).rotate(new Quaternionf(camera.rotation()).conjugate());p.x=-p.x;p.z=-p.z;inverse.transformPosition(p).mul(16);p.y=-p.y;return p;
  }
  private static float angular(float[][] values,double phase,float random,int axis,float life) {
    if(values==null||values.length<2)return 0;double integral=0;
    for(int i=0;i<values.length-1&&i<(values.length-1)*phase;i++) {
      double a=i/(double)(values.length-1),b=java.lang.Math.min(phase,(i+1)/(double)(values.length-1));
      float al=sample(values,a,axis*2),ah=sample(values,a,axis*2+1),bl=sample(values,b,axis*2),bh=sample(values,b,axis*2+1);
      integral+=((al+(ah-al)*random)+(bl+(bh-bl)*random))*.5*(b-a);
    }
    return (float)(integral*life);
  }
  private static float[][] nativeHitVertices(Group g,double phase,float rnd) {
    float size=g.sizes==null?1:sample(g.sizes,phase,0);
    if(g.sizes!=null&&g.sizes[0].length==2)size+=(sample(g.sizes,phase,1)-size)*rnd;
    float[] axes=new float[3],angles=new float[3];
    for(int k=0;k<3;k++) {
      axes[k]=(g.sizeXYZMin==null?g.sizeXYZ[k]:g.sizeXYZMin[k]+(g.sizeXYZ[k]-g.sizeXYZMin[k])*rnd)*size;
      angles[k]=(g.initialEuler==null?0:g.initialEuler[k*2]+(g.initialEuler[k*2+1]-g.initialEuler[k*2])*rnd)+angular(g.angularVelocity,phase,rnd,k,g.life);
    }
    var rotation=new Matrix3f().rotateY(angles[1]).rotateX(angles[0]).rotateZ(angles[2]);
    Vector3f spawn=new Vector3f();
    if(g.sourceShape!=null&&g.sourceShape.has("enabled")&&g.sourceShape.get("enabled").getAsBoolean()&&g.sourceShape.has("radius")) {
      float radius=g.sourceShape.getAsJsonObject("radius").get("value").getAsFloat();
      float z=2*(float)fract(rnd*17.31)-1,theta=(float)(2*java.lang.Math.PI*fract(rnd*7.73));
      float r=radius*(float)java.lang.Math.cbrt(fract(rnd*23.17)),xy=(float)java.lang.Math.sqrt(java.lang.Math.max(0,1-z*z));
      spawn.set(r*xy*(float)java.lang.Math.cos(theta),r*xy*(float)java.lang.Math.sin(theta),r*z);
    }
    float[][] out=new float[g.nativeLocalVertices.length][3];
    for(int i=0;i<out.length;i++) {
      float[] v=g.nativeLocalVertices[i];var p=new Vector3f(v[0]*axes[0],v[1]*axes[1],v[2]*axes[2]);rotation.transform(p);p.add(spawn);
      for(int k=0;k<3;k++)out[i][k]=g.localToModel[k][0]*p.x+g.localToModel[k][1]*p.y+g.localToModel[k][2]*p.z+g.localToModel[k][3];
    }
    return out;
  }
  private static float[] point(float x,float y,float z,int bone,OriginalMotion.Frame f) {
    if(bone<0||bone>=f.matrices().length)return new float[]{x,y,z};
    float[] m=f.matrices()[bone];return new float[]{m[0]*x-m[1]*y+m[2]*z+m[3],-(m[4]*x-m[5]*y+m[6]*z+m[7]),m[8]*x-m[9]*y+m[10]*z+m[11]};
  }
  private static float[][] deform(float[][] src,int bone,OriginalMotion.Frame motion){float[][] p=new float[src.length][];for(int i=0;i<p.length;i++)p[i]=point(src[i][0],src[i][1],src[i][2],bone,motion);return p;}
  private static void quad(VertexConsumer v,Matrix4f pose,Vector3f c,Vector3f r,Vector3f u,float u0,float u1,float v0,float v1,float[] rgba){
    Vector3f p=new Vector3f();
    for(int i=0;i<4;i++){p.set(c).fma(i==0||i==3?-1:1,r).fma(i<2?-1:1,u);
      v.vertex(pose,p.x/16,-p.y/16,p.z/16).color(rgba[0],rgba[1],rgba[2],rgba[3]).uv(i==0||i==3?u0:u1,i<2?v0:v1).endVertex();}
  }
  private static void params(Material m,double time,float alpha) {
    var s=FlameRenderType.shader;
    s.safeGetUniform("EffectTime").set((float)time);
    s.safeGetUniform("MainST").set(m.mainST);s.safeGetUniform("MaskST").set(m.maskST);s.safeGetUniform("NoiseST").set(m.noiseST);
    s.safeGetUniform("WrapMain").set(m.wrapMain==null?ZERO2:m.wrapMain);
    s.safeGetUniform("WrapMask").set(m.wrapMask==null?ZERO2:m.wrapMask);
    s.safeGetUniform("WrapNoise").set(m.wrapNoise==null?ZERO2:m.wrapNoise);
    s.safeGetUniform("Scroll").set(m.scroll);s.safeGetUniform("Params").set(m.params);
    s.safeGetUniform("Force").set(m.force==null?ZERO2:m.force);
    s.safeGetUniform("HeatTime").set(m.heatTime);s.safeGetUniform("FlowOffset").set(m.flowOffset);
    s.safeGetUniform("Custom").set(0f,0f,0f,0f);
    s.safeGetUniform("Tint").set(m.tint[0],m.tint[1],m.tint[2],m.tint[3]*alpha);
  }
  public static void laser(PoseStack p,Vec3 s,Vec3 e,Vec3 c,double a,boolean hit){DefaultWorldFlames.laser(p,s,e,c,a,hit);}
  public static void impact(PoseStack p,Vec3 e,Vec3 c,double a){DefaultWorldFlames.impact(p,e,c,a);}
  public static void mark(PoseStack p,Vec3 e,double a){DefaultWorldFlames.mark(p,e,a);}
  public static void crown(PoseStack p,Vec3 e,Quaternionf c,double q,double a){DefaultWorldFlames.crown(p,e,c,q,a);}
  public static void strike(PoseStack p,JudgmentFlame.CrownStrike s,double a,Quaternionf c){
    DefaultWorldFlames.strike(p,s,a,c);
    // Existing arrival time and uniquely identified source owner; cosmetic only.
    if(a>=CrownHoming.duration(s.start(),s.end())&&!CROWN_HITS.containsKey(s)){
      if(CROWN_HITS.size()>=512)CROWN_HITS.clear();CROWN_HITS.put(s,Boolean.TRUE);
      hit(DefaultWorldFlames.crownOwner(s));
    }
  }
  public static void burst(PoseStack p,Vec3 b,Quaternionf c,double a){DefaultWorldFlames.burst(p,b,c,a);}
}
