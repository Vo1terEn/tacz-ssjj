package cn.punishmenteye;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
/** Every luminous primitive is followed by its exact central inverse, including UV and flicker. */
public final class CloudScene {
 public record Quad(String texture,Vec3[] p,float[] uv,float alpha){}
 static Vec3 sphere(double theta,double phi){return new Vec3(Math.cos(theta)*Math.cos(phi),Math.sin(phi),Math.sin(theta)*Math.cos(phi));}
 static Vec3 ellipsoid(Vec3 p,double grow){return new Vec3(p.x*8*grow,p.y*4*grow,p.z*8*grow);}
 static Vec3 rotate(Vec3 p,Quaternionf q){var v=new Vector3f((float)p.x,(float)p.y,(float)p.z);q.transform(v);return new Vec3(v.x,v.y,v.z);}
 static float[] uv(int frame,int cols,int rows,float left,float right){int f=Math.floorMod(frame,cols*rows);float pad=.016f;return new float[]{(f%cols+pad+left*(1-2*pad))/cols,(f/cols+1-pad)/rows,(f%cols+pad+right*(1-2*pad))/cols,(f/cols+1-pad)/rows,(f%cols+pad+right*(1-2*pad))/cols,(f/cols+pad)/rows,(f%cols+pad+left*(1-2*pad))/cols,(f/cols+pad)/rows};}
 static void pair(List<Quad> out,String texture,Vec3[] p,float[] uv,float alpha){if(alpha<=0)return;out.add(new Quad(texture,p,uv,alpha));out.add(new Quad(texture,Arrays.stream(p).map(v->v.scale(-1)).toArray(Vec3[]::new),uv.clone(),alpha));}
 static void billboard(List<Quad> out,String texture,Vec3 center,float size,Quaternionf camera,int frame,int cols,int rows,float alpha){Vec3 x=rotate(new Vec3(size/2,0,0),camera),y=rotate(new Vec3(0,size/2,0),camera);pair(out,texture,new Vec3[]{center.subtract(x).subtract(y),center.add(x).subtract(y),center.add(x).add(y),center.subtract(x).add(y)},uv(frame,cols,rows,0,1),alpha);}
 public static Vec3[] chain(int index,float age,double grow){
  // Use the source atlas waveform directly. Do not introduce extra geometry bends or node balls.
  double turn=age*.009+index*Math.PI/4;var d=new Vec3(Math.cos(turn),.35*Math.sin(index*1.9+age*.006),Math.sin(turn)).normalize();
  return new Vec3[]{ellipsoid(d.scale(.06),grow),ellipsoid(d.scale(.83),grow)};
 }
 public static List<Quad> quads(float age,int duration,int seed,Quaternionf camera){
  var out=new ArrayList<Quad>();if(age<0||age>=duration)return out;float fade=Math.min(1,(duration-age)/10f),grow=Math.min(1,.18f+age/4f);int frame=(int)(age*.75f);double flow=age*.018+(seed&255)*.003;
  // A closed translucent ellipsoid, generated as a hemisphere plus its exact inverse.
  int slices=16,stacks=8;
  for(int j=0;j<stacks;j++)for(int i=0;i<slices;i++){
   double a=flow+Math.PI*i/slices,b=flow+Math.PI*(i+1)/slices,c=-Math.PI/2+Math.PI*j/stacks,d=-Math.PI/2+Math.PI*(j+1)/stacks;
   Vec3[] points={ellipsoid(sphere(a,c),grow),ellipsoid(sphere(b,c),grow),ellipsoid(sphere(b,d),grow),ellipsoid(sphere(a,d),grow)};
   pair(out,"cloud_flow_t073",points,uv(frame,3,5,i/(float)slices,(i+1)/(float)slices),.15f*fade);
  }
  // Three flowing broad great-ellipse belts. All remain on the same 8/4/8 ellipsoid.
  for(int band=0;band<3;band++){
   var rotation=new Quaternionf().rotateY((float)flow).rotateX(band==1?1.1f:band==2?-1.1f:0);
   for(int i=0;i<24;i++){
    double a=Math.PI*i/24,b=Math.PI*(i+1)/24;Vec3[] p={ellipsoid(rotate(sphere(a,-.10),rotation),grow),ellipsoid(rotate(sphere(b,-.10),rotation),grow),ellipsoid(rotate(sphere(b,.10),rotation),grow),ellipsoid(rotate(sphere(a,.10),rotation),grow)};
    pair(out,"cloud_flow_t073",p,uv(frame+band*3,3,5,i/24f,(i+1)/24f),.55f*fade);
   }
  }
  Vec3 view=rotate(new Vec3(0,0,1),camera);
  for(int n=0;n<4;n++){
   Vec3[] path=chain(n,age,grow),sides=new Vec3[path.length];
   for(int j=0;j<path.length;j++){var tangent=path[Math.min(j+1,path.length-1)].subtract(path[Math.max(0,j-1)]);var side=tangent.cross(view);if(side.lengthSqr()<1e-8)side=tangent.cross(new Vec3(1,0,0));sides[j]=side.normalize().scale(.6*grow);}
   for(int j=0;j<path.length-1;j++)pair(out,"cloud_chain_t066",new Vec3[]{path[j].subtract(sides[j]),path[j+1].subtract(sides[j+1]),path[j+1].add(sides[j+1]),path[j].add(sides[j])},uv(frame+n,1,6,0,1),.95f*fade);
  }
  // Opposed sparks share the exact same frame and opacity, rather than independent random timers.
  for(int n=0;n<6;n++){
   if(Math.floorMod((int)age/2+n*3,7)>3)continue;
   double theta=flow+n*Math.PI/3,phi=.65*Math.sin(n*1.7+age*.01),r=.16;
   Vec3[] p={ellipsoid(sphere(theta-r,phi-r),grow),ellipsoid(sphere(theta+r,phi-r),grow),ellipsoid(sphere(theta+r,phi+r),grow),ellipsoid(sphere(theta-r,phi+r),grow)};
   pair(out,"cloud_arcs",p,uv(frame+n*2,4,4,0,1),.95f*fade);
  }
  billboard(out,"cloud_glow",Vec3.ZERO,2.7f*grow,camera,0,1,1,.35f*fade);
  billboard(out,"cloud_core",Vec3.ZERO,1.25f*grow,camera,(int)(age*1.3f),4,2,.65f*fade);
  return out;
 }
}
