package cn.punishmenteye;
import cn.fusionarsenal.config.ProgressionConfig;
/** Confirmed mechanics; real game ticks, not wall-clock timers. */
public final class NofaRules {
 public static final int SHOTS=30,DURATION=120,INTERVAL=2;
 /** Historical scene/test API. Production cloud damage uses CloudRanges. */
 public static final double RADIUS=8,HALF_HEIGHT=4;
 public static final float LIGHTNING=10,CLOUD=20;
 public static boolean target(boolean marked,boolean active,boolean aimed,boolean visible){return marked?(active||aimed):aimed&&visible;}
 public static boolean diamond(double left,double right,double bottom,double top,double slope){
  double x=left>0?left:right<0?-right:0,y=bottom>0?bottom:top<0?-top:0;
  return x+y<=slope;
 }
 /** Historical cylinder regression; never used to select live cloud victims. */
 public static boolean cloud(double dx,double dy,double dz){return dx*dx+dz*dz<=RADIUS*RADIUS&&Math.abs(dy)<=HALF_HEIGHT;}
 public static boolean silhouette(java.util.List<double[]> points,double slope){
  if(points.isEmpty())return false;
  points.sort(java.util.Comparator.<double[]>comparingDouble(v->v[0]).thenComparingDouble(v->v[1]));
  var hull=new java.util.ArrayList<double[]>();
  for(var p:points){while(hull.size()>1&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}
  int lower=hull.size();for(int i=points.size()-2;i>=0;i--){var p=points.get(i);while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}if(hull.size()>1)hull.remove(hull.size()-1);
  // Clip the actual convex projected silhouette, rather than its rectangular envelope.
  for(var normal:new double[][]{{1,1},{1,-1},{-1,1},{-1,-1}}){
   var result=new java.util.ArrayList<double[]>();if(hull.isEmpty())return false;var prev=hull.get(hull.size()-1);double pd=prev[0]*normal[0]+prev[1]*normal[1]-slope;
   for(var p:hull){double d=p[0]*normal[0]+p[1]*normal[1]-slope;if((d<=0)!=(pd<=0)){double a=pd/(pd-d);result.add(new double[]{prev[0]+a*(p[0]-prev[0]),prev[1]+a*(p[1]-prev[1])});}if(d<=0)result.add(p);prev=p;pd=d;}hull=result;
  }
  return !hull.isEmpty();
 }
 private static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
 public static boolean pulse(long age){return age>=0&&age<DURATION&&age%ProgressionConfig.integer("eye.cloud_pulse_ticks")==0;}
 public static int count(int prior){return Math.floorMod(prior+1,ProgressionConfig.integer("eye.ball_interval_shots"));}
}
