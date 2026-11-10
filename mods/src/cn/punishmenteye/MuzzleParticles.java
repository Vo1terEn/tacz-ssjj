package cn.punishmenteye;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Source particle meshes and effectors. Unity RNG uses stable samples; ColorValue3 preserves .NET RNG. */
public final class MuzzleParticles {
    private MuzzleParticles() {}

    /** Raw Unity mesh coordinates and bottom-left UVs, before the emitter transform. */
    public static final class Mesh {
        public float[][] vertices, normals, uv;
        public int[][] faces;
    }

    public static final class Emitter {
        public String name, texture, blend;
        public Mesh mesh;
        public int particleCount, colorType, rotateAxis, billboardType, cols, rows, layer, shapeType, JustAlpha;
        public boolean uniformScale, shaderBillboard, loop, randomOnBorn, randomFrame, shapeVolume, hasGradientColor;
        public float[] alphaRange, lifetimeRange, sizeRange, angleRange, rotVelocityRange, velocityRange;
        public float[][] colorRange, sizeRanges, alphaCurve, localMatrix, sourceToWeaponAxes;
        public float[] gradientRatios, sourceToWeaponOffset;
        public int[] gradientColors;
        public float radius, length, sourceScale, prewarm;
        public StartTime startTime;
        // New definitions supply direction; legacy E014 keeps its byte-exact path.
        public boolean hasSizeEffector, hasUVEffector, hasForceEffector, directionByShape;
        public boolean hasGravityEffector, hasAccelerateEffector, hasOscillatorEffector, hasAttractEffector, hasOrbitEffector;
        public float[] sizeTimes, sizeXCurve, sizeYCurve, sizeZCurve, uvTimes, uCurve, vCurve;
        public float force, gravity, accelerate, oscillatorDegree, oscillatorDistance, attractVelocity, orbitDegree, orbitRadius;
        public float[] forceDirection, gravityDirection, attractPoint, materialTint, emitterRotation;
        public Shape shape;
        public Direction direction;
        public SourceColor sourceColor;
        public boolean isWorldParticle, hasAlphaEffector, hasColorEffector, hasSpriteSheetEffector;
        public int realCount;
    }

    public static final class StartTime {
        public int oneDType;
        public float _value, _min, _max, _maxCurveAnchorValue;
        public String _curveType;
        public Anchor[] _anchors;
    }

    public static final class Anchor { public float _time, _value; }
    public static final class SourceVector { public float x,y,z; }
    public static final class Shape {
        public int shapeType, _shell, _volum;
        public SourceVector _rectFrom, _rectTo;
        public float _angle, _length, _radius, sinAngle, cosAngle, _radiusSmall, _radiusBig, _height;
    }
    public static final class Direction {
        public int threeDType;
        public SourceVector value, _min, _max, _center;
        public float _innerRadius, _outerRadius;
    }
    public static final class SourceColor {
        public int colorType, _value, _color0, _color1;
        public int[] _colors;
        public float[] _colorRatios;
    }

    /** Separate streams preserve a sample for the entire lifetime and across partial ticks. */
    public static float random(Emitter e, int index, int cycle, int stream) {
        int h=e.name.hashCode() + index*0x9e3779b9 + cycle*0x632be5ab + stream*0x85157af5;
        h^=h>>>16; h*=0x7feb352d; h^=h>>>15; h*=0x846ca68b; h^=h>>>16;
        return (h&0xffffff)/16777216f;
    }

    private static float sample(Emitter e, float[] range, int index, int cycle, int stream) {
        return range[0]+(range[1]-range[0])*random(e,index,cycle,stream);
    }

