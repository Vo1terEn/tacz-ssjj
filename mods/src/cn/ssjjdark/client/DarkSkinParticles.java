package cn.ssjjdark.client;

import cn.ssjjdimension.client.DimensionParticles;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Source skin particles retain individual rebirth, continuous spin and discrete atlas cells. */
public final class DarkSkinParticles {
    public static final String TEXTURES="ssjj_dark:textures/effects/skins/";
    public static final class Data {public int schema;public String gun,bone;public Emitter[] emitters;private transient Emitter[] ordered;}
    public static final class Emitter {
        public String name,texture,mode,render;
        public float duration,prewarm;
        public float sourceOrbitDegree,sourceOrbitRadius;
        public boolean loop;
        public boolean depthWrite;
        public int rows,columns;
        public int billboardType,rotateAxis;
        public Integer renderQueue;
        public float[][] sourceMatrix;
        public float[] sourceRotation;
        public float[] billboard_axis;
        public DimensionParticles.Mesh geometry;
        public Particle[] particles;
        private transient Prepared prepared;
    }
    public static final class Particle {
        public float birth,life,sourceAngle,sourceSpin;
        public float[] sourcePosition,sourceVelocity;
        public float[] sourceForce,sourceGravity;
        public float[][] sourceSizeFrames;
        public float[][] frames;
    }
    private DarkSkinParticles() {}

    public static void validate(Data data,String owner) {
        validate(data,owner,60);
    }
    /** Moving source dragon glows are authored at100s; validate without altering their phase. */
    public static void validateDragon(Data data,String owner) {
        validate(data,owner,120);
    }
    private static void validate(Data data,String owner,float maximumLifetime) {
        if(data==null||data.emitters==null)throw new IllegalArgumentException("Missing Dark Crystal source effects");
        var checked=new DimensionParticles.Data();checked.schema=data.schema;checked.gun=data.gun;checked.bone=data.bone;
        checked.emitters=new DimensionParticles.Emitter[data.emitters.length];
        for(int i=0;i<data.emitters.length;i++) {
            var source=data.emitters[i];if(source==null)throw new IllegalArgumentException("Missing source emitter");
            if(!Float.isFinite(source.prewarm)||source.prewarm<0||source.prewarm>60)throw new IllegalArgumentException("Invalid source prewarm");
            if(!Float.isFinite(source.sourceOrbitDegree)||!Float.isFinite(source.sourceOrbitRadius)||source.sourceOrbitRadius<0||source.sourceOrbitRadius>1000||Math.abs(source.sourceOrbitDegree)>100||source.sourceOrbitRadius!=0&&source.billboardType!=0)throw new IllegalArgumentException("Invalid source orbit");
            if(source.renderQueue==null||source.renderQueue<0||source.renderQueue>5000)throw new IllegalArgumentException("Missing source material queue");
            if(source.rows<0||source.columns<0||source.rows>64||source.columns>64||(source.rows==0)!=(source.columns==0))
                throw new IllegalArgumentException("Invalid source atlas");
            if((source.billboardType!=0&&source.billboardType!=1&&source.billboardType!=4)||source.rotateAxis<0||source.rotateAxis>2)
                throw new IllegalArgumentException("Unsupported source particle orientation");
            var matrix=matrix(source.sourceMatrix);if(!matrix.isFinite()||Math.abs(matrix.determinant())<1e-12f)throw new IllegalArgumentException("Invalid source emitter matrix");
            vector(source.sourceRotation,4);float norm=0;for(float f:source.sourceRotation)norm+=f*f;
            if(norm<.5f||norm>1.5f)throw new IllegalArgumentException("Invalid source emitter rotation");
            DimensionParticles.validateMesh(source.geometry);
            if(!Float.isFinite(source.duration)||source.duration<=0||source.duration>maximumLifetime)throw new IllegalArgumentException("Invalid source duration");
            float validationScale=Math.max(1,source.duration/60);
            var emitter=base(source);emitter.duration=source.duration/validationScale;
            if(source.particles==null)throw new IllegalArgumentException("Missing source particles");
            emitter.particles=new DimensionParticles.Particle[source.particles.length];
            for(int p=0;p<source.particles.length;p++) {
                var particle=source.particles[p];if(particle==null||!Float.isFinite(particle.sourceAngle)||!Float.isFinite(particle.sourceSpin))throw new IllegalArgumentException("Invalid source spin");
                vector(particle.sourcePosition,3);vector(particle.sourceVelocity,3);
                if(particle.sourceSizeFrames==null||particle.sourceSizeFrames.length<2||particle.sourceSizeFrames.length>512)throw new IllegalArgumentException("Invalid source size table");
                for(var frame:particle.sourceSizeFrames)vector(frame,3);
                if(!Float.isFinite(particle.life)||particle.life<=0||particle.life>maximumLifetime)throw new IllegalArgumentException("Invalid source lifetime");
                var track=new DimensionParticles.Particle();track.birth=particle.birth/validationScale;track.life=particle.life/validationScale;track.frames=particle.frames;
                emitter.particles[p]=track;
            }
            checked.emitters[i]=emitter;
        }
        DimensionParticles.validate(checked,owner,"WeaponAnchor",TEXTURES);
        data.ordered=ordered(data);
        for(var emitter:data.ordered)emitter.prepared=new Prepared(emitter);
    }

