package cn.punishmenteye;
import com.mojang.blaze3d.vertex.*;import org.joml.Vector3f;import java.util.*;
public final class EyeBodyRender {
 private static final class Buffer {final float[] normals;final int[] seen;final Vector3f temp=new Vector3f();int generation;Buffer(int n){normals=new float[n*3];seen=new int[n];}void next(){if(++generation==0){Arrays.fill(seen,0);generation=1;}}}
 private static final class State {int depth;final ArrayList<Buffer> buffers=new ArrayList<>();}
 private static final ThreadLocal<State> LOCAL=ThreadLocal.withInitial(State::new);
 private EyeBodyRender(){}public static void clear(){LOCAL.remove();}
 public static void render(WeaponVisuals.Part mesh,PoseStack pose,VertexConsumer out,int light,int overlay){
  if(!cn.judgfire.RenderOptions.enabled("render.eye_gun"))return;State state=LOCAL.get();int depth=state.depth++;try{
   if(depth==0)for(int k=0;k<state.buffers.size();k++){Buffer cached=state.buffers.get(k);if(cached!=null&&cached.seen.length>cn.judgfire.RenderOptions.vertices())state.buffers.set(k,null);}
   int n=mesh.vertices.length;Buffer buffer;
   if(depth<8&&n<=cn.judgfire.RenderOptions.vertices()&&cn.judgfire.RenderOptions.enabled("render.reuse_geometry")){while(state.buffers.size()<=depth)state.buffers.add(null);buffer=state.buffers.get(depth);if(buffer==null||buffer.seen.length<n||buffer.seen.length>cn.judgfire.RenderOptions.vertices()){buffer=new Buffer(n);state.buffers.set(depth,buffer);}}else buffer=new Buffer(n);
   buffer.next();for(int[] face:mesh.faces)for(int j=0;j<4;j++){int i=face[j==0?0:j==1?2:1],offset=i*3;float[] p=mesh.vertices[i],uv=mesh.uv[i];if(buffer.seen[i]!=buffer.generation){float[] normal=mesh.normals[i];Vector3f v=buffer.temp.set(normal[0],-normal[1],normal[2]).mul(pose.last().normal()).normalize();buffer.normals[offset]=v.x;buffer.normals[offset+1]=v.y;buffer.normals[offset+2]=v.z;buffer.seen[i]=buffer.generation;}
    out.vertex(pose.last().pose(),p[0]/16,-p[1]/16,p[2]/16).color(1f,1f,1f,1f).uv(uv[0],uv[1]).overlayCoords(overlay).uv2(light).normal(buffer.normals[offset],buffer.normals[offset+1],buffer.normals[offset+2]).endVertex();}
  }finally{state.depth--;}
 }
}

