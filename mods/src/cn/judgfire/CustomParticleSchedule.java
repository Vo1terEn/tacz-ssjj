package cn.judgfire;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deterministic clock for the supplied SSJJ custom-particle definitions, not Unity PS rate.
 *
 * <p>Deserialize an exported layer into {@link Spec} with Gson, construct once per emitter/
 * owner/activation, then call {@link #sample(double)} with seconds since activation. No Gson,
 * Minecraft, GL or wall-clock dependency. Instances are render-thread confined.
 *
 * <p>The original custom engine method bodies were not supplied. The explicit candidate policy
 * is: startTime curves distribute initial slot births at slot/max; loop renews each slot at its
 * death; RandomOnBorn chooses whether random initial properties refresh on renewal; prewarm is
 * a seconds offset; positive playTime stops new births, leaving existing particles to die.
 * These are inferences, not an assertion that recovered source code proves those semantics.
 * Serialized curves are evaluated piecewise-linearly with right-continuous duplicate keys.
 * No compatibility rate, forced uniform rate or hidden idle phase is ever consulted.
 */
public final class CustomParticleSchedule {
  public static final String INFERENCE_NOTE =
      "Candidate SSJJ policy: slot/max start-curve input, per-slot renewal, numeric prewarm "
          + "seconds, playTime birth cutoff, RandomOnBorn refreshes on renewal, linear curves. "
          + "Original custom engine implementation was not supplied; validate against video.";

  /** Gson may read this directly from one body_runtime_candidate group/emitter object. */
  public static class Spec {
    public int max;
    public float life = 1;
    public float[] lifeRange;
    /** Spacing between births; particle age/lifetime and movement remain real-time. */
    public float birthIntervalScale = 1;
    public Schedule customSchedule;
    public float[][] sourceStartTimeSamplesRange;
    /** Alias for callers whose preprocessor omits the source prefix. */
    public float[][] startTimeSamplesRange;
    public Raw customParticleRaw;
  }

  /** Field names/types follow the exported source customSchedule, including original casing. */
  public static class Schedule {
    public OneD startTime;
    public float playTime, nextTime, prewarm;
    public int loop = 1, RandomOnBorn, isPlay = 1, isWorldParticle;
  }

  /** Only the scheduler-owned raw field is read; Gson ignores the other full raw effectors. */
  public static class Raw { public OneD lifeTime; }
  public static class Anchor { public float _time, _value; }
  public static class OneD {
    public int oneDType;
    public float _value, _maxCurveAnchorValue = 1, _min, _max;
    public Anchor[] _anchors;
    public String _curveType;
  }

  /** birthSeconds may be negative for a prewarmed particle; age is always in [0,lifetime). */
  public record Particle(int index, long cycle, double birthSeconds, double ageSeconds,
                         double lifetime, double normalizedAge, double stableRandom,
                         long randomSeed) {
    /** Independent deterministic channel, stable throughout this birth. */
    public double random(int channel) { return unit(mix(randomSeed + GOLDEN * (channel + 1L))); }
  }

  private static final long GOLDEN = 0x9e3779b97f4a7c15L;
  private static final int START_RANDOM = 1, LIFE_RANDOM = 2;
  private final int count;
  private final long seed;
  private final boolean loop, refreshRandom, playing, fixedLifetime;
  private final double prewarm, playTime, birthIntervalScale;
  private final Value lifetime;
  private final Slot[] slots;
  private double lastClock = Double.NEGATIVE_INFINITY;

  private static final class Slot {
    final int index;
    final double firstBirth, curveInput;
    long cycle;
    double birth, life;
    Slot(int index, double firstBirth, double curveInput) {
      this.index=index; this.firstBirth=firstBirth; this.curveInput=curveInput;
    }
  }

  public CustomParticleSchedule(Spec spec, long seed) {
    if(spec==null) throw new IllegalArgumentException("Missing custom particle spec");
    if(spec.max<0 || spec.max>65536) throw new IllegalArgumentException("Invalid ParticleNumber");
    Schedule source=spec.customSchedule==null ? new Schedule() : spec.customSchedule;
    count=spec.max; this.seed=seed; loop=source.loop!=0;
    refreshRandom=source.RandomOnBorn!=0; playing=source.isPlay!=0;
    prewarm=nonnegative(source.prewarm,"prewarm");
    playTime=nonnegative(source.playTime,"playTime");
    birthIntervalScale=nonnegative(spec.birthIntervalScale,"birthIntervalScale");
    if(birthIntervalScale<1) throw new IllegalArgumentException("birthIntervalScale must be at least one");
    OneD rawLife=spec.customParticleRaw==null ? null : spec.customParticleRaw.lifeTime;
    lifetime=rawLife!=null ? new Value(rawLife)
        : spec.lifeRange!=null && spec.lifeRange.length>=2
            ? new Value(spec.lifeRange[0],spec.lifeRange[1]) : new Value(spec.life,spec.life);
    lifetime.requirePositive("lifeTime");
    fixedLifetime=!refreshRandom || !lifetime.randomRange;
    Value start=source.startTime==null ? null : new Value(source.startTime);
    float[][] startSamples=copy(spec.sourceStartTimeSamplesRange!=null
        ? spec.sourceStartTimeSamplesRange : spec.startTimeSamplesRange);
    slots=new Slot[count];
    for(int i=0;i<count;i++) {
      double x=i/(double)count;
      double r=random(i,0,START_RANDOM);
      double initial=start!=null ? start.at(x,r)
          : sampleRange(startSamples,x,0,r,0);
      if(!Double.isFinite(initial)) throw new IllegalArgumentException("Non-finite startTime");
      // Negative authored starts are legitimate: they describe a particle already in progress.
      Slot s=new Slot(i,initial*birthIntervalScale,x); reset(s); slots[i]=s;
    }
    // nextTime is retained by the Gson DTO, but not treated as an authored delay. It is zero in
    // all 42 supplied layers and appears to be mutable source engine cursor state.
  }

  public int capacity() { return count; }

  /** Return live slots. Repeated calls and backward seeks are deterministic. */
  public List<Particle> sample(double elapsedSeconds) {
    ArrayList<Particle> result=new ArrayList<>(count);
    forEach(elapsedSeconds,result::add);
    return result;
  }

  /** Same clock without the temporary List; Particle records are independent snapshots. */
  public void forEach(double elapsedSeconds, Consumer<Particle> consumer) {
    if(!Double.isFinite(elapsedSeconds)) throw new IllegalArgumentException("Non-finite clock");
    if(consumer==null) throw new IllegalArgumentException("Missing particle consumer");
    if(!playing || elapsedSeconds<0) return;
    double clock=elapsedSeconds+prewarm;
    if(!Double.isFinite(clock)) throw new IllegalArgumentException("Clock overflow");
    if(clock<lastClock) for(Slot s:slots) reset(s);
    lastClock=clock;
    for(Slot s:slots) {
      if(clock<s.firstBirth) continue;
      if(loop) {
        // Constant (or RandomOnBorn=0) lifetimes permit O(1) seeks, including long idle clocks.
        if(fixedLifetime) {
          double cycles=Math.floor((clock-s.firstBirth)/(s.life*birthIntervalScale));
          if(cycles>Long.MAX_VALUE-1) throw new IllegalArgumentException("Cycle overflow");
          s.cycle=(long)cycles;
          s.birth=s.firstBirth+s.cycle*s.life*birthIntervalScale;
        } else {
          // Variable lifetimes are renewed cumulatively. A stateless fract(time/life) changes
          // historical births whenever random lifetime changes and is therefore incorrect.
          while(clock>=s.birth+s.life*birthIntervalScale && allowedBirth(s.birth+s.life*birthIntervalScale)) {
            s.birth+=s.life*birthIntervalScale;
            if(s.cycle==Long.MAX_VALUE) throw new IllegalArgumentException("Cycle overflow");
            s.cycle++;
            s.life=life(s);
          }
        }
      }
      double age=clock-s.birth;
      if(!allowedBirth(s.birth) || age<0 || age>=s.life) continue;
      long key=particleSeed(s.index,refreshRandom?s.cycle:0);
      double birth=s.birth-prewarm;
      consumer.accept(new Particle(s.index,s.cycle,birth,age,s.life,age/s.life,
          unit(mix(key+GOLDEN)),key));
    }
  }

  /** Clear only cached variable-lifetime cursor state; seeds and authored schedule stay intact. */
  public void reset() {
    for(Slot s:slots) reset(s);
    lastClock=Double.NEGATIVE_INFINITY;
  }

  private boolean allowedBirth(double birth) { return playTime==0 || birth<playTime; }
  private void reset(Slot s) { s.cycle=0; s.birth=s.firstBirth; s.life=life(s); }
  private double life(Slot s) {
    return lifetime.at(s.curveInput,random(s.index,refreshRandom?s.cycle:0,LIFE_RANDOM));
  }
  private long particleSeed(int index,long cycle) {
    return mix(seed ^ mix(GOLDEN*(index+1L)) ^ mix(0xd1b54a32d192ed03L*(cycle+1)));
  }
  private double random(int index,long cycle,int channel) {
    return unit(mix(particleSeed(index,cycle)+GOLDEN*(channel+1L)));
  }
  private static long mix(long x) {
    x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
    x=(x^(x>>>27))*0x94d049bb133111ebL;
    return x^(x>>>31);
  }
  private static double unit(long x) { return (x>>>11)*0x1.0p-53; }
  private static double nonnegative(double x,String name) {
    if(!Double.isFinite(x)||x<0) throw new IllegalArgumentException("Invalid "+name);
    return x;
  }

  /** Uniform 65-sample (or arbitrary-count) lifetime curve. Endpoint values clamp. */
  public static float sample(float[][] table,double phase,int column,float fallback) {
    if(table==null||table.length==0) return fallback;
    if(!Double.isFinite(phase)) throw new IllegalArgumentException("Non-finite curve phase");
    double position=Math.max(0,Math.min(1,phase))*(table.length-1);
    int a=(int)Math.floor(position),b=Math.min(a+1,table.length-1);
    if(column<0||table[a]==null||table[b]==null
        ||column>=table[a].length||column>=table[b].length) return fallback;
    return (float)(table[a][column]+(table[b][column]-table[a][column])*(position-a));
  }
  public static float sample(float[][] table,double phase,int column) {
    return sample(table,phase,column,1);
  }

  /** Sample one min/max pair; pair0 is scalar/X, pair1 Y, pair2 Z for sizeAxes65x6. */
  public static float sampleRange(float[][] table,double phase,int pair,double random,float fallback) {
    float min=sample(table,phase,pair*2,fallback);
    float max=sample(table,phase,pair*2+1,min);
    return (float)(min+(max-min)*Math.max(0,Math.min(1,random)));
  }

  /** Serialized nonuniform keys. Equal-time keys select the last value (right-continuity). */
  public static float curve(float[] times,float[] values,double x,float fallback) {
    if(times==null||values==null||times.length==0||values.length==0) return fallback;
    if(times.length!=values.length) throw new IllegalArgumentException("Curve key mismatch");
    if(!Double.isFinite(x)) throw new IllegalArgumentException("Non-finite curve input");
    int n=times.length;
    if(x<times[0]) return values[0];
    int lo=0,hi=n;
    while(lo<hi) { int m=(lo+hi)>>>1; if(times[m]<=x) lo=m+1; else hi=m; }
    int a=lo-1;
    if(a>=n-1) return values[n-1];
    double f=(x-times[a])/(times[a+1]-times[a]);
    return (float)(values[a]+(values[a+1]-values[a])*f);
  }

  /** Evaluate one source OneD declaration without introducing scheduling/rate semantics. */
  public static double value(OneD declaration,double curveInput,double random) {
    return new Value(declaration).at(curveInput,random);
  }
  /** Compile a numeric declaration once for a per-frame particle consumer. */
  public static Value compileValue(OneD declaration){return new Value(declaration);}

  private static float[][] copy(float[][] values) {
    if(values==null) return null;
    float[][] result=new float[values.length][];
    for(int i=0;i<values.length;i++) {
      result[i]=values[i]==null?null:values[i].clone();
      if(result[i]!=null) for(float x:result[i])
        if(!Float.isFinite(x)) throw new IllegalArgumentException("Non-finite sample");
    }
    return result;
  }

  public static final class Value {
    final boolean randomRange;
    final double min,max,scale;
    final float[] times,values;
    Value(double min,double max) {
      if(!Double.isFinite(min)||!Double.isFinite(max))
        throw new IllegalArgumentException("Non-finite OneD range");
      this.min=min;this.max=max;scale=1;randomRange=min!=max;times=values=null;
    }
    Value(OneD source) {
      if(source==null) throw new IllegalArgumentException("Missing OneD declaration");
      if(source.oneDType<0||source.oneDType>2)
        throw new IllegalArgumentException("Unknown OneD type "+source.oneDType);
      randomRange=source.oneDType==2 && source._min!=source._max;
      min=source.oneDType==2?source._min:source._value;
      max=source.oneDType==2?source._max:source._value;
      scale=source._maxCurveAnchorValue;
      if(!Double.isFinite(min)||!Double.isFinite(max)||!Double.isFinite(scale))
        throw new IllegalArgumentException("Non-finite OneD declaration");
      if(source.oneDType==1 && source._anchors!=null && source._anchors.length>0) {
        times=new float[source._anchors.length];values=new float[times.length];
        for(int i=0;i<times.length;i++) {
          Anchor a=source._anchors[i];
          if(a==null||!Float.isFinite(a._time)||!Float.isFinite(a._value)
              ||(i>0&&a._time<times[i-1])) throw new IllegalArgumentException("Invalid OneD anchors");
          times[i]=a._time;values[i]=a._value;
        }
      } else times=values=null;
    }
    public double at(double x,double random) {
      // OneD serializes C# floats. Keep the final source multiplication rounded to float,
      // then accumulate birth clocks in double so long-running renewal does not drift.
      return times!=null ? (float)(curve(times,values,x,0)*scale)
          : min+(max-min)*Math.max(0,Math.min(1,random));
    }
    void requirePositive(String name) {
      if(times==null) {
        if(min<=0||max<=0) throw new IllegalArgumentException("Non-positive "+name);
      } else for(float v:values) if(v*scale<=0)
        throw new IllegalArgumentException("Non-positive "+name+" curve");
    }
  }
}
