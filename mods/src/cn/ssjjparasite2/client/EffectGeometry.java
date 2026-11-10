package cn.ssjjparasite2.client;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Stateless source-FX sampler shared by production rendering and offline QA. */
public final class EffectGeometry {
    public static final class Mesh { public float[][] positions,uv; public int[][] triangles; }
    public static final class Material { public String texture; public float[] color,st; }
    public static final class Track { public int component; public float constant; public float[][] keys; }
    public static final class Animation { public float duration; public Track[] tracks; }
    public static final class Layer { public String name; public Mesh geometry; public Material material; public Animation animation; }
    public static final class Shape { public boolean enabled; public int type; public float radius,angle,length,thickness=1,spherical;public float[] scale,position,rotation; }
    public static final class UV { public boolean enabled,forwardRows; public int x,y; public float cycles; public float[] frame; }
    public static final class Burst {public float time,repeat; public int count,cycles;}
    public static final class Emitter {
        public String name; public float[][] transform; public float duration,delay,rate;
        public int maxParticles,mode; public float lengthScale,velocityScale;
        public float[] lifetime,speed,size,rotation,rotationX,rotationY,initialColor,sizeCurve,sizeYCurve,sizeZCurve,spin,acceleration;
        public float[][] color; public Shape shape; public UV uv; public Material material; public Mesh geometry;
        public Burst[] bursts;
    }
    public static final class Effect { public Layer[] layers; public Emitter[] emitters; }
    public static final class Data { public int schema; public String units,gun,bone; public float[] muzzleLocal; public Effect body,muzzle,impact; }
    public record Batch(Material material,Mesh mesh,float[] color,float[] uvST) {}
    private EffectGeometry() {}
    public static Matrix4f matrix(float[][] a) {
        // Element setters retain JOML identity fast-path flags in this runtime.
        // Bulk set recomputes properties so translate/transformPosition honor
        // the imported emitter rotation, origin and nonuniform scale.
        float[] columns=new float[16];for(int r=0;r<4;r++)for(int c=0;c<4;c++)columns[c*4+r]=a[r][c];return new Matrix4f().set(columns);
    }
    public static float sample(float[] a,float t) {
        float p=Math.max(0,Math.min(1,t))*(a.length-1);int i=(int)p,j=Math.min(i+1,a.length-1);return a[i]+(a[j]-a[i])*(p-i);
    }
    private static float[] color(float[][] a,float t) {
        float[] out=new float[4];for(int k=0;k<4;k++){float p=Math.max(0,Math.min(1,t))*(a.length-1);int i=(int)p,j=Math.min(i+1,a.length-1);out[k]=a[i][k]+(a[j][k]-a[i][k])*(p-i);}return out;
    }
    public static float track(Track tr,float time) {
        float[][] keys=tr.keys;if(keys==null||keys.length==0)return tr.constant;
        int i=0;while(i+1<keys.length&&keys[i+1][0]<=time)i++;
        float[] k=keys[i];float t=Math.max(0,time-k[0]);return ((k[1]*t+k[2])*t+k[3])*t+k[4];
    }
    public static float[] uv(Layer layer,float time) {
        float[] st=layer.material.st.clone();Animation a=layer.animation;
        if(a!=null)for(Track tr:a.tracks)st[tr.component]=track(tr,time%a.duration);
        return st;
    }
    private static float random(int seed,int i,int slot) {
        int x=seed^(i*0x9e3779b9)^(slot*0x85ebca6b);x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;x^=x>>>16;return (x&0xffffff)/16777216f;
    }
    private static float range(float[] a,float r){return a[0]+(a[1]-a[0])*r;}
    public static List<Batch> sample(Effect effect,float time,int seed,boolean loop,Vector3f cameraRight,Vector3f cameraUp) {
        List<Batch> out=new ArrayList<>();if(time<0)return out;
        for(Layer layer:effect.layers)out.add(new Batch(layer.material,layer.geometry,layer.material.color,uv(layer,time)));
        for(int ei=0;ei<effect.emitters.length;ei++) {
            Emitter e=effect.emitters[ei];int localSeed=seed+ei*7919;
            if(e.maxParticles<=0)continue;
            float maxLife=Math.max(e.lifetime[0],e.lifetime[1]);
            int count=Math.min(e.maxParticles,(int)Math.ceil(e.rate*(loop?maxLife:e.duration)-.000001));
            // Absolute-time births avoid resetting the loop when moving or
            // changing render contexts. Source rate and lifetime define overlap.
            int last=loop&&e.rate>0?(int)Math.floor((time-e.delay)*e.rate):count-1;
            int first=loop?Math.max(0,last-count+1):0;
            List<float[]> births=new ArrayList<>();
            if(e.rate>0)for(int i=first;i<=last;i++)births.add(new float[]{i,e.delay+i/e.rate});
            // Revisit only the live window, retaining the source burst schedule
            // across emitter loops. A 90-cycle source burst must not stop after
            // the first maxParticles births have expired.
            int periodFirst=loop?Math.max(0,(int)Math.floor((time-e.delay-maxLife)/e.duration)):0;
            int periodLast=loop?Math.max(0,(int)Math.floor((time-e.delay)/e.duration)):0;
            for(int period=periodFirst;period<=periodLast;period++)for(int bi=0;bi<e.bursts.length;bi++){
                Burst b=e.bursts[bi];float start=e.delay+period*e.duration+b.time;
                int cycleFirst=b.repeat>0?Math.max(0,(int)Math.floor((time-maxLife-start)/b.repeat)):0;
                int cycleLast=b.repeat>0?Math.min(b.cycles-1,(int)Math.floor((time-start)/b.repeat)):b.cycles-1;
                for(int cycle=cycleFirst;cycle<=cycleLast;cycle++){
                    float born=start+cycle*b.repeat;if(born>time||born<=time-maxLife)continue;
                    for(int particle=0;particle<b.count&&births.size()<e.maxParticles;particle++)births.add(new float[]{100000+period*8192+bi*1024+cycle*b.count+particle,born});
                }
            }
            for(float[] birth:births) {
                int i=(int)birth[0];float born=birth[1],age=time-born;
                float life=range(e.lifetime,random(localSeed,i,0));if(age<0||age>=life)continue;
                float p=age/life,size=range(e.size,random(localSeed,i,1));float speed=range(e.speed,random(localSeed,i,2));
                float rotation=range(e.rotation,random(localSeed,i,3))+sample(e.spin,p)*age;
                float[] col=color(e.color,p);for(int k=0;k<4;k++)col[k]*=e.initialColor[k]*e.material.color[k];
                if(col[3]<.001f)continue;
                float[] st=e.material.st.clone();
                if(e.uv.enabled){int frames=e.uv.x*e.uv.y;int frame=Math.min(frames-1,(int)(sample(e.uv.frame,p)*e.uv.cycles*frames)%frames);st[0]/=e.uv.x;st[1]/=e.uv.y;st[2]+=(frame%e.uv.x)/(float)e.uv.x;
                    // emit() subtracts the V offset after the imported UV flip. High-energy
                    // scope atlases need a negative offset to advance PNG rows downwards.
                    // The default retains all existing normal-state effect sampling.
                    st[3]+=(e.uv.forwardRows?-1:1)*(frame/e.uv.x)/(float)e.uv.y;}
                Matrix4f source=matrix(e.transform);float radius=e.shape.enabled?e.shape.radius:0;
                float az=random(localSeed,i,4)*(float)(Math.PI*2),r=(float)Math.sqrt(random(localSeed,i,5))*radius;
                float angle=e.shape.enabled?(float)Math.toRadians(e.shape.angle)*random(localSeed,i,6):0;
                Vector3f velocity=new Vector3f((float)Math.cos(az)*(float)Math.sin(angle)*speed,(float)Math.sin(az)*(float)Math.sin(angle)*speed,(float)Math.cos(angle)*speed);
                Vector3f offset=new Vector3f(r*(float)Math.cos(az),r*(float)Math.sin(az),0).fma(age,velocity);
                if(e.shape.enabled&&e.shape.type==0){float z=2*random(localSeed,i,7)-1,xy=(float)Math.sqrt(1-z*z);velocity.set(xy*(float)Math.cos(az),xy*(float)Math.sin(az),z);offset.set(velocity).mul(radius*(float)Math.cbrt(random(localSeed,i,5))).fma(age*speed,velocity);}
                if(e.shape.enabled&&e.shape.scale!=null){
                    Vector3f birthPoint=new Vector3f(offset).fma(-age,velocity);
                    if(e.shape.type==5){float[] sc=e.shape.scale;birthPoint.set((random(localSeed,i,8)-.5f)*sc[0],(random(localSeed,i,9)-.5f)*sc[1],(random(localSeed,i,10)-.5f)*sc[2]);if(e.shape.spherical>0&&birthPoint.lengthSquared()>1e-8)velocity.set(birthPoint).normalize().mul(speed);}
                    else if(e.shape.type==8){float depth=random(localSeed,i,8)*e.shape.length;float radial=(radius+depth*(float)Math.tan(Math.toRadians(e.shape.angle)))*(e.shape.thickness==0?1:(float)Math.sqrt(random(localSeed,i,5)));birthPoint.set(radial*(float)Math.cos(az),radial*(float)Math.sin(az),depth);birthPoint.mul(e.shape.scale[0],e.shape.scale[1],e.shape.scale[2]);}
                    if(e.shape.rotation!=null){float[] q=e.shape.rotation;Matrix4f rot=new Matrix4f().rotateY((float)Math.toRadians(q[1])).rotateX((float)Math.toRadians(q[0])).rotateZ((float)Math.toRadians(q[2]));rot.transformDirection(birthPoint);rot.transformDirection(velocity);}
                    if(e.shape.position!=null)birthPoint.add(e.shape.position[0],e.shape.position[1],e.shape.position[2]);offset.set(birthPoint).fma(age,velocity);
                }
                source.translate(offset);
                Mesh rendered;
                if(e.mode==4) {
                    if(e.rotationY!=null)source.rotateY(range(e.rotationY,random(localSeed,i,11)));if(e.rotationX!=null)source.rotateX(range(e.rotationX,random(localSeed,i,12)));
                    source.rotateZ(rotation).scale(size*sample(e.sizeCurve,p),size*sample(e.sizeYCurve,p),size*sample(e.sizeZCurve,p));
                    rendered=transformed(e.geometry,source,true);
                    if(e.acceleration!=null)for(float[] point:rendered.positions)for(int axis=0;axis<3;axis++)point[axis]+=e.acceleration[axis]*age*age*.5f;
                } else {
                    Vector3f center=source.transformPosition(new Vector3f()).mul(.01f);center.x=-center.x;
                    center.add(e.acceleration[0]*age*age*.5f,e.acceleration[1]*age*age*.5f,e.acceleration[2]*age*age*.5f);
                    float w=.01f*size*sample(e.sizeCurve,p),h=.01f*size*sample(e.sizeYCurve,p);
                    Vector3f right=new Vector3f(cameraRight),up=new Vector3f(cameraUp);
                    if(e.mode==1) {
                        Vector3f direction=source.transformDirection(new Vector3f(0,0,1));direction.x=-direction.x;
                        if(direction.lengthSquared()>.00001f){up.set(direction).normalize();Vector3f normal=new Vector3f(cameraRight).cross(cameraUp);right.set(up).cross(normal);if(right.lengthSquared()<.00001f)right.set(cameraRight);else right.normalize();}
                        h=Math.max(h,w*e.lengthScale+.01f*speed*e.velocityScale);
                    }
                    Vector3f rvec=new Vector3f(right).mul((float)Math.cos(rotation)).fma((float)Math.sin(rotation),up).mul(w*.5f);
                    Vector3f uvec=new Vector3f(up).mul((float)Math.cos(rotation)).fma(-(float)Math.sin(rotation),right).mul(h*.5f);
                    rendered=quad(center,rvec,uvec);
                }
                out.add(new Batch(e.material,rendered,col,st));
            }
        }
        return out;
    }
    private static Mesh transformed(Mesh mesh,Matrix4f transform,boolean reflect) {
        Mesh out=new Mesh();out.positions=new float[mesh.positions.length][3];out.uv=mesh.uv;out.triangles=mesh.triangles;
        for(int i=0;i<out.positions.length;i++){float[] a=mesh.positions[i];Vector3f p=transform.transformPosition(new Vector3f(a[0],a[1],a[2])).mul(.01f);out.positions[i]=new float[]{reflect?-p.x:p.x,p.y,p.z};}return out;
    }
    private static Mesh quad(Vector3f center,Vector3f right,Vector3f up) {
        Mesh m=new Mesh();m.positions=new float[4][3];int[][] signs={{-1,-1},{1,-1},{1,1},{-1,1}};
        for(int i=0;i<4;i++){Vector3f p=new Vector3f(center).fma(signs[i][0],right).fma(signs[i][1],up);m.positions[i]=new float[]{p.x,p.y,p.z};}
        m.uv=new float[][]{{0,1},{1,1},{1,0},{0,0}};m.triangles=new int[][]{{0,1,2},{0,2,3}};return m;
    }
    public static float lifetime(Effect e) {
        float max=0;for(Emitter p:e.emitters)max=Math.max(max,p.delay+p.duration+Math.max(p.lifetime[0],p.lifetime[1]));return max;
    }
    public static void validate(Data d) {
        if(d==null||d.schema!=1||!ParasiteScopes.family(new net.minecraft.resources.ResourceLocation(d.gun))||!"WeaponAnchor2".equals(d.bone))throw new IllegalArgumentException("Invalid Parasite FX schema");
        for(Effect e:new Effect[]{d.body,d.muzzle,d.impact}){
            if(e==null||e.layers==null||e.emitters==null||e.layers.length>16||e.emitters.length>16)throw new IllegalArgumentException("Unbounded effect data");
            for(Layer l:e.layers){check(l.geometry);material(l.material);if(l.animation!=null&&l.animation.duration<=0)throw new IllegalArgumentException("Invalid UV loop");}
            for(Emitter p:e.emitters){if(p.maxParticles<0||p.maxParticles>64||p.rate<0||p.duration<=0||p.duration>15||p.lifetime[0]<=0||p.lifetime[1]>15||p.bursts.length>8||p.mode<0||p.mode>4)throw new IllegalArgumentException("Invalid particle limits");for(Burst b:p.bursts)if(b.count<0||b.count>64||b.cycles<1||b.cycles>1024||!Float.isFinite(b.repeat)||b.repeat<0)throw new IllegalArgumentException("Unbounded burst");material(p.material);if(p.geometry!=null)check(p.geometry);}
        }
    }
    private static void material(Material m){if(m==null||m.texture==null||!m.texture.startsWith("ssjjparasite2:textures/effects/")||m.color.length!=4||m.st.length!=4)throw new IllegalArgumentException("Invalid effect material");}
    private static void check(Mesh m){if(m==null||m.positions==null||m.positions.length>20000||m.positions.length!=m.uv.length)throw new IllegalArgumentException("Invalid effect mesh");for(float[] p:m.positions)for(float v:p)if(!Float.isFinite(v)||Math.abs(v)>2000)throw new IllegalArgumentException("Invalid FX vertex");for(int[] f:m.triangles)for(int i:f)if(i<0||i>=m.positions.length)throw new IllegalArgumentException("Invalid FX index");}
}
