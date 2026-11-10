package cn.judgfire;
import java.util.*;import com.mojang.blaze3d.vertex.*;import org.joml.Vector3f;
public final class GunMeshRender {
 private static final class Slot {float[][] positions;final Vector3f normal=new Vector3f();}
 private static final class State {int depth;final ArrayList<Slot> slots=new ArrayList<>();}
 private static final ThreadLocal<State> LOCAL=ThreadLocal.withInitial(State::new);
 private GunMeshRender(){}public static void clear(){LOCAL.remove();}
 public static void render(MotionSkin.Mesh mesh,float[][] matrices,PoseStack pose,VertexConsumer out,int light,int overlay){
  if(!RenderOptions.enabled("render.fire_gun"))return;State state=LOCAL.get();int depth=state.depth++;try{
   if(depth==0)for(Slot cached:state.slots)if(cached.positions!=null&&cached.positions.length>RenderOptions.vertices())cached.positions=null;
   Slot slot;if(depth<8&&mesh.vertices.length<=RenderOptions.vertices()&&RenderOptions.enabled("render.reuse_geometry")){while(state.slots.size()<=depth)state.slots.add(new Slot());slot=state.slots.get(depth);}else slot=new Slot();
   if(slot.positions==null||slot.positions.length!=mesh.vertices.length)slot.positions=new float[mesh.vertices.length][3];float[][] vertices=slot.positions;
   for(int i=0;i<vertices.length;i++){float[] v=mesh.vertices[i];MotionSkin.deform(matrices,mesh.indices[i],mesh.weights[i],v[0],v[1],v[2],vertices[i]);}
   for(int i=0;i<mesh.faces.length;i++){int[] face=mesh.faces[i];float[][] uv=mesh.uv[i];float[] a=vertices[face[0]],b=vertices[face[1]],c=vertices[face[2]];
    Vector3f normal=slot.normal.set(b[0]-a[0],b[1]-a[1],b[2]-a[2]).cross(c[0]-a[0],c[1]-a[1],c[2]-a[2]);if(normal.lengthSquared()<1e-12)continue;normal.normalize().mul(pose.last().normal());
    for(int side=0;side<2;side++)for(int j=0;j<4;j++){int index=face.length==3?Math.min(j,2):j;if(side==1)index=face.length-1-index;float[] p=vertices[face[index]],tex=uv[index];float sign=side==0?1:-1;out.vertex(pose.last().pose(),p[0]/16,p[1]/16,p[2]/16).color(1f,1f,1f,1f).uv(tex[0],tex[1]).overlayCoords(overlay).uv2(light).normal(normal.x*sign,normal.y*sign,normal.z*sign).endVertex();}
   }
  }finally{state.depth--;}
 }
}