    /** Source startTime's normalized curve is sampled along the emitter's particle indices. */
    public static float delay(Emitter e,int index) {
        StartTime s=e.startTime;
        if(s==null)return 0;
        if(s.oneDType==0)return s._value;
        if(s.oneDType==2)return s._min+(s._max-s._min)*random(e,index,0,70);
        if(s._anchors==null||s._anchors.length==0)return 0;
        // Start() advances its sample before initializing the first particle.
        float t=(index+1f)/Math.max(1,e.particleCount);
        if(e.direction!=null) {
            // Source CurveAnchor.InterpolateLinear really subtracts the first key's
            // TIME from the second VALUE. Preserve that DLL behavior for new data.
            if(!"0".equals(s._curveType))throw unsupported(e,"startTime interpolation "+s._curveType);
            float value=s._anchors[0]._value;
            for(int i=0;i<s._anchors.length-1;i++)if(s._anchors[i+1]._time>t){
                Anchor a=s._anchors[i],b=s._anchors[i+1];
                value=a._value+(t-a._time)/(b._time-a._time)*(b._value-a._time);break;
            }
            return value*s._maxCurveAnchorValue;
        }
        int hi=0,last=s._anchors.length-1;
        // OneDValue.GetValue leaves its initial first-key value when no next.time > t.
        if(t>=s._anchors[last]._time)return s._anchors[0]._value*s._maxCurveAnchorValue;
        while(hi<last&&t>s._anchors[hi]._time)hi++;
        int lo=Math.max(0,hi-1);float span=s._anchors[hi]._time-s._anchors[lo]._time;
        float f=span>0?clamp((t-s._anchors[lo]._time)/span):0;
        return (s._anchors[lo]._value+(s._anchors[hi]._value-s._anchors[lo]._value)*f)*s._maxCurveAnchorValue;
    }

    /** [elapsed within lifetime, lifetime, cycle], or null before emission/after a nonlooping life. */
    public static float[] clock(Emitter e,int index,float ticks) {
        float remaining=ticks/20f+e.prewarm-delay(e,index);
        if(remaining<0)return null;
        // Lifetime is assigned only in Start(); internalInit() does not replace it on a loop.
        float life=Math.max(.001f,sample(e,e.lifetimeRange,index,0,0));
        int cycle=(int)(remaining/life);
        if(cycle>0&&!e.loop)return null;
        remaining-=cycle*life;
        return new float[]{remaining,life,cycle};
    }

    private static float clamp(float n) { return Math.max(0,Math.min(1,n)); }

    private static float curve(float[][] keys,float t) {
        if(keys==null||keys.length==0)return 1;
        int hi=0,last=keys.length-1;while(hi<last&&t>keys[hi][0])hi++;
        int lo=Math.max(0,hi-1);float span=keys[hi][0]-keys[lo][0];
        float f=span>0?clamp((t-keys[lo][0])/span):0;
        return keys[lo][1]+(keys[hi][1]-keys[lo][1])*f;
    }

    private static IllegalArgumentException unsupported(Emitter e,String feature) {
        return new IllegalArgumentException("Unsupported source particle "+e.name+": "+feature);
    }

    /** DLL UpdateSize/UpdateUV: strict next.time > phase, including repeated zero keys. */
    public static float effector(float[] times,float[] values,float phase) {
        if(times==null||values==null||times.length==0||times.length!=values.length)
            throw new IllegalArgumentException("Missing source effector keys");
        int last=times.length-1;
        if(phase>times[last])return values[last];
        for(int i=0;i<times.length;i++)if(times[i]>phase){
            if(i==0)return values[0];
            float span=times[i]-times[i-1];
            return (phase-times[i-1])/span*values[i]+(times[i]-phase)/span*values[i-1];
        }
        return values[last];
    }

