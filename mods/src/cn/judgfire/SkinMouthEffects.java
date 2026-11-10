package cn.judgfire;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Own-skin mounted mouth effects. No gameplay, input listener or world line rendering. */
public final class SkinMouthEffects {
  private SkinMouthEffects() {}
  private static final String[] NAMES={"","qinglong","frozen","lightning"};
  /**
   * Host inference, not an authored custom-particle duration: Shot has no firing=false packet.
   * One second bridges the current gun's 90rpm (2/3 second) interval and automatic Shot events.
   * A real source-style firing=false observation may stop immediately through setFiring.
   */
  public static final long INFERRED_RELEASE_GRACE_NANOS=1_000_000_000L;
  private static final Map<Integer,SkinBodyEffects.Definition> DEFINITIONS=new HashMap<>();
  private record Key(int owner,int skin) {}
  private static final class Active {
    final long born;final SkinBodyEffects.Session session;long lastFire;
    Active(int owner,int skin,long now,SkinBodyEffects.Definition definition) {
      born=lastFire=now;
      session=SkinBodyEffects.newSession(definition,((long)owner<<32)^now^(skin*0x9e3779b97f4a7c15L));
    }
  }
  private static final Map<Key,Active> ACTIVE=new HashMap<>();

  public static void reload(ResourceManager resources) {
    clear();DEFINITIONS.clear();
    for(int skin=1;skin<=3;skin++) {
      try(var input=resources.open(new ResourceLocation("judgfire","fx/skins/muzzle/"+NAMES[skin]+".json"));
          var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
        var definition=SkinBodyEffects.loadDetached(skin,reader,path->path);
        install(skin,definition);
        LogUtils.getLogger().info("Native skin {}: {} mounted mouth layers",NAMES[skin],definition.layerCount());
      }catch(Exception error){LogUtils.getLogger().error("Cannot load native mounted mouth "+NAMES[skin],error);}
    }
  }
  /** Also usable by an offline harness; does not replace the continuous body definition map. */
  public static void install(int skin,SkinBodyEffects.Definition definition) {
    if(skin<1||skin>3||definition==null||definition.skinId()!=skin)throw new IllegalArgumentException("Mouth skin mismatch");
    DEFINITIONS.put(skin,definition);ACTIVE.keySet().removeIf(k->k.skin==skin);
  }
  /** Main source cone axis before the current animation delta; the cone points along local +Y. */
  public static float[] modelSourceForward(int skin) {
    var definition=DEFINITIONS.get(skin);if(definition==null)return null;
    SkinBodyEffects.Layer reference=null;
    for(var layers:new SkinBodyEffects.Layer[][]{definition.groups,definition.emitters})if(layers!=null)
      for(var layer:layers) {
        if(!layer.enabled||!layer.source_active||layer.sourceShapeType()!=1)continue;
        if(reference==null||layer.name.compareTo(reference.name)<0)reference=layer;
      }
    if(reference==null||reference.bone!=6||reference.localToModel==null)return null;
    float[] axis=new float[]{reference.localToModel[0][1],reference.localToModel[1][1],reference.localToModel[2][1]};
    return RenderPoseGuard.direction(axis)?axis:null;
  }
  /** Both local GunFireEvent and confirmed automatic/remote Shot may refresh this same state. */
  public static void fired(int owner,int skin,long now) {
    var definition=DEFINITIONS.get(skin);if(definition==null)return;
    ACTIVE.entrySet().removeIf(e->now-e.getValue().lastFire>30_000_000_000L||e.getKey().owner==owner&&e.getKey().skin!=skin);
    Key key=new Key(owner,skin);Active active=ACTIVE.get(key);
    if(active==null||now<active.born||now-active.lastFire>INFERRED_RELEASE_GRACE_NANOS) {
      active=new Active(owner,skin,now,definition);ACTIVE.put(key,active);
    }
    active.lastFire=Math.max(active.lastFire,now);
  }
  /** If the host knows the actual firing state, false hides the mouth without a guessed tail. */
  public static void setFiring(int owner,int skin,boolean firing,long now) {
    if(firing)fired(owner,skin,now);else ACTIVE.remove(new Key(owner,skin));
  }
  public static void render(int owner,int skin,Matrix4f pose,OriginalMotion.Frame motion,long now) {
    Active active=active(owner,skin,now);if(active==null)return;
    SkinBodyEffects.render(active.session,(now-active.born)/1e9,pose,motion);
  }
  /** Same actual consumer without GL, for own-skin geometry/UV/color/activation inspection. */
  public static List<SkinBodyEffects.Draw> sample(int owner,int skin,long now,float[][] motion,
                                                float[] cameraRight,float[] cameraUp) {
    Active active=active(owner,skin,now);return active==null?List.of():
        SkinBodyEffects.sample(active.session,(now-active.born)/1e9,motion,cameraRight,cameraUp);
  }
  private static Active active(int owner,int skin,long now) {
    Key key=new Key(owner,skin);Active active=ACTIVE.get(key);
    if(active!=null&&(now<active.born||now-active.lastFire>INFERRED_RELEASE_GRACE_NANOS)) {
      ACTIVE.remove(key);return null;
    }
    return active;
  }
  /** Session-only reset for world changes. Definitions remain loaded until the next reload. */
  public static void clear(){ACTIVE.clear();}
}
