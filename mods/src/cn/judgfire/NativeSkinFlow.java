package cn.judgfire;

import java.util.*;
import org.joml.Matrix4f;

/** Own-skin long mesh ribbons share the default skin's idle-loop policy, independently of attacks. */
public final class NativeSkinFlow {
  private NativeSkinFlow() {}
  private static final long HIDDEN_NANOS=300_000_000L,STALE_NANOS=30_000_000_000L;
  private static final Map<Integer,DefaultFlameEffects.Definition> NATIVE=new HashMap<>();
  private static final Map<Integer,SkinBodyEffects.Definition> CUSTOM=new HashMap<>();
  private record Key(int owner,int skin) {}
  private static final class Clock {
    final long born;long seen;final int renderOwner;final SkinBodyEffects.Session session;
    Clock(int owner,int skin,long now) {
      born=seen=now;
      // DefaultFlameEffects owns native emitter cursors indexed by owner. Reserve a fresh
      // negative owner for this skin activation; live entity IDs and unknown(-1) never alias it.
      // Do not recycle IDs on clear: any old renderer cursor expires via its existing cleanup.
      if(nextRenderOwner==Integer.MIN_VALUE)throw new IllegalStateException("Flow activation ID exhausted");
      renderOwner=nextRenderOwner--;
      var definition=CUSTOM.get(skin);session=definition==null?null:
          SkinBodyEffects.newSession(definition,((long)owner<<32)^skin*9749L);
    }
  }
  private static int nextRenderOwner=-2;
  private static final Map<Key,Clock> CLOCKS=new HashMap<>();

  public static void clear(){CLOCKS.clear();}
  public static void reset(){clear();NATIVE.clear();CUSTOM.clear();}
  static void installNative(int skin,DefaultFlameEffects.Definition definition) {
    NATIVE.put(skin,definition);CUSTOM.remove(skin);CLOCKS.keySet().removeIf(k->k.skin==skin);
  }
  static void installCustom(int skin,SkinBodyEffects.Definition definition) {
    CUSTOM.put(skin,definition);NATIVE.remove(skin);CLOCKS.keySet().removeIf(k->k.skin==skin);
  }
  private static Clock clock(int owner,int skin,long now) {
    CLOCKS.values().removeIf(c->now<c.born||now-c.seen>STALE_NANOS);
    Key key=new Key(owner,skin);Clock clock=CLOCKS.get(key);
    if(clock==null||now<clock.born||now-clock.seen>HIDDEN_NANOS){clock=new Clock(owner,skin,now);CLOCKS.put(key,clock);}
    clock.seen=now;return clock;
  }
  public static void render(int owner,int skin,Matrix4f pose,OriginalMotion.Frame motion,long now) {
    if(pose==null||motion==null||!RenderPoseGuard.usable(pose))return;
    if(!NATIVE.containsKey(skin)&&!CUSTOM.containsKey(skin))return;
    Clock clock=clock(owner,skin,now);var nativeDefinition=NATIVE.get(skin);
    if(nativeDefinition!=null)DefaultFlameEffects.renderDefinition(nativeDefinition,clock.renderOwner,pose,motion,now,null);
    else SkinBodyEffects.render(clock.session,(now-clock.born)/1e9,pose,motion);
  }
}
