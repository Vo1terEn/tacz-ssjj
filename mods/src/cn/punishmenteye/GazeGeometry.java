package cn.punishmenteye;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;

/** The entire eye quad stays above the interpolated entity bounding-box top. */
public final class GazeGeometry {
    public static final float WIDTH=1.28f,HEIGHT=.8f,GAP=.18f,ALPHA=.86f;
    public static void render(PoseStack pose,VertexConsumer out,double x,double feetY,double z,float bodyHeight,Quaternionf camera){
        render(pose,out,x,feetY,z,bodyHeight,camera,0);
    }
    public static void render(PoseStack pose,VertexConsumer out,double x,double feetY,double z,float bodyHeight,Quaternionf camera,int frame){
        pose.pushPose();pose.translate(x,feetY+bodyHeight+GAP+HEIGHT/2,z);pose.mulPose(camera);
        // Half a texel inset avoids bleeding into a neighboring animation frame.
        float low=(Math.floorMod(frame,6)+.5f/320)/6,high=(Math.floorMod(frame,6)+1-.5f/320)/6;
        vertex(pose,out,-WIDTH/2,-HEIGHT/2,0,high);vertex(pose,out,WIDTH/2,-HEIGHT/2,1,high);
        vertex(pose,out,WIDTH/2,HEIGHT/2,1,low);vertex(pose,out,-WIDTH/2,HEIGHT/2,0,low);pose.popPose();
    }
    static void vertex(PoseStack pose,VertexConsumer out,float x,float y,float u,float v){
        out.vertex(pose.last().pose(),x,y,0).color(1f,1f,1f,ALPHA).uv(u,v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0,0,1).endVertex();
    }
}
