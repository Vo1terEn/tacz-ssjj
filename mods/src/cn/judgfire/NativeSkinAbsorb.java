package cn.judgfire;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Each skin's own short hit absorb, attached to the current gun's captured pose.
 * Native qinglong/frozen use their own source meshes/vertex RGBA; lightning uses
 * the same explicitly inferred custom consumer as its source body/mouth effects.
 * No world-point placement or original-skin E015 material is used here.
 */
public final class NativeSkinAbsorb {
  private NativeSkinAbsorb() {}
  private static final Gson GSON=new Gson();
  private static final String[] NAMES={"","qinglong","frozen","lightning"};
  private static final Map<Integer,DefaultFlameEffects.Definition> NATIVE=new HashMap<>();
  private static final Map<Integer,SkinBodyEffects.Definition> CUSTOM=new HashMap<>();
  private static final Map<Integer,Double> WINDOWS=new HashMap<>();
  private record Key(int owner,int skin) {}
  private record Activation(long born,SkinBodyEffects.Session custom) {}
  private static final Map<Key,ArrayDeque<Activation>> HITS=new HashMap<>();

  /** Event-state clear keeps already loaded definitions available after a level change. */
  public static void clear(){HITS.clear();NativeSkinFlow.clear();}
  public static void reload(ResourceManager resources) {
    clear();NATIVE.clear();CUSTOM.clear();WINDOWS.clear();NativeSkinFlow.reset();
    for(int skin=1;skin<=3;skin++) {
      try(var input=resources.open(new ResourceLocation("judgfire","fx/skins/"+NAMES[skin]+"_absorb.json"));
          var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
        install(skin,reader);
        LogUtils.getLogger().info("Native skin {}: own gun-space absorb loaded",NAMES[skin]);
      }catch(Exception failure){LogUtils.getLogger().error("Cannot load gun-space absorb for "+NAMES[skin],failure);}
    }
  }

  /** Pure loading entry point for offline source/consumer validation. */
  public static void install(int skin,Reader reader) {
    if(skin<1||skin>3)throw new IllegalArgumentException("Expected skin1/2/3");
    JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
    if(!NAMES[skin].equals(root.get("skin").getAsString()))throw new IllegalArgumentException("Absorb source skin mismatch");
    double window=root.get("max_trigger_window_seconds").getAsDouble();
    if(!Double.isFinite(window)||window<=0||window>60)throw new IllegalArgumentException("Invalid absorb window");
    String engine=root.get("engine").getAsString();
    if("native".equals(engine)) {
      var definition=GSON.fromJson(root,DefaultFlameEffects.Definition.class);
      if(definition.groups==null||definition.emitters==null||definition.materials==null)throw new IllegalArgumentException("Incomplete native absorb");
      for(var group:definition.groups)if(!group.hitOnly)throw new IllegalArgumentException("Absorb mesh must be hit-only");
      for(var emitter:definition.emitters)if(!emitter.hitOnly||emitter.mouthOnly)throw new IllegalArgumentException("Absorb emitter must be hit-only");
      var flow=new DefaultFlameEffects.Definition();flow.materials=definition.materials;
      var ribbons=new ArrayList<DefaultFlameEffects.Group>();var hits=new ArrayList<DefaultFlameEffects.Group>();
      for(var group:definition.groups) {
        boolean ribbon=skin==1&&"qinglong_absorb/E148/chouqu (2)/-839988374468700093".equals(group.name)
            ||skin==2&&"frozen_absorb/E240/chouqu/-2417882664037530998".equals(group.name);
        if(ribbon){group.hitOnly=false;group.bodyLoop=true;group.loop=true;ribbons.add(group);}else hits.add(group);
      }
      if(ribbons.size()!=1)throw new IllegalArgumentException("Expected one own-skin long flow mesh");
      flow.groups=ribbons.toArray(DefaultFlameEffects.Group[]::new);flow.emitters=new DefaultFlameEffects.Emitter[0];
      definition.groups=hits.toArray(DefaultFlameEffects.Group[]::new);NativeSkinFlow.installNative(skin,flow);
      NATIVE.put(skin,definition);CUSTOM.remove(skin);
    }else if("custom".equals(engine)) {
      var flow=root.deepCopy();var ribbons=new com.google.gson.JsonArray();var hits=new com.google.gson.JsonArray();
      for(var value:root.getAsJsonArray("groups")) {
        var group=value.getAsJsonObject();
        if(skin==3&&"lightning_absorb/shuzi".equals(group.get("name").getAsString())) {
          var ribbon=group.deepCopy();ribbon.getAsJsonObject("customParticleRaw").addProperty("loop",1);ribbons.add(ribbon);
        }else hits.add(group);
      }
      if(ribbons.size()!=1)throw new IllegalArgumentException("Expected one own-skin long custom flow mesh");
      flow.add("groups",ribbons);flow.add("emitters",new com.google.gson.JsonArray());root.add("groups",hits);
      NativeSkinFlow.installCustom(skin,SkinBodyEffects.loadDetached(skin,new StringReader(flow.toString()),path->path));
      var definition=SkinBodyEffects.loadDetached(skin,new StringReader(root.toString()),path->path);
      CUSTOM.put(skin,definition);NATIVE.remove(skin);
    }else throw new IllegalArgumentException("Unsupported absorb engine "+engine);
    WINDOWS.put(skin,window);HITS.keySet().removeIf(key->key.skin==skin);
  }

