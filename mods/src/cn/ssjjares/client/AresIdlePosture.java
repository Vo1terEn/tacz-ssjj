package cn.ssjjares.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/** A small rigid upper-chest correction for the relaxed, unarmed standing idle. */
public final class AresIdlePosture {
    private static final Map<AresSourceBodyVisuals.State,float[]> BLENDS=new WeakHashMap<>();
    private static final float FORWARD_RADIANS=-15f*Mth.DEG_TO_RAD;
    private static final float TRANSITION_SECONDS=.18f;
    private AresIdlePosture(){}

    public static void apply(AresSourceBodyVisuals.SourceModel model){
        var state=model.current;
        boolean idle="idle".equals(state.action),leavingIdle="idle".equals(state.from)&&state.blend<1;
        if(!idle&&!leavingIdle){BLENDS.remove(state);return;}
        float target=!state.gun&&state.overlayWeight<=.0001f?1:0;
        float now=state.lastTime;
        float[] blend=BLENDS.get(state);
        if(blend==null){blend=new float[]{target,now};BLENDS.put(state,blend);}
        else if(Float.isFinite(now)&&Float.isFinite(blend[1])){
            float dt=Mth.clamp(now-blend[1],0,.1f),step=dt/TRANSITION_SECONDS;
            blend[0]+=Mth.clamp(target-blend[0],-step,step);blend[1]=now;
        }else{blend[0]=target;blend[1]=now;}
        float weight=blend[0];
        if(!idle)weight*=1-state.blend;
        else if(state.from!=null&&!"idle".equals(state.from))weight*=state.blend;
        if(weight<=.00001f)return;
        int chest=model.find("Bip01_Spine2"),offset=chest*7;
        parentRotation(model,model.mesh.parent[chest],model.parentRot.identity());
        model.delta.rotationZ(FORWARD_RADIANS*weight);
        model.qa.set(model.parentRot).conjugate().mul(model.delta).mul(model.parentRot);
        float[] local=model.scratch.local;
        model.qa.mul(model.qb.set(local[offset+3],local[offset+4],local[offset+5],local[offset+6])).normalize();
        local[offset+3]=model.qa.x;local[offset+4]=model.qa.y;local[offset+5]=model.qa.z;local[offset+6]=model.qa.w;
    }

    private static void parentRotation(AresSourceBodyVisuals.SourceModel model,int bone,Quaternionf out){
        if(bone<0)return;
        parentRotation(model,model.mesh.parent[bone],out);
        int k=bone*7;float[] local=model.scratch.local;
        out.mul(model.qb.set(local[k+3],local[k+4],local[k+5],local[k+6]));
    }
}
