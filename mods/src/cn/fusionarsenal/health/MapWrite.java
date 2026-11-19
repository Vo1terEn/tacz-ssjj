package cn.fusionarsenal.health;

import java.util.Map;
import java.util.Objects;

/** Writes only an already-proven numeric entry; never inserts a key. */
public final class MapWrite {
    private MapWrite() {}
    public static boolean safeRead(Map<?,?> map){
        try{
            for(String name:new String[]{"get","containsKey","entrySet"}){
                var method=name.equals("entrySet")?map.getClass().getMethod(name):map.getClass().getMethod(name,Object.class);
                if(method.getDeclaringClass().getClassLoader()!=null)return false;
            }
            return true;
        }catch(ReflectiveOperationException ignored){return false;}
    }
    private static boolean number(Object value){
        return (value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long
            ||value instanceof Float||value instanceof Double)&&Double.isFinite(((Number)value).doubleValue());
    }
    private static boolean sameKey(Object first,Object second){
        return first==second||first!=null&&second!=null&&first.getClass()==second.getClass()
            &&(first instanceof String||first instanceof java.util.UUID||first instanceof Integer||first instanceof Long)&&first.equals(second);
    }
    public static boolean write(Object object,Object key,Object expected,Object replacement){
        if(!(object instanceof Map<?,?> map)||!safeRead(map)||!number(expected)||!number(replacement)
            ||expected.getClass()!=replacement.getClass())return false;
        synchronized(map){
            if(!map.containsKey(key)||!Objects.equals(map.get(key),expected))return false;
            Map.Entry<?,?> selected=null;int count=0;
            for(Map.Entry<?,?> entry:map.entrySet()){
                if(++count>65536)return false;
                if(sameKey(entry.getKey(),key)){selected=entry;break;}
            }
            if(selected==null||selected.getClass().getClassLoader()!=null||!Objects.equals(selected.getValue(),expected))return false;
            try{
                set(selected,replacement);
                if(map.containsKey(key)&&Objects.equals(map.get(key),replacement))return true;
            }catch(RuntimeException ignored){}
            try{if(!Objects.equals(map.get(key),expected))set(selected,expected);}catch(RuntimeException ignored){}
            return false;
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static void set(Map.Entry entry,Object value){entry.setValue(value);}
}