  public static void hit(int owner,int skin,long now) {
    if(owner<0||!WINDOWS.containsKey(skin))return;
    prune(now);
    SkinBodyEffects.Definition definition=CUSTOM.get(skin);
    SkinBodyEffects.Session session=definition==null?null:SkinBodyEffects.newSession(definition,
        NativeEmitterClock.mix(now^((long)owner<<32)^skin*9749L));
    var history=HITS.computeIfAbsent(new Key(owner,skin),key->new ArrayDeque<>());
    history.addLast(new Activation(now,session));while(history.size()>24)history.removeFirst();
  }

  private static void prune(long now) {
    HITS.entrySet().removeIf(entry->{
      double window=WINDOWS.getOrDefault(entry.getKey().skin,0.);
      entry.getValue().removeIf(hit->(now-hit.born)/1e9>=window);
      return entry.getValue().isEmpty();
    });
  }
  static List<Long> activeHitTimes(int owner,int skin,long now) {
    prune(now);var history=HITS.get(new Key(owner,skin));
    if(history==null)return List.of();
    ArrayList<Long> births=new ArrayList<>();for(var hit:history)if(now>=hit.born)births.add(hit.born);
    return births;
  }

  /** Pure actual custom sampling for offline projection; no GL/game method called. */
  public static List<SkinBodyEffects.Draw> sampleCustom(int owner,int skin,long now,float[][] motion,
                                                     float[] right,float[] up) {
    prune(now);var history=HITS.get(new Key(owner,skin));if(history==null)return List.of();
    var definition=CUSTOM.get(skin);if(definition==null)return List.of();
    ArrayList<SkinBodyEffects.TimedSession> sessions=new ArrayList<>(history.size());
    for(var hit:history)if(hit.custom!=null&&now>=hit.born)
      sessions.add(new SkinBodyEffects.TimedSession(hit.custom,(now-hit.born)/1e9,hit.born/1e9));
    return SkinBodyEffects.sampleNewest(sessions,motion,right,up);
  }

  public static void render(int owner,int skin,Matrix4f pose,OriginalMotion.Frame motion,long now) {
    if(pose==null||motion==null||!WINDOWS.containsKey(skin))return;
    NativeSkinFlow.render(owner,skin,pose,motion,now);
    var nativeDefinition=NATIVE.get(skin);
    if(nativeDefinition!=null) {
      List<Long> births=activeHitTimes(owner,skin,now);if(births.isEmpty())return;
      DefaultFlameEffects.renderDefinition(nativeDefinition,owner,pose,motion,now,births);
      return;
    }
    var definition=CUSTOM.get(skin);if(definition==null)return;
    Matrix4f inverse=RenderPoseGuard.inverse(pose);if(inverse==null)return;
    Vector3f right=inverse.transformDirection(new Vector3f(1,0,0)).normalize();
    Vector3f up=inverse.transformDirection(new Vector3f(0,1,0)).normalize();
    var draws=sampleCustom(owner,skin,now,motion.matrices(),
        new float[]{right.x,-right.y,right.z},new float[]{up.x,-up.y,up.z});
    SkinBodyEffects.draw(definition,draws,pose,now/1e9);
  }
}
