package cn.ssjjdimension.client;
/** Measured original 1920x1080 clip: background (879,463), hint (921,460), fill (906,478). */
public final class DimensionDecoyHud {
 public record Layout(float x,float y,float scale){}
 private DimensionDecoyHud(){}
 public static boolean visible(int tier,int activeTicks){return tier==2&&activeTicks>0;}
 public static Layout layout(int width,int height){float scale=Math.min(width/1920f,height/1080f);return new Layout(width*.5f-81*scale,height*.5f-77*scale,scale);}
}
