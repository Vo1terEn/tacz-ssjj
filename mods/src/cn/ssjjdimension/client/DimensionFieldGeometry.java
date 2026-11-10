package cn.ssjjdimension.client;

import java.util.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original lingyu source lookup tables; fixed world meshes, billboards and CPU QA. */
public final class DimensionFieldGeometry {
    public static final class Mesh { public float[][] positions,uv;public int[][] triangles; }
    public static final class Particle { public float birth,life,spinRate;public float[] spinAxis;public float[][] frames; }
    public static final class Emitter { public String name,texture,render;public boolean mesh,boundary;public float period;public int rows,columns,billboard;public Mesh geometry;public Particle[] particles; }
    public static final class Data { public int schema;public String card;public boolean loop,restored;public float duration,sourceRadius,prewarm;public Emitter[] emitters; }
    public record Batch(String texture,boolean additive,Mesh mesh,float[] color,float[] uv) {}
    private DimensionFieldGeometry() {}
    public static void validate(Data data) {
        if(data==null||data.schema!=1||data.card==null||data.duration<=0||data.duration>8||data.sourceRadius<=0||data.sourceRadius>16||data.emitters==null||data.emitters.length>64)
            throw new IllegalArgumentException("Invalid field effect profile");
        int total=0;
        for(var e:data.emitters) {
            if(e==null||e.texture==null||!e.texture.startsWith("ssjj_dimension:textures/effects/field/")||e.particles==null)
                throw new IllegalArgumentException("Invalid field effect emitter");
            if(e.mesh&&(e.geometry==null||e.geometry.positions==null||e.geometry.uv==null||e.geometry.triangles==null))
                throw new IllegalArgumentException("Missing field effect mesh");
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
        if(total>512)throw new IllegalArgumentException("Field effect particle cap exceeded");
    }
    public static float[] state(Particle p,float phase) {
        float x=Math.max(0,Math.min(1,phase))*(p.frames.length-1);int a=(int)x,b=Math.min(a+1,p.frames.length-1);float f=x-a;
        float[] out=new float[18];for(int i=0;i<18;i++)out[i]=p.frames[a][i]+(p.frames[b][i]-p.frames[a][i])*f;
        var q=new Quaternionf(p.frames[a][6],p.frames[a][7],p.frames[a][8],p.frames[a][9]).normalize()
            .slerp(new Quaternionf(p.frames[b][6],p.frames[b][7],p.frames[b][8],p.frames[b][9]).normalize(),f).normalize();
        out[6]=q.x;out[7]=q.y;out[8]=q.z;out[9]=q.w;return out;
    }
    public static List<Batch> sample(Data data,float seconds,Vector3f right,Vector3f up,float radius) {
        var result=new ArrayList<Batch>();if(data==null||seconds<0||!Float.isFinite(seconds)||radius<=0||!Float.isFinite(radius))return result;
        if(data.restored&&data.loop)seconds+=data.prewarm;
        for(var e:data.emitters)for(var p:e.particles) {
            float period=data.restored?p.life:e.period>0?e.period:data.duration;
            int first=data.loop?Math.max(0,(int)Math.floor((seconds-p.birth-p.life)/period)+1):0;
            int last=data.loop?(int)Math.floor((seconds-p.birth)/period):0;
            for(int cycle=first;cycle<=last;cycle++) {
                float age=seconds-p.birth-cycle*period;if(age<0||age>=p.life)continue;
                var s=state(p,age/p.life);if(s[13]<=.0001)continue;
                Mesh mesh=new Mesh();var q=new Quaternionf(s[6],s[7],s[8],s[9]);
                // Keep source spin continuous across the one-second ground-ring
                // emission cycles. Legacy profiles retain their original sampler.
                if(data.restored&&data.loop&&cycle>0&&p.spinAxis!=null&&p.spinRate!=0)
                    q.rotateAxis(p.spinRate*period*cycle,p.spinAxis[0],p.spinAxis[1],p.spinAxis[2]);
                if(e.mesh) {
                    mesh.uv=e.geometry.uv;mesh.triangles=e.geometry.triangles;mesh.positions=new float[e.geometry.positions.length][3];
                    for(int i=0;i<mesh.positions.length;i++) {
                        var p0=e.geometry.positions[i];var v=q.transform(new Vector3f(p0[0]*s[3],p0[1]*s[4],p0[2]*s[5])).add(s[0],s[1],s[2]);
                        if(data.restored&&(e.billboard==0||e.billboard==1)) {
                            var axisRight=new Vector3f(right);var axisUp=new Vector3f(up);
                            if(e.billboard==1){axisRight.y=0;if(axisRight.lengthSquared()<1e-8f)axisRight.set(1,0,0);else axisRight.normalize();axisUp.set(0,1,0);}
                            var axisForward=new Vector3f(axisRight).cross(axisUp);
                            var local=q.transform(new Vector3f(p0[0]*s[3],p0[1]*s[4],p0[2]*s[5]));
                            v.set(s[0],s[1],s[2]).fma(local.x,axisRight).fma(local.y,axisUp).fma(local.z,axisForward);
                        }
                        mesh.positions[i]=new float[]{v.x,v.y,v.z};
                    }
                } else {
                    var rx=q.transform(new Vector3f(1,0,0));var uy=q.transform(new Vector3f(0,1,0));
                    var facingRight=new Vector3f(right);var facingUp=new Vector3f(up);
                    if(data.restored&&e.billboard==1) {
                        facingRight.y=0;if(facingRight.lengthSquared()<1e-8f)facingRight.set(1,0,0);else facingRight.normalize();
                        facingUp.set(0,1,0);
                    }
                    var r=new Vector3f(facingRight).mul(rx.x).fma(rx.y,facingUp).mul(s[3]*.5f);
                    var u=new Vector3f(facingRight).mul(uy.x).fma(uy.y,facingUp).mul(s[4]*.5f);
                    mesh.positions=new float[4][3];int[][] signs={{-1,-1},{1,-1},{1,1},{-1,1}};
                    for(int i=0;i<4;i++){var v=new Vector3f(s[0],s[1],s[2]).fma(signs[i][0],r).fma(signs[i][1],u);mesh.positions[i]=new float[]{v.x,v.y,v.z};}
                    mesh.uv=new float[][]{{0,1},{1,1},{1,0},{0,0}};mesh.triangles=new int[][]{{0,1,2},{0,2,3}};
                }
                // The source circular ground texture defines the gameplay boundary. Other
                // source decoration fits inside it; transparent Quad corners stay uncut.
                float fit=1f;
                if(!data.restored&&!e.boundary) {
                    float outer=0;for(var point:mesh.positions)outer=Math.max(outer,(float)Math.hypot(point[0],point[2]));
                    if(outer>data.sourceRadius)fit=data.sourceRadius/outer;
                }
                float worldScale=radius/data.sourceRadius;
                for(var point:mesh.positions){point[0]*=fit*worldScale;point[2]*=fit*worldScale;point[1]*=worldScale;}
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
