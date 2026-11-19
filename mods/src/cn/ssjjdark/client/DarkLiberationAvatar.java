package cn.ssjjdark.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Private copies of the original 30 fps avatar and weapon frames. No live entity/model is animated. */
public final class DarkLiberationAvatar {
    public record Part(String name,float[] uv) {}
    public record Data(Part[] parts,float[][] avatar,float[][] weapon,float[] uv,int[] faces) {}
    public record Pose(float[] avatar,float[] weapon) {}
    private static final int[] TRIANGLE={0,1,2,2};
    private DarkLiberationAvatar(){}
    private static float[] flatten(JsonArray a){
        var values=new java.util.ArrayList<Float>();flatten(a,values);float[] result=new float[values.size()];
        for(int i=0;i<result.length;i++)result[i]=values.get(i);return result;
    }
    private static void flatten(JsonArray a,java.util.List<Float> values){for(var v:a)if(v.isJsonArray())flatten(v.getAsJsonArray(),values);else values.add(v.getAsFloat());}
    public static Data read(ResourceManager resources)throws IOException {
        JsonObject meta;
        try(var in=resources.open(new ResourceLocation("ssjj_dark","runtime/avatar.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){meta=JsonParser.parseReader(reader).getAsJsonObject();}
        var original=meta.getAsJsonArray("parts");Part[] parts=new Part[original.size()];
        for(int i=0;i<parts.length;i++){var p=original.get(i).getAsJsonObject();parts[i]=new Part(p.get("name").getAsString(),flatten(p.getAsJsonArray("uv")));if(parts[i].uv.length!=48)throw new IOException("Invalid original avatar UVs");}
        float[] uv=flatten(meta.getAsJsonArray("weapon_uv")),raw=flatten(meta.getAsJsonArray("weapon_faces"));int[] faces=new int[raw.length];
        for(int i=0;i<faces.length;i++){faces[i]=(int)raw[i];if(faces[i]!=raw[i]||faces[i]<0||faces[i]>=uv.length/2)throw new IOException("Invalid original weapon index");}
        if(parts.length!=10||faces.length%3!=0)throw new IOException("Invalid original avatar structure");
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(resources.open(new ResourceLocation("ssjj_dark","runtime/avatar.bin.gz")))))){
            int version=in.readInt(),count=in.readInt(),vertices=in.readInt(),weaponVertices=in.readInt();
            if(version!=1||count!=91||vertices!=parts.length*24||weaponVertices!=uv.length/2)throw new IOException("Invalid original avatar frames");
            float[][] avatar=frames(in,count,vertices*3),weapon=frames(in,count,weaponVertices*3);
            if(in.read()!=-1)throw new IOException("Trailing original avatar bytes");return new Data(parts,avatar,weapon,uv,faces);
        }
    }
    private static float[][] frames(DataInputStream in,int count,int stride)throws IOException {
        float[][] frames=new float[count][stride];for(var frame:frames)for(int i=0;i<stride;i++){frame[i]=in.readFloat();if(!Float.isFinite(frame[i]))throw new IOException("Nonfinite original avatar frame");}return frames;
    }
    /** Exactly the old source interpolation, including its blend back to source frame zero. */
    public static float[] sample(float[][] frames,float seconds,float recovery){
        float frame=Math.max(0,Math.min(90,seconds*30)),fraction=frame-(int)frame;int a=(int)frame,b=Math.min(90,a+1);
        float[] result=new float[frames[0].length];for(int i=0;i<result.length;i++)result[i]=(frames[a][i]*(1-fraction)+frames[b][i]*fraction)*(1-recovery)+frames[0][i]*recovery;return result;
    }
    public static Pose pose(Data data,float seconds,float recovery){return new Pose(sample(data.avatar,seconds,recovery),sample(data.weapon,seconds,recovery));}
    public static int body(Data data,Pose pose,Matrix4f matrix,VertexConsumer out,float alpha,int light,boolean slim){
        int vertices=0;
        for(int part=0;part<data.parts.length;part++){
            float[] p=pose.avatar,uv=data.parts[part].uv;int start=part*72;String name=data.parts[part].name;
            Vector3f center=new Vector3f();for(int v=0;v<24;v++)center.add(p[start+v*3],p[start+v*3+1],p[start+v*3+2]);center.div(24);
            Vector3f axis=new Vector3f(p[start+3]-p[start],p[start+4]-p[start+1],p[start+5]-p[start+2]).normalize();
            boolean arm=slim&&(name.endsWith("_arm")||name.endsWith("_forearm"));
            for(int v=0;v<24;v++){
                int i=start+v*3;Vector3f point=new Vector3f(p[i],p[i+1],p[i+2]);if(arm){float width=new Vector3f(point).sub(center).dot(axis);point.fma(-width*.25f,axis);}
                vertex(out,matrix,point.x,point.y,point.z,uv[v*2],uv[v*2+1],alpha,light);vertices++;
            }
        }return vertices;
    }
    public static int weapon(Data data,Pose pose,Matrix4f matrix,VertexConsumer out,float alpha,int light){
        int vertices=0;for(int triangle=0;triangle<data.faces.length;triangle+=3)for(int corner:TRIANGLE){int index=data.faces[triangle+corner];vertex(out,matrix,pose.weapon[index*3],pose.weapon[index*3+1],pose.weapon[index*3+2],data.uv[index*2],1-data.uv[index*2+1],alpha,light);vertices++;}return vertices;
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,float x,float y,float z,float u,float v,float alpha,int light){out.vertex(matrix,x,y,z).color(1f,1f,1f,alpha).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0,1,0).endVertex();}
}
