package cn.judgfire;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Native constant-rate Unity PS births. Each birth keeps its own sampled lifetime.
 * Source random sequence is unavailable; stable per-birth channels are a reconstruction.
 * This deliberately does not interpret SSJJ custom startTime/playTime fields.
 */
public final class NativeEmitterClock {
  public static class Spec {
    public int max;
    public float life=1,rate,duration=1,delay;
    public float[] lifeRange;
    public boolean loop=true,prewarm;
  }
  public record Birth(long ordinal,double seconds,float life,long seed) {
    public float random(int channel){return unit(seed+0x9e3779b97f4a7c15L*(channel+1L));}
  }
  private final Spec spec;
  private final long seed;
  private final double first;
  private final ArrayDeque<Birth> alive=new ArrayDeque<>();
  private long ordinal;
  private double last=Double.NEGATIVE_INFINITY;
  public NativeEmitterClock(Spec spec,long seed) {
    this.spec=spec;this.seed=seed;
    if(spec.max<0||spec.max>65536||!Float.isFinite(spec.rate)||spec.rate<0)throw new IllegalArgumentException("Invalid native emission");
    float low=spec.lifeRange==null?spec.life:spec.lifeRange[0],high=spec.lifeRange==null?spec.life:spec.lifeRange[1];
    if(!Float.isFinite(low)||!Float.isFinite(high)||low<=0||high<=0)throw new IllegalArgumentException("Invalid native lifetime");
    first=(spec.prewarm?-Math.max(0,spec.duration):0)+spec.delay;
  }
  public List<Birth> sample(double seconds) {
    if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid native clock");
    if(seconds<last){alive.clear();ordinal=0;}last=seconds;
    if(spec.rate>0&&spec.max>0) {
      for(double born=first+ordinal/(double)spec.rate;born<=seconds;born=first+ordinal/(double)spec.rate) {
        if(!spec.loop&&born>=spec.delay+spec.duration)break;
        final double time=born;alive.removeIf(p->time-p.seconds()>=p.life());
        long key=mix(seed+ordinal*0x9e3779b97f4a7c15L);
        if(alive.size()<spec.max) {
          float life=spec.lifeRange==null?spec.life:spec.lifeRange[0]+(spec.lifeRange[1]-spec.lifeRange[0])*unit(key);
          alive.addLast(new Birth(ordinal,born,life,key));
        }
        ordinal++;
      }
    }
    alive.removeIf(p->seconds-p.seconds()>=p.life());return new ArrayList<>(alive);
  }
  public static long mix(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
  public static float unit(long v){return (float)((mix(v)>>>40)*0x1.0p-24);}
}