    /** Validate the selected native definitions, including their source-space gravity. */
    public static void validate(Emitter e) {
        if(e.direction!=null&&e.shape==null)throw unsupported(e,"missing source shape");
        if(e.hasAccelerateEffector||e.hasOrbitEffector||e.hasAlphaEffector||e.hasColorEffector)
            throw unsupported(e,"acceleration/orbit or separate alpha/color effector");
        if(e.hasGravityEffector){
            if(e.gravityDirection==null||e.gravityDirection.length!=3||e.emitterRotation==null||e.emitterRotation.length!=4)throw unsupported(e,"missing source gravity direction/emitter rotation");
            for(float value:e.gravityDirection)if(!Float.isFinite(value))throw unsupported(e,"nonfinite source gravity direction");
            float length=0;for(float value:e.emitterRotation){if(!Float.isFinite(value))throw unsupported(e,"nonfinite emitter rotation");length+=value*value;}
            if(length<1e-12f||!Float.isFinite(e.gravity))throw unsupported(e,"invalid source gravity/rotation");
        }
        if(e.hasAttractEffector&&(e.attractPoint==null||e.attractPoint.length!=3))throw unsupported(e,"missing attract point");
        if(e.hasOscillatorEffector&&e.oscillatorDistance!=0&&e.oscillatorDegree!=0)throw unsupported(e,"nonzero oscillator requires native camera-space validation");
        if(e.billboardType!=0&&e.billboardType!=1&&e.billboardType!=4)throw unsupported(e,"billboard "+e.billboardType);
        if(e.billboardType==1&&(e.emitterRotation==null||e.emitterRotation.length!=4))throw unsupported(e,"missing pure emitter quaternion");
        if(e.isWorldParticle)throw unsupported(e,"world-space simulation");
        if(e.hasSizeEffector){effector(e.sizeTimes,e.sizeXCurve,0);effector(e.sizeTimes,e.sizeYCurve,0);effector(e.sizeTimes,e.sizeZCurve,0);}
        if(e.hasUVEffector){effector(e.uvTimes,e.uCurve,0);effector(e.uvTimes,e.vCurve,0);}
        if(e.hasForceEffector&&(e.forceDirection==null||e.forceDirection.length!=3))throw unsupported(e,"missing force direction");
    }

    /** RGB uses the source ColorValue range; JustAlpha suppresses the stored RGB gradient. */
    public static float[] rgba(Emitter e,int index,float[] state) {
        int cycle=e.randomOnBorn?(int)state[2]:0;float phase=state[0]/state[1];
        // ColorRandomBetweenValue uses two identical gradients evaluated at the
        // initialization argument: Start's birth sample, then a random sample on rebirth.
        float colorSample=cycle==0?(index+1f)/Math.max(1,e.particleCount):random(e,index,cycle,1);
        float[] rgba=new float[4];
        for(int c=0;c<3;c++)rgba[c]=e.colorRange[0][c]+(e.colorRange[1][c]-e.colorRange[0][c])*colorSample;
        // A lifetime RGB gradient replaces initialization color completely. This
        // also avoids inventing normalization for unused out-of-range ColorValue keys.
        if(e.sourceColor!=null&&!(e.hasGradientColor&&e.JustAlpha==0&&e.gradientColors!=null&&e.gradientColors.length>0)) {
            float[] source=initialColor(e.sourceColor,colorSample);
            System.arraycopy(source,0,rgba,0,3);
        }
        if(e.hasGradientColor&&e.JustAlpha==0&&e.gradientColors!=null&&e.gradientColors.length>0) {
            int hi=0,last=e.gradientColors.length-1;while(hi<last&&phase>e.gradientRatios[hi])hi++;
            int lo=Math.max(0,hi-1);float span=e.gradientRatios[hi]-e.gradientRatios[lo];
            float f=span>0?clamp((phase-e.gradientRatios[lo])/span):0;
            for(int c=0;c<3;c++){int shift=16-c*8;float a=(e.gradientColors[lo]>>>shift&255)/255f,b=(e.gradientColors[hi]>>>shift&255)/255f;rgba[c]=a+(b-a)*f;}
        }
        rgba[3]=clamp(sample(e,e.alphaRange,index,cycle,2))*curve(e.alphaCurve,phase);
        if(e.materialTint!=null)for(int c=0;c<4;c++)rgba[c]*=e.materialTint[c];
        return rgba;
    }

    /** Source Shape.GetValue branch1: zero-angle cone, linear-radius sampling, +Y volume. */
    public static Vector3f position(Emitter e,int index,int cycle) {
        if(e.direction!=null)return shape(e,index,cycle)[0];
        double angle=random(e,index,cycle,3)*Math.PI*2;
        float r=e.radius*random(e,index,cycle,4);
        return new Vector3f((float)(-r*Math.sin(angle)),e.shapeVolume?e.length*random(e,index,cycle,5):0,(float)(r*Math.cos(angle)));
    }

