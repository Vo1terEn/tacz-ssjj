package cn.judgfire;

import com.google.gson.*;
import java.util.*;

/**
 * Shared, GL-independent SSJJ custom-particle candidate mathematics. Coordinates remain in
 * source particle-local units; the caller applies its own node/mount matrix exactly once.
 * Never interprets the unrelated Unity ParticleSystem shape/curve enums or compatibility rate.
 *
 * The supplied asset bundles contain data, not the original custom engine method bodies.
 * Curve interpolation follows CustomParticleSchedule's documented candidate policy. Shape
 * enum 0=box, 1=cone, 2=sphere, 3=point, cone +Y axis, _volum length distribution, direction
 * fallback, force/gravity as acceleration, orbit as radians/sec and rotation velocity as
 * degrees/sec are explicit inferences. They must be checked against reference video.
 */
public final class CustomParticleTransform {
  private CustomParticleTransform() {}
  public static final String INFERENCE_NOTE =
      "SSJJ candidate policy: shape0 box/1 cone+Y/2 sphere/3 point; shell and volume spawn; "
          + "shape direction fallback; forceDir*force and gravityDir*gravity acceleration; "
          + "orbit radians/sec, rotvelocity degrees/sec; curves linear/right-continuous. "
          + "Original custom engine method bodies were not supplied.";
  private static final Gson GSON=new Gson();
  private static final Map<JsonObject,Compiled> CACHE=new IdentityHashMap<>();
  private static final double TAU=2*Math.PI;

  /** UV is Unity UV space: scaleU,scaleV,offsetU,offsetV; PNG V inversion belongs to shader. */
  public record Sample(float[] localCenter,float[] localVelocity,float[] sizeXYZ,
                       float[] eulerRadians,float[] rgba,float[] uvScaleOffset) {}

  public static Compiled compile(JsonObject raw) { return new Compiled(raw,1); }
  /** Source-family override where supplied root-local bounds establish another cone axis. */
  public static Compiled compile(JsonObject raw,int coneAxis) { return new Compiled(raw,coneAxis); }
  public static void clearCache() { CACHE.clear(); }
  /** Convenience entry point. Retain compile(raw) per layer when sampling many particles. */
  public static Sample sample(JsonObject raw,CustomParticleSchedule.Particle particle) {
    Compiled c=CACHE.get(raw);
    if(c==null) { if(CACHE.size()>=1024)CACHE.clear();c=compile(raw);CACHE.put(raw,c); }
    return c.sample(particle);
  }

