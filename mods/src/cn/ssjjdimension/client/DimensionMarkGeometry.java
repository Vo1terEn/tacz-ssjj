package cn.ssjjdimension.client;

import java.util.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Source-derived particle lookup tables, shared by rendering and CPU visual QA. */
public final class DimensionMarkGeometry {
    public static final class Mesh { public float[][] positions,uv;public int[][] triangles; }
    public static final class Particle { public float birth,life;public float[][] frames; }
    public static final class Emitter { public String name,texture,render;public boolean mesh;public int rows,columns;public Mesh geometry;public Particle[] particles; }
    public static final class Data { public int schema;public String card;public boolean loop;public float duration;public Emitter[] emitters; }
    public record Batch(String texture,boolean additive,Mesh mesh,float[] color,float[] uv) {}
    private DimensionMarkGeometry() {}
    public static void validate(Data data) {
        if(data==null||data.schema!=1||data.card==null||data.duration<=0||data.duration>4||data.emitters==null||data.emitters.length>16)
            throw new IllegalArgumentException("Invalid target effect profile");
        int total=0;
        for(var e:data.emitters) {
            if(e==null||e.texture==null||!e.texture.startsWith("ssjj_dimension:textures/effects/marks/")||e.particles==null)
                throw new IllegalArgumentException("Invalid target effect emitter");
            if(e.mesh&&(e.geometry==null||e.geometry.positions==null||e.geometry.uv==null||e.geometry.triangles==null))
                throw new IllegalArgumentException("Missing target effect mesh");
            total+=e.particles.length;
            for(var p:e.particles) {
                if(p==null||p.birth<0||p.life<=0||p.life>4||p.frames==null||p.frames.length<2||p.frames.length>257)
                    throw new IllegalArgumentException("Invalid source particle lifetime");
                for(var f:p.frames) {
                    if(f==null||f.length!=18)throw new IllegalArgumentException("Invalid source state width");
                    for(float x:f)if(!Float.isFinite(x))throw new IllegalArgumentException("Nonfinite source state");
                }
            }
        }
        if(total>256)throw new IllegalArgumentException("Target effect particle cap exceeded");
    }
    public static float[] state(Particle p,float phase) {
        float x=Math.max(0,Math.min(1,phase))*(p.frames.length-1);int a=(int)x,b=Math.min(a+1,p.frames.length-1);float f=x-a;
        float[] out=new float[18];for(int i=0;i<18;i++)out[i]=p.frames[a][i]+(p.frames[b][i]-p.frames[a][i])*f;
        var q=new Quaternionf(p.frames[a][6],p.frames[a][7],p.frames[a][8],p.frames[a][9]).normalize()
            .slerp(new Quaternionf(p.frames[b][6],p.frames[b][7],p.frames[b][8],p.frames[b][9]).normalize(),f).normalize();
        out[6]=q.x;out[7]=q.y;out[8]=q.z;out[9]=q.w;return out;
    }
    public static List<Batch> sample(Data data,float seconds,Vector3f right,Vector3f up) {
        var result=new ArrayList<Batch>();if(data==null||seconds<0||!Float.isFinite(seconds))return result;
        for(var e:data.emitters)for(var p:e.particles) {
            int first=data.loop?Math.max(0,(int)Math.floor((seconds-p.birth-p.life)/data.duration)+1):0;
            int last=data.loop?(int)Math.floor((seconds-p.birth)/data.duration):0;
            for(int cycle=first;cycle<=last;cycle++) {
                float age=seconds-p.birth-cycle*data.duration;if(age<0||age>=p.life)continue;
                var s=state(p,age/p.life);if(s[13]<=.0001)continue;
                Mesh mesh=new Mesh();var q=new Quaternionf(s[6],s[7],s[8],s[9]);
                if(e.mesh) {
                    mesh.uv=e.geometry.uv;mesh.triangles=e.geometry.triangles;mesh.positions=new float[e.geometry.positions.length][3];
                    for(int i=0;i<mesh.positions.length;i++) {
                        var p0=e.geometry.positions[i];var v=q.transform(new Vector3f(p0[0]*s[3],p0[1]*s[4],p0[2]*s[5])).add(s[0],s[1],s[2]);
                        mesh.positions[i]=new float[]{v.x,v.y,v.z};
                    }
                } else {
                    var rx=q.transform(new Vector3f(1,0,0));var uy=q.transform(new Vector3f(0,1,0));
                    var r=new Vector3f(right).mul(rx.x).fma(rx.y,up).mul(s[3]*.5f);
                    var u=new Vector3f(right).mul(uy.x).fma(uy.y,up).mul(s[4]*.5f);
                    mesh.positions=new float[4][3];int[][] signs={{-1,-1},{1,-1},{1,1},{-1,1}};
                    for(int i=0;i<4;i++){var v=new Vector3f(s[0],s[1],s[2]).fma(signs[i][0],r).fma(signs[i][1],u);mesh.positions[i]=new float[]{v.x,v.y,v.z};}
                    mesh.uv=new float[][]{{0,1},{1,1},{1,0},{0,0}};mesh.triangles=new int[][]{{0,1,2},{0,2,3}};
                }
                float u=s[16],v=s[17];
                if(e.rows>0&&e.columns>0) {
                    int frame=Math.min((int)(age/p.life*e.rows*e.columns),e.rows*e.columns-1);
                    u+=(frame%e.columns)/(float)e.columns;v+=(frame/e.columns)/(float)e.rows;
                }
                result.add(new Batch(e.texture,"additive".equals(e.render),mesh,new float[]{s[10],s[11],s[12],s[13]},new float[]{s[14],s[15],u,v}));
            }
        }
        return result;
    }
}
