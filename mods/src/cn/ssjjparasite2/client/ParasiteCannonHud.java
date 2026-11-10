package cn.ssjjparasite2.client;
import java.util.*;
/** Source 210x90 skill card; the native weapon icon starts at width-117. */
public final class ParasiteCannonHud {
 public record Sprite(String texture,int x,int y,int w,int h,int sourceW,int sourceH,int textureW,int textureH){}
 public static List<Sprite> sprites(int width,int height,boolean active,float progress){
  float s=Math.min(.5f,Math.max(.15f,(width-129f)/210f));int w=Math.round(210*s),h=Math.round(90*s),x=Math.max(4,width-125-w),y=Math.max(4,height-24-h);
  var sprites=new ArrayList<Sprite>();sprites.add(new Sprite(active?"active":"ready",x,y,w,h,210,90,210,90));
  int pixels=Math.round(132*Math.max(0,Math.min(1,progress)));if(pixels>0)sprites.add(new Sprite("progress",x+Math.round(75*s),y+Math.round(64*s),Math.round(pixels*s),Math.max(1,Math.round(7*s)),pixels,7,132,7));return sprites;
 }
}
