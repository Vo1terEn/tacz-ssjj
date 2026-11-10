package cn.ssjjdimension;

import java.util.*;

/** Independent pellet timers: cap is shared by all shooters on one target. */
public final class DimensionMarkRules {
    public static final int CAP = 5;
    public record Mark(UUID owner, float damage, long next, int remaining,String profile,DimensionAttachments.Snapshot equipment,int interval,long until) {
        public Mark(UUID owner,float damage,long next,int remaining,String profile,DimensionAttachments.Snapshot equipment){this(owner,damage,next,remaining,profile,equipment,DimensionConfig.integer("mark_interval_ticks"),next+(long)(remaining-1)*DimensionConfig.integer("mark_interval_ticks"));}
        public Mark(UUID owner,float damage,long next,int remaining,String profile){this(owner,damage,next,remaining,profile,DimensionAttachments.NONE);}
        public Mark(UUID owner,float damage,long next,int remaining){this(owner,damage,next,remaining,"tier2");}
    }
    private DimensionMarkRules() {}
    public static int cap(){return DimensionConfig.integer("mark_cap");}
    public static int seconds(String namespace, String path) {
        if (!"ssjj_dimension".equals(namespace)) return 0;
        return switch(path) { case "dimension_star" -> 1; case "dimension_star1" -> 2;
            case "dimension_star2" -> 3; default -> 0; };
    }
    public static boolean add(List<Mark> marks, UUID owner, float damage, long now, int seconds) {
        return add(marks,owner,damage,now,seconds,"tier"+(Math.min(3,seconds)-1));
    }
    public static boolean add(List<Mark> marks, UUID owner,float damage,long now,int seconds,String profile) {
        return add(marks,owner,damage,now,seconds,profile,DimensionAttachments.NONE);
    }
    public static boolean add(List<Mark> marks,UUID owner,float damage,long now,int seconds,String profile,DimensionAttachments.Snapshot equipment){
        if (marks.size() >= cap() || owner == null || !Float.isFinite(damage) || damage <= 0 || seconds < 1 || seconds > 1200) return false;
        int interval=DimensionConfig.integer("mark_interval_ticks"),life=seconds*20;
        marks.add(new Mark(owner,damage,now+Math.min(interval,life),(life+interval-1)/interval,profile,equipment,interval,now+life));
        return true;
    }
    public static List<Mark> due(List<Mark> marks, long now) {
        var due = new ArrayList<Mark>();
        for (var it=marks.listIterator(); it.hasNext();) {
            var m=it.next();
            if (now < m.next()) continue;
            due.add(m);
            if (m.remaining() == 1) it.remove();
            else it.set(new Mark(m.owner(), m.damage(), Math.min(m.next()+m.interval(),m.until()), m.remaining()-1,m.profile(),m.equipment(),m.interval(),m.until()));
        }
        return due;
    }
    public static float healing(float maximum) { return maximum * (float)DimensionConfig.number("mark_healing_fraction"); }
}
