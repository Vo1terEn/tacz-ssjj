package cn.judgfire;

import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Cosmetic admission only: gameplay and every server packet remain untouched. */
public final class FireSkillFxBudget {
  private record Hit(Vec3 start,Vec3 end,int skin,long time) {}
  private static final ArrayDeque<Hit> RECENT=new ArrayDeque<>();
  private static Object level;
  private FireSkillFxBudget() {}
  public static boolean visible(Vec3 mount) {
    var mc=Minecraft.getInstance();
    return mc.level!=null&&within(mc.gameRenderer.getMainCamera().getPosition(),mount);
  }
  static boolean within(Vec3 eye,Vec3 mount){return eye.distanceToSqr(mount)<=64*64;}
  public static boolean strike(JudgmentFlame.CrownStrike packet) {
    return admit(Minecraft.getInstance().level,packet.start(),packet.end(),packet.skin(),System.nanoTime());
  }
  static boolean admit(Object current,Vec3 start,Vec3 end,int skin,long now) {
    if(current!=level){RECENT.clear();level=current;}
    if(current==null)return false;
    while(!RECENT.isEmpty()&&now-RECENT.peekFirst().time>=75_000_000L)RECENT.removeFirst();
    for(Hit hit:RECENT)if(hit.skin==skin&&hit.start.distanceToSqr(start)<.0625&&hit.end.distanceToSqr(end)<.0625)return false;
    RECENT.addLast(new Hit(start,end,skin,now));
    while(RECENT.size()>128)RECENT.removeFirst();
    return true;
  }
  public static void offer(java.util.List<Object> values,double[] distances,Object value,double distance) {
    int size=values.size(),at=size;while(at>0&&distance<distances[at-1])at--;
    if(at>=distances.length)return;
    if(size<distances.length)values.add(value);
    for(int i=Math.min(size,distances.length-1);i>at;i--){values.set(i,values.get(i-1));distances[i]=distances[i-1];}
    values.set(at,value);distances[at]=distance;
  }
  public static void clear(){RECENT.clear();level=null;FireTrailGradient.clear();}
}
