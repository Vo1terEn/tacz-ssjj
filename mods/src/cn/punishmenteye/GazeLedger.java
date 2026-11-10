package cn.punishmenteye;

import java.util.*;

/** Target-shared, refresh-only deadlines. No potion, entity NBT, or client authority. */
public final class GazeLedger {
    private final Map<UUID,Long> ends=new HashMap<>();
    public void refresh(UUID target,long now,int duration){if(duration>0)ends.put(target,now+duration);}
    public int remaining(UUID target,long now){return (int)Math.max(0,Math.min(Integer.MAX_VALUE,ends.getOrDefault(target,now)-now));}
    public boolean active(UUID target,long now){return remaining(target,now)>0;}
    public Set<UUID> targets(){return Set.copyOf(ends.keySet());}
    public void remove(UUID target){ends.remove(target);}
}
