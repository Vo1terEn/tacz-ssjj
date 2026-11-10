package cn.judgfire;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A shared temporary aim frame for the mouth and its endpoint line. Never moves the gun. */
public final class EndpointBeamAim {
  private EndpointBeamAim() {}
  public static float[][] align(float[][] motion,int bone,float[] muzzle,float[] forward,float[] endpoint) {
    if(motion==null||bone<0||bone>=motion.length||muzzle==null||!RenderPoseGuard.direction(forward)||endpoint==null)return motion;
    float[] m=motion[bone];if(m==null||m.length<12)return motion;
    // The rig stores Y-down; both particle geometry and endpoint use Y-up model units.
    Matrix4f basis=new Matrix4f(m[0],-m[4],m[8],0,-m[1],m[5],-m[9],0,m[2],-m[6],m[10],0,m[3],-m[7],m[11],1);
    if(!RenderPoseGuard.usable(basis))return motion;
    Vector3f origin=basis.transformPosition(new Vector3f(muzzle));
    Vector3f from=basis.transformDirection(new Vector3f(forward));
    Vector3f to=new Vector3f(endpoint).sub(origin);
    if(!RenderPoseGuard.direction(new float[]{to.x,to.y,to.z})||from.dot(to)<=0)return motion;
    Quaternionf rotation=new Quaternionf().rotationTo(from.normalize(),to.normalize());
    Matrix4f aimed=new Matrix4f().translation(origin).rotate(rotation).translate(-origin.x,-origin.y,-origin.z).mul(basis);
    if(!RenderPoseGuard.usable(aimed))return motion;
    float[][] result=motion.clone();result[bone]=new float[]{
      aimed.m00(),-aimed.m10(),aimed.m20(),aimed.m30(),
      -aimed.m01(),aimed.m11(),-aimed.m21(),-aimed.m31(),
      aimed.m02(),-aimed.m12(),aimed.m22(),aimed.m32()};
    return result;
  }
}