  public static final class Compiled {
    private final ShapeData shape;
    private final DirectionData direction;
    private final CustomParticleSchedule.Value[] sizes=new CustomParticleSchedule.Value[3];
    private final CustomParticleSchedule.Value alpha,speed,rotation,rotationVelocity;
    private final boolean curvedRotationVelocity;
    private final int particleNumber,colorType,colorValue;
    private final float[] color0,color1,colorTimesInitial;
    private final float[][] colorsInitial;
    private final float[] baseAcceleration,attractPoint;
    private final float accelerationScale,attractionScale;
    private final double orbitSpeed,orbitRadius,oscillatorSpeed,oscillatorDistance;
    private final boolean uniform,gradient,justAlpha,sizeEffector,alphaEffector,colorEffector,uvEffector;
    private final boolean force,gravity,orbit,accelerate,oscillator,attract,shapeDirection;
    private final int rotateAxis,columns,rows,randomFrame,coneAxis;
    private final float sheetLife;
    private final float[] gradientColorTimes,gradientAlphaTimes,keyTimes,alphaTimes,colorTimes,uvTimes;
    private final float[][] gradientColors;
    private final float[] gradientAlphas,alphaValues,colorR,colorG,colorB,uValues,vValues;
    private final float[][] sizeValues=new float[3][];
    private final List<String> diagnostics;
    private Compiled(JsonObject source,int sourceConeAxis) {
      if(source==null)throw new IllegalArgumentException("Missing customParticleRaw");
      if(sourceConeAxis<0||sourceConeAxis>2)throw new IllegalArgumentException("Invalid source cone axis");
      coneAxis=sourceConeAxis;
      JsonObject raw=source.deepCopy();shape=new ShapeData(obj(raw,"shape"));direction=new DirectionData(obj(raw,"direction"));
      alpha=CustomParticleSchedule.compileValue(one(raw,"alpha",1));speed=CustomParticleSchedule.compileValue(one(raw,"velocity",0));rotation=CustomParticleSchedule.compileValue(one(raw,"rot",0));
      var rotationVelocitySource=one(raw,"rotvelocity",0);curvedRotationVelocity=rotationVelocitySource.oneDType==1;
      rotationVelocity=CustomParticleSchedule.compileValue(rotationVelocitySource);uniform=flag(raw,"uniformScale_");
      for(int k=0;k<3;k++)sizes[k]=CustomParticleSchedule.compileValue(one(raw,uniform?"sizeX":"size"+"XYZ".charAt(k),1));
      particleNumber=Math.max(1,integer(raw,"ParticleNumber",1));
      JsonObject initialColor=obj(raw,"color");colorType=integer(initialColor,"colorType",0);colorValue=integer(initialColor,"_value",0xffffff);
      color0=color(integer(initialColor,"_color0",0xffffff));color1=color(integer(initialColor,"_color1",0xffffff));
      colorTimesInitial=array(initialColor,"_colorRatios",1f/255);float[] initialPacked=array(initialColor,"_colors",1);
      colorsInitial=new float[initialPacked.length][];for(int i=0;i<initialPacked.length;i++)colorsInitial[i]=color((int)initialPacked[i]);
      gradient=flag(raw,"hasGradientColorEffector");justAlpha=flag(raw,"JustAlpha");
      sizeEffector=flag(raw,"hasSizeEffector");alphaEffector=flag(raw,"hasAlphaEffector");
      colorEffector=flag(raw,"hasColorEffector");uvEffector=flag(raw,"hasUVEffector");
      force=flag(raw,"hasForceEffector");gravity=flag(raw,"hasGravityEffector");
      orbit=flag(raw,"hasOrbitEffector");accelerate=flag(raw,"hasAccelerateEffector");
      oscillator=flag(raw,"hasOscillatorEffector");attract=flag(raw,"hasAttractEffector");
      baseAcceleration=new float[3];
      if(force)addInPlace(baseAcceleration,mul(vector(raw,"_forceDir"),num(raw,"_force",0)));
      if(gravity)addInPlace(baseAcceleration,mul(vector(raw,"gravityDir"),num(raw,"gravity",0)));
      accelerationScale=num(raw,"_accelerate",0);attractionScale=num(raw,"_vel",0);attractPoint=vector(raw,"_attractPoint");
      orbitSpeed=num(raw,"_Orbitdegree",0);orbitRadius=num(raw,"_Orbitradius",0);
      oscillatorSpeed=num(raw,"_degree",0);oscillatorDistance=num(raw,"_distance",0);
      shapeDirection=flag(raw,"directionByShape");rotateAxis=integer(raw,"RotateAxis",2);
      boolean sheet=flag(raw,"hasSpriteSheetEffector");
      columns=sheet?Math.max(1,integer(raw,"columns",1)):1;
      rows=sheet?Math.max(1,integer(raw,"rows",1)):1;
      if((long)columns*rows>1048576)throw new IllegalArgumentException("Invalid custom sheet");
      randomFrame=integer(raw,"randomFrame",0);sheetLife=num(raw,"lifeDuration",0);
      gradientColorTimes=array(raw,"_colorRatios",1f/255);
      gradientAlphaTimes=array(raw,"_alphaRatios",1f/255);
      float[] packed=array(raw,"_colors",1);gradientColors=new float[packed.length][];
      for(int k=0;k<packed.length;k++)gradientColors[k]=color((int)packed[k]);
      gradientAlphas=array(raw,"_alphas",1);keyTimes=array(raw,"_keyFrameLifeTime",1);
      for(int k=0;k<3;k++)sizeValues[k]=array(raw,"_size"+"XYZ".charAt(k),1);
      alphaTimes=array(raw,"_alphakeyFrameLifeTime",1);alphaValues=array(raw,"_alphalst",1);
      colorTimes=array(raw,"_colorkeyFrameLifeTime",1);
      colorR=array(raw,"_colorr",1);colorG=array(raw,"_colorg",1);colorB=array(raw,"_colorb",1);
      uvTimes=array(raw,"_uvkeyFrameLifeTime",1);uValues=array(raw,"_u",1);vValues=array(raw,"_v",1);
      ArrayList<String> notes=new ArrayList<>();
      notes.add(INFERENCE_NOTE);
      if(attract)notes.add("hasAttractEffector: inferred point attraction acceleration _vel; no original method body");
      if(oscillator)notes.add("hasOscillatorEffector: inferred local-X sinusoidal displacement _distance*sin(_degree*age)");
      if(direction.type>2&&!shapeDirection)
        notes.add("direction threeDType>2: retained raw.value fallback; original enum implementation unavailable");
      if(flag(raw,"isWorldParticle"))notes.add("World simulation requires caller birth-transform history; local sample alone is insufficient");
      diagnostics=List.copyOf(notes);
    }
    public List<String> diagnostics() { return diagnostics; }
    public Sample sample(CustomParticleSchedule.Particle p) {
      if(p==null||!Double.isFinite(p.ageSeconds())||p.ageSeconds()<0)
        throw new IllegalArgumentException("Invalid custom particle");
      double t=p.normalizedAge(),age=p.ageSeconds();
      float[] spawn=spawn(shape,p,coneAxis),dir=shapeDirection?shapeDirection(shape,spawn,p,coneAxis):direction.sample(t,p);
      if(!shapeDirection&&length2(dir)<1e-12)dir=shapeDirection(shape,spawn,p,coneAxis);
      normalize(dir);
      double birthInput=p.index()/(double)particleNumber;
      float initialSpeed=(float)speed.at(birthInput,p.random(20));
      float[] velocity=mul(dir,initialSpeed),center=add(spawn,mul(velocity,(float)age));
      float[] acceleration=baseAcceleration.clone();
      if(accelerate)addInPlace(acceleration,mul(dir,accelerationScale));
      if(attract) {
        float[] towards=sub(attractPoint,spawn);normalize(towards);
        addInPlace(acceleration,mul(towards,attractionScale));
      }
      addInPlace(center,mul(acceleration,(float)(.5*age*age)));
      addInPlace(velocity,mul(acceleration,(float)age));
      if(orbit) {
        // Offset zero at birth; radians/sec is inferred from supplied _Orbitdegree ~ pi/4.
        double w=orbitSpeed,r=orbitRadius,angle=w*age;
        center[0]+=(float)(r*(Math.cos(angle)-1));center[1]+=(float)(r*Math.sin(angle));
        velocity[0]-=(float)(r*w*Math.sin(angle));velocity[1]+=(float)(r*w*Math.cos(angle));
      }
      if(oscillator) {
        double w=oscillatorSpeed,a=oscillatorDistance;
        center[0]+=(float)(a*Math.sin(w*age));velocity[0]+=(float)(a*w*Math.cos(w*age));
      }
      float[] size=new float[3];
      double sizeRandom=p.random(30);
      for(int k=0;k<3;k++) {
        size[k]=(float)sizes[k].at(birthInput,uniform?sizeRandom:p.random(30+k));
        if(sizeEffector)size[k]*=curve(keyTimes,sizeValues[k],t,1);
      }
      float[] rgb=initialColor(p.random(40));
      float a=(float)alpha.at(t,p.random(41));
      if(gradient) {
        if(!justAlpha)for(int k=0;k<3;k++)rgb[k]*=colorCurve(gradientColorTimes,gradientColors,t,k,1);
        a*=curve(gradientAlphaTimes,gradientAlphas,t,1);
      }
      if(alphaEffector)a*=curve(alphaTimes,alphaValues,t,1);
      if(colorEffector) {rgb[0]*=curve(colorTimes,colorR,t,1);rgb[1]*=curve(colorTimes,colorG,t,1);rgb[2]*=curve(colorTimes,colorB,t,1);}
      float[] euler=new float[3];
      double angle=rotation.at(birthInput,p.random(50));
      angle+=integrate(rotationVelocity,curvedRotationVelocity,p,p.random(51));
      euler[Math.max(0,Math.min(2,rotateAxis))]=(float)Math.toRadians(angle);
      int cells=columns*rows,frame;
      if(randomFrame!=0)frame=Math.min(cells-1,(int)(p.random(60)*cells));
      else {
        double progress=sheetLife>0?age/sheetLife:t;
        frame=Math.max(0,Math.min(cells-1,(int)(progress*cells)));
      }
      float du=uvEffector?curve(uvTimes,uValues,t,0):0,dv=uvEffector?curve(uvTimes,vValues,t,0):0;
      return new Sample(center,velocity,size,euler,new float[]{rgb[0],rgb[1],rgb[2],a},
          new float[]{1f/columns,1f/rows,(frame%columns)/(float)columns+du,(frame/columns)/(float)rows+dv});
    }
    private float[] initialColor(double random) {
      if(colorType==2){float[] result=color0.clone();for(int k=0;k<3;k++)result[k]+=(color1[k]-result[k])*random;return result;}
      if(colorType==1&&colorsInitial.length>0)return new float[]{colorCurve(colorTimesInitial,colorsInitial,random,0,1),colorCurve(colorTimesInitial,colorsInitial,random,1,1),colorCurve(colorTimesInitial,colorsInitial,random,2,1)};
      return color(colorValue);
    }
  }

