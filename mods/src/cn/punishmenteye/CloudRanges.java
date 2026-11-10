package cn.punishmenteye;

import java.util.*;

/** Shared damage volume of the persistent source cloud body.
 * Body birth/velocity follow the source clock. Flares, floor glow and stray
 * sparks do not enlarge it. Rendering retains original geometry/native scale.
 */
public final class CloudRanges {
 private CloudRanges(){}
 public record Section(double y,double radius){}
 public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){}
 public static final class Motion {
  private final float delay,prewarm,life;
  private final double[][] birth,velocity;
  public Motion(float delay,float prewarm,float life,double[][] birth,double[][] velocity){
   if(life<=0||birth.length==0||birth.length!=velocity.length)throw new IllegalArgumentException("Invalid cloud body clock");
   this.delay=delay;this.prewarm=prewarm;this.life=life;
   this.birth=Arrays.stream(birth).map(double[]::clone).toArray(double[][]::new);this.velocity=Arrays.stream(velocity).map(double[]::clone).toArray(double[][]::new);
  }
  double[] offset(double ticks){
   float time=Math.max(0,(float)ticks/20f+prewarm-delay);int cycle=(int)(time/life);time-=cycle*life;
   if(cycle>=birth.length)throw new IllegalArgumentException("Cloud age exceeds generated source lifecycle");
   return new double[]{birth[cycle][0]+time*velocity[cycle][0],birth[cycle][1]+time*velocity[cycle][1],birth[cycle][2]+time*velocity[cycle][2]};
  }
 }
 public static final class Profile {
  private final String style,bodyEmitter,sourceSha256;
  private final double x,y,z;
  private final List<Section> sections;
  private final Bounds bounds;
  private final Motion motion;
  public Profile(String style,String bodyEmitter,String sourceSha256,double x,double y,double z,List<Section> sections){
   this(style,bodyEmitter,sourceSha256,x,y,z,sections,null);
  }
  public Profile(String style,String bodyEmitter,String sourceSha256,double x,double y,double z,List<Section> sections,Motion motion){
   if(style==null||bodyEmitter==null||sourceSha256==null||sections==null||sections.size()<2)throw new IllegalArgumentException("Incomplete cloud profile");
   this.style=style;this.bodyEmitter=bodyEmitter;this.sourceSha256=sourceSha256;this.x=x;this.y=y;this.z=z;this.sections=List.copyOf(sections);this.motion=motion;
   double prior=Double.NEGATIVE_INFINITY,maxRadius=0;
   for(var section:sections){if(!Double.isFinite(section.y)||!Double.isFinite(section.radius)||section.y<=prior||section.radius<=0)throw new IllegalArgumentException("Invalid cloud contour");prior=section.y;maxRadius=Math.max(maxRadius,section.radius);}
   bounds=new Bounds(x-maxRadius,y+sections.get(0).y,z-maxRadius,x+maxRadius,y+sections.get(sections.size()-1).y,z+maxRadius);
  }
  public String style(){return style;}public String bodyEmitter(){return bodyEmitter;}public String sourceSha256(){return sourceSha256;}
  public double x(){return x;}public double y(){return y;}public double z(){return z;}
  public List<Section> sections(){return sections;}public Bounds bounds(){return bounds;}
  /** Same source birth/velocity/loop clock as the rendered persistent body. */
  public Profile at(double ticks){if(!Double.isFinite(ticks)||ticks<0)throw new IllegalArgumentException("Invalid cloud age");if(motion==null)return this;var offset=motion.offset(ticks);return new Profile(style,bodyEmitter,sourceSha256,x+offset[0],y+offset[1],z+offset[2],sections);}
  /** Radius of the source rotation body's piecewise-linear silhouette at relative Y. */
  public double radiusAt(double worldY){
   double local=worldY-y;
   double bottom=sections.get(0).y,top=sections.get(sections.size()-1).y;
   if(local<bottom-1e-9||local>top+1e-9)return 0;
   local=Math.max(bottom,Math.min(top,local));
   for(int i=1;i<sections.size();i++)if(local<=sections.get(i).y){var a=sections.get(i-1);var b=sections.get(i);return a.radius+(b.radius-a.radius)*(local-a.y)/(b.y-a.y);}
   return sections.get(sections.size()-1).radius;
  }
  public boolean contains(double dx,double dy,double dz){return intersects(dx,dy,dz,dx,dy,dz);}
  /** Exact solid-of-revolution/AABB overlap: nearest horizontal point, and the
   * maximum radial contour over the box's overlapping vertical interval.
   * All six arguments are relative to the original cloud impact position.
   */
  public boolean intersects(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
   if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)||minX>maxX||minY>maxY||minZ>maxZ)return false;
   double lo=Math.max(minY,bounds.minY),hi=Math.min(maxY,bounds.maxY);if(lo>hi)return false;
   double radius=Math.max(radiusAt(lo),radiusAt(hi));
   for(var section:sections)if(y+section.y>=lo&&y+section.y<=hi)radius=Math.max(radius,section.radius);
   double nx=minX>x?minX-x:maxX<x?maxX-x:0,nz=minZ>z?minZ-z:maxZ<z?maxZ-z:0;
   return nx*nx+nz*nz<=radius*radius+1e-10;
  }
 }
 private static final Map<String,Profile> PROFILES=CloudRangeProfiles.create();
 public static String key(String style){return style==null||style.isEmpty()?"base":style;}
 /** Unknown styles do not create invisible fallback damage. */
 public static Profile profile(String style){return PROFILES.get(key(style));}
 public static Map<String,Profile> profiles(){return PROFILES;}
}
