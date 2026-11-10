package cn.judgfire;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.io.Reader;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Independent consumer of the supplied qinglong/frozen/lightning body candidates. No event,
 * resource reload or gun routing is registered here: the integration caller owns activation,
 * sessions and resource locations. It never reads a compatibility emission rate.
 *
 * Sampled meshes are in CurrentRig Y-up model units. Candidate localToModel already contains
 * that skin's mount, inverse bind and its single coordinate reflection. Native-local geometry
 * is reconstructed before that matrix, then current body0/head6 motion delta is applied.
 *
 * Scheduling and custom effect composition are explicitly inferred where original custom
 * method bodies are missing; see CustomParticleSchedule/CustomParticleTransform. Camera
 * billboard orientation is applied once: shaderBillboard1 camera-facing, type1 axis-locked
 * around source RotateAxis, type4 fixed authored plane. Type1 semantics are also a candidate
 * inference. This implementation is not a claim of reference-video acceptance.
 */
public final class SkinBodyEffects {
  private SkinBodyEffects() {}
  private static final Gson GSON=new Gson();
  private static final String WHITE="judgfire:textures/fx/reset5/white.png";
  private static final Map<Integer,Definition> DEFINITIONS=new HashMap<>();
  private static MultiBufferSource.BufferSource buffers;

  public static final class Material {
    public String name,shaderTag;public String[] textures;
    public float[] mainST,tint;public boolean additive,tintIncludesPixelFactor2;
    public float[] wrapMain={0,0},wrapMask={0,0},wrapNoise={0,0};
    public int[] filters={1,1,1};public boolean polygonOffset;
    public JsonObject samplers,renderState;public JsonArray textureSources;
  }
  /** Does not redeclare the inherited scheduler's customParticleRaw/other fields. */
  public static final class Layer extends CustomParticleSchedule.Spec {
    public String name,sourceMeshKey;public int material,bone,billboardType,RotateAxis;
    public boolean enabled,source_active,nativeQuadOnly;public float billboardShader;
    public int sourceIsWorldParticle;public float[][] nativeLocalVertices,vertices,uv,localToModel;
    public int[][] faces;
    private transient JsonObject fullRaw;
    private transient CustomParticleTransform.Compiled transform;
    public int sourceShapeType(){return fullRaw!=null&&fullRaw.has("shape")?fullRaw.getAsJsonObject("shape").get("shapeType").getAsInt():-1;}
  }
  public static final class Definition {
    public String skin;public Material[] materials;public Layer[] groups,emitters;
    private transient int skinId;private transient List<Layer> layers;
    private transient List<String> diagnostics;
    public int skinId(){return skinId;}
    public int layerCount(){return layers.size();}
    public List<String> diagnostics(){return diagnostics;}
  }
  /** One instance per weapon/owner/activation. A new session means a new activation clock. */
  public static final class Session {
    private final Definition definition;private final List<State> states;
    private Session(Definition d,long seed) {
      definition=d;ArrayList<State> list=new ArrayList<>();int index=0;
      for(Layer layer:d.layers)list.add(new State(layer,new CustomParticleSchedule(layer,seed^((long)layer.name.hashCode()<<32)^++index)));
      states=List.copyOf(list);
    }
    public Definition definition(){return definition;}
    public void reset(){for(State state:states)state.clock.reset();}
  }
  private record State(Layer layer,CustomParticleSchedule clock) {}
  /** One true source mesh/quad instance; UVs remain in Unity space. No texture pixels are generated. */
  public record Draw(String layerName,int material,float[][] vertices,float[][] uv,int[][] faces,
                     float[] rgba,int particleIndex,long cycle,double birthSeconds) {}