    private static Emitter[] ordered(Data data) {
        var sorted=data.emitters.clone();java.util.Arrays.sort(sorted,java.util.Comparator.comparingInt(e->e.renderQueue));return sorted;
    }

    private static void vector(float[] values,int length) {
        if(values==null||values.length!=length)throw new IllegalArgumentException("Invalid source vector");
        for(float f:values)if(!Float.isFinite(f))throw new IllegalArgumentException("Nonfinite source vector");
    }
    private static Matrix4f matrix(float[][] rows) {
        if(rows==null||rows.length!=4)throw new IllegalArgumentException("Invalid source matrix");
        var matrix=new Matrix4f();for(int r=0;r<4;r++){vector(rows[r],4);for(int c=0;c<4;c++)matrix.set(c,r,rows[r][c]);}return matrix.determineProperties();
    }

    private static DimensionParticles.Emitter base(Emitter source) {
        var emitter=new DimensionParticles.Emitter();emitter.name=source.name;emitter.texture=source.texture;
        emitter.mode=source.mode;emitter.render=source.render;emitter.geometry=source.geometry;emitter.billboard_axis=source.billboard_axis;
        return emitter;
    }

    public static List<DimensionParticles.Batch> sample(Data data,float seconds,Vector3f right,Vector3f up,Vector3f camera) {
        var result=new ArrayList<DimensionParticles.Batch>();if(data==null||!Float.isFinite(seconds)||seconds<0)return result;
        for(var emitter:data.ordered==null?ordered(data):data.ordered)for(var particle:emitter.particles) {
            float spinElapsed=seconds-particle.birth,elapsed=spinElapsed+emitter.prewarm;if(elapsed<0)continue;
            long cycle=emitter.loop?(long)Math.floor(elapsed/particle.life):0;
            float age=emitter.loop?(float)(elapsed-cycle*(double)particle.life):elapsed;
            if(age<0||age>=particle.life)continue;
            var track=new DimensionParticles.Particle();track.frames=particle.frames;
            float[] state=DimensionParticles.state(track,age/particle.life);
            orbit(emitter,elapsed,state);
            if(state[13]<=.0001f)continue;
            if(emitter.rows>0) {
                int cells=emitter.rows*emitter.columns;
                int cell=Math.min(cells-1,(int)Math.floor(age/particle.life*cells));
                state[16]+=(float)(cell%emitter.columns)/emitter.columns;
                // The shared source draw path subtracts the stored V offset.
                state[17]-=(float)(cell/emitter.columns)/emitter.rows;
            }
            var geometry=emitter.geometry;var mesh=new DimensionParticles.Mesh();mesh.uv=geometry.uv;mesh.triangles=geometry.triangles;
            mesh.positions=new float[geometry.positions.length][3];
            var linear=linear(emitter,particle,spinElapsed,age/particle.life,right,up,camera);
            for(int i=0;i<mesh.positions.length;i++) {
                float[] p=geometry.positions[i];var v=linear.transform(new Vector3f(p[0],p[1],p[2])).add(state[0],state[1],state[2]);
                mesh.positions[i]=new float[]{v.x,v.y,v.z};
            }
            result.add(new DimensionParticles.Batch(emitter.texture,emitter.render,mesh,
                new float[]{state[10],state[11],state[12],state[13]},new float[]{state[14],state[15],state[16],state[17]}));
        }
        return result;
    }

