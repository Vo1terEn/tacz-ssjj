package cn.judgfire;

import com.google.gson.JsonObject;
import java.util.IdentityHashMap;

/** Compile immutable resource JSON once; render samples retain the source float/Hermite arithmetic. */
public final class FireTrailGradient {
  private record Gradient(float[][] time,float[][] value) {}
  private record Curve(float[] time,float[] value,float[] inSlope,float[] outSlope) {}
  private static final IdentityHashMap<JsonObject,Gradient> GRADIENTS=new IdentityHashMap<>();
  private static final IdentityHashMap<JsonObject,Curve> CURVES=new IdentityHashMap<>();
  private FireTrailGradient() {}
  public static void clear(){GRADIENTS.clear();CURVES.clear();}
  public static float[] gradient(JsonObject g,float t) {
    Gradient table=GRADIENTS.get(g);
    if(table==null){
      float[][] times=new float[4][],values=new float[4][];String[] channels={"r","g","b","a"};
      for(int c=0;c<4;c++){
        int count=g.get(c==3?"m_NumAlphaKeys":"m_NumColorKeys").getAsInt();String prefix=c==3?"atime":"ctime";
        times[c]=new float[count];values[c]=new float[count];
        for(int i=0;i<count;i++){times[c][i]=g.get(prefix+i).getAsFloat()/65535f;values[c][i]=g.getAsJsonObject("key"+i).get(channels[c]).getAsFloat();}
      }
      table=new Gradient(times,values);if(GRADIENTS.size()>=256)GRADIENTS.clear();GRADIENTS.put(g,table);
    }
    float[] result=new float[4];
    for(int c=0;c<4;c++){
      float[] times=table.time[c],values=table.value[c];int count=times.length,j=0;
      while(j+1<count&&times[j+1]<t)j++;int k=Math.min(count-1,j+1);float a=times[j],b=times[k];
      float f=b>a?Math.max(0,Math.min(1,(t-a)/(b-a))):0;result[c]=values[j]*(1-f)+values[k]*f;
    }
    return result;
  }
  public static float curve(JsonObject source,float t) {
    Curve c=CURVES.get(source);
    if(c==null){var keys=source.getAsJsonArray("m_Curve");int count=keys.size();float[] time=new float[count],value=new float[count],in=new float[count],out=new float[count];
      for(int i=0;i<count;i++){var k=keys.get(i).getAsJsonObject();time[i]=k.get("time").getAsFloat();value[i]=k.get("value").getAsFloat();in[i]=k.get("inSlope").getAsFloat();out[i]=k.get("outSlope").getAsFloat();}
      c=new Curve(time,value,in,out);if(CURVES.size()>=256)CURVES.clear();CURVES.put(source,c);
    }
    if(c.time.length==0)return 1;if(t<=c.time[0])return c.value[0];
    for(int i=1;i<c.time.length;i++){float ta=c.time[i-1],tb=c.time[i];if(t<=tb){float x=(t-ta)/(tb-ta),x2=x*x,x3=x2*x;return (2*x3-3*x2+1)*c.value[i-1]+(x3-2*x2+x)*(tb-ta)*c.outSlope[i-1]+(-2*x3+3*x2)*c.value[i]+(x3-x2)*(tb-ta)*c.inSlope[i];}}
    return c.value[c.value.length-1];
  }
}
