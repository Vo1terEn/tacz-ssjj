package cn.fusionarsenal.health;

import java.io.InputStream;

/** Launch-stage observations, with an explicitly weaker resource fallback. */
public final class ClassSnapshotAccess {
    private ClassSnapshotAccess() {}
    private static final int LIMIT=2*1024*1024;
    private static final String CACHE="cn.fusionarsenal.launch.HealthBytecode";
    private static final ClassValue<String[]> EVIDENCE=new ClassValue<>(){
        protected String[] computeValue(Class<?> type){return new String[]{"RESOURCE_OBSERVATION"};}
    };
    private static Class<?> bridge(Class<?> type)throws ClassNotFoundException{
        return Class.forName(CACHE,false,type.getClassLoader());
    }
    public static long revision(Class<?> type){
        try{return ((Number)bridge(type).getMethod("classRevision",Class.class).invoke(null,type)).longValue();}
        catch(ReflectiveOperationException|LinkageError ignored){return 0;}
    }
    public static byte[] read(Class<?> type)throws Exception{
        try{
            Object result=bridge(type).getMethod("classBytes",Class.class).invoke(null,type);
            if(result instanceof byte[] bytes&&bytes.length>0&&bytes.length<=LIMIT){
                EVIDENCE.get(type)[0]="MODLAUNCHER_OBSERVATION";return bytes;
            }
        }catch(ReflectiveOperationException|LinkageError ignored){}
        try(InputStream stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
            if(stream==null)throw new IllegalStateException("class bytes unavailable");
            byte[] bytes=stream.readNBytes(LIMIT+1);
            if(bytes.length>LIMIT)throw new IllegalStateException("class byte budget");
            EVIDENCE.get(type)[0]="RESOURCE_OBSERVATION";return bytes;
        }
    }
    /** Neither value means the final JVM method body was captured. */
    public static String evidence(Class<?> type){return EVIDENCE.get(type)[0];}
}
