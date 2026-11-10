package cn.judgfire;

/** Finite Unity ParticleSystem shape reconstruction. Enum values are verified against
 * UnityCsReference ParticleSystemEnums.cs. The native C++ random generator is unavailable;
 * callers provide stable per-birth random channels. This is unrelated to SSJJ custom shape.
 */
public final class NativeParticleShape {
  private NativeParticleShape(){}
  public static class Spec {
    public boolean enabled;public int type;public float radius,angle,length,radiusThickness=1,arc=360;
    public float[] box,position,rotation;
  }
  public record Sample(float[] position,float[] direction){}
  public static Sample sample(Spec s,float u,float v,float w,float q){
    float[] p={0,0,0},d={0,0,1};if(s==null||!s.enabled)return new Sample(p,d);
    double phi=2*Math.PI*u,cs=Math.cos(phi),sn=Math.sin(phi),thick=Math.max(0,Math.min(1,s.radiusThickness));
    if(s.type>=0&&s.type<=3){
      double z=s.type>=2?v:2*v-1,xy=Math.sqrt(Math.max(0,1-z*z));
      double inner=s.type==1||s.type==3?1:1-thick,r=s.radius*Math.cbrt(inner*inner*inner+w*(1-inner*inner*inner));
      d=new float[]{(float)(xy*cs),(float)(xy*sn),(float)z};for(int k=0;k<3;k++)p[k]=(float)(d[k]*r);
    }else if(s.type==5||s.type==15||s.type==16||s.type==18){
      p=new float[]{u-.5f,v-.5f,s.type==18?0:w-.5f};
      if(s.type==15||s.type==16){int face=Math.min(2,(int)(q*3));p[face]=p[face]<0?-.5f:.5f;if(s.type==16)p[(face+1)%3]=p[(face+1)%3]<0?-.5f:.5f;}
    }else if(s.type==12){p[0]=(u*2-1)*s.radius;}
    else if(s.type==4||s.type==7||s.type==8||s.type==9||s.type==10||s.type==11){
      double inner=s.type==7||s.type==9||s.type==11?1:1-thick,rad=Math.sqrt(inner*inner+v*(1-inner*inner));
      p[0]=(float)(s.radius*rad*cs);p[1]=(float)(s.radius*rad*sn);
      if(s.type==10||s.type==11)d=new float[]{(float)cs,(float)sn,0};
      else{
        double theta=Math.toRadians(s.angle),sin=Math.sin(theta)*rad,cos=Math.cos(theta),norm=Math.hypot(sin,cos);
        if(norm>1e-12)d=new float[]{(float)(sin*cs/norm),(float)(sin*sn/norm),(float)(cos/norm)};
        if(s.type==8||s.type==9){double distance=s.length*w;for(int k=0;k<3;k++)p[k]+=d[k]*distance;}
      }
    }
    // ShapeModule TRS precedes the particle-system node's complete world basis.
    if(s.box!=null)for(int k=0;k<3;k++)p[k]*=s.box[k];
    p=rotate(p,s.rotation);d=rotate(d,s.rotation);
    if(s.position!=null)for(int k=0;k<3;k++)p[k]+=s.position[k];
    double norm=Math.sqrt(d[0]*d[0]+d[1]*d[1]+d[2]*d[2]);if(norm>1e-12)for(int k=0;k<3;k++)d[k]/=(float)norm;
    return new Sample(p,d);
  }
  private static float[] rotate(float[] p,float[] e){
    if(e==null)return p;double x=Math.toRadians(e[0]),y=Math.toRadians(e[1]),z=Math.toRadians(e[2]);
    double a=p[0]*Math.cos(z)-p[1]*Math.sin(z),b=p[0]*Math.sin(z)+p[1]*Math.cos(z),c=p[2];
    double yy=b*Math.cos(x)-c*Math.sin(x),zz=b*Math.sin(x)+c*Math.cos(x);
    return new float[]{(float)(a*Math.cos(y)+zz*Math.sin(y)),(float)yy,(float)(-a*Math.sin(y)+zz*Math.cos(y))};
  }
}
