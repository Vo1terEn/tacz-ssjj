package cn.judgfire;
import java.util.*;
/** Borrowed arrays are private to immediate renderer passes; public binding calls remain snapshots. */
public final class EffectRenderReuse {
 private static final class Surface {MotionSkin.Mesh mesh;float[][] matrices,positions;}
 private static final class Pool {final LinkedHashMap<Integer,float[][]> outputs=new LinkedHashMap<>(4,.75f,true);}
 private static final class State {int depth;final ArrayList<Pool> pools=new ArrayList<>();final ArrayList<Surface> surfaces=new ArrayList<>();long hits,misses,allocations;}
 private static final ThreadLocal<State> LOCAL=ThreadLocal.withInitial(State::new);
 private EffectRenderReuse(){}
 public static void begin(){var s=LOCAL.get();s.depth++;}
 public static void end(){var s=LOCAL.get();if(s.depth<=0)throw new IllegalStateException("Unbalanced effect render scope");s.depth--;}
 public static boolean active(){return EffectPerformanceConfig.reuse()&&LOCAL.get().depth>0;}
 public static void clear(){LOCAL.remove();}
 public static long[] statistics(){var s=LOCAL.get();return new long[]{s.hits,s.misses,s.allocations,s.surfaces.size(),s.depth};}
 private static float[][] output(int count){var s=LOCAL.get();if(!active()||s.depth>8||count>EffectPerformanceConfig.vertices())return new float[count][3];
  while(s.pools.size()<s.depth)s.pools.add(new Pool());var p=s.pools.get(s.depth-1);int cap=EffectPerformanceConfig.entries();
  while(p.outputs.size()>cap)p.outputs.remove(p.outputs.keySet().iterator().next());var data=p.outputs.get(count);
  if(data==null){if(p.outputs.size()>=cap)p.outputs.remove(p.outputs.keySet().iterator().next());data=new float[count][3];p.outputs.put(count,data);s.allocations++;}return data;
 }
 private static boolean equal(float[][] a,float[][] b){if(a==null||b==null||a.length!=b.length)return false;for(int i=0;i<a.length;i++)if(!Arrays.equals(a[i],b[i]))return false;return true;}
 private static float[][] snapshot(float[][] value,float[][] copy){if(copy==null||copy.length!=value.length)copy=new float[value.length][];for(int i=0;i<copy.length;i++){if(copy[i]==null||copy[i].length!=value[i].length)copy[i]=value[i].clone();else System.arraycopy(value[i],0,copy[i],0,value[i].length);}return copy;}
 /** Only used as an internal, immediately consumed barycentric surface. */
 private static float[][] surface(MotionSkin.Mesh mesh,float[][] matrices){var s=LOCAL.get();int cap=EffectPerformanceConfig.entries();
  while(s.surfaces.size()>cap)s.surfaces.remove(0);
  if(!active()||mesh.vertices.length>EffectPerformanceConfig.vertices())return MotionSkin.deform(mesh,matrices);
  Surface entry=null;for(var p:s.surfaces)if(p.mesh==mesh){entry=p;break;}
  if(entry!=null&&equal(entry.matrices,matrices)){s.hits++;return entry.positions;}
  s.misses++;if(entry==null){if(s.surfaces.size()>=cap)s.surfaces.remove(0);entry=new Surface();entry.mesh=mesh;entry.positions=new float[mesh.vertices.length][3];s.allocations++;s.surfaces.add(entry);}
  entry.matrices=snapshot(matrices,entry.matrices);
  for(int i=0;i<mesh.vertices.length;i++){float[] v=mesh.vertices[i];MotionSkin.deform(matrices,mesh.indices[i],mesh.weights[i],v[0],v[1],v[2],entry.positions[i]);}
  return entry.positions;
 }
 public static float[][] rigid(float[][] points,int bone,OriginalMotion.Frame frame){float[][] result=output(points.length);
  if(bone<0||bone>=frame.matrices().length){for(int i=0;i<points.length;i++)System.arraycopy(points[i],0,result[i],0,3);return result;}
  float[] m=frame.matrices()[bone];for(int i=0;i<points.length;i++){float[] p=points[i];float x=p[0],y=p[1],z=p[2];result[i][0]=m[0]*x-m[1]*y+m[2]*z+m[3];result[i][1]=-(m[4]*x-m[5]*y+m[6]*z+m[7]);result[i][2]=m[8]*x-m[9]*y+m[10]*z+m[11];}return result;
 }
 public static float[][] binding(BodyEffectBinding binding,float[][] points,OriginalMotion.Frame frame){if(frame==null)return points;if(binding.bone>=0)return rigid(points,binding.bone,frame);
  float[][] result=output(points.length),vertices=surface(frame.rig().smooth,frame.matrices());
  for(int i=0;i<result.length;i++){float[] a=vertices[binding.triangles[i][0]],b=vertices[binding.triangles[i][1]],c=vertices[binding.triangles[i][2]],weight=binding.barycentric[i];
   float nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]),ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]),nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
   float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz),offset=length>.000001f?binding.normal_offset/length:0;
   result[i][0]=a[0]*weight[0]+b[0]*weight[1]+c[0]*weight[2]+nx*offset;result[i][1]=-(a[1]*weight[0]+b[1]*weight[1]+c[1]*weight[2]+ny*offset);result[i][2]=a[2]*weight[0]+b[2]*weight[1]+c[2]*weight[2]+nz*offset;
  }return result;
 }
}
