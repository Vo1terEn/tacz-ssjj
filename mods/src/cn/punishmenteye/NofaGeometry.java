package cn.punishmenteye;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
/** Shared actual client geometry, also used by offline native-vertex verification. */
public final class NofaGeometry {
 public static void quad(PoseStack pose,VertexConsumer out,Vec3[] points,float[] uv,float alpha){
  for(int i=0;i<4;i++){var p=points[i];out.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(1f,1f,1f,alpha).uv(uv[i*2],uv[i*2+1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0,1,0).endVertex();}
 }
 public static void render(PoseStack pose,VertexConsumer out,Vec3 p,float w,float h,Quaternionf camera,float u0,float v0,float u1,float v1,float alpha){
  pose.pushPose();pose.translate(p.x,p.y,p.z);pose.mulPose(camera);
  vertex(pose,out,-w/2,-h/2,u0,v1,alpha);vertex(pose,out,w/2,-h/2,u1,v1,alpha);vertex(pose,out,w/2,h/2,u1,v0,alpha);vertex(pose,out,-w/2,h/2,u0,v0,alpha);pose.popPose();
 }
 static void vertex(PoseStack p,VertexConsumer out,float x,float y,float u,float v,float a){out.vertex(p.last().pose(),x,y,0).color(1f,1f,1f,a).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0,0,1).endVertex();}
}