  /**
   * Map provided relative PNG paths to packaged ResourceLocation strings explicitly. For example,
   * a resolver may map '../textures/<sha>.png' into 'judgfire:textures/fx/skins/<sha>.png'.
   * This class does not copy assets or assume those output resources already exist.
   */
  public static Definition load(int skin,Reader reader,UnaryOperator<String> textureResolver) {
    return loadCandidate(skin,reader,textureResolver,true);
  }
  /** Same source consumer for independent event effects, without replacing the body lookup. */
  public static Definition loadDetached(int skin,Reader reader,UnaryOperator<String> textureResolver) {
    return loadCandidate(skin,reader,textureResolver,false);
  }
  private static Definition loadCandidate(int skin,Reader reader,UnaryOperator<String> textureResolver,boolean register) {
    BodyScratch.clear();
    if(skin<1||skin>3)throw new IllegalArgumentException("Expected skin1 qinglong/2 frozen/3 lightning");
    Objects.requireNonNull(reader,"candidate reader");Objects.requireNonNull(textureResolver,"texture resolver");
    JsonObject root=GSON.fromJson(reader,JsonObject.class);
    if(root==null)throw new IllegalArgumentException("Empty skin candidate");
    String[] names={"","qinglong","frozen","lightning"};
    if(!root.has("skin")||!names[skin].equals(root.get("skin").getAsString()))
      throw new IllegalArgumentException("Skin id/source mismatch");
    Definition d=GSON.fromJson(root,Definition.class);d.skinId=skin;
    if(d.materials==null)throw new IllegalArgumentException("Missing native materials");
    ArrayList<String> notes=new ArrayList<>();ArrayList<Layer> layers=new ArrayList<>();
    notes.add(CustomParticleSchedule.INFERENCE_NOTE);notes.add(CustomParticleTransform.INFERENCE_NOTE);
    notes.add("billboardType1 axis-locked around RotateAxis is inferred; custom shader camera billboard is applied once");
    notes.add("Source texture wrap/filter is retained; SSJJ custom source polygon offset(-1,-1) is opt-in, leaving original skin unchanged");
    for(Material m:d.materials) {
      if(!"ssjj_newparticles".equals(m.shaderTag))throw new IllegalArgumentException("Unsupported source shader: "+m.shaderTag);
      if(!m.tintIncludesPixelFactor2)throw new IllegalArgumentException("Candidate tint must already include source factor2");
      if(m.textures==null||m.textures.length==0||m.textures[0]==null)throw new IllegalArgumentException("Missing source main texture "+m.name);
      String source=m.textures[0];m.textures=new String[]{resolve(textureResolver,source),WHITE,WHITE};
      require(m.mainST,4,"mainST");require(m.tint,4,"tint");
      JsonObject sampler=mainSampler(m);
      m.wrapMain=new float[]{samplerValue(sampler,"m_WrapU","wrapUVW",0),samplerValue(sampler,"m_WrapV","wrapUVW",1)};
      m.filters=new int[]{samplerValue(sampler,"m_FilterMode","filterMode",-1),1,1};
      for(float mode:m.wrapMain)if(mode<0||mode>3)throw new IllegalArgumentException("Source wrap mode "+mode);
      if(m.filters[0]<0||m.filters[0]>2)throw new IllegalArgumentException("Source filter mode "+m.filters[0]);
      if(m.filters[0]==2)notes.add(m.name+": source trilinear uses supplied PNG level0 only; original mip chain not supplied to GL adapter");
      m.polygonOffset=m.renderState!=null&&m.renderState.has("offsetFactor")&&m.renderState.has("offsetUnits")
          &&m.renderState.get("offsetFactor").getAsFloat()==-1f&&m.renderState.get("offsetUnits").getAsFloat()==-1f;
    }
    for(String kind:new String[]{"groups","emitters"}) {
      Layer[] values="groups".equals(kind)?d.groups:d.emitters;
      JsonArray rawLayers=root.has(kind)?root.getAsJsonArray(kind):new JsonArray();
      if(values==null)values=new Layer[0];
      if(values.length!=rawLayers.size())throw new IllegalArgumentException("Candidate layer mismatch");
      for(int i=0;i<values.length;i++) {
        Layer layer=values[i];JsonObject rawLayer=rawLayers.get(i).getAsJsonObject();
        if(!rawLayer.has("customParticleRaw")||!rawLayer.get("customParticleRaw").isJsonObject())throw new IllegalArgumentException("Missing raw custom particle "+layer.name);
        layer.fullRaw=rawLayer.getAsJsonObject("customParticleRaw").deepCopy();
        // Detached absorb/mouth exporters retain the complete source custom
        // component but do not all emit the scheduler's convenience DTO. The
        // default Schedule loops, so silently leaving this null turns source
        // one-shots into constantly renewing particles. Read these authored
        // fields from the same raw component used by the transform consumer.
        layer.customSchedule=GSON.fromJson(layer.fullRaw,CustomParticleSchedule.Schedule.class);
        layer.transform=CustomParticleTransform.compile(layer.fullRaw);
        if(layer.material<0||layer.material>=d.materials.length)throw new IllegalArgumentException("Invalid material "+layer.name);
        if(layer.bone!=0&&layer.bone!=6)throw new IllegalArgumentException("Unexpected skin current-rig bone "+layer.name);
        matrix(layer.localToModel);if(!layer.enabled||!layer.source_active)continue;
        if(layer.sourceIsWorldParticle!=0) {
          notes.add(layer.name+": not rendered; World custom particles require birth transform history, not current gun attachment");continue;
        }
        if(layer.nativeQuadOnly) {
          if(!"unity default resources:10210".equals(layer.sourceMeshKey))throw new IllegalArgumentException("Non-native quad "+layer.name);
        } else {
          if(layer.nativeLocalVertices==null||layer.uv==null||layer.faces==null||layer.nativeLocalVertices.length!=layer.uv.length)
            throw new IllegalArgumentException("Missing source mesh/UV "+layer.name);
          for(float[] v:layer.nativeLocalVertices)require(v,3,"vertex");
          for(float[] uv:layer.uv)require(uv,2,"uv");
          for(int[] face:layer.faces)for(int index:face)if(index<0||index>=layer.nativeLocalVertices.length)throw new IllegalArgumentException("Bad source face "+layer.name);
        }
        if(layer.billboardType!=0&&layer.billboardType!=1&&layer.billboardType!=4) {
          notes.add(layer.name+": not rendered; unsupported source billboardType "+layer.billboardType);continue;
        }
        layers.add(layer);
      }
    }
    d.layers=List.copyOf(layers);d.diagnostics=List.copyOf(notes);if(register)DEFINITIONS.put(skin,d);return d;
  }
  public static Session newSession(int skin,long stableActivationSeed) {
    Definition d=DEFINITIONS.get(skin);if(d==null)throw new IllegalStateException("Skin definition not loaded: "+skin);
    return new Session(d,stableActivationSeed);
  }
  public static Session newSession(Definition definition,long stableActivationSeed) {
    return new Session(Objects.requireNonNull(definition),stableActivationSeed);
  }
  public static void clear() {DEFINITIONS.clear();CustomParticleTransform.clearCache();BodyScratch.clear();}

