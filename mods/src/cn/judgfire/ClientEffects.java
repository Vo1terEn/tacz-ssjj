package cn.judgfire;

import java.util.*;
import net.minecraft.client.Minecraft;

/** Compile container only: transplant nearestMarks into the CURRENT runtime owner, never this class. */
public final class ClientEffects {
  private record Marked(int target,long expires,Object level,int skin) {}
  private static final Map<Integer,Marked> MARKS=new HashMap<>();
  static Collection<?> nearestMarks() {
    var mc=Minecraft.getInstance();var selected=new ArrayList<Object>(8);if(mc.level==null)return selected;
    var eye=mc.gameRenderer.getMainCamera().getPosition();long now=mc.level.getGameTime();double[] distance=new double[8];
    for(Marked marked:MARKS.values()) {
      if(marked.level!=mc.level||now>=marked.expires)continue;
      var target=mc.level.getEntity(marked.target);
      if(target==null||!target.isAlive()||target.isRemoved())continue;
      double d=eye.distanceToSqr(target.position());if(!Double.isFinite(d)||d>64*64)continue;
      FireSkillFxBudget.offer(selected,distance,marked,d);
    }
    return selected;
  }
}
