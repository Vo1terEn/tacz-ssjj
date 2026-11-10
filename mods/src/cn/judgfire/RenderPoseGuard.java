package cn.judgfire;

import org.joml.Matrix4f;

/** Hidden animation nodes legitimately have zero scale. They have no visible
 * particle frame and must be skipped before inversion, rather than normalized
 * into NaNs or replaced by an identity that would move effects to the camera. */
public final class RenderPoseGuard {
  private RenderPoseGuard() {}
  public static boolean finite(Matrix4f m) {
    if(m==null)return false;
    return Float.isFinite(m.m00())&&Float.isFinite(m.m01())&&Float.isFinite(m.m02())&&Float.isFinite(m.m03())
        &&Float.isFinite(m.m10())&&Float.isFinite(m.m11())&&Float.isFinite(m.m12())&&Float.isFinite(m.m13())
        &&Float.isFinite(m.m20())&&Float.isFinite(m.m21())&&Float.isFinite(m.m22())&&Float.isFinite(m.m23())
        &&Float.isFinite(m.m30())&&Float.isFinite(m.m31())&&Float.isFinite(m.m32())&&Float.isFinite(m.m33());
  }
  public static boolean usable(Matrix4f m) {
    if(!finite(m))return false;
    // A zero scale followed by a rotation leaves linearly dependent nonzero
    // columns. Float determinant rounding alone can mistake that for a valid
    // frame. Compare the double determinant to the column lengths as well.
    double a=m.m00(),b=m.m10(),c=m.m20();
    double d=m.m01(),e=m.m11(),f=m.m21();
    double g=m.m02(),h=m.m12(),i=m.m22();
    double determinant=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
    double volume=Math.sqrt(a*a+d*d+g*g)*Math.sqrt(b*b+e*e+h*h)*Math.sqrt(c*c+f*f+i*i);
    return Double.isFinite(determinant)&&Double.isFinite(volume)
        &&Math.abs(determinant)>1e-20&&Math.abs(determinant)>volume*1e-6;
  }
  public static Matrix4f inverse(Matrix4f pose) {
    if(!usable(pose))return null;
    Matrix4f inverse=new Matrix4f(pose).invert();
    return finite(inverse)?inverse:null;
  }
  public static boolean direction(float[] v) {
    if(v==null||v.length<3)return false;
    double length=0;
    for(int k=0;k<3;k++){if(!Float.isFinite(v[k]))return false;length+=(double)v[k]*v[k];}
    return Double.isFinite(length)&&length>1e-30;
  }
}