  /** Apply source particle scale, then Z-X-Y Euler rotation, then local translation. */
  public static float[] transformLocal(float[] vertex,Sample s) {
    float[] p={vertex[0]*s.sizeXYZ[0],vertex[1]*s.sizeXYZ[1],vertex[2]*s.sizeXYZ[2]};
    p=rotateZXY(p,s.eulerRadians);return add(p,s.localCenter);
  }
  /** Rotation only, useful when the caller reconstructs a camera-facing source basis once. */
  public static float[] rotateZXY(float[] p,float[] e) {
    double cz=Math.cos(e[2]),sz=Math.sin(e[2]),cx=Math.cos(e[0]),sx=Math.sin(e[0]),cy=Math.cos(e[1]),sy=Math.sin(e[1]);
    double x=cz*p[0]-sz*p[1],y=sz*p[0]+cz*p[1],z=p[2];
    double y1=cx*y-sx*z,z1=sx*y+cx*z;
    return new float[]{(float)(cy*x+sy*z1),(float)y1,(float)(-sy*x+cy*z1)};
  }
  private static double integrate(CustomParticleSchedule.Value d,boolean curved,CustomParticleSchedule.Particle p,double random) {
    if(!curved)return d.at(0,random)*p.ageSeconds();
    // Integrate the documented piecewise-linear candidate in normalized life coordinates.
    double integral=0,end=p.normalizedAge();int steps=64;
    for(int i=0;i<steps&&i/(double)steps<end;i++) {
      double a=i/(double)steps,b=Math.min(end,(i+1)/(double)steps);
      integral+=(d.at(a,random)+d.at(b,random))*.5*(b-a);
    }
    return integral*p.lifetime();
  }
  private static final class ShapeData {
    final int type;final boolean shell,volume;
    final float[] rectFrom,rectTo;
    final double radius,length,tanAngle,sinAngle,cosAngle;
    ShapeData(JsonObject s){
      type=integer(s,"shapeType",3);shell=flag(s,"_shell");volume=flag(s,"_volum");
      rectFrom=vector(s,"_rectFrom");rectTo=vector(s,"_rectTo");
      radius=num(s,"_radius",0);length=num(s,"_length",0);double angle=Math.toRadians(num(s,"_angle",0));
      tanAngle=Math.tan(angle);sinAngle=Math.sin(angle);cosAngle=Math.cos(angle);
    }
  }
  private static final class DirectionData {
    final int type;final float[] value,min,max;final float[][] times=new float[3][],values=new float[3][];
    DirectionData(JsonObject d){
      type=integer(d,"threeDType",0);value=vector(d,"value");min=vector(d,"_min");max=vector(d,"_max");
      for(int k=0;k<3;k++){
        String key="anchors"+"XYZ".charAt(k);JsonArray a=d.has(key)&&d.get(key).isJsonArray()?d.getAsJsonArray(key):new JsonArray();
        times[k]=new float[a.size()];values[k]=new float[a.size()];
        for(int i=0;i<a.size();i++){JsonObject x=a.get(i).getAsJsonObject();times[k][i]=num(x,"_time",0);values[k][i]=num(x,"_value",0);}
      }
    }
    float[] sample(double t,CustomParticleSchedule.Particle p){
      if(type==2){float[] v=new float[3];for(int k=0;k<3;k++)v[k]=(float)(min[k]+(max[k]-min[k])*p.random(11+k));return v;}
      if(type==1){float[] v=new float[3];for(int k=0;k<3;k++)v[k]=curve(times[k],values[k],t,0);return v;}
      return value.clone();
    }
  }
  private static float[] spawn(ShapeData s,CustomParticleSchedule.Particle p,int coneAxis) {
    int type=s.type;
    if(type==0) {
      float[] a=s.rectFrom,b=s.rectTo,v=new float[3];
      for(int k=0;k<3;k++)v[k]=(float)(a[k]+(b[k]-a[k])*p.random(1+k));return v;
    }
    if(type==1) {
      double radius=s.radius,len=s.volume?s.length*p.random(4):0;
      radius+=len*s.tanAngle;
      if(!s.shell)radius*=Math.sqrt(p.random(5));
      // SSJJ custom cones use +Y, rather than Unity ParticleSystem's +Z.
      // Serialized bounds are relative to the prefab ROOT. Removing that
      // root before comparing node axes consistently gives +Y for the body
      // and muzzle cones; including the root's Ry180 would reverse the sign.
      double theta=TAU*p.random(6);return coneAxes((float)(radius*Math.cos(theta)),(float)len,(float)(radius*Math.sin(theta)),coneAxis);
    }
    if(type==2) {
      float[] v=sphere(p,7);double r=s.radius;
      if(!s.shell)r*=Math.cbrt(p.random(10));return mul(v,(float)r);
    }
    return new float[3];
  }
  private static float[] shapeDirection(ShapeData s,float[] spawn,CustomParticleSchedule.Particle p,int coneAxis) {
    int type=s.type;
    if(type==2) {float[] v=spawn.clone();if(length2(v)<1e-12)v=sphere(p,7);return v;}
    if(type==1) {
      double theta=TAU*p.random(6);
      return coneAxes((float)(Math.cos(theta)*s.sinAngle),(float)s.cosAngle,(float)(Math.sin(theta)*s.sinAngle),coneAxis);
    }
    return new float[]{0,0,1};
  }
  private static float[] coneAxes(float x,float y,float z,int axis) {
    return axis==0?new float[]{y,x,z}:axis==2?new float[]{x,z,y}:new float[]{x,y,z};
  }
  private static float[] sphere(CustomParticleSchedule.Particle p,int channel) {
    double z=2*p.random(channel)-1,a=TAU*p.random(channel+1),r=Math.sqrt(Math.max(0,1-z*z));
    return new float[]{(float)(r*Math.cos(a)),(float)(r*Math.sin(a)),(float)z};
  }
  private static float colorCurve(float[] times,float[][] colors,double t,int channel,float fallback) {
    if(times.length==0||colors.length==0)return fallback;
    if(!Double.isFinite(t))throw new IllegalArgumentException("Non-finite curve input");
    int n=Math.min(times.length,colors.length);if(t<times[0])return colors[0][channel];
    int lo=0,hi=n;while(lo<hi){int m=(lo+hi)>>>1;if(times[m]<=t)lo=m+1;else hi=m;}
    int a=lo-1;if(a>=n-1)return colors[n-1][channel];
    double f=(t-times[a])/(times[a+1]-times[a]);
    return (float)(colors[a][channel]+(colors[a+1][channel]-colors[a][channel])*f);
  }
  private static float curve(float[] times,float[] values,double t,float fallback) {
    if(times.length==0||values.length==0)return fallback;
    if(!Double.isFinite(t))throw new IllegalArgumentException("Non-finite curve input");
    int n=Math.min(times.length,values.length);if(t<times[0])return values[0];
    int lo=0,hi=n;while(lo<hi){int m=(lo+hi)>>>1;if(times[m]<=t)lo=m+1;else hi=m;}
    int a=lo-1;if(a>=n-1)return values[n-1];
    double f=(t-times[a])/(times[a+1]-times[a]);
    return (float)(values[a]+(values[a+1]-values[a])*f);
  }
  private static CustomParticleSchedule.OneD one(JsonObject o,String key,float fallback) {
    if(o.has(key)&&o.get(key).isJsonObject())return GSON.fromJson(o.get(key),CustomParticleSchedule.OneD.class);
    CustomParticleSchedule.OneD d=new CustomParticleSchedule.OneD();d._value=fallback;return d;
  }
  private static float[] array(JsonObject o,String key,float scale) {
    if(!o.has(key)||!o.get(key).isJsonArray())return new float[0];JsonArray a=o.getAsJsonArray(key);float[] v=new float[a.size()];
    for(int k=0;k<v.length;k++){v[k]=a.get(k).getAsFloat()*scale;if(!Float.isFinite(v[k]))throw new IllegalArgumentException("Nonfinite "+key);}return v;
  }
  private static JsonObject obj(JsonObject o,String key) {return o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject();}
  private static float num(JsonObject o,String key,float fallback) {return o.has(key)&&o.get(key).isJsonPrimitive()?o.get(key).getAsFloat():fallback;}
  private static int integer(JsonObject o,String key,int fallback){return (int)num(o,key,fallback);}
  private static boolean flag(JsonObject o,String key) {JsonElement e=o.get(key);return e!=null&&e.isJsonPrimitive()&&(e.getAsJsonPrimitive().isBoolean()?e.getAsBoolean():e.getAsFloat()!=0);}
  private static float[] vector(JsonObject o,String key) {JsonObject v=obj(o,key);return new float[]{num(v,"x",0),num(v,"y",0),num(v,"z",0)};}
  private static float[] color(int n){return new float[]{((n>>>16)&255)/255f,((n>>>8)&255)/255f,(n&255)/255f};}
  private static float length2(float[] v){return v[0]*v[0]+v[1]*v[1]+v[2]*v[2];}
  private static void normalize(float[] v){double n=Math.sqrt(length2(v));if(n>1e-9)for(int i=0;i<3;i++)v[i]/=n;}
  private static float[] mul(float[] v,float m){return new float[]{v[0]*m,v[1]*m,v[2]*m};}
  private static float[] add(float[] a,float[] b){return new float[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
  private static float[] sub(float[] a,float[] b){return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
  private static void addInPlace(float[] a,float[] b){for(int k=0;k<3;k++)a[k]+=b[k];}
}
