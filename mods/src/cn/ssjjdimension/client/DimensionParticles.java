package cn.ssjjdimension.client;

import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Stateless source-specific LUT sampler, shared verbatim by runtime and offline QA. */
public final class DimensionParticles {
    public static final class Mesh {public float[][] positions,uv;public int[][] triangles;}
    public static final class Particle {public float birth,life;public float[][] frames;}
    public static final class Emitter {public String name,texture,mode,render;public float duration;public float[] billboard_axis;public Mesh geometry;public Particle[] particles;}
    public static final class Data {public int schema;public String gun,bone;public Emitter[] emitters;}
    public record Batch(String texture,String render,Mesh mesh,float[] color,float[] uvST) {}
    private DimensionParticles() {}
    public static void validate(Data data) {
        if(data==null || data.schema!=1 || !cn.ssjjdimension.DimensionScopes.family(net.minecraft.resources.ResourceLocation.tryParse(data.gun)) || !"weapon_particle_effect".equals(data.bone)
            || data.emitters==null || data.emitters.length>32)throw new IllegalArgumentException("Invalid Dimension source particle schema");
        int total=0;
        for(Emitter e:data.emitters) {
            if(e==null || e.name==null || e.texture==null || !e.texture.startsWith("ssjj_dimension:textures/effects/")
                || !("billboard".equals(e.mode)||"mesh".equals(e.mode)) || !("alpha".equals(e.render)||"additive".equals(e.render))
                || !Float.isFinite(e.duration) || e.duration<=0 || e.duration>60 || e.particles==null || e.particles.length>1024)
                throw new IllegalArgumentException("Invalid source emitter");
            if("mesh".equals(e.mode))validateMesh(e.geometry);
            if(e.billboard_axis!=null) {
                if(!"billboard".equals(e.mode) || e.billboard_axis.length!=3)throw new IllegalArgumentException("Invalid source billboard long axis");
                float norm=0;for(float value:e.billboard_axis){if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite billboard source axis");norm+=value*value;}
                if(norm<.5f || norm>1.5f)throw new IllegalArgumentException("Billboard source axis must be normalized");
            }
            total+=e.particles.length;
            for(Particle p:e.particles) {
                if(p==null || !Float.isFinite(p.birth) || p.birth<0 || p.birth>=e.duration || !Float.isFinite(p.life)
                    || p.life<=0 || p.life>60 || p.frames==null || p.frames.length<2 || p.frames.length>512)
                    throw new IllegalArgumentException("Invalid source particle lifetime/table");
                for(float[] frame:p.frames) {
                    if(frame==null || frame.length!=18)throw new IllegalArgumentException("Wrong source state width");
                    for(float value:frame)if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite source state");
                    float norm=0;for(int k=6;k<10;k++)norm+=frame[k]*frame[k];
                    if(norm<.5f || norm>1.5f)throw new IllegalArgumentException("Invalid source particle quaternion");
                    for(int k=10;k<14;k++)if(frame[k]<0)throw new IllegalArgumentException("Negative source color");
                }
            }
        }
        if(total>4096)throw new IllegalArgumentException("Unbounded source particle count");
    }
    public static void validateMesh(Mesh mesh) {
        if(mesh==null || mesh.positions==null || mesh.uv==null || mesh.triangles==null || mesh.positions.length==0
            || mesh.positions.length>20000 || mesh.positions.length!=mesh.uv.length)throw new IllegalArgumentException("Invalid source particle mesh");
        for(int i=0;i<mesh.positions.length;i++) {
            if(mesh.positions[i].length!=3 || mesh.uv[i].length!=2)throw new IllegalArgumentException("Invalid source mesh streams");
            for(float n:mesh.positions[i])if(!Float.isFinite(n))throw new IllegalArgumentException("Non-finite source mesh point");
            for(float n:mesh.uv[i])if(!Float.isFinite(n))throw new IllegalArgumentException("Non-finite source mesh UV");
        }
        for(int[] face:mesh.triangles){if(face.length!=3)throw new IllegalArgumentException("Invalid source face");for(int i:face)if(i<0||i>=mesh.positions.length)throw new IllegalArgumentException("Source face outside vertex buffer");}
    }
    public static float[] state(Particle particle,float normalizedLife) {
        float position=Math.max(0,Math.min(1,normalizedLife))*(particle.frames.length-1);
        int i=(int)position,j=Math.min(i+1,particle.frames.length-1);float f=position-i;
        float[] a=particle.frames[i],b=particle.frames[j],out=new float[18];
        for(int k=0;k<18;k++)out[k]=a[k]+(b[k]-a[k])*f;
        Quaternionf q=new Quaternionf(a[6],a[7],a[8],a[9]).normalize().slerp(new Quaternionf(b[6],b[7],b[8],b[9]).normalize(),f).normalize();
        out[6]=q.x;out[7]=q.y;out[8]=q.z;out[9]=q.w;return out;
    }
    public static List<Batch> sample(Data data,float seconds,Vector3f cameraRight,Vector3f cameraUp) {
        return sample(data,seconds,cameraRight,cameraUp,null);
    }
    public static List<Batch> sample(Data data,float seconds,Vector3f cameraRight,Vector3f cameraUp,Vector3f cameraPosition) {
        List<Batch> out=new ArrayList<>();if(seconds<0 || !Float.isFinite(seconds))return out;
        for(Emitter emitter:data.emitters)for(Particle particle:emitter.particles) {
            int first=Math.max(0,(int)Math.floor((seconds-particle.birth-particle.life)/emitter.duration)+1);
            int last=(int)Math.floor((seconds-particle.birth)/emitter.duration);
            for(int cycle=first;cycle<=last;cycle++) {
                float age=seconds-(particle.birth+cycle*emitter.duration);
                if(age<0 || age>=particle.life)continue;
                float[] s=state(particle,age/particle.life);if(s[13]<=.0001f)continue;
                Mesh mesh;
                if("billboard".equals(emitter.mode))mesh=quad(emitter,s,cameraRight,cameraUp,cameraPosition);
                else {
                    mesh=new Mesh();mesh.uv=emitter.geometry.uv;mesh.triangles=emitter.geometry.triangles;
                    mesh.positions=new float[emitter.geometry.positions.length][3];
                    Quaternionf rotation=new Quaternionf(s[6],s[7],s[8],s[9]);
                    for(int i=0;i<mesh.positions.length;i++) {
                        float[] p=emitter.geometry.positions[i];var v=rotation.transform(new Vector3f(p[0]*s[3],p[1]*s[4],p[2]*s[5])).add(s[0],s[1],s[2]);
                        mesh.positions[i]=new float[]{v.x,v.y,v.z};
                    }
                }
                out.add(new Batch(emitter.texture,emitter.render,mesh,new float[]{s[10],s[11],s[12],s[13]},new float[]{s[14],s[15],s[16],s[17]}));
            }
        }
        return out;
    }
    private static Mesh quad(Emitter emitter,float[] s,Vector3f cameraRight,Vector3f cameraUp,Vector3f cameraPosition) {
        Vector3f right=new Vector3f(cameraRight).normalize();
        Vector3f normal=new Vector3f(right).cross(cameraUp).normalize();
        Vector3f up=new Vector3f(normal).cross(right).normalize();
        if(emitter.billboard_axis!=null) {
            var a=emitter.billboard_axis;var axis=new Vector3f(a[0],a[1],a[2]).normalize();
            Vector3f projected=new Vector3f(right).mul(axis.dot(right)).fma(axis.dot(up),up);
            if(cameraPosition!=null) {
                // Intersect the animated barrel line with the billboard plane
                // through this particle center, along the actual camera ray.
                // Orthogonal XY projection ignores depth and gives the wrong
                // screen direction for a near, off-center first-person gun.
                Vector3f ray=new Vector3f(s[0],s[1],s[2]).sub(cameraPosition);
                float depth=ray.dot(normal);
                if(Math.abs(depth)>1e-5f)projected.set(axis).fma(-axis.dot(normal)/depth,ray);
            }
            if(projected.lengthSquared()>1e-10f)right.set(projected).cross(normal).normalize();
            up.set(projected);
        }
        var rotation=new Quaternionf(s[6],s[7],s[8],s[9]);
        Vector3f x=rotation.transform(new Vector3f(1,0,0)),y=rotation.transform(new Vector3f(0,1,0));
        Vector3f r=new Vector3f(right).mul(x.x).fma(x.y,up).mul(s[3]*.5f);
        Vector3f u=new Vector3f(right).mul(y.x).fma(y.y,up).mul(s[4]*.5f);
        Mesh mesh=new Mesh();mesh.positions=new float[4][3];int[][] signs={{-1,-1},{1,-1},{1,1},{-1,1}};
        for(int i=0;i<4;i++){var p=new Vector3f(s[0],s[1],s[2]).fma(signs[i][0],r).fma(signs[i][1],u);mesh.positions[i]=new float[]{p.x,p.y,p.z};}
        mesh.uv=new float[][]{{0,1},{1,1},{1,0},{0,0}};mesh.triangles=new int[][]{{0,1,2},{0,2,3}};return mesh;
    }
}