  /** Pure sampling entry point. right/up are camera basis in CurrentRig Y-up model space. */
  public static List<Draw> sample(Session session,double elapsedSeconds,float[][] motionMatrices,
                                  float[] cameraRight,float[] cameraUp) {
    Objects.requireNonNull(session);if(!Double.isFinite(elapsedSeconds))throw new IllegalArgumentException("Invalid activation clock");
    if(!RenderPoseGuard.direction(cameraRight)||!RenderPoseGuard.direction(cameraUp))return List.of();
    float[] right=unit(cameraRight),up=unit(cameraUp);ArrayList<Draw> draws=new ArrayList<>();
    for(State state:session.states) {
      var particles=state.clock.sample(elapsedSeconds);if(particles.isEmpty())continue;
      Prepared prepared=new Prepared(state.layer,motionMatrices,right,up);
      for(var particle:particles)draws.add(prepared.draw(particle));
    }
    return draws;
  }

  /** A hit's activation clock and its absolute origin; source birth ordering is unchanged. */
  public record TimedSession(Session session,double elapsedSeconds,double originSeconds) {}
  private record Live(State state,CustomParticleSchedule.Particle particle,double born) {}
  /**
   * Select the same newest-per-emitter source pool before constructing its geometry. Building
   * the meshes for every historical hit and then discarding them made a 37-particle source
   * repeatedly transform up to 888 particles. List.sort is stable, including equal births.
   */
  public static List<Draw> sampleNewest(List<TimedSession> sessions,float[][] motionMatrices,
                                      float[] cameraRight,float[] cameraUp) {
    if(!RenderPoseGuard.direction(cameraRight)||!RenderPoseGuard.direction(cameraUp))return List.of();
    float[] right=unit(cameraRight),up=unit(cameraUp);
    ArrayList<Live> live=new ArrayList<>();
    for(var timed:sessions)for(State state:timed.session.states)
      for(var p:state.clock.sample(timed.elapsedSeconds))live.add(new Live(state,p,timed.originSeconds+p.birthSeconds()));
    live.sort(Comparator.comparingDouble(Live::born).reversed());
    Map<String,Integer> used=new HashMap<>();Map<Layer,Prepared> prepared=new IdentityHashMap<>();
    ArrayList<Draw> draws=new ArrayList<>();
    for(var candidate:live) {
      Layer layer=candidate.state.layer;int count=used.getOrDefault(layer.name,0);
      if(count>=layer.max)continue;
      used.put(layer.name,count+1);
      Prepared geometry=prepared.get(layer);
      if(geometry==null){geometry=new Prepared(layer,motionMatrices,right,up);prepared.put(layer,geometry);}
      draws.add(geometry.draw(candidate.particle));
    }
    return draws;
  }

