package cn.ssjjhyper.client;

import cn.ssjjdimension.client.DimensionParticles;
import com.mojang.blaze3d.vertex.*;
import java.util.*;

/** Encodes an authored effect once, retaining consecutive materials and every HDR pass. */
public final class HyperChargeMeshPlan {
    public record Run(String texture, boolean additive, BufferBuilder.RenderedBuffer buffer) {}
    private static final BufferBuilder MAIN=new BufferBuilder(65536), PASS=new BufferBuilder(65536);
    private HyperChargeMeshPlan() {}

    public static List<Run> compile(List<DimensionParticles.Batch> batches) {
        var result=new ArrayList<Run>();String texture=null;boolean additive=false;
        for(var batch:batches) {
            boolean next="additive".equals(batch.render());
            if(!batch.texture().equals(texture)||next!=additive) {
                if(texture!=null)result.add(new Run(texture,additive,MAIN.end()));
                texture=batch.texture();additive=next;
                MAIN.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            }
            encode(MAIN,batch,additive);
        }
        if(texture!=null)result.add(new Run(texture,additive,MAIN.end()));
        return result;
    }
    private static void encode(BufferBuilder out,DimensionParticles.Batch batch,boolean additive) {
        var c=batch.color();float rgb=Math.max(1,Math.max(c[0],Math.max(c[1],c[2])));
        float energy=additive?Math.min(16,rgb*c[3]):Math.min(1,c[3]);
        float red=Math.min(1,c[0]/(additive?rgb:1)),green=Math.min(1,c[1]/(additive?rgb:1)),blue=Math.min(1,c[2]/(additive?rgb:1));
        if(additive&&energy>1) {
            PASS.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            write(PASS,batch,red,green,blue,1);
            var encoded=PASS.end();
            try {
                var bytes=encoded.vertexBuffer();int full=(int)energy;
                for(int pass=0;pass<full;pass++){bytes.position(0);out.putBulkData(bytes);}
                float fraction=energy-full;
                if(fraction>0){byte alpha=(byte)(int)(fraction*255);for(int offset=15;offset<bytes.limit();offset+=36)bytes.put(offset,alpha);bytes.position(0);out.putBulkData(bytes);}
            } finally {encoded.release();}
        } else for(int pass=0;pass<(additive?(int)Math.ceil(energy):1);pass++)
            write(out,batch,red,green,blue,additive?Math.min(1,energy-pass):energy);
    }
    private static void write(VertexConsumer out,DimensionParticles.Batch batch,float r,float g,float b,float alpha) {
        var mesh=batch.mesh();var st=batch.uvST();
        for(int[] face:mesh.triangles)for(int corner=0;corner<4;corner++) {
            int i=face[Math.min(corner,2)];var p=mesh.positions[i];var uv=mesh.uv[i];
            out.vertex(p[0],p[1],p[2],r,g,b,alpha,uv[0]*st[0]+st[2],uv[1]*st[1]-st[3],0,15728880,0,0,1);
        }
    }
}
