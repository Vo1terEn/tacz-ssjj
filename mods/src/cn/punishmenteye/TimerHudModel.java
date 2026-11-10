package cn.punishmenteye;
import java.util.*;
/** Actual HUD draw commands shared by the client and offline layout previews. No text commands. */
public final class TimerHudModel {
 public record Sprite(String texture,int x,int y,int w,int h,int sourceWidth,int sourceHeight,int textureWidth,int textureHeight,float alpha){}
 public record Remaining(int active,int cooldown){}
 /** The server clamps cooldown to its full duration while the skill is running.
  * Extrapolate only cooldown time that elapsed after this snapshot's active deadline. */
 public static Remaining remaining(int snapshotActive,int snapshotCooldown,long elapsedTicks){
  return remaining(snapshotActive,snapshotCooldown,false,elapsedTicks);
 }
 public static Remaining remaining(int snapshotActive,int snapshotCooldown,boolean permanent,long elapsedTicks){
  return remaining(snapshotActive,snapshotCooldown,permanent,elapsedTicks,TimerRules.BASE_DURATION,TimerRules.BASE_COOLDOWN);
 }
 public static Remaining remaining(int snapshotActive,int snapshotCooldown,boolean permanent,long elapsedTicks,int duration,int totalCooldown){
  if(permanent)return new Remaining(duration,0);
  int active=Math.max(0,Math.min(duration,snapshotActive));
  int cooldown=Math.max(0,Math.min(totalCooldown,snapshotCooldown));
  long elapsed=Math.max(0,elapsedTicks),cooldownElapsed=Math.max(0,elapsed-active);
  return new Remaining((int)Math.max(0,active-elapsed),(int)Math.max(0,cooldown-cooldownElapsed));
 }
 /** Active artwork depletes; after expiry the original progress artwork recharges from zero. */
 public static float progress(int active,int cooldown){return progress(active,cooldown,TimerRules.BASE_DURATION,TimerRules.BASE_COOLDOWN);}
 public static float progress(int active,int cooldown,int duration,int totalCooldown){
  active=Math.max(0,Math.min(duration,active));
  cooldown=Math.max(0,Math.min(totalCooldown,cooldown));
  return active>0?active/(float)duration:1-cooldown/(float)totalCooldown;
 }
 public static java.util.List<Sprite> sprites(int width,int height,int active,int cooldown){return sprites(width,height,active,cooldown,TimerRules.BASE_DURATION,TimerRules.BASE_COOLDOWN);}
 public static java.util.List<Sprite> sprites(int width,int height,int active,int cooldown,int duration,int totalCooldown){
  active=Math.max(0,Math.min(duration,active));cooldown=Math.max(0,Math.min(totalCooldown,cooldown));
  // Same layout as the reference weapon: card left of the native icon, with an eight-unit gap.
  float scale=Math.min(.5f,Math.max(.15f,(width-129f)/210f));
  int w=Math.round(210*scale),h=Math.round(90*scale),x=Math.max(4,width-125-w),y=Math.max(4,height-24-h);
  boolean running=active>0;float ratio=progress(active,cooldown,duration,totalCooldown);
  var result=new ArrayList<Sprite>();result.add(new Sprite(running?"skill_active":"skill_ready",x,y,w,h,210,90,210,90,!running&&cooldown>0?.5f:1));
  int pixels=Math.round(125*ratio);if(pixels>0)result.add(new Sprite("skill_progress",x+Math.round(80*scale),y+Math.round(64*scale),Math.round(pixels*scale),Math.max(1,Math.round(7*scale)),pixels,7,125,7,1));
  return result;
 }
}