  /** All pose and billboard bases are layer-invariant within this sample. */
  private static final class Prepared {
    final Layer layer;final float[][] local,srcUV;final int[][] faces;
    final float[] motion,r,u;final float sx,sy;final boolean billboard;
    Prepared(Layer layer,float[][] matrices,float[] right,float[] up) {
      this.layer=layer;local=layer.nativeQuadOnly?QUAD:layer.nativeLocalVertices;
      srcUV=layer.nativeQuadOnly?QUAD_UV:layer.uv;faces=layer.nativeQuadOnly?QUAD_FACES:layer.faces;
      motion=matrices==null||layer.bone<0||layer.bone>=matrices.length?null:matrices[layer.bone];
      if(motion!=null)require(motion,12,"motion matrix");
      boolean cameraFacing=layer.billboardShader>.5f,axisLocked=!cameraFacing&&layer.billboardType==1;
      billboard=cameraFacing||axisLocked;
      float[] rr=right,uu=up;
      if(axisLocked) {
        int axis=Math.max(0,Math.min(2,layer.RotateAxis));
        float[] nativeAxis={layer.localToModel[0][axis],layer.localToModel[1][axis],layer.localToModel[2][axis]};
        uu=unit(direction(matrices,layer.bone,nativeAxis));
        rr=unit(cross(uu,cross(right,up)));if(length2(rr)<1e-10f)rr=right;
      }
      r=rr;u=uu;
      sx=billboard?axisScale(layer.localToModel,0,matrices,layer.bone):0;
      sy=billboard?axisScale(layer.localToModel,1,matrices,layer.bone):0;
    }
    Draw draw(CustomParticleSchedule.Particle p) {
      CustomParticleTransform.Sample transform=layer.transform.sample(p);
      BodyScratch.Mesh scratch=BodyScratch.mesh(local.length,srcUV.length);float[][] vertices=scratch.positions;
      float[] center=billboard?point(motion,affine(layer.localToModel,transform.localCenter())):null;
      float[] euler=transform.eulerRadians();
      double cz=Math.cos(euler[2]),sz=Math.sin(euler[2]),cx=Math.cos(euler[0]),ssx=Math.sin(euler[0]),cy=Math.cos(euler[1]),ssy=Math.sin(euler[1]);
      for(int index=0;index<local.length;index++) {
        // Keep the original float scale/rotation/translation rounding points while
        // evaluating each particle's trigonometry once, without intermediate vectors.
        float px=local[index][0]*transform.sizeXYZ()[0],py=local[index][1]*transform.sizeXYZ()[1],pz=local[index][2]*transform.sizeXYZ()[2];
        double x=cz*px-sz*py,y=sz*px+cz*py,z=pz;
        double y1=cx*y-ssx*z,z1=ssx*y+cx*z;
        float rx=(float)(cy*x+ssy*z1),ry=(float)y1,rz=(float)(-ssy*x+cy*z1);
        if(billboard) {
          BodyScratch.set(vertices,index,center[0]+r[0]*rx*sx+u[0]*ry*sy,
              center[1]+r[1]*rx*sx+u[1]*ry*sy,center[2]+r[2]*rx*sx+u[2]*ry*sy);
        } else {
          float[] localCenter=transform.localCenter();rx+=localCenter[0];ry+=localCenter[1];rz+=localCenter[2];
          float[][] a=layer.localToModel;
          float ax=a[0][0]*rx+a[0][1]*ry+a[0][2]*rz+a[0][3];
          float ay=a[1][0]*rx+a[1][1]*ry+a[1][2]*rz+a[1][3];
          float az=a[2][0]*rx+a[2][1]*ry+a[2][2]*rz+a[2][3];
          if(motion==null)BodyScratch.set(vertices,index,ax,ay,az);else BodyScratch.set(vertices,index,
              motion[0]*ax-motion[1]*ay+motion[2]*az+motion[3],
              -(motion[4]*ax-motion[5]*ay+motion[6]*az+motion[7]),
              motion[8]*ax-motion[9]*ay+motion[10]*az+motion[11]);
        }
      }
      float[] uvTransform=transform.uvScaleOffset();float[][] uv=scratch.uv;
      for(int i=0;i<uv.length;i++){uv[i][0]=srcUV[i][0]*uvTransform[0]+uvTransform[2];uv[i][1]=srcUV[i][1]*uvTransform[1]+uvTransform[3];}
      return new Draw(layer.name,layer.material,vertices,uv,faces,transform.rgba(),p.index(),p.cycle(),p.birthSeconds());
    }
  }

