package cn.ssjjzeus.client;

import cn.ssjjdimension.client.DimensionEffectRenderType;
import cn.ssjjdimension.client.DimensionParticles;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Original world effects have their own lifetime, outside the world's shared
 * entity buffer queue. Flush each render event before another renderer changes
 * the camera/shader state. No textures or geometry are generated here. */
final class ZeusEntityEffectRenderer {
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));
    private ZeusEntityEffectRenderer() {}
    static void draw(DimensionParticles.Data source,float seconds,Vector3f right,Vector3f up,PoseStack pose) {
        for(var batch:DimensionParticles.sample(source,seconds,right,up)) {
            var type=DimensionEffectRenderType.of(new ResourceLocation(batch.texture()),"additive".equals(batch.render()));
            var out=BUFFERS.getBuffer(type);var mesh=batch.mesh();var color=batch.color();var uvST=batch.uvST();
            for(int[] face:mesh.triangles)for(int corner=0;corner<4;corner++) {
                int i=face[Math.min(corner,2)];var point=mesh.positions[i];var uv=mesh.uv[i];
                out.vertex(pose.last().pose(),point[0],point[1],point[2])
                    .color(clamp(color[0]),clamp(color[1]),clamp(color[2]),clamp(color[3]))
                    .uv(uv[0]*uvST[0]+uvST[2],uv[1]*uvST[1]-uvST[3])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.last().normal(),0,0,1).endVertex();
            }
        }
    }
    private static float clamp(float value){return Math.max(0,Math.min(1,value));}
    static void flush(){BUFFERS.endBatch();}
}
