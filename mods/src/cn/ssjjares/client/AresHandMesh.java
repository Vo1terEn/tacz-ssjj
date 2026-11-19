package cn.ssjjares.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Original four-weight Ares viewhands. No YSM default animation or scale track. */
public final class AresHandMesh {
    final int bones,vertices;
    final int[] parents,joints,left,right;
    final float[] data,weights;
    final Matrix4f[] local,inverse;
    final Clip[] clips;
    record Clip(float duration,int frames,float[] rotations){}
    private AresHandMesh(int b,int v,int[] p,int[] j,int[] l,int[] r,float[] d,float[] w,Matrix4f[] lm,Matrix4f[] im,Clip[] c){bones=b;vertices=v;parents=p;joints=j;left=l;right=r;data=d;weights=w;local=lm;inverse=im;clips=c;}
    private static int count(DataInputStream in,int lo,int hi)throws IOException{int n=in.readInt();if(n<lo||n>hi)throw new IOException("Ares hands count");return n;}
    private static float number(DataInputStream in)throws IOException{float f=in.readFloat();if(!Float.isFinite(f))throw new IOException("Ares hands finite");return f;}
    private static String text(DataInputStream in)throws IOException{int n=count(in,1,128);byte[] b=in.readNBytes(n);if(b.length!=n)throw new IOException("Ares hands EOF");return new String(b,java.nio.charset.StandardCharsets.UTF_8);}
    private static Matrix4f matrix(DataInputStream in)throws IOException{float[] m=new float[12];for(int i=0;i<12;i++)m[i]=number(in);return new Matrix4f(m[0],m[4],m[8],0,m[1],m[5],m[9],0,m[2],m[6],m[10],0,m[3],m[7],m[11],1);}
    public static AresHandMesh read(InputStream stream)throws IOException{
        try(var in=new DataInputStream(stream)){
            if(in.readInt()!=0x41464832||in.readInt()!=1)throw new IOException("Ares hands format");
            int b=count(in,1,128),v=count(in,1,30000),tris=count(in,1,30000),c=count(in,4,4);
            int[] p=new int[b];Matrix4f[] lm=new Matrix4f[b],im=new Matrix4f[b];
            for(int i=0;i<b;i++){text(in);p[i]=count(in,-1,i-1);lm[i]=matrix(in);im[i]=matrix(in);}
            float[] d=new float[v*8],w=new float[v*4];int[] j=new int[v*4];
            for(int i=0;i<v;i++){for(int k=0;k<8;k++)d[i*8+k]=number(in);for(int k=0;k<4;k++)j[i*4+k]=count(in,0,b-1);float total=0;for(int k=0;k<4;k++){w[i*4+k]=number(in);if(w[i*4+k]<0||w[i*4+k]>1.00001f)throw new IOException("Ares hand weight");total+=w[i*4+k];}if(Math.abs(total-1)>0.00001f)throw new IOException("Ares hand weights");}
            int[] ls=new int[tris*3],rs=new int[tris*3];int nl=0,nr=0;
            for(int i=0;i<tris;i++){int side=count(in,1,2);for(int k=0;k<3;k++){int ix=count(in,0,v-1);if(side==1)ls[nl++]=ix;else rs[nr++]=ix;}}
            Clip[] clips=new Clip[c];String[] expected={"TORSO_STAND","TORSO_RAISE","TORSO_ATTACK","TORSO_RELOAD"};
            for(int i=0;i<c;i++){if(!expected[i].equals(text(in)))throw new IOException("Ares hand clip");float duration=number(in);if(duration<=0||duration>30)throw new IOException("Ares hand duration");int frames=count(in,2,1000);float[] q=new float[frames*b*4];for(int f=0;f<q.length;f+=4){float norm=0;for(int k=0;k<4;k++){q[f+k]=number(in);norm+=q[f+k]*q[f+k];}if(Math.abs(norm-1)>0.001f)throw new IOException("Ares hand quaternion");}clips[i]=new Clip(duration,frames,q);}
            if(in.read()!=-1)throw new IOException("Ares hand trailing bytes");
            return new AresHandMesh(b,v,p,j,java.util.Arrays.copyOf(ls,nl),java.util.Arrays.copyOf(rs,nr),d,w,lm,im,clips);
        }
    }
    public static final class Scratch {
        final Matrix4f[] global,skin;
        final Matrix4f local=new Matrix4f(),rotation=new Matrix4f();
        final Quaternionf sample=new Quaternionf(),held=new Quaternionf();
        final float[] positions,normals;
        Scratch(AresHandMesh m){global=new Matrix4f[m.bones];skin=new Matrix4f[m.bones];for(int i=0;i<m.bones;i++){global[i]=new Matrix4f();skin[i]=new Matrix4f();}positions=new float[m.vertices*3];normals=new float[m.vertices*3];}
    }
    Scratch scratch(){return new Scratch(this);}
    private void sample(int clip,float progress,int bone,Quaternionf out){
        Clip c=clips[clip];float f=Math.max(0,Math.min(1,progress))*(c.frames-1);int a=(int)f,z=Math.min(a+1,c.frames-1);int ai=(a*bones+bone)*4,zi=(z*bones+bone)*4;float dot=0;for(int k=0;k<4;k++)dot+=c.rotations[ai+k]*c.rotations[zi+k];float sign=dot<0?-1:1,t=f-a;
        out.set(c.rotations[ai]*(1-t)+c.rotations[zi]*sign*t,c.rotations[ai+1]*(1-t)+c.rotations[zi+1]*sign*t,c.rotations[ai+2]*(1-t)+c.rotations[zi+2]*sign*t,c.rotations[ai+3]*(1-t)+c.rotations[zi+3]*sign*t).normalize();
    }
    /** All motion is rotational below the wrist; source bind offsets never change. */
    public void pose(Scratch s,int clip,float progress,float animationWeight){
        for(int b=0;b<bones;b++){
            if(clip<0)s.sample.identity();else sample(clip,progress,b,s.sample);
            s.held.identity().slerp(s.sample,animationWeight);s.rotation.rotation(s.held);s.local.set(local[b]).mul(s.rotation);
            if(parents[b]<0)s.global[b].set(s.local);else s.global[b].set(s.global[parents[b]]).mul(s.local);
            s.skin[b].set(s.global[b]).mul(inverse[b]);
        }
        for(int v=0;v<vertices;v++){
            int d=v*8,q=v*4,o=v*3;float x=data[d],y=data[d+1],z=data[d+2],nx=data[d+3],ny=data[d+4],nz=data[d+5],px=0,py=0,pz=0,ax=0,ay=0,az=0;
            for(int k=0;k<4;k++){
                float w=weights[q+k];if(w==0)continue;Matrix4f m=s.skin[joints[q+k]];
                px+=(m.m00()*x+m.m10()*y+m.m20()*z+m.m30())*w;py+=(m.m01()*x+m.m11()*y+m.m21()*z+m.m31())*w;pz+=(m.m02()*x+m.m12()*y+m.m22()*z+m.m32())*w;
                ax+=(m.m00()*nx+m.m10()*ny+m.m20()*nz)*w;ay+=(m.m01()*nx+m.m11()*ny+m.m21()*nz)*w;az+=(m.m02()*nx+m.m12()*ny+m.m22()*nz)*w;
            }
            s.positions[o]=px/16;s.positions[o+1]=py/16;s.positions[o+2]=pz/16;float len=(float)Math.sqrt(ax*ax+ay*ay+az*az);if(len<1e-6f)len=1;s.normals[o]=ax/len;s.normals[o+1]=ay/len;s.normals[o+2]=az/len;
        }
    }
    public void draw(PoseStack stack,VertexConsumer consumer,int light,int overlay,boolean leftArm,Scratch s){
        int[] indices=leftArm?left:right;var pose=stack.last();
        for(int f=0;f<indices.length;f+=3)for(int k=0;k<4;k++){int v=indices[f+Math.min(k,2)],p=v*3,d=v*8;consumer.vertex(pose.pose(),s.positions[p],s.positions[p+1],s.positions[p+2]).color(255,255,255,255).uv(data[d+6],data[d+7]).overlayCoords(overlay).uv2(light).normal(pose.normal(),s.normals[p],s.normals[p+1],s.normals[p+2]).endVertex();}
    }
}