  /** Optional GL adapter; it is never called unless the integration route explicitly calls it. */
  public static void render(Session session,double elapsedSeconds,Matrix4f pose,OriginalMotion.Frame motion) {
    BodyScratch.begin();try{renderScoped(session,elapsedSeconds,pose,motion);}finally{BodyScratch.end();}
  }
  private static void renderScoped(Session session,double elapsedSeconds,Matrix4f pose,OriginalMotion.Frame motion) {
    if(session==null||pose==null||motion==null||FlameRenderType.hdrShader==null)return;
    Matrix4f inverse=RenderPoseGuard.inverse(pose);if(inverse==null)return;
    Vector3f r=inverse.transformDirection(new Vector3f(1,0,0)).normalize();
    Vector3f u=inverse.transformDirection(new Vector3f(0,1,0)).normalize();
    List<Draw> draws=sample(session,elapsedSeconds,motion.matrices(),new float[]{r.x,-r.y,r.z},new float[]{u.x,-u.y,u.z});
    draw(session.definition,draws,pose,elapsedSeconds);
  }
  public static void draw(Definition definition,List<Draw> draws,Matrix4f pose,double time) {
    if(FlameRenderType.hdrShader==null||!RenderPoseGuard.usable(pose)||draws.isEmpty())return;
    if(buffers==null)buffers=MultiBufferSource.immediate(new BufferBuilder(262144));
    var s=FlameRenderType.hdrShader;
    s.safeGetUniform("EffectTime").set((float)time);s.safeGetUniform("Tint").set(1f,1f,1f,1f);
    s.safeGetUniform("MaskST").set(1f,1f,0f,0f);s.safeGetUniform("NoiseST").set(1f,1f,0f,0f);
    s.safeGetUniform("Scroll").set(0f,0f,0f,0f);s.safeGetUniform("Params").set(0f,1f,1f,0f);
    s.safeGetUniform("Force").set(0f,0f);s.safeGetUniform("HeatTime").set(0f);
    s.safeGetUniform("FlowOffset").set(0f);s.safeGetUniform("Custom").set(0f,0f,0f,0f);
    RenderType activeType=null;VertexConsumer v=null;int activeMaterial=-1;
    VertexSorting sorting=RenderSystem.getVertexSorting();
    for(Draw draw:draws) {
      Material m=definition.materials[draw.material];
      if(draw.material!=activeMaterial) {
        // Preserve source particle/layer order, including translucent overlaps.
        // Flush before changing any uniform consumed by the previous batch.
        if(activeType!=null)buffers.endBatch(activeType);
        activeMaterial=draw.material;
        activeType=FlameRenderType.getHdr(m.textures,m.additive,m.filters,m.polygonOffset);
        s.safeGetUniform("MainST").set(m.mainST);
        s.safeGetUniform("WrapMain").set(m.wrapMain);s.safeGetUniform("WrapMask").set(m.wrapMask);s.safeGetUniform("WrapNoise").set(m.wrapNoise);
        v=buffers.getBuffer(activeType);
      }
      // Preserve the old CPU multiplication and float HDR values exactly; the
      // shader Tint is white and each primitive has its original constant tint.
      float[] rgba=draw.rgba;
      float[] tint={m.tint[0]*rgba[0],m.tint[1]*rgba[1],m.tint[2]*rgba[2],m.tint[3]*rgba[3]};
      // The old per-particle RenderType sorted quads inside each mesh. Retain
      // that exact engine sort locally, never sorting across particle bounds.
      for(int faceIndex:faceOrder(draw,pose,sorting))for(int j=0;j<4;j++) {
        int[] face=draw.faces[faceIndex];
        int index=face[Math.min(j,face.length-1)];float[] p=draw.vertices[index],uv=draw.uv[index];
        FlameRenderType.vertexHdr(v,pose,p[0]/16,-p[1]/16,p[2]/16,tint,uv[0],uv[1]);
      }
    }
    if(activeType!=null)buffers.endBatch(activeType);
  }

  /** Same v0/v2 midpoint and current engine sorter as BufferBuilder's per-mesh upload. */
  static int[] faceOrder(Draw draw,Matrix4f pose,VertexSorting sorting) {
    return BodyScratch.order(draw,pose,sorting);
  }

