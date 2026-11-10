package cn.judgfire;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
/** Replaces full sorting when only the first element is used; ties retain encounter order. */
public final class CrownScanReuse {
 private static final ThreadLocal<ArrayDeque<Double>> RANGE=ThreadLocal.withInitial(ArrayDeque::new);
 private CrownScanReuse(){}
 public static void begin(LivingEntity owner){RANGE.get().push(FireCombatStages.range(owner.getMainHandItem()));}
 public static void end(){var values=RANGE.get();if(values.isEmpty())throw new IllegalStateException("Unbalanced Crown scan");values.pop();if(values.isEmpty())RANGE.remove();}
 public static boolean inRange(double x,double y,double z,LivingEntity owner){var stack=RANGE.get();double range=stack.isEmpty()?FireCombatStages.range(owner.getMainHandItem()):stack.peek();return range>0&&Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))<=range;}
 public static <T>void first(List<T> targets,Comparator<? super T> comparator){if(targets.size()<2)return;int best=0;for(int i=1;i<targets.size();i++)if(comparator.compare(targets.get(i),targets.get(best))<0)best=i;if(best!=0)Collections.swap(targets,0,best);}
}
