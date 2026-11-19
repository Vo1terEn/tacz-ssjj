package cn.ssjjdimension.client;

import java.util.*;
import java.util.function.Supplier;

/** Exact-time samples shared only inside one render pass; no retained world/resource references. */
public final class DimensionSkillSamples {
    private final IdentityHashMap<Object,Map<Long,List<?>>> values=new IdentityHashMap<>();
    private int count;
    @SuppressWarnings("unchecked")
    public <T> List<T> get(Object source,float seconds,float radius,Supplier<List<T>> sample){
        long key=((long)Float.floatToIntBits(seconds)<<32)|(Float.floatToIntBits(radius)&0xffffffffL);
        var times=values.computeIfAbsent(source,unused->new HashMap<>());
        var old=times.get(key);if(old!=null)return (List<T>)old;
        var fresh=sample.get();times.put(key,fresh);count++;return fresh;
    }
    public int count(){return count;}
    public void clear(){values.clear();count=0;}
}
