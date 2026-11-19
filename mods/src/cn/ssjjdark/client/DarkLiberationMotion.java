package cn.ssjjdark.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable retarget of the original skill; the actual model keeps its own bones and lengths. */
public final class DarkLiberationMotion {
    public static final class Bone {public String name;public int parent;public float[] pivot,baseRotation,staticEuler;public float[][] vertices;}
    public static final class Data {public int schema,fps,frames;public float duration,genericScale;public Map<String,float[][]> generic,ares,genericPosition,aresPosition;public Bone[] genericMesh;}
    private static volatile Data data;
    private DarkLiberationMotion(){}
    public static void reset(){data=null;}
    public static boolean ready(){return data!=null;}
    public static Data read(ResourceManager resources)throws IOException {
        try(var in=new GZIPInputStream(resources.open(new ResourceLocation("ssjj_dark","ysm/liberation_motion.json.gz")));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            Data d=new Gson().fromJson(reader,Data.class);
            if(d.schema!=2||d.fps!=30||d.frames!=91||d.duration!=3||d.generic.size()!=14||d.ares.size()!=38||d.genericMesh.length<40||d.genericMesh.length>200||d.genericScale!=.7f||d.genericPosition==null||d.aresPosition==null||!d.genericPosition.keySet().equals(Set.of("AllBody"))||!d.aresPosition.keySet().equals(Set.of("Bip01_Pelvis")))throw new IOException("Invalid complete source YSM liberation motion");
            for(var tracks:List.of(d.generic,d.ares))for(var frames:tracks.values()){
                if(frames.length!=91)throw new IOException("Invalid source liberation frames");
                for(float[] q:frames){if(q.length!=4)throw new IOException("Invalid source quaternion");float norm=0;for(float f:q){if(!Float.isFinite(f))throw new IOException("Nonfinite source quaternion");norm+=f*f;}if(Math.abs(norm-1)>.001)throw new IOException("Unnormalized source quaternion");}
            }
            for(int i=0;i<d.genericMesh.length;i++){var b=d.genericMesh[i];if(b.parent>=i||b.parent< -1||b.pivot.length!=3||b.baseRotation.length!=4||b.staticEuler.length!=3||b.vertices.length%4!=0)throw new IOException("Invalid original Default skeleton");for(var v:b.vertices){if(v.length!=5)throw new IOException("Invalid source Default vertex");for(float f:v)if(!Float.isFinite(f))throw new IOException("Nonfinite source Default vertex");}}
            for(var tracks:List.of(d.genericPosition,d.aresPosition))for(var frames:tracks.values()){if(frames.length!=91)throw new IOException("Invalid complete source positions");for(var p:frames){if(p.length!=3)throw new IOException("Invalid source translation");for(float f:p)if(!Float.isFinite(f))throw new IOException("Nonfinite source translation");}}
            return data=d;
        }
    }
    public static Set<String> genericBones(){var d=data;return d==null?Set.of():d.generic.keySet();}
    public static Set<String> positionBones(){var d=data;return d==null?Set.of():d.genericPosition.keySet();}
    public static Vector3f position(Map<String,float[][]> tracks,String name,float seconds,Vector3f out){
        var frames=tracks.get(name);if(frames==null)return null;float frame=Math.max(0,Math.min(90,seconds*30)),f=frame-(int)frame;int a=(int)frame,b=Math.min(90,a+1);return out.set(frames[a][0],frames[a][1],frames[a][2]).lerp(new Vector3f(frames[b][0],frames[b][1],frames[b][2]),f);
    }
    /** Default native YSM position channels are pixels; native X is reflected. */
    private static Vector3f genericPositionRaw(String name,float seconds,float recovery,Vector3f original){var d=data;if(d==null||!d.genericPosition.containsKey(name)||recovery>=1)return original;return position(d.genericPosition,name,seconds,new Vector3f()).lerp(original,Math.max(0,Math.min(1,recovery)));}
    public static Vector3f genericPosition(String name,float seconds,float recovery,Vector3f original){
        var result=genericPositionRaw(name,seconds,recovery,original);if(data!=null&&data.genericPosition.containsKey(name)&&recovery>0&&recovery<1)result.y-=footMin(genericPoseRaw(seconds,recovery))*16;return result;
    }
    /** Ares positions are in its original weighted-mesh source coordinate system. */
    public static Vector3f aresPosition(String name,float seconds,Vector3f out){var d=data;return d==null?null:position(d.aresPosition,name,seconds,out);}
    public static Quaternionf sample(Map<String,float[][]> clips,String name,float seconds,Quaternionf out){
        var frames=clips.get(name);if(frames==null)return null;float frame=Math.max(0,Math.min(90,seconds*30)),f=frame-(int)frame;int a=(int)frame,b=Math.min(90,a+1);
        return out.set(frames[a][0],frames[a][1],frames[a][2],frames[a][3]).slerp(new Quaternionf(frames[b][0],frames[b][1],frames[b][2],frames[b][3]),f).normalize();
    }
    public static Quaternionf ares(String name,float seconds,Quaternionf out){var d=data;return d==null?null:sample(d.ares,name,seconds,out);}
    /** Explicit inverse of rotationZYX(z,y,x), including the mixed x*y*z terms. */
    private static Vector3f angles(Quaternionf q,Vector3f out){
        float x=q.x,y=q.y,z=q.z,w=q.w;return out.set((float)Math.atan2(2*(w*x+y*z),1-2*(x*x+y*y)),(float)Math.asin(Math.max(-1,Math.min(1,2*(w*y-z*x)))),(float)Math.atan2(2*(w*z+x*y),1-2*(y*y+z*z)));
    }
    public static Vector3f generic(String name,float seconds,float recovery,Vector3f original){
        var d=data;if(d==null||!d.generic.containsKey(name)||recovery>=1)return original;
        var q=sample(d.generic,name,seconds,new Quaternionf());var back=new Quaternionf().rotationZYX(original.z,original.y,original.x);q.slerp(back,Math.max(0,Math.min(1,recovery))).normalize();
        return angles(q,new Vector3f());
    }
    /** A private copy of the real Default geometry; used only by the independent phantom. */
    public static Matrix4f[] genericPose(float seconds,float recovery){
        var world=genericPoseRaw(seconds,recovery);if(world!=null&&recovery>0&&recovery<1){float shift=footMin(world);var d=data;for(int i=0;i<world.length;i++){int root=i;while(d.genericMesh[root].parent>=0&& !"AllBody".equals(d.genericMesh[root].name))root=d.genericMesh[root].parent;if("AllBody".equals(d.genericMesh[root].name))world[i].m31(world[i].m31()-shift);}}return world;
    }
    private static float footMin(Matrix4f[] world){
        var d=data;float min=Float.POSITIVE_INFINITY;var p=new Vector3f();for(int i=0;i<world.length;i++)if("LeftFoot".equals(d.genericMesh[i].name)||"RightFoot".equals(d.genericMesh[i].name))for(var v:d.genericMesh[i].vertices){world[i].transformPosition(v[0],v[1],v[2],p);min=Math.min(min,p.y);}return Float.isFinite(min)?min:0;
    }
    private static Matrix4f[] genericPoseRaw(float seconds,float recovery){
        var d=data;if(d==null)return null;Matrix4f[] world=new Matrix4f[d.genericMesh.length];
        for(int i=0;i<world.length;i++){
            var b=d.genericMesh[i];var q=new Quaternionf(b.baseRotation[0],b.baseRotation[1],b.baseRotation[2],b.baseRotation[3]);
            if(d.generic.containsKey(b.name)){
                var original=angles(q,new Vector3f()).sub(b.staticEuler[0],b.staticEuler[1],b.staticEuler[2]);var angles=generic(b.name,seconds,recovery,original).add(b.staticEuler[0],b.staticEuler[1],b.staticEuler[2]);q.rotationZYX(angles.z,angles.y,angles.x);
            }
            var p=genericPositionRaw(b.name,seconds,recovery,new Vector3f());var local=new Matrix4f().translation(-p.x/16,p.y/16,p.z/16).translate(b.pivot[0],b.pivot[1],b.pivot[2]).rotate(q).translate(-b.pivot[0],-b.pivot[1],-b.pivot[2]);world[i]=b.parent<0?local:new Matrix4f(world[b.parent]).mul(local);
        }return world;
    }
    public static int drawGeneric(Matrix4f[] joints,Matrix4f pose,VertexConsumer out,int light){
        var d=data;int count=0;var point=new Vector3f();for(int i=0;i<joints.length;i++){var m=new Matrix4f(pose).mul(joints[i]);for(var v:d.genericMesh[i].vertices){m.transformPosition(v[0],v[1],v[2],point);out.vertex(point.x,point.y,point.z).color(1f,1f,1f,1f).uv(v[3],v[4]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0,1,0).endVertex();count++;}}return count;
    }
    public static Matrix4f genericHand(Matrix4f[] joints,Matrix4f out){var d=data;for(int i=0;i<joints.length;i++)if("RightHandLocator".equals(d.genericMesh[i].name)){var b=d.genericMesh[i];return out.set(joints[i]).translate(b.pivot[0],b.pivot[1],b.pivot[2]);}throw new IllegalStateException("Missing original Default hand locator");}
}