    private static Vector3f vector(SourceVector v) { return v==null?new Vector3f():new Vector3f(v.x,v.y,v.z); }
    private static Vector3f normalized(Vector3f v) { return v.lengthSquared()>1e-10f?v.normalize():v.zero(); }

    /** Source Shape.GetValue: box, cone, cylinder and sphere, with its linear radius distribution. */
    public static Vector3f[] shape(Emitter e,int index,int cycle) {
        Shape s=e.shape;if(s==null)return new Vector3f[]{position(e,index,cycle),new Vector3f(0,1,0)};
        float angle=random(e,index,cycle,3)*(float)(Math.PI*2);
        Vector3f p,d;
        switch(s.shapeType){
            case 0 -> {
                Vector3f from=vector(s._rectFrom),to=vector(s._rectTo);
                p=new Vector3f(from.x+(to.x-from.x)*random(e,index,cycle,3),from.y+(to.y-from.y)*random(e,index,cycle,4),from.z+(to.z-from.z)*random(e,index,cycle,5));
                d=normalized(new Vector3f(p));
            }
            case 1 -> {
                d=normalized(new Vector3f(-s.sinAngle*(float)Math.sin(angle),s.cosAngle,s.sinAngle*(float)Math.cos(angle)));
                float radius=s._shell!=0?s._radius:s._radius*random(e,index,cycle,4);
                p=new Vector3f(-radius*(float)Math.sin(angle),0,radius*(float)Math.cos(angle));
                if(s._volum!=0){
                    if(Math.abs(d.y)<1e-8f)throw unsupported(e,"zero-Y volumetric cone direction");
                    p.fma(s._length/d.y*random(e,index,cycle,5),d);
                }
            }
            case 2 -> {
                float radius=(s._radiusBig-s._radiusSmall)*random(e,index,cycle,4)+s._radiusSmall;
                p=new Vector3f(-radius*(float)Math.sin(angle),s._height*(2*random(e,index,cycle,5)-1),radius*(float)Math.cos(angle));d=new Vector3f(0,0,1);
            }
            case 3 -> {
                float radius=(s._radiusBig-s._radiusSmall)*random(e,index,cycle,4)+s._radiusSmall;
                float latitude=(float)Math.PI*random(e,index,cycle,5)+(float)Math.PI/2;
                p=new Vector3f(-radius*(float)Math.cos(latitude)*(float)Math.sin(angle),radius*(float)Math.sin(latitude),radius*(float)Math.cos(latitude)*(float)Math.cos(angle));
                d=normalized(new Vector3f(p));
            }
            default -> throw unsupported(e,"shape "+s.shapeType);
        }
        return new Vector3f[]{p,d};
    }

    /** ThreeDValue.GetValue is not normalized before multiplying the sampled velocity. */
    public static Vector3f direction(Emitter e,int index,int cycle) {
        Direction d=e.direction;if(d==null)return new Vector3f();
        switch(d.threeDType){
            case 0: return vector(d.value);
            case 3: {
                Vector3f a=vector(d._min),b=vector(d._max);
                return new Vector3f(a.x+(b.x-a.x)*random(e,index,cycle,20),a.y+(b.y-a.y)*random(e,index,cycle,21),a.z+(b.z-a.z)*random(e,index,cycle,22));
            }
            case 4: {
                float radius=(d._outerRadius-d._innerRadius)*random(e,index,cycle,20)+d._innerRadius;
                float angle=(float)(Math.PI*2)*random(e,index,cycle,21),latitude=(float)Math.PI*random(e,index,cycle,22)+(float)Math.PI/2;
                return new Vector3f(radius*(float)Math.cos(latitude)*(float)Math.cos(angle),radius*(float)Math.sin(latitude),radius*(float)Math.cos(latitude)*(float)Math.sin(angle)).add(vector(d._center));
            }
            default: throw unsupported(e,"ThreeDValue "+d.threeDType);
        }
    }

    /** UpdateIntiData runs on every rebirth even when RandomOnBorn is false. */
    public static Vector3f movingPosition(Emitter e,int index,float[] state) {
        return shape(e,index,(int)state[2])[0].fma(state[0],velocity(e,index,state));
    }

