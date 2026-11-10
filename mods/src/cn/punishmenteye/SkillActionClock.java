package cn.punishmenteye;
/** One action per newly accepted skill request; periodic state snapshots never replay it. */
public final class SkillActionClock {
 private int duration=TimerRules.BASE_DURATION;
 private long requested=Long.MIN_VALUE,started=Long.MIN_VALUE;
 public void request(long now){request(now,TimerRules.BASE_DURATION);}
 public void request(long now,int duration){this.duration=duration;requested=now;}
 public void cancel(){requested=Long.MIN_VALUE;}
 public boolean accept(long now,int active,boolean permanent){
  return accept(now,active,permanent,duration);
 }
 public boolean accept(long now,int active,boolean permanent,int serverDuration){
  long pending=requested;
  if(pending==Long.MIN_VALUE)return false;
  if(permanent||now<pending||now-pending>20){cancel();return false;}
  // An older periodic snapshot can arrive before the server processes this V request.
  if(serverDuration!=duration||active<duration-1||active>duration)return false;
  cancel();
  if(started!=Long.MIN_VALUE&&now-started<=2)return false;
  started=now;return true;
 }
}
