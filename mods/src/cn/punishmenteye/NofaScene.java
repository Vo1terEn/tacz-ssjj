package cn.punishmenteye;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
/** Runtime effect composition shared with native-vertex offline verification. */
public final class NofaScene {
 public record Layer(String texture,Vec3 offset,float w,float h,Quaternionf rotation,float u0,float v0,float u1,float v1,float alpha){}
 static void sprite(List<Layer> out,String texture,Vec3 p,float w,float h,Quaternionf rotation,int frame,int cols,int rows,float a){
  int f=Math.floorMod(frame,cols*rows);float pad=.016f;
  if(a>0&&w>0&&h>0)out.add(new Layer(texture,p,w,h,new Quaternionf(rotation),(f%cols+pad)/cols,(f/cols+pad)/rows,(f%cols+1-pad)/cols,(f/cols+1-pad)/rows,Math.min(1,a)));
 }
 public static List<Layer> layers(int type,float age,int duration,int seed,Quaternionf camera){
  var out=new ArrayList<Layer>();if(age<0||age>=duration)return out;
  if(type==1){sprite(out,"nofa_ball",Vec3.ZERO,.75f,.75f,camera,(int)(age*1.5f),4,2,.95f);return out;}
  if(type==0||type==4){
   // Keep the strike vertical in world space; camera pitch must not lean it toward the viewer.
   var right=camera.transform(new org.joml.Vector3f(1,0,0));var upright=new Quaternionf().rotationY((float)Math.atan2(-right.z,right.x));
   float fade=Math.min(1,(duration-age)/2f);
   sprite(out,type==4?"lightning_gray_t116":"lightning_t116",new Vec3(0,3.5,0),1.2f,7,upright,(int)age,1,8,.95f*fade);
   return out;
  }
  return out;
 }
}