  private static final float[][] QUAD={{-.5f,-.5f,0},{.5f,-.5f,0},{.5f,.5f,0},{-.5f,.5f,0}};
  private static final float[][] QUAD_UV={{0,0},{1,0},{1,1},{0,1}};
  private static final int[][] QUAD_FACES={{0,1,2},{0,2,3}};
  private static String resolve(UnaryOperator<String> resolver,String source) {
    String value=resolver.apply(source);
    if(value==null||!value.contains(":")||value.contains(".."))throw new IllegalArgumentException("Texture resolver did not return a resource location: "+source);
    return value;
  }
  private static JsonObject mainSampler(Material m) {
    if(m.samplers!=null&&m.textureSources!=null)for(JsonElement value:m.textureSources) {
      JsonObject texture=value.getAsJsonObject();if(!texture.has("slot")||!"_MainTex".equals(texture.get("slot").getAsString()))continue;
      String key=texture.get("cab").getAsString()+":"+texture.get("id").getAsString();
      if(m.samplers.has(key)&&m.samplers.get(key).isJsonObject())return m.samplers.getAsJsonObject(key);
    }
    throw new IllegalArgumentException("Missing exact source _MainTex sampler: "+m.name);
  }
  private static int samplerValue(JsonObject sampler,String setting,String explicit,int axis) {
    if(sampler.has(explicit)&&!sampler.get(explicit).isJsonNull()) {
      JsonElement value=sampler.get(explicit);
      if(axis<0&&value.isJsonPrimitive())return value.getAsInt();
      if(axis>=0&&value.isJsonArray()&&value.getAsJsonArray().size()>axis&&!value.getAsJsonArray().get(axis).isJsonNull())return value.getAsJsonArray().get(axis).getAsInt();
    }
    if(sampler.has("settings")) {
      var matcher=Pattern.compile(Pattern.quote(setting)+"=(-?\\d+)").matcher(sampler.get("settings").getAsString());
      if(matcher.find())return Integer.parseInt(matcher.group(1));
    }
    throw new IllegalArgumentException("Missing source sampler setting "+setting);
  }
  private static void matrix(float[][] m) {if(m==null||m.length!=4)throw new IllegalArgumentException("Missing localToModel");for(float[] row:m)require(row,4,"matrix row");}
  private static void require(float[] v,int n,String label){if(v==null||v.length<n)throw new IllegalArgumentException("Missing "+label);for(float x:v)if(!Float.isFinite(x))throw new IllegalArgumentException("Nonfinite "+label);}
  private static float[] affine(float[][] m,float[] p){float[] v=new float[3];for(int k=0;k<3;k++)v[k]=m[k][0]*p[0]+m[k][1]*p[1]+m[k][2]*p[2]+m[k][3];return v;}
  private static float[] point(float[][] matrices,int bone,float[] p) {
    if(matrices==null||bone<0||bone>=matrices.length)return p.clone();float[] m=matrices[bone];require(m,12,"motion matrix");
    return point(m,p);
  }
  private static float[] point(float[] m,float[] p) {
    if(m==null)return p.clone();
    return new float[]{m[0]*p[0]-m[1]*p[1]+m[2]*p[2]+m[3],-(m[4]*p[0]-m[5]*p[1]+m[6]*p[2]+m[7]),m[8]*p[0]-m[9]*p[1]+m[10]*p[2]+m[11]};
  }
  private static float[] direction(float[][] matrices,int bone,float[] v){
    if(matrices==null||bone<0||bone>=matrices.length)return v.clone();float[] m=matrices[bone];require(m,12,"motion matrix");
    // Apply the linear part directly; subtracting transformed positions loses precision when
    // a large bone translation is combined with the very small fitted source basis entries.
    return new float[]{m[0]*v[0]-m[1]*v[1]+m[2]*v[2],-(m[4]*v[0]-m[5]*v[1]+m[6]*v[2]),m[8]*v[0]-m[9]*v[1]+m[10]*v[2]};
  }
  private static float axisScale(float[][] m,int axis,float[][] motion,int bone){return (float)Math.sqrt(length2(direction(motion,bone,new float[]{m[0][axis],m[1][axis],m[2][axis]})));}
  private static float[] unit(float[] v){require(v,3,"camera basis");float[] out=v.clone();double n=Math.sqrt(length2(out));if(n>1e-12)for(int k=0;k<3;k++)out[k]/=n;return out;}
  private static float length2(float[] v){return v[0]*v[0]+v[1]*v[1]+v[2]*v[2];}
  private static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
}
