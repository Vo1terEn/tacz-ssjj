package cn.judgfire;
import java.util.*;import org.joml.Vector3f;import org.joml.Vector4f;import org.joml.Matrix4f;import com.mojang.blaze3d.vertex.VertexSorting;
/** Arrays borrowed only inside immediately consumed render scopes. */
public final class BodyScratch {
 public static final class Mesh {public final float[][] positions,uv;Mesh(int n,int u){positions=new float[n][3];uv=new float[u][2];}}
 private static final class Pool {int cursor,retained,centerVertices;final ArrayList<Mesh> meshes=new ArrayList<>();final LinkedHashMap<Integer,Vector3f[]> centers=new LinkedHashMap<>(4,.75f,true);}
 private static final class State {int depth;final ArrayList<Pool> pools=new ArrayList<>();}
 private static final ThreadLocal<State> LOCAL=ThreadLocal.withInitial(State::new);
 private BodyScratch(){}
 public static void begin(){State s=LOCAL.get();s.depth++;if(s.depth<=8){while(s.pools.size()<s.depth)s.pools.add(new Pool());Pool p=s.pools.get(s.depth-1);p.cursor=0;if(p.retained>RenderOptions.vertices()){p.meshes.clear();p.retained=0;}trimCenters(p,0);}}
 private static void trimCenters(Pool p,int needed){while(!p.centers.isEmpty()&&(p.centers.size()+(needed>0?1:0)>RenderOptions.entries()||p.centerVertices+needed>RenderOptions.vertices())){Integer first=p.centers.keySet().iterator().next();p.centerVertices-=p.centers.remove(first).length;}}
 public static void end(){State s=LOCAL.get();if(s.depth<=0)throw new IllegalStateException("Unbalanced body render");s.depth--;}
 public static void clear(){LOCAL.remove();GunMeshRender.clear();}
 public static boolean active(){int d=LOCAL.get().depth;return d>0&&d<=8&&RenderOptions.enabled("render.reuse_geometry");}
 public static Mesh mesh(int n,int u){if(!active()||n+u>RenderOptions.vertices())return new Mesh(n,u);Pool p=LOCAL.get().pools.get(LOCAL.get().depth-1);int index=p.cursor++;if(index>=256)return new Mesh(n,u);while(p.meshes.size()<=index)p.meshes.add(null);Mesh m=p.meshes.get(index);if(m==null||m.positions.length!=n||m.uv.length!=u){int old=m==null?0:m.positions.length+m.uv.length;if(p.retained-old+n+u>RenderOptions.vertices())return new Mesh(n,u);m=new Mesh(n,u);p.meshes.set(index,m);p.retained+=n+u-old;}return m;}
 public static void set(float[][] a,int i,float x,float y,float z){a[i][0]=x;a[i][1]=y;a[i][2]=z;}
 public static int[] order(SkinBodyEffects.Draw draw,Matrix4f pose,VertexSorting sorting){
  Vector3f[] centers=null;int n=draw.faces().length;
  if(active()&&n<=RenderOptions.vertices()){Pool p=LOCAL.get().pools.get(LOCAL.get().depth-1);centers=p.centers.get(n);if(centers==null){trimCenters(p,n);centers=new Vector3f[n];for(int i=0;i<n;i++)centers[i]=new Vector3f();p.centers.put(n,centers);p.centerVertices+=n;}}
  if(centers==null){centers=new Vector3f[n];for(int i=0;i<n;i++)centers[i]=new Vector3f();}
  Vector4f a=new Vector4f(),b=new Vector4f();for(int i=0;i<n;i++){int[] face=draw.faces()[i];float[] v=draw.vertices()[face[0]],w=draw.vertices()[face[Math.min(2,face.length-1)]];pose.transform(a.set(v[0]/16,-v[1]/16,v[2]/16,1));pose.transform(b.set(w[0]/16,-w[1]/16,w[2]/16,1));centers[i].set((a.x+b.x)/2,(a.y+b.y)/2,(a.z+b.z)/2);}return sorting.sort(centers);
 }
}