    public static Vector3f velocity(Emitter e,int index,float[] state) {
        int cycle=(int)state[2];
        Vector3f velocity=e.directionByShape?shape(e,index,cycle)[1]:direction(e,index,cycle);
        velocity.mul(sample(e,e.velocityRange,index,cycle,23));
        if(e.hasForceEffector)velocity.add(new Vector3f(e.forceDirection[0],e.forceDirection[1],e.forceDirection[2]).mul(e.force).mul(state[0]).mul(.5f));
        // SSJJ Particle.UpdateGravity uses gravity*t without the force effector's 1/2.
        // Convert source-world gravity through the pure emitter rotation before UpdatePos.
        if(e.hasGravityEffector){
            Vector3f gravity=new Vector3f(e.gravityDirection).mul(state[0]*e.gravity);
            new Quaternionf(e.emitterRotation[0],e.emitterRotation[1],e.emitterRotation[2],e.emitterRotation[3]).normalize().invert().transform(gravity);
            velocity.add(gravity);
        }
        // Native Particle.UpdateAttract is evaluated after force and before UpdatePos.
        // It accelerates toward the INITIAL position-to-point direction, not current pos.
        if(e.hasAttractEffector){
            Vector3f toward=new Vector3f(e.attractPoint[0],e.attractPoint[1],e.attractPoint[2]).sub(shape(e,index,cycle)[0]);
            normalized(toward).mul(state[0]*.5f*e.attractVelocity);velocity.add(toward);
        }
        return velocity;
    }

    private static float channel(int color,int c){return (color>>>(16-c*8)&255)/255f;}

    /** Actual .NET Framework System.Random, used by source ColorRandomValue. */
    private static final class DotNetRandom {
        final int[] seed=new int[56];int next=0,nextp=21;
        DotNetRandom(int initial){
            int mj=161803398-(initial==Integer.MIN_VALUE?Integer.MAX_VALUE:Math.abs(initial));
            seed[55]=mj;int mk=1;
            for(int i=1;i<55;i++){
                int ii=21*i%55;seed[ii]=mk;mk=mj-mk;if(mk<0)mk+=Integer.MAX_VALUE;mj=seed[ii];
            }
            for(int k=1;k<5;k++)for(int i=1;i<56;i++){
                seed[i]-=seed[1+(i+30)%55];if(seed[i]<0)seed[i]+=Integer.MAX_VALUE;
            }
        }
        int next(int min,int max){
            if(++next>=56)next=1;if(++nextp>=56)nextp=1;
            int value=seed[next]-seed[nextp];if(value==Integer.MAX_VALUE)value--;if(value<0)value+=Integer.MAX_VALUE;
            seed[next]=value;return (int)(value*(1.0/Integer.MAX_VALUE)*(max-min))+min;
        }
    }

    public static float[] initialColor(SourceColor color,float sample) {
        float[] result=new float[3];
        if(color.colorType==3){
            DotNetRandom random=new DotNetRandom((int)(sample*100000));
            for(int c=0;c<3;c++){
                float a=channel(color._color0,c),b=channel(color._color1,c);
                result[c]=random.next((int)(Math.min(a,b)*256),(int)(Math.max(a,b)*256))/256f;
            }
            return result;
        }
        if(color.colorType==0){for(int c=0;c<3;c++)result[c]=channel(color._value,c);return result;}
        if(color.colorType==2){for(int c=0;c<3;c++){float a=channel(color._color0,c);result[c]=a+(channel(color._color1,c)-a)*sample;}return result;}
        if(color.colorType==1){
            // ValueTypeUtil.GenerateConstGradient passes these times literally;
            // it does not use Particle's separate ratio/256 convention.
            float[] times=color._colorRatios;int[] values=color._colors;
            if(times==null||values==null||times.length==0||times.length!=values.length)throw new IllegalArgumentException("Missing ColorValue gradient");
            // Source GenerateConstGradient passes these times directly to Unity.
            // Some world assets serialize every key >1. Keep the raw time domain;
            // do not silently reinterpret it as the separate /256 particle gradient.
            // Unity's native malformed-key packing is unavailable: this adapter
            // explicitly uses the first raw key before its time (documented export boundary).
            for(float time:times)if(!Float.isFinite(time))throw new IllegalArgumentException("Nonfinite ColorValue gradient time");
            int hi=0,last=times.length-1;while(hi<last&&sample>times[hi])hi++;
            int lo=Math.max(0,hi-1);float span=times[hi]-times[lo],f=span>0?clamp((sample-times[lo])/span):0;
            for(int c=0;c<3;c++){float a=channel(values[lo],c);result[c]=a+(channel(values[hi],c)-a)*f;}return result;
        }
        throw new IllegalArgumentException("Unsupported source ColorValue "+color.colorType);
    }

