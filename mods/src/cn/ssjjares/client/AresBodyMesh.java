package cn.ssjjares.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Shared original mesh; only six pose matrices and unique vertices deform per player. */
public final class AresBodyMesh {
    private static final float[][] PIVOTS={{0,0,0},{0,0,0},{5f/16,2f/16,0},{-5f/16,2f/16,0},{1.9f/16,12f/16,0},{-1.9f/16,12f/16,0}};
    final int vertices; final float[] data; final int[] indices; final Matrix4f[] bind;
    public static final class Scratch {
        final Matrix4f[] matrices=new Matrix4f[6]; final Matrix3f[] normals=new Matrix3f[6];
        final PoseStack partPose=new PoseStack(); float[] position=new float[0],normal=new float[0];
        public Scratch(){for(int i=0;i<6;i++){matrices[i]=new Matrix4f();normals[i]=new Matrix3f();}}
        void size(int count){if(position.length<count*3){position=new float[count*3];normal=new float[count*3];}}
        public float[] positions(){return position;}
        public float[] normals(){return normal;}
    }
    private AresBodyMesh(int vertices,float[] data,int[] indices,Matrix4f[] bind){this.vertices=vertices;this.data=data;this.indices=indices;this.bind=bind;}
    public static AresBodyMesh read(InputStream input)throws IOException{
        try(var in=new DataInputStream(input)){
            if(in.readInt()!=0x41525331||in.readInt()!=1)throw new IOException("Invalid Ares mesh header");
            int count=in.readInt(),faces=in.readInt(),parts=in.readInt();
            if(count<1||count>100000||faces<3||faces>600000||faces%3!=0||parts!=6)throw new IOException("Invalid Ares mesh bounds");
            Matrix4f[] bind=new Matrix4f[6];
            for(int i=0;i<6;i++){float[] row=new float[16];for(int k=0;k<16;k++)row[k]=finite(in.readFloat());bind[i]=new Matrix4f().set(row).transpose();}
            float[] data=new float[count*14];for(int i=0;i<data.length;i++)data[i]=finite(in.readFloat());
            for(int i=0;i<count;i++){float total=0;for(int k=8;k<14;k++){if(data[i*14+k]<0||data[i*14+k]>1.0001f)throw new IOException("Invalid source skin weight");total+=data[i*14+k];}if(Math.abs(total-1)>.001)throw new IOException("Unnormalized source skin");}
            int[] indices=new int[faces];for(int i=0;i<faces;i++){indices[i]=in.readInt();if(indices[i]<0||indices[i]>=count)throw new IOException("Invalid Ares index");}
            if(in.read()!=-1)throw new IOException("Trailing Ares mesh data");
            return new AresBodyMesh(count,data,indices,bind);
        }
    }
    private static float finite(float value)throws IOException{if(!Float.isFinite(value))throw new IOException("Non-finite Ares data");return value;}
    private static ModelPart part(PlayerModel<?> m,int i){return switch(i){case 0->m.body;case 1->m.head;case 2->m.leftArm;case 3->m.rightArm;case 4->m.leftLeg;default->m.rightLeg;};}
    public void pose(PlayerModel<?> model,Scratch scratch){
        for(int i=0;i<6;i++){
            var p=part(model,i);scratch.partPose.setIdentity();p.translateAndRotate(scratch.partPose);
            var neutral=PIVOTS[i];
            // Slim arm x is 5 rather than the vanilla cube width; both native
            // models use the same shoulder pivot. Crouch/aim shifts stay native.
            scratch.matrices[i].set(scratch.partPose.last().pose()).translate(-neutral[0],-neutral[1],-neutral[2]).mul(bind[i]);
            scratch.normals[i].set(scratch.matrices[i]).invert().transpose();
        }
        deform(scratch);
    }
    public void deform(Scratch scratch){
        scratch.size(vertices);
        for(int v=0;v<vertices;v++){
            int offset=v*14;float x=data[offset],y=data[offset+1],z=data[offset+2],nx=data[offset+3],ny=data[offset+4],nz=data[offset+5];
            float ox=0,oy=0,oz=0,onx=0,ony=0,onz=0;
            for(int i=0;i<6;i++){
                float w=data[offset+8+i];if(w==0)continue;var m=scratch.matrices[i];var n=scratch.normals[i];
                ox+=w*(m.m00()*x+m.m10()*y+m.m20()*z+m.m30());oy+=w*(m.m01()*x+m.m11()*y+m.m21()*z+m.m31());oz+=w*(m.m02()*x+m.m12()*y+m.m22()*z+m.m32());
                onx+=w*(n.m00()*nx+n.m10()*ny+n.m20()*nz);ony+=w*(n.m01()*nx+n.m11()*ny+n.m21()*nz);onz+=w*(n.m02()*nx+n.m12()*ny+n.m22()*nz);
            }
            float len=(float)Math.sqrt(onx*onx+ony*ony+onz*onz);if(len>1e-8f){onx/=len;ony/=len;onz/=len;}
            int k=v*3;scratch.position[k]=ox;scratch.position[k+1]=oy;scratch.position[k+2]=oz;scratch.normal[k]=onx;scratch.normal[k+1]=ony;scratch.normal[k+2]=onz;
        }
    }
    public void draw(PoseStack pose,VertexConsumer out,int light,int overlay,Scratch scratch){
        var p=pose.last();
        for(int f=0;f<indices.length;f+=3)for(int c=0;c<4;c++){
            int i=indices[f+Math.min(c,2)],v=i*3,d=i*14;
            out.vertex(p.pose(),scratch.position[v],scratch.position[v+1],scratch.position[v+2]).color(1f,1f,1f,1f).uv(data[d+6],data[d+7]).overlayCoords(overlay).uv2(light).normal(p.normal(),scratch.normal[v],scratch.normal[v+1],scratch.normal[v+2]).endVertex();
        }
    }
    public int vertexCount(){return vertices;}
    public int triangleCount(){return indices.length/3;}
}