    /** Keep S_emitter * R_particle order: quaternion/scale decomposition would erase source shear. */
    public static Matrix3f linear(Emitter emitter,Particle particle,float elapsed,float phase,Vector3f right,Vector3f up,Vector3f camera) {
        var source=matrix(emitter.sourceMatrix);var reflection=new Matrix3f().scaling(-1,1,1);
        float angle=(float)Math.IEEEremainder(particle.sourceAngle+particle.sourceSpin*(double)elapsed,Math.PI*2);
        var rotation=emitter.rotateAxis==2?new Quaternionf().rotationZ(angle):emitter.rotateAxis==1?new Quaternionf().rotationY(angle):new Quaternionf().rotationX(angle);
        if(emitter.billboardType==1) {
            // Reuse the delivered DLL-checked mixed-space camera/velocity formula verbatim.
            var raw=new cn.punishmenteye.MuzzleParticles.Emitter();raw.localMatrix=emitter.sourceMatrix;raw.emitterRotation=emitter.sourceRotation;raw.rotateAxis=emitter.rotateAxis;
            var mapping=new Matrix4f().scaling(-.01f,.01f,.01f).mul(source);
            Vector3f cameraRaw=new Matrix4f(mapping).invert().transformPosition(new Vector3f(camera));
            var dummyView=new Matrix4f().translation(-cameraRaw.x,-cameraRaw.y,-cameraRaw.z);
            var p=particle.sourcePosition;var v=particle.sourceVelocity;
            Vector3f position=new Vector3f(p[0],p[1],p[2]).fma(phase*particle.life,new Vector3f(v[0],v[1],v[2]));
            rotation=cn.punishmenteye.MuzzleParticles.velocityRotation(raw,dummyView,position,new Vector3f(v[0],v[1],v[2]),angle);
        }
        float frame=Math.max(0,Math.min(1,phase))*(particle.sourceSizeFrames.length-1);int lo=(int)frame,hi=Math.min(lo+1,particle.sourceSizeFrames.length-1);float fraction=frame-lo;
        var a=particle.sourceSizeFrames[lo];var b=particle.sourceSizeFrames[hi];
        var size=new Vector3f(a[0]+(b[0]-a[0])*fraction,a[1]+(b[1]-a[1])*fraction,a[2]+(b[2]-a[2])*fraction);
        if(emitter.billboardType==0) {
            var scale=source.getScale(new Vector3f());
            var r=new Vector3f(right).normalize();var n=new Vector3f(r).cross(up).normalize();var u=new Vector3f(n).cross(r).normalize();
            var basis=new Matrix3f().setColumn(0,r).setColumn(1,u).setColumn(2,n);
            return basis.mul(new Matrix3f().scaling(scale.x,scale.y,-scale.z)).rotate(rotation).scale(size).mul(reflection);
        }
        return new Matrix3f(reflection).mul(new Matrix3f(source)).rotate(rotation).scale(size).mul(reflection);
    }

    public static float lifetime(Data data) {
        float end=0;for(var e:data.emitters)for(var p:e.particles)end=Math.max(end,p.birth+p.life);return end;
    }

    /** Original UpdateOrbit uses continuous elapsed radians in emitter-local centimetres. */
    private static void orbit(Emitter emitter,float elapsed,float[] state) {
        if(emitter.sourceOrbitRadius==0)return;
        double angle=elapsed*(double)emitter.sourceOrbitDegree;
        double x=Math.sin(angle)*emitter.sourceOrbitRadius*.01,y=Math.cos(angle)*emitter.sourceOrbitRadius*.01;
        var m=emitter.sourceMatrix;
        state[0]-=(float)(m[0][0]*x+m[0][1]*y);
        state[1]+=(float)(m[1][0]*x+m[1][1]*y);
        state[2]+=(float)(m[2][0]*x+m[2][1]*y);
    }

