package cn.punishmenteye;
import com.mojang.blaze3d.vertex.*;
import org.joml.Vector3f;
/** Source E014 meshes, sprite lifetime, color curve and scrolling UVs. Age is skill ticks. */
public final class MuzzleGeometry {
 public static final class Definition {public Pass[] passes;public MuzzleParticles.Emitter[] emitters;}
 public static final class Pass {
  public String name,texture,blend;public WeaponVisuals.Part mesh;public int cols,rows,layer;
  public float alpha,fps,scrollU,scrollV,lifetime;public float[] tint,lifetimeRange,gradientRatios;public int[] gradientColors;
 }
 public static float alpha(Pass pass,float age){return pass.alpha;}
 /** Source Start samples lifetime once; looping particles keep that same lifetime. */
 public static float lifetime(Pass pass,int cycle){
  if(pass.lifetimeRange==null||pass.lifetimeRange.length!=2)return pass.lifetime;
  int h=pass.name.hashCode();h^=h>>>16;h*=0x7feb352d;h^=h>>>15;h*=0x846ca68b;h^=h>>>16;
  float random=(h&0xffffff)/16777216f;
  return pass.lifetimeRange[0]+random*(pass.lifetimeRange[1]-pass.lifetimeRange[0]);
 }
 private static float[] clock(Pass pass,float age){
  float seconds=Math.max(0,age)/20f;int cycle=0;float length=lifetime(pass,cycle);
  while(seconds>=length&&length>0){seconds-=length;length=lifetime(pass,++cycle);}
  return new float[]{seconds/Math.max(length,.001f),length};
 }
 public static float phase(Pass pass,float age){return clock(pass,age)[0];}
 public static int frame(Pass pass,float age){return Math.min(pass.cols*pass.rows-1,(int)(phase(pass,age)*pass.cols*pass.rows));}
 public static float[] tint(Pass pass,float age){
  if(pass.gradientColors==null||pass.gradientColors.length==0)return pass.tint.clone();
  float t=phase(pass,age);int last=pass.gradientColors.length-1,hi=0;
  while(hi<last&&t>pass.gradientRatios[hi])hi++;
  int lo=Math.max(0,hi-1);float span=pass.gradientRatios[hi]-pass.gradientRatios[lo];
  float mix=span>0?Math.max(0,Math.min(1,(t-pass.gradientRatios[lo])/span)):0;
  float[] rgb=new float[3];for(int c=0;c<3;c++){int shift=16-c*8;float a=(pass.gradientColors[lo]>>>shift&255)/255f,b=(pass.gradientColors[hi]>>>shift&255)/255f;rgb[c]=(a+(b-a)*mix)*pass.tint[c];}return rgb;
 }
 public static float[] uv(Pass pass,float age,float u,float v){
  int frame=frame(pass,age);float seconds=Math.max(0,age)/20f;
  // Scroll only the single-image material. Leave wrap to the texture sampler;
  // fract at each vertex would stretch triangles across a wrap boundary.
  return new float[]{(frame%pass.cols+u)/pass.cols+seconds*pass.scrollU,(frame/pass.cols+v)/pass.rows-seconds*pass.scrollV};
 }
 public static void render(Pass pass,PoseStack pose,VertexConsumer out,float age,int overlay){
  float alpha=alpha(pass,age);float[] rgb=tint(pass,age);
  for(int[] face:pass.mesh.faces)for(int j:new int[]{0,2,1,1}){
   int i=face[j];float[] p=pass.mesh.vertices[i],uv=pass.mesh.uv[i],n=pass.mesh.normals[i];var normal=new Vector3f(n[0],-n[1],n[2]).mul(pose.last().normal()).normalize();
   float[] tex=uv(pass,age,uv[0],uv[1]);
   out.vertex(pose.last().pose(),p[0]/16,-p[1]/16,p[2]/16).color(rgb[0],rgb[1],rgb[2],alpha).uv(tex[0],tex[1]).overlayCoords(overlay).uv2(15728880).normal(normal.x,normal.y,normal.z).endVertex();
  }
 }
}
