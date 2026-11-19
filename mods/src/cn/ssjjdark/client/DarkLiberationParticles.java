package cn.ssjjdark.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Native Unity modules already bake their full rotation/size into the source lifetime LUT. */
public final class DarkLiberationParticles {
    private static final class Work {final Quaternionf rotation=new Quaternionf();final Vector3f right=new Vector3f(),up=new Vector3f(),normal=new Vector3f();}
    private static final ThreadLocal<Work> WORK=ThreadLocal.withInitial(Work::new);
    private DarkLiberationParticles(){}
    public static void release(DarkSkinParticles.Frame frame){for(int i=0;i<frame.count;i++)frame.get(i).emitter=null;frame.count=0;}
    public static void fill(DarkSkinParticles.Data data,float seconds,Vector3f right,Vector3f up,Vector3f camera,DarkSkinParticles.Frame frame){
        release(frame);
        DarkSkinParticles.fill(data,seconds,right,up,camera,frame);
        for(int i=0;i<frame.count;i++){var draw=frame.get(i);if(DarkLiberationMaterial.nativeLinear(draw.emitter))linear(draw,right,up);}
    }
    public static void linear(DarkSkinParticles.Draw draw,Vector3f right,Vector3f up){
        var work=WORK.get();var s=draw.state;var q=work.rotation.set(s[6],s[7],s[8],s[9]).normalize();
        if("mesh".equals(draw.emitter.mode))draw.linear.rotation(q).scale(s[3],s[4],s[5]);
        else{
            var r=work.right.set(right).normalize();var n=work.normal.set(r).cross(up).normalize();var u=work.up.set(n).cross(r).normalize();
            draw.linear.setColumn(0,r).setColumn(1,u).setColumn(2,n).rotate(q).scale(s[3],s[4],s[5]);
        }
    }
}
