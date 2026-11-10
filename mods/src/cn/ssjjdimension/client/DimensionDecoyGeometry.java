package cn.ssjjdimension.client;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Source activation LUTs in the same joint space as the ordinary gun particles. */
public final class DimensionDecoyGeometry {
    public static final class Emitter {
        public String name,texture,render,mode;
        public boolean loop;
        public float period;
        public int rows,columns;
        public DimensionParticles.Mesh geometry;
        public DimensionParticles.Particle[] particles;
    }
    public static final class Data {
        public int schema;
        public String gun,bone,kind;
        public float duration;
        public Emitter[] emitters;
    }
    private DimensionDecoyGeometry() {}
    public static void validate(Data data) {
        if(data==null||data.schema!=1||!"ssjj_dimension:dimension_star2".equals(data.gun)
            ||!DimensionParticleEffects.BONE.equals(data.bone)||data.kind==null||!Float.isFinite(data.duration)
            ||data.duration<=0||data.duration>600||data.emitters==null||data.emitters.length>16)
            throw new IllegalArgumentException("Invalid tier-II activation profile");
        int total=0;
        for(var e:data.emitters) {
            if(e==null||e.name==null||e.texture==null||!e.texture.startsWith("ssjj_dimension:textures/effects/decoy/")
                ||!("additive".equals(e.render)||"alpha".equals(e.render))||!Set.of("mesh","billboard","camera_mesh","velocity").contains(e.mode)
                ||!Float.isFinite(e.period)||e.period<=0||e.period>600||e.particles==null||e.rows<0||e.columns<0||e.rows>32||e.columns>32)
                throw new IllegalArgumentException("Invalid source activation emitter");
            new ResourceLocation(e.texture);
            if("mesh".equals(e.mode)||"camera_mesh".equals(e.mode))DimensionParticles.validateMesh(e.geometry);
            total+=e.particles.length;
            for(var p:e.particles) {
                if(p==null||!Float.isFinite(p.birth)||p.birth<0||!Float.isFinite(p.life)||p.life<=0||p.life>600
                    ||p.frames==null||p.frames.length<2||p.frames.length>257)throw new IllegalArgumentException("Invalid activation particle");
                for(var f:p.frames) {
                    if(f==null||f.length!=18)throw new IllegalArgumentException("Invalid activation state width");
                    for(float v:f)if(!Float.isFinite(v))throw new IllegalArgumentException("Nonfinite activation state");
                }
            }
        }
        if(total>256)throw new IllegalArgumentException("Activation particle cap exceeded");
    }
    public static List<DimensionParticles.Batch> sample(Data data,float seconds,Vector3f right,Vector3f up) {
        var result=new ArrayList<DimensionParticles.Batch>();
        if(data==null||!Float.isFinite(seconds)||seconds<0)return result;
        for(var e:data.emitters)for(var p:e.particles) {
            // Native UserNewParticle reinitializes each particle independently
            // after its own lifetime; initial birth delay is not repeated.
            float elapsed=seconds-p.birth;if(elapsed<0)continue;
            int cycle=e.loop?(int)Math.floor(elapsed/p.life):0;
            {
                float age=elapsed-cycle*p.life;
                if(age<0||age>=p.life)continue;
                float[] s=DimensionParticles.state(p,age/p.life);if(s[13]<=.0001f)continue;
                var mesh=new DimensionParticles.Mesh();var q=new Quaternionf(s[6],s[7],s[8],s[9]);
                if("mesh".equals(e.mode)||"camera_mesh".equals(e.mode)) {
                    mesh.uv=e.geometry.uv;mesh.triangles=e.geometry.triangles;mesh.positions=new float[e.geometry.positions.length][3];
                    for(int i=0;i<mesh.positions.length;i++) {
                        var p0=e.geometry.positions[i];var v=q.transform(new Vector3f(p0[0]*s[3],p0[1]*s[4],p0[2]*s[5]));
                        if("camera_mesh".equals(e.mode))v=new Vector3f(right).mul(v.x).fma(v.y,up).fma(v.z,new Vector3f(right).cross(up));
                        v.add(s[0],s[1],s[2]);
                        mesh.positions[i]=new float[]{v.x,v.y,v.z};
                    }
                } else {
                    var screenRight=new Vector3f(right);var screenUp=new Vector3f(up);
                    if("velocity".equals(e.mode)) {
                        // Source billboard1 is velocity aligned. Retain its
                        // long axis in the camera plane using LUT derivatives.
                        int frame=Math.min(p.frames.length-2,(int)(age/p.life*(p.frames.length-1)));
                        var a=p.frames[frame];var b=p.frames[frame+1];var motion=new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]);
                        float dx=motion.dot(right),dy=motion.dot(up),length=(float)Math.sqrt(dx*dx+dy*dy);
                        if(length>1e-8f){screenRight.set(right).mul(dy/length).fma(-dx/length,up);screenUp.set(right).mul(dx/length).fma(dy/length,up);}
                    }
                    var x=q.transform(new Vector3f(1,0,0));var y=q.transform(new Vector3f(0,1,0));
                    var r=new Vector3f(screenRight).mul(x.x).fma(x.y,screenUp).mul(s[3]*.5f);
                    var u=new Vector3f(screenRight).mul(y.x).fma(y.y,screenUp).mul(s[4]*.5f);
                    mesh.positions=new float[4][3];int[][] signs={{-1,-1},{1,-1},{1,1},{-1,1}};
                    for(int i=0;i<4;i++){var v=new Vector3f(s[0],s[1],s[2]).fma(signs[i][0],r).fma(signs[i][1],u);mesh.positions[i]=new float[]{v.x,v.y,v.z};}
                    mesh.uv=new float[][]{{0,1},{1,1},{1,0},{0,0}};mesh.triangles=new int[][]{{0,1,2},{0,2,3}};
                }
                float offsetU=s[16],offsetV=s[17];
                if(e.rows>0&&e.columns>0){int frame=Math.min((int)(age/p.life*e.rows*e.columns),e.rows*e.columns-1);offsetU+=(frame%e.columns)/(float)e.columns;offsetV+=(frame/e.columns)/(float)e.rows;}
                result.add(new DimensionParticles.Batch(e.texture,e.render,mesh,new float[]{s[10],s[11],s[12],s[13]},new float[]{s[14],s[15],offsetU,offsetV}));
            }
        }
        return result;
    }
}
