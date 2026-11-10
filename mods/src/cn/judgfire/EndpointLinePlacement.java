package cn.judgfire;

/** Pure endpoint bridge in CurrentRig Y-up model units. No game, GL, particle or atlas state. */
public final class EndpointLinePlacement {
  private EndpointLinePlacement() {}
  public record Result(float[][] vertices,float[] sourceOrigin,float distance) {}
  /**
   * originWeights are an affine reconstruction of the true native mesh origin, preserved
   * through all authored scale/spin/motion. maxSourceExtent includes the source peak curve,
   * so growth comes from the actual sampled vertices, not a second guessed lifetime curve.
   */
  public static Result place(float[][] vertices,float[] originWeights,float[] sourceForward,
                             float[] endpoint,float maxSourceExtent,float widthDistanceCap) {
    if(vertices==null||originWeights==null||vertices.length!=originWeights.length||maxSourceExtent<=0)return null;
    float[] origin=new float[3];for(int i=0;i<vertices.length;i++)for(int k=0;k<3;k++)origin[k]+=vertices[i][k]*originWeights[i];
    float[] f=unit(sourceForward),d=sub(endpoint,origin);float distance=length(d);
    if(distance<1e-4f||dot(d,f)<=0)return null;
    for(int k=0;k<3;k++)d[k]/=distance;
    float[][] result=new float[vertices.length][3];
    for(int i=0;i<vertices.length;i++) {
      float[] v=sub(vertices[i],origin);float along=dot(v,f);
      float fraction=Math.max(0,Math.min(1,along/maxSourceExtent));
      float[] transverse=new float[]{v[0]-f[0]*along,v[1]-f[1]*along,v[2]-f[2]*along};
      transverse=rotateFromTo(transverse,f,d);
      float radius=length(transverse),cap=distance*Math.max(0,widthDistanceCap);
      if(radius>cap&&radius>1e-12f)for(int k=0;k<3;k++)transverse[k]*=cap/radius;
      for(int k=0;k<3;k++)result[i][k]=origin[k]+d[k]*(distance*fraction)+transverse[k];
    }
    return new Result(result,origin,distance);
  }
  private static float[] rotateFromTo(float[] v,float[] from,float[] to) {
    float cosine=Math.max(-1,Math.min(1,dot(from,to)));float[] axis=cross(from,to);
    if(cosine<-.99999f) {
      float[] basis=Math.abs(from[0])<.8f?new float[]{1,0,0}:new float[]{0,1,0};axis=unit(cross(from,basis));
      float p=dot(axis,v);return new float[]{2*axis[0]*p-v[0],2*axis[1]*p-v[1],2*axis[2]*p-v[2]};
    }
    float[] a=cross(axis,v),b=cross(axis,a);float divisor=1+cosine;
    return new float[]{v[0]+a[0]+b[0]/divisor,v[1]+a[1]+b[1]/divisor,v[2]+a[2]+b[2]/divisor};
  }
  private static float[] sub(float[] a,float[] b){return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
  private static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
  private static float length(float[] v){return(float)Math.sqrt(dot(v,v));}
  private static float[] unit(float[] v){float n=length(v);if(n<1e-12f)throw new IllegalArgumentException("Zero source forward");return new float[]{v[0]/n,v[1]/n,v[2]/n};}
  private static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
}
