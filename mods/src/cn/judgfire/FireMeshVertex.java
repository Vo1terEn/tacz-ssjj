package cn.judgfire;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Scalar corners retain the original arithmetic order without four temporary vectors per card. */
public final class FireMeshVertex {
  private static final ThreadLocal<Vector3f> POINT=ThreadLocal.withInitial(Vector3f::new);
  private FireMeshVertex() {}
  public static Vector3f point(float x,float y,float z){return POINT.get().set(x,y,z);}
  public static void quad(VertexConsumer out,Matrix4f m,Vector3f p,Vector3f r,Vector3f u,float u0,float u1,float v0,float v1,float[] rgba) {
    vertex(out,m,(p.x-r.x)-u.x,(p.y-r.y)-u.y,(p.z-r.z)-u.z,u0,v0,rgba);
    vertex(out,m,(p.x+r.x)-u.x,(p.y+r.y)-u.y,(p.z+r.z)-u.z,u1,v0,rgba);
    vertex(out,m,(p.x+r.x)+u.x,(p.y+r.y)+u.y,(p.z+r.z)+u.z,u1,v1,rgba);
    vertex(out,m,(p.x-r.x)+u.x,(p.y-r.y)+u.y,(p.z-r.z)+u.z,u0,v1,rgba);
  }
  private static void vertex(VertexConsumer out,Matrix4f m,float x,float y,float z,float u,float v,float[] c) {
    out.vertex(m,x,y,z).color(c[0],c[1],c[2],Math.max(0,Math.min(1,c[3]))).uv(u,v).endVertex();
  }
}
