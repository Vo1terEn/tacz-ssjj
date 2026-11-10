package cn.punishmenteye;

/** Pure client clock. Repeated permanent snapshots keep one continuous FX timeline. */
public final class SkillVisualClock {
 public record Entry(long until,long started,boolean permanent,int duration){public Entry(long until,long started,boolean permanent){this(until,started,permanent,TimerRules.BASE_DURATION);}}
 public static Entry accept(Entry previous,int active,boolean permanent,long now){
  return accept(previous,active,permanent,now,TimerRules.BASE_DURATION);
 }
 public static Entry accept(Entry previous,int active,boolean permanent,long now,int duration){
  if(permanent)return new Entry(0,previous!=null&&previous.permanent?previous.started:now,true,duration);
  return active>0?new Entry(now+active,0,false,duration):null;
 }
 public static float age(Entry entry,long now,float partial,boolean eligible,boolean stone){
  if(entry==null||!eligible)return -1;
  if(entry.permanent)return stone?(float)(Math.max(0,now-entry.started)%24000)+partial:-1;
  long remaining=entry.until-now;
  return remaining>0?entry.duration-remaining+partial:-1;
 }
 public static boolean expired(Entry entry,long now){return !entry.permanent&&entry.until<=now;}
}
