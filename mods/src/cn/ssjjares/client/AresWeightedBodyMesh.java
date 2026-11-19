package cn.ssjjares.client;

import cn.ssjjcompat.client.FastEffectVertices;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** One indexed source vertex has one skinned position, including at every joint seam. */
public final class AresWeightedBodyMesh {
    public final int vertices,bones;public final int[] indices,parent;public final String[] names;
    final float[] source;final byte[] joints;final Matrix4f[] inverse;final Matrix4f conversion;
    private final byte[] influenceMasks;
    final Map<String,Clip> clips=new LinkedHashMap<>();boolean[] upper;int[] order;int root;
    public record Clip(String name,float duration,boolean loop,int frames,float[] values){}
    public static final class Scratch {
        public final Matrix4f[] world,skin;final Matrix3f[] normals;final Quaternionf a=new Quaternionf(),b=new Quaternionf(),c=new Quaternionf();
        public final float[] local,previous,overlay,position,normal;final Matrix4f temp=new Matrix4f();
        Scratch(int count,int vertices){world=new Matrix4f[count];skin=new Matrix4f[count];normals=new Matrix3f[count];for(int i=0;i<count;i++){world[i]=new Matrix4f();skin[i]=new Matrix4f();normals[i]=new Matrix3f();}local=new float[count*7];previous=new float[count*7];overlay=new float[count*7];position=new float[vertices*3];normal=new float[vertices*3];}
    }
    private AresWeightedBodyMesh(int count, int[] indices, int[] parent, String[] names, float[] source,
                                 byte[] joints, Matrix4f[] inverse, Matrix4f conversion) {
        this.vertices = count;
        this.indices = indices;
        this.parent = parent;
        this.names = names;
        this.source = source;
        this.joints = joints;
        this.inverse = inverse;
        this.conversion = conversion;
        this.bones = parent.length;
        influenceMasks = new byte[count];
        for (int i = 0; i < count; i++) {
            for (int slot = 0; slot < 4; slot++) {
                if (source[i * 12 + 8 + slot] != 0) influenceMasks[i] |= 1 << slot;
            }
        }
    }
    static float finite(float f)throws IOException{if(!Float.isFinite(f))throw new IOException("Non-finite weighted Ares data");return f;}
    static float[] floats(DataInputStream in,int count)throws IOException{float[] f=new float[count];for(int i=0;i<count;i++)f[i]=finite(in.readFloat());return f;}
    public static AresWeightedBodyMesh read(InputStream input,InputStream motion)throws IOException {
        AresWeightedBodyMesh mesh;
        try(var in=new DataInputStream(input)){
            if(in.readInt()!=0x41535733||in.readInt()!=3)throw new IOException("Ares weighted mesh version");
            int count=in.readInt(),corners=in.readInt(),n=in.readInt();if(count<1||count>50000||corners<3||corners>300000||corners%3!=0||n!=38)throw new IOException("Ares mesh bounds");
            var C=new Matrix4f().set(floats(in,16)).transpose();var inverse=new Matrix4f[n];int[] parent=new int[n];String[] names=new String[n];
            for(int i=0;i<n;i++){names[i]=in.readUTF();parent[i]=in.readInt();if(parent[i]<-1||parent[i]>=n||parent[i]==i)throw new IOException("Ares parent");inverse[i]=new Matrix4f().set(floats(in,16)).transpose();}
            float[] source=new float[count*12];byte[] joints=new byte[count*4];
            for(int i=0;i<count;i++){for(int k=0;k<12;k++)source[i*12+k]=finite(in.readFloat());float sum=0;for(int k=0;k<4;k++){float w=source[i*12+8+k];if(w<0||w>1.0001f)throw new IOException("Ares weight");sum+=w;int j=in.readUnsignedByte();if(j>=n)throw new IOException("Ares joint");joints[i*4+k]=(byte)j;}if(Math.abs(sum-1)>.0001f)throw new IOException("Ares weight sum");}
            int[] indices=new int[corners];for(int i=0;i<corners;i++){indices[i]=in.readInt();if(indices[i]<0||indices[i]>=count)throw new IOException("Ares index");}if(in.read()!=-1)throw new IOException("Trailing Ares mesh");
            mesh=new AresWeightedBodyMesh(count,indices,parent,names,source,joints,inverse,C);
        }
        try(var in=new DataInputStream(motion)){
            if(in.readInt()!=0x41534d33||in.readInt()!=3||in.readInt()!=mesh.bones)throw new IOException("Ares motion version");
            int count=in.readInt();mesh.root=in.readInt();if(count<1||count>128||mesh.root<0||mesh.root>=mesh.bones)throw new IOException("Ares clip bounds");mesh.upper=new boolean[mesh.bones];for(int j=0;j<mesh.bones;j++)mesh.upper[j]=in.readUnsignedByte()!=0;
            for(int i=0;i<count;i++){String name=in.readUTF();float duration=finite(in.readFloat());boolean loop=in.readBoolean();int frames=in.readInt();if(duration<=0||duration>60||frames<2||frames>4000||mesh.clips.containsKey(name))throw new IOException("Ares clip");float[] values=floats(in,frames*mesh.bones*7);for(int v=0;v<values.length;v+=7){float l=0;for(int c=3;c<7;c++)l+=values[v+c]*values[v+c];if(Math.abs(l-1)>.001f)throw new IOException("Ares quaternion");}mesh.clips.put(name,new Clip(name,duration,loop,frames,values));}if(in.read()!=-1)throw new IOException("Trailing Ares motion");
        }
        mesh.order=new int[mesh.bones];int used=0;boolean[] done=new boolean[mesh.bones];for(int round=0;round<mesh.bones;round++)for(int i=0;i<mesh.bones;i++)if(!done[i]&&(mesh.parent[i]<0||done[mesh.parent[i]])){done[i]=true;mesh.order[used++]=i;}if(used!=mesh.bones)throw new IOException("Cyclic Ares skeleton");return mesh;
    }
    public Scratch scratch(){return new Scratch(bones,vertices);}
    public Clip clip(String name){return clips.getOrDefault(name,clips.get("idle"));}
    public void sample(String name,float time,float[] out,Scratch s){
        Clip c=clip(name);if(c.loop)time=(time%c.duration+c.duration)%c.duration;else time=Math.max(0,Math.min(c.duration,time));
        float frame=time/c.duration*(c.frames-1);int a=Math.min(c.frames-1,(int)frame),b=Math.min(c.frames-1,a+1);float alpha=frame-a;
        for(int i=0;i<bones;i++){int o=i*7,aa=(a*bones+i)*7,bb=(b*bones+i)*7;for(int k=0;k<3;k++)out[o+k]=c.values[aa+k]+(c.values[bb+k]-c.values[aa+k])*alpha;s.a.set(c.values[aa+3],c.values[aa+4],c.values[aa+5],c.values[aa+6]);s.b.set(c.values[bb+3],c.values[bb+4],c.values[bb+5],c.values[bb+6]);s.a.slerp(s.b,alpha).normalize();out[o+3]=s.a.x;out[o+4]=s.a.y;out[o+5]=s.a.z;out[o+6]=s.a.w;}
    }
    public void pose(Scratch s,String action,float time,String from,float fromTime,float blend,String overlay,float overlayTime,float overlayWeight){
        sample(action,time,s.local,s);
        if(blend<1&&from!=null){sample(from,fromTime,s.previous,s);for(int i=0;i<bones;i++)mix(s.previous,s.local,i,blend,s);System.arraycopy(s.previous,0,s.local,0,s.local.length);}
        if(overlay!=null&&overlayWeight>0){sample(overlay,overlayTime,s.overlay,s);for(int i=0;i<bones;i++)if(upper[i])mix(s.local,s.overlay,i,overlayWeight,s);}
        skin(s);
    }
    private static void mix(float[] a,float[] b,int i,float weight,Scratch s){int k=i*7;for(int j=0;j<3;j++)a[k+j]+=(b[k+j]-a[k+j])*weight;s.a.set(a[k+3],a[k+4],a[k+5],a[k+6]);s.b.set(b[k+3],b[k+4],b[k+5],b[k+6]);s.a.slerp(s.b,weight).normalize();a[k+3]=s.a.x;a[k+4]=s.a.y;a[k+5]=s.a.z;a[k+6]=s.a.w;}
    public void skin(Scratch s){
        for(int i:order){int k=i*7;s.a.set(s.local[k+3],s.local[k+4],s.local[k+5],s.local[k+6]);s.temp.translationRotate(s.local[k],s.local[k+1],s.local[k+2],s.a);if(parent[i]>=0)s.world[i].set(s.world[parent[i]]).mul(s.temp);else s.world[i].set(s.temp);s.skin[i].set(conversion).mul(s.world[i]).mul(inverse[i]);s.normals[i].set(s.skin[i]).invert().transpose();}
        skinVertices(s);
    }
    /** Native delta = C * animatedWorld * inverseBind * inverse(C), in blocks. */
    public void skinFromNative(Scratch s,Matrix4f[] delta){
        if(delta.length!=bones)throw new IllegalArgumentException("Ares native bone count");
        for(int i=0;i<bones;i++){s.skin[i].set(delta[i]).mul(conversion);s.normals[i].set(s.skin[i]).invert().transpose();}
        skinVertices(s);
    }
    private void skinVertices(Scratch scratch) {
        for (int i = 0; i < vertices; i++) {
            int vertex = i * 12;
            float x = source[vertex], y = source[vertex + 1], z = source[vertex + 2];
            float nx = source[vertex + 3], ny = source[vertex + 4], nz = source[vertex + 5];
            float px = 0, py = 0, pz = 0, ax = 0, ay = 0, az = 0;
            // Visit set bits in source order to preserve blending and rounding for sparse weights.
            for (int mask = influenceMasks[i]; mask != 0; mask &= mask - 1) {
                int slot = Integer.numberOfTrailingZeros(mask);
                float weight = source[vertex + 8 + slot];
                int bone = joints[i * 4 + slot] & 255;
                var transform = scratch.skin[bone];
                var normal = scratch.normals[bone];
                px += weight * (transform.m00() * x + transform.m10() * y + transform.m20() * z + transform.m30());
                py += weight * (transform.m01() * x + transform.m11() * y + transform.m21() * z + transform.m31());
                pz += weight * (transform.m02() * x + transform.m12() * y + transform.m22() * z + transform.m32());
                ax += weight * (normal.m00() * nx + normal.m10() * ny + normal.m20() * nz);
                ay += weight * (normal.m01() * nx + normal.m11() * ny + normal.m21() * nz);
                az += weight * (normal.m02() * nx + normal.m12() * ny + normal.m22() * nz);
            }
            float length = (float)Math.sqrt(ax * ax + ay * ay + az * az);
            if (length > 1e-8f) {
                ax /= length;
                ay /= length;
                az /= length;
            }
            int output = i * 3;
            scratch.position[output] = px;
            scratch.position[output + 1] = py;
            scratch.position[output + 2] = pz;
            scratch.normal[output] = ax;
            scratch.normal[output + 1] = ay;
            scratch.normal[output + 2] = az;
        }
    }

    public void draw(PoseStack pose, VertexConsumer out, int light, int overlay, Scratch scratch) {
        var transform = pose.last().pose();
        var normals = pose.last().normal();
        for (int face = 0; face < indices.length; face += 3) {
            for (int corner = 0; corner < 4; corner++) {
                // NEW_ENTITY takes quads; duplicate the final triangle corner.
                int index = indices[face + Math.min(corner, 2)];
                int xyz = index * 3, uv = index * 12 + 6;
                var vertex = FastEffectVertices.vertex(out, transform, scratch.position[xyz],
                    scratch.position[xyz + 1], scratch.position[xyz + 2])
                    .color(1f, 1f, 1f, 1f).uv(source[uv], source[uv + 1]).overlayCoords(overlay).uv2(light);
                FastEffectVertices.normal(vertex, normals, scratch.normal[xyz],
                    scratch.normal[xyz + 1], scratch.normal[xyz + 2]).endVertex();
            }
        }
    }
    public Matrix4f jointPose(Scratch s,String name,Matrix4f out){for(int i=0;i<bones;i++)if(names[i].equals(name))return out.set(conversion).mul(s.world[i]);return out.identity();}
}