    /** Immutable source geometry/matrix work is done once per resource reload. */
    private static final class Prepared {
        final Matrix3f left,stretch;
        final Quaternionf inverseRotation;
        final float tx,ty,tz;
        final float[] vertices;
        Prepared(Emitter emitter) {
            var source=matrix(emitter.sourceMatrix);left=new Matrix3f().scaling(-1,1,1).mul(new Matrix3f(source));
            var scale=source.getScale(new Vector3f());stretch=new Matrix3f().scaling(scale.x,scale.y,-scale.z);
            var q=emitter.sourceRotation;inverseRotation=new Quaternionf(q[0],q[1],q[2],q[3]).normalize().invert();
            tx=source.m30();ty=source.m31();tz=source.m32();
            var mesh=emitter.geometry;vertices=new float[mesh.triangles.length*3*5];int cursor=0;
            for(var face:mesh.triangles)for(int corner=0;corner<3;corner++) {
                int index=face[corner];var p=mesh.positions[index];var uv=mesh.uv[index];
                vertices[cursor++]=p[0];vertices[cursor++]=p[1];vertices[cursor++]=p[2];vertices[cursor++]=uv[0];vertices[cursor++]=uv[1];
            }
        }
    }
    /** Mutable per-draw storage; retained between frames, never shared by concurrent delegates. */
    public static final class Draw {
        public Emitter emitter;
        public final float[] state=new float[18];
        public final Matrix3f linear=new Matrix3f();
        private final Matrix4f local=new Matrix4f(),view=new Matrix4f();
    }
    public static final class Frame {
        private Draw[] draws=new Draw[128];
        public int count;
        private final Matrix3f basis=new Matrix3f(),lookMatrix=new Matrix3f(),reflection=new Matrix3f().scaling(-1,1,1);
        private final Quaternionf qa=new Quaternionf(),qb=new Quaternionf(),rotation=new Quaternionf(),look=new Quaternionf();
        private final Vector3f r=new Vector3f(),u=new Vector3f(),n=new Vector3f(),forward=new Vector3f(),side=new Vector3f(),correctedUp=new Vector3f(),sourceUp=new Vector3f(),velocity=new Vector3f(),size=new Vector3f();
        public Draw get(int index){if(index<0||index>=count)throw new IndexOutOfBoundsException(index);return draws[index];}
        private Draw next() {
            if(count==draws.length)draws=java.util.Arrays.copyOf(draws,draws.length*2);
            var draw=draws[count];if(draw==null)draws[count]=draw=new Draw();count++;return draw;
        }
    }
    private static void state(Particle particle,float phase,float[] out,Frame frame) {
        float position=Math.max(0,Math.min(1,phase))*(particle.frames.length-1);int a=(int)position,b=Math.min(a+1,particle.frames.length-1);float ratio=position-a;
        var x=particle.frames[a];var y=particle.frames[b];for(int i=0;i<18;i++)out[i]=x[i]+(y[i]-x[i])*ratio;
        frame.qa.set(x[6],x[7],x[8],x[9]).normalize().slerp(frame.qb.set(y[6],y[7],y[8],y[9]).normalize(),ratio).normalize();
        out[6]=frame.qa.x;out[7]=frame.qa.y;out[8]=frame.qa.z;out[9]=frame.qa.w;
    }
    private static void look(Frame frame) {
        if(frame.velocity.lengthSquared()<1e-10f){frame.look.identity();return;}
        frame.forward.set(frame.velocity).normalize();frame.side.set(frame.sourceUp).cross(frame.forward);
        if(frame.side.lengthSquared()<1e-10f){frame.look.rotationTo(0,0,1,frame.forward.x,frame.forward.y,frame.forward.z);return;}
        frame.side.normalize();frame.correctedUp.set(frame.forward).cross(frame.side);
        frame.look.setFromNormalized(frame.lookMatrix.setColumn(0,frame.side).setColumn(1,frame.correctedUp).setColumn(2,frame.forward));
    }
    private static void linear(Emitter emitter,Particle particle,float spinElapsed,float phase,Vector3f camera,Frame frame,Matrix3f destination) {
        var cached=emitter.prepared;if(cached==null)emitter.prepared=cached=new Prepared(emitter);
        float angle=(float)Math.IEEEremainder(particle.sourceAngle+particle.sourceSpin*(double)spinElapsed,Math.PI*2);
        if(emitter.rotateAxis==2)frame.rotation.rotationZ(angle);else if(emitter.rotateAxis==1)frame.rotation.rotationY(angle);else frame.rotation.rotationX(angle);
        if(emitter.billboardType==1) {
            var p=particle.sourcePosition;var v=particle.sourceVelocity;float age=phase*particle.life;
            float fx=0,fy=0,fz=0,gx=0,gy=0,gz=0;
            if(particle.sourceForce!=null){fx=particle.sourceForce[0];fy=particle.sourceForce[1];fz=particle.sourceForce[2];}
            if(particle.sourceGravity!=null){gx=particle.sourceGravity[0];gy=particle.sourceGravity[1];gz=particle.sourceGravity[2];}
            frame.velocity.set(v[0]+(.5f*fx+gx)*age,v[1]+(.5f*fy+gy)*age,v[2]+(.5f*fz+gz)*age);
            if(emitter.rotateAxis==2) {
                // A * inverse(.01 * Jx * A) * camera == 100 * Jx * camera.
                // This is the same delivered DLL mixed-space expression without per-particle inversions.
                frame.sourceUp.set(-100*camera.x-cached.tx-p[0]-frame.velocity.x*age,100*camera.y-cached.ty-p[1]-frame.velocity.y*age,100*camera.z-cached.tz-p[2]-frame.velocity.z*age);
                cached.inverseRotation.transform(frame.sourceUp);look(frame);frame.rotation.set(frame.look).rotateX((float)Math.PI/2).rotateZ(angle);
            } else {frame.sourceUp.set(0,1,0);look(frame);frame.rotation.mul(frame.look.rotateX((float)Math.PI/2));}
        }
        float position=Math.max(0,Math.min(1,phase))*(particle.sourceSizeFrames.length-1);int a=(int)position,b=Math.min(a+1,particle.sourceSizeFrames.length-1);float ratio=position-a;
        var x=particle.sourceSizeFrames[a];var y=particle.sourceSizeFrames[b];frame.size.set(x[0]+(y[0]-x[0])*ratio,x[1]+(y[1]-x[1])*ratio,x[2]+(y[2]-x[2])*ratio);
        if(emitter.billboardType==0)destination.set(frame.basis).mul(cached.stretch).rotate(frame.rotation).scale(frame.size).mul(frame.reflection);
        else destination.set(cached.left).rotate(frame.rotation).scale(frame.size).mul(frame.reflection);
    }
    /** Allocation-free after warmup; immutable topology and all source particle layers are retained. */
    public static void fill(Data data,float seconds,Vector3f right,Vector3f up,Vector3f camera,Frame frame) {
        frame.count=0;if(data==null||!Float.isFinite(seconds)||seconds<0)return;
        frame.r.set(right).normalize();frame.n.set(frame.r).cross(up).normalize();frame.u.set(frame.n).cross(frame.r).normalize();
        frame.basis.setColumn(0,frame.r).setColumn(1,frame.u).setColumn(2,frame.n);
        if(data.ordered==null)data.ordered=ordered(data);
        for(var emitter:data.ordered)for(var particle:emitter.particles) {
            float spinElapsed=seconds-particle.birth,elapsed=spinElapsed+emitter.prewarm;if(elapsed<0)continue;
            long cycle=emitter.loop?(long)Math.floor(elapsed/particle.life):0;float age=emitter.loop?(float)(elapsed-cycle*(double)particle.life):elapsed;
            if(age<0||age>=particle.life)continue;var draw=frame.next();float phase=age/particle.life;state(particle,phase,draw.state,frame);orbit(emitter,elapsed,draw.state);
            if(draw.state[13]<=.0001f){frame.count--;continue;}draw.emitter=emitter;
            if(emitter.rows>0){int cells=emitter.rows*emitter.columns,cell=Math.min(cells-1,(int)Math.floor(phase*cells));draw.state[16]+=(float)(cell%emitter.columns)/emitter.columns;draw.state[17]-=(float)(cell/emitter.columns)/emitter.rows;}
            linear(emitter,particle,spinElapsed,phase,camera,frame,draw.linear);
        }
    }
    /** Use BufferBuilder's native full-vertex fast path: matrix overloads allocate vectors per vertex in MC1.20.1. */
    public static long emit(Draw draw,Matrix4f pose,Matrix3f normal,com.mojang.blaze3d.vertex.VertexConsumer out) {
        var s=draw.state;draw.local.set(draw.linear).setTranslation(s[0],s[1],s[2]);pose.mul(draw.local,draw.view);var matrix=draw.view;
        var vertices=draw.emitter.prepared.vertices;float red=Math.min(1,s[10]),green=Math.min(1,s[11]),blue=Math.min(1,s[12]),alpha=Math.min(1,s[13]);
        float nx=normal.m20(),ny=normal.m21(),nz=normal.m22();
        for(int i=0;i<vertices.length;i+=5) {
            float x=vertices[i],y=vertices[i+1],z=vertices[i+2];
            float px=matrix.m00()*x+matrix.m10()*y+matrix.m20()*z+matrix.m30(),py=matrix.m01()*x+matrix.m11()*y+matrix.m21()*z+matrix.m31(),pz=matrix.m02()*x+matrix.m12()*y+matrix.m22()*z+matrix.m32();
            out.vertex(px,py,pz,red,green,blue,alpha,vertices[i+3]*s[14]+s[16],vertices[i+4]*s[15]-s[17],0,15728880,nx,ny,nz);
        }
        return vertices.length/5;
    }
}