    private static Matrix4f fromRows(float[][] rows) {
        Matrix4f m=new Matrix4f();
        for(int r=0;r<4;r++)for(int c=0;c<4;c++)m.set(c,r,rows[r][c]);
        return m.determineProperties();
    }

    /** Unity source -> receiver's signed model coordinates -> the actual caller view matrix. */
    private static Matrix4f emitterToView(Emitter e,PoseStack pose) {
        Matrix4f mapping=new Matrix4f();
        for(int r=0;r<3;r++)for(int c=0;c<3;c++)mapping.set(c,r,e.sourceToWeaponAxes[r][c]*e.sourceScale);
        mapping.m30(e.sourceToWeaponOffset[0]).m31(e.sourceToWeaponOffset[1]).m32(e.sourceToWeaponOffset[2]);
        mapping.determineProperties();
        return new Matrix4f(pose.last().pose()).scale(1/16f,-1/16f,1/16f).mul(mapping).mul(fromRows(e.localMatrix));
    }

    private static Quaternionf lookRotation(Vector3f forward,Vector3f up) {
        if(forward.lengthSquared()<1e-10f)return new Quaternionf();
        forward=new Vector3f(forward).normalize();Vector3f right=new Vector3f(up).cross(forward);
        if(right.lengthSquared()<1e-10f)return new Quaternionf().rotationTo(new Vector3f(0,0,1),forward);
        right.normalize();Vector3f correctedUp=new Vector3f(forward).cross(right);
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,right).setColumn(1,correctedUp).setColumn(2,forward));
    }

    /** DLL billboard1: LookRotation along current velocity, then the ctor's +90deg X. */
    public static Quaternionf velocityRotation(Emitter e,Matrix4f emitter,Vector3f position,Vector3f velocity,float rotation) {
        Quaternionf euler=e.rotateAxis==2?new Quaternionf().rotationZ(rotation):e.rotateAxis==1?new Quaternionf().rotationY(rotation):new Quaternionf().rotationX(rotation);
        if(e.rotateAxis!=2)return euler.mul(lookRotation(velocity,new Vector3f(0,1,0)).rotateX((float)Math.PI/2));
        Matrix4f local=fromRows(e.localMatrix);
        Vector3f cameraLocal=new Matrix4f(emitter).invert().transformPosition(new Vector3f());
        Vector3f cameraSource=local.transformPosition(cameraLocal);
        Vector3f up=cameraSource.sub(local.getTranslation(new Vector3f())).sub(position);
        // The original subtracts raw particle.pos from emitter world position.
        // Keep that mixed-space expression in the existing source-weapon basis.
        new Quaternionf(e.emitterRotation[0],e.emitterRotation[1],e.emitterRotation[2],e.emitterRotation[3]).normalize().invert().transform(up);
        return lookRotation(velocity,up).rotateX((float)Math.PI/2).rotateZ(rotation);
    }

    /** Source radian fields are used directly; SSJJ converts them to Euler degrees internally. */
    private static Matrix4f particleToView(Emitter e,Matrix4f emitter,int index,float[] state) {
        int cycle=e.randomOnBorn?(int)state[2]:0;
        Vector3f position=e.direction==null?position(e,index,cycle):movingPosition(e,index,state);
        float size=sample(e,e.sizeRange,index,cycle,6);
        // UpdateRot uses allPlayerTime-startDelay2, which survives particle rebirth.
        float rotationTime=state[0]+state[1]*state[2]-e.prewarm;
        float rotation=sample(e,e.angleRange,index,cycle,7)+sample(e,e.rotVelocityRange,index,cycle,8)*rotationTime;
        Matrix4f m;
        if(e.billboardType==0) {
            // Source CPU: inverse(emitter.rotation) * camera.rotation * Euler(axis*rot).
            // In view space the camera rotation cancels. Unity's camera has +Z forward;
            // the render view has -Z forward. E014 emitter ancestry has uniform scale.
            Vector3f center=emitter.transformPosition(position),scale=emitter.getScale(new Vector3f());
            m=new Matrix4f().translation(center).scale(scale.x,scale.y,-scale.z);
        } else m=new Matrix4f(emitter).translate(position);
        if(e.billboardType==1&&e.direction!=null)m.rotate(velocityRotation(e,emitter,position,velocity(e,index,state),rotation));
        else if(e.rotateAxis==2)m.rotateZ(rotation);else if(e.rotateAxis==1)m.rotateY(rotation);else m.rotateX(rotation);
        if(e.hasSizeEffector){
            float phase=state[0]/state[1];
            float sx=effector(e.sizeTimes,e.sizeXCurve,phase),sy=effector(e.sizeTimes,e.sizeYCurve,phase),sz=effector(e.sizeTimes,e.sizeZCurve,phase);
            if(e.uniformScale)m.scale(sx*size,sy*size,sz*size);
            else m.scale(sx*sample(e,e.sizeRanges[0],index,cycle,6),sy*sample(e,e.sizeRanges[1],index,cycle,9),sz*sample(e,e.sizeRanges[2],index,cycle,10));
        } else if(e.uniformScale)m.scale(size);
        else m.scale(sample(e,e.sizeRanges[0],index,cycle,6),sample(e,e.sizeRanges[1],index,cycle,9),sample(e,e.sizeRanges[2],index,cycle,10));
        return m;
    }

    /** Source Start() uses CPU billboards and clears _Billboard; the native shader branch is retained. */
    public static void render(Emitter e,PoseStack pose,VertexConsumer out,float ticks,int overlay) {
        if(e.direction!=null)validate(e);
        Matrix4f emitter=emitterToView(e,pose);
        for(int index=0;index<e.particleCount;index++) {
            float[] state=clock(e,index,ticks);if(state==null)continue;
            Matrix4f matrix=particleToView(e,emitter,index,state);
            Vector3f right=new Vector3f(1,0,0),up=new Vector3f(0,1,0),forward=new Vector3f(0,0,1);
            if(e.shaderBillboard) {
                Vector3f n=new Matrix4f(matrix).invert().transformPosition(new Vector3f()).negate();
                if(n.lengthSquared()<1e-12f)n.set(0,0,1);else n.normalize();
                Vector3f reference=Math.abs(n.y)>.999f?new Vector3f(0,0,1):new Vector3f(0,1,0);
                right=reference.cross(n).normalize();up=new Vector3f(n).cross(right).normalize();forward=n.negate();
            }
            float[] rgba=rgba(e,index,state);
            int count=Math.max(1,e.cols*e.rows),frame=Math.min(count-1,(int)(state[0]/state[1]*count));
            if(e.randomFrame)frame=Math.min(count-1,(int)(random(e,index,(int)state[2],11)*count));
            if(e.direction!=null){
                int real=e.realCount>0?e.realCount:count;
                frame=(int)(state[0]/state[1]*real)%real%count;
                if(e.randomFrame)frame=(int)(random(e,index,e.randomOnBorn?(int)state[2]:0,11)*real)%real%count;
            }
            Vector3f[] transformed=new Vector3f[e.mesh.vertices.length];
            for(int i=0;i<transformed.length;i++) {
                float[] p=e.mesh.vertices[i];
                Vector3f v=new Vector3f(right).mul(p[0]).fma(p[1],up).fma(p[2],forward);
                transformed[i]=matrix.transformPosition(v);
            }
            for(int[] face:e.mesh.faces) {
                Vector3f normal=new Vector3f(transformed[face[1]]).sub(transformed[face[0]]).cross(new Vector3f(transformed[face[2]]).sub(transformed[face[0]]));
                if(normal.lengthSquared()>1e-12f)normal.normalize();else normal.set(0,0,1);
                for(int j:new int[]{0,2,1,1}) {
                    int i=face[j];Vector3f v=transformed[i];float[] uv=e.mesh.uv[i];
                    float u=(frame%e.cols+uv[0])/e.cols,texV=(frame/e.cols+1-uv[1])/e.rows;
                    if(e.hasUVEffector){
                        float phase=state[0]/state[1];
                        u=uv[0]+effector(e.uvTimes,e.uCurve,phase);
                        texV=1-uv[1]+effector(e.uvTimes,e.vCurve,phase);
                    }
                    out.vertex(v.x,v.y,v.z).color(rgba[0],rgba[1],rgba[2],rgba[3]).uv(u,texV).overlayCoords(overlay).uv2(15728880).normal(normal.x,normal.y,normal.z).endVertex();
                }
            }
        }
    }
    public static void renderEffect(Emitter e,PoseStack pose,VertexConsumer out,float ticks,int overlay){
      if(!cn.judgfire.RenderOptions.enabled("render.eye_effects"))return;
      EffectScratch.begin();try{renderOptimized(e,pose,out,ticks,overlay);}finally{EffectScratch.end();}
    }
    /** Source Start() uses CPU billboards and clears _Billboard; the native shader branch is retained. */
    private static void renderOptimized(Emitter e,PoseStack pose,VertexConsumer out,float ticks,int overlay) {
        if(e.direction!=null)validate(e);
        Matrix4f emitter=emitterToView(e,pose);
        for(int index=0;index<e.particleCount;index++) {
            float[] state=clock(e,index,ticks);if(state==null)continue;
            Matrix4f matrix=particleToView(e,emitter,index,state);
            Vector3f right=new Vector3f(1,0,0),up=new Vector3f(0,1,0),forward=new Vector3f(0,0,1);
            if(e.shaderBillboard) {
                Vector3f n=new Matrix4f(matrix).invert().transformPosition(new Vector3f()).negate();
                if(n.lengthSquared()<1e-12f)n.set(0,0,1);else n.normalize();
                Vector3f reference=Math.abs(n.y)>.999f?new Vector3f(0,0,1):new Vector3f(0,1,0);
                right=reference.cross(n).normalize();up=new Vector3f(n).cross(right).normalize();forward=n.negate();
            }
            float[] rgba=rgba(e,index,state);
            int count=Math.max(1,e.cols*e.rows),frame=Math.min(count-1,(int)(state[0]/state[1]*count));
            if(e.randomFrame)frame=Math.min(count-1,(int)(random(e,index,(int)state[2],11)*count));
            if(e.direction!=null){
                int real=e.realCount>0?e.realCount:count;
                frame=(int)(state[0]/state[1]*real)%real%count;
                if(e.randomFrame)frame=(int)(random(e,index,e.randomOnBorn?(int)state[2]:0,11)*real)%real%count;
            }
            Vector3f[] transformed=EffectScratch.vertices(e.mesh.vertices.length);
            for(int i=0;i<e.mesh.vertices.length;i++) {
                float[] p=e.mesh.vertices[i];
                Vector3f v=transformed[i].set(right).mul(p[0]).fma(p[1],up).fma(p[2],forward);
                transformed[i]=matrix.transformPosition(v);
            }
            for(int[] face:e.mesh.faces) {
                for(int j:EffectScratch.ORDER) {
                    int i=face[j];Vector3f v=transformed[i];float[] uv=e.mesh.uv[i];
                    float u=(frame%e.cols+uv[0])/e.cols,texV=(frame/e.cols+1-uv[1])/e.rows;
                    if(e.hasUVEffector){
                        float phase=state[0]/state[1];
                        u=uv[0]+effector(e.uvTimes,e.uCurve,phase);
                        texV=1-uv[1]+effector(e.uvTimes,e.vCurve,phase);
                    }
                    out.vertex(v.x,v.y,v.z).color(rgba[0],rgba[1],rgba[2],rgba[3]).uv(u,texV).overlayCoords(overlay).uv2(15728880).normal(0,0,1).endVertex();
                }
            }
        }
    }
}
