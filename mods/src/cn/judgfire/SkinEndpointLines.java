package cn.judgfire;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Own 119/124/123 lineLink geometry in gun space, bounded by the authoritative endpoint. */
public final class SkinEndpointLines {
  private SkinEndpointLines() {}
  private static final Gson GSON=new Gson();
  private static final String[] NAMES={"","qinglong","frozen","lightning"};
  public static final class Placement {
    String mode;float modelExtentAtSourcePeak,transverseRadiusDistanceCap;
    boolean positiveLocalYHalfAndV;float[] nativeOriginWeights,modelSourceForward,modelRestMuzzle;
  }
  private static final class Meta {String name;int bone;Placement linePlacement;}
  private record Definition(SkinBodyEffects.Definition materialGeometry,Map<String,Meta> layers) {}
  private record Key(int owner,int skin,long born) {}
  private static final Map<Integer,Definition> DEFINITIONS=new HashMap<>();
  private static final Map<Key,SkinBodyEffects.Session> SHOTS=new HashMap<>();
  public static void reload(ResourceManager resources) {
    clear();DEFINITIONS.clear();
    for(int skin=1;skin<=3;skin++)try(var input=resources.open(new ResourceLocation("judgfire","fx/skins/line/"+NAMES[skin]+".json"));
        var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
      load(skin,reader,path->path);
    }catch(Exception error){LogUtils.getLogger().error("Cannot load own skin endpoint line "+NAMES[skin],error);}
  }
  /** Detached loading preserves each skin's already registered continuous body definition. */
  public static void load(int skin,Reader reader,java.util.function.UnaryOperator<String> resolver) {
    JsonObject raw=GSON.fromJson(reader,JsonObject.class);
    var geometry=SkinBodyEffects.loadDetached(skin,new StringReader(raw.toString()),resolver);
    Map<String,Meta> layers=new HashMap<>();
    for(String kind:new String[]{"groups","emitters"})for(var value:raw.getAsJsonArray(kind)) {
      Meta meta=GSON.fromJson(value,Meta.class);if(meta.linePlacement==null)throw new IllegalArgumentException("Missing source line placement");
      layers.put(meta.name,meta);
    }
    DEFINITIONS.put(skin,new Definition(geometry,Map.copyOf(layers)));
    SHOTS.keySet().removeIf(key->key.skin==skin);
  }
  /** born/now use System.nanoTime units; caller passes the matching trace's true birth. */
  public static void render(int owner,int skin,Matrix4f pose,OriginalMotion.Frame motion,
                            Vec3 confirmedEndpoint,long born,long now) {
    if(pose==null||motion==null||confirmedEndpoint==null)return;
    Matrix4f inverse=RenderPoseGuard.inverse(pose);if(inverse==null)return;
    var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
    Vec3 relative=confirmedEndpoint.subtract(camera.getPosition());
    Vector3f target=new Vector3f((float)relative.x,(float)relative.y,(float)relative.z).rotate(new Quaternionf(camera.rotation()).conjugate());
    target.x=-target.x;target.z=-target.z;EndpointProjection.adjustViewEndpoint(pose,target);inverse.transformPosition(target).mul(16);target.y=-target.y;
    Vector3f right=inverse.transformDirection(new Vector3f(1,0,0)).normalize(),up=inverse.transformDirection(new Vector3f(0,1,0)).normalize();
    var draws=sample(owner,skin,born,now,new float[]{target.x,target.y,target.z},motion.matrices(),new float[]{right.x,-right.y,right.z},new float[]{up.x,-up.y,up.z});
    Definition definition=DEFINITIONS.get(skin);if(definition!=null)SkinBodyEffects.draw(definition.materialGeometry,draws,pose,(now-born)/1e9);
  }
  /** Mouth and line must share the current muzzle-to-hit axis while the camera moves. */
  public static OriginalMotion.Frame aimedMotion(int skin,Matrix4f pose,OriginalMotion.Frame motion,Vec3 endpoint) {
    Definition definition=DEFINITIONS.get(skin);if(definition==null||motion==null||endpoint==null)return motion;
    Matrix4f inverse=RenderPoseGuard.inverse(pose);if(inverse==null)return motion;
    var camera=Minecraft.getInstance().gameRenderer.getMainCamera();Vec3 relative=endpoint.subtract(camera.getPosition());
    Vector3f target=new Vector3f((float)relative.x,(float)relative.y,(float)relative.z).rotate(new Quaternionf(camera.rotation()).conjugate());
    target.x=-target.x;target.z=-target.z;EndpointProjection.adjustViewEndpoint(pose,target);inverse.transformPosition(target).mul(16);target.y=-target.y;
    // The link supplies the true muzzle pivot; the mouth has its own source cone axis.
    // A line quad's plane axis is not interchangeable with a mouth emitter's +Y axis.
    Meta reference=definition.layers.values().stream().filter(m->!"glow".equals(m.linePlacement.mode)).sorted(Comparator.comparing(m->m.name)).findFirst().orElse(null);
    if(reference==null)return motion;Placement p=reference.linePlacement;
    float[] forward=SkinMouthEffects.modelSourceForward(skin);if(forward==null)return motion;
    float[][] matrices=EndpointBeamAim.align(motion.matrices(),reference.bone,p.modelRestMuzzle,forward,new float[]{target.x,target.y,target.z});
    return matrices==motion.matrices()?motion:new OriginalMotion.Frame(motion.rig(),matrices);
  }
  /** Actual source custom consumer plus endpoint bridge; no Minecraft/GL needed. */
  public static List<SkinBodyEffects.Draw> sample(int owner,int skin,long born,long now,float[] endpointModel,
                                                  float[][] motion,float[] right,float[] up) {
    Definition definition=DEFINITIONS.get(skin);if(definition==null||now<born||now-born>1_000_000_000L)return List.of();
    // There is one active line per shooter. Old shot glows must not stack on today's muzzle.
    SHOTS.keySet().removeIf(key->now-key.born>1_000_000_000L||key.owner==owner&&(key.skin!=skin||key.born!=born));
    Key key=new Key(owner,skin,born);var session=SHOTS.computeIfAbsent(key,k->SkinBodyEffects.newSession(definition.materialGeometry,((long)owner<<32)^born^(skin*9749L)));
    var nativeDraws=SkinBodyEffects.sample(session,(now-born)/1e9,motion,right,up);ArrayList<SkinBodyEffects.Draw> out=new ArrayList<>();
    for(var draw:nativeDraws) {
      Meta meta=definition.layers.get(draw.layerName());Placement p=meta.linePlacement;
      if("glow".equals(p.mode)){out.add(draw);continue;}
      float[][] vertices=draw.vertices(),uv=draw.uv();float[] weights=p.nativeOriginWeights;
      // The 4x4 fire sheets carry alpha on positive local Y. Crop geometry and UV together;
      // each skin retains its own atlas and authored crossing plane, never an original recolor.
      if(p.positiveLocalYHalfAndV) {
        vertices=copy(vertices);uv=copy(uv);vertices[0]=mid(vertices[0],vertices[3]);vertices[1]=mid(vertices[1],vertices[2]);
        uv[0]=mid(uv[0],uv[3]);uv[1]=mid(uv[1],uv[2]);weights=new float[]{.5f,.5f,0,0};
      }
      float[] forward=direction(motion,meta.bone,p.modelSourceForward);
      float scale=(float)Math.sqrt(forward[0]*forward[0]+forward[1]*forward[1]+forward[2]*forward[2]);
      var placed=EndpointLinePlacement.place(vertices,weights,forward,endpointModel,p.modelExtentAtSourcePeak*scale,p.transverseRadiusDistanceCap);
      if(placed!=null)out.add(new SkinBodyEffects.Draw(draw.layerName(),draw.material(),placed.vertices(),uv,draw.faces(),draw.rgba(),draw.particleIndex(),draw.cycle(),draw.birthSeconds()));
    }
    return out;
  }
  public static void clear(){SHOTS.clear();}
  private static float[][] copy(float[][] a){float[][] b=new float[a.length][];for(int i=0;i<a.length;i++)b[i]=a[i].clone();return b;}
  private static float[] mid(float[] a,float[] b){float[] c=new float[a.length];for(int i=0;i<c.length;i++)c[i]=(a[i]+b[i])*.5f;return c;}
  private static float[] direction(float[][] motion,int bone,float[] v){
    if(motion==null||bone<0||bone>=motion.length)return v.clone();float[] m=motion[bone];
    return new float[]{m[0]*v[0]-m[1]*v[1]+m[2]*v[2],-(m[4]*v[0]-m[5]*v[1]+m[6]*v[2]),m[8]*v[0]-m[9]*v[1]+m[10]*v[2]};
  }
}
