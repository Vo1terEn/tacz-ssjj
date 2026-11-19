package cn.ssjjdark.client;

import com.elfmcys.yesstevemodel.OOOo0OooOOoOooooooo000O0;
import com.elfmcys.yesstevemodel.O0Oooo00oOo00O00OoOOOooO;
import com.elfmcys.yesstevemodel.OooOo0o00OO00O000oOOo0O0;
import org.joml.Vector3f;

/** Loaded reflectively only after optional YSM 2.6.5 is present. */
public final class DarkYsmLiberationChannel extends OooOo0o00OO00O000oOOo0O0 {
    private final String bone;
    private final OOOo0OooOOoOooooooo000O0 original;
    private final boolean position;
    public DarkYsmLiberationChannel(String bone,Object original){
        this(bone,original,false);
    }
    public DarkYsmLiberationChannel(String bone,Object original,boolean position){
        super(original==null?1:((OOOo0OooOOoOooooooo000O0)original).o0OOooo0o0OO00OoOOOo0o0O(),null,null);
        this.bone=bone;this.original=(OOOo0OooOOoOooooooo000O0)original;this.position=position;
    }
    @Override public float Oo0Oo0o00O00Oo0OOoOOoooo(){return original==null?0:original.Oo0Oo0o00O00Oo0OOoOOoooo();}
    @Override public float O00OOOooOoooOoo0o0o0oO0O(){return original==null?1:original.O00OOOooOoooOoo0o0o0oO0O();}
    /** YSM's transition controller casts the initial channel to its constant-keyframe class. */
    @Override public Vector3f Oo0Oo0o00O00Oo0OOoOOoooo(O0Oooo00oOo00O00OoOOOooO<?> context){return Oo0Oo0o00O00Oo0OOoOOoooo(context,0);}
    @Override public Vector3f Oo0Oo0o00O00Oo0OOoOOoooo(O0Oooo00oOo00O00OoOOOooO<?> context,float fraction){
        Vector3f base=original==null?new Vector3f():original.Oo0Oo0o00O00Oo0OOoOOoooo(context,fraction);
        return position?DarkYsmLiberation.positionChannel(bone,context,base):DarkYsmLiberation.channel(bone,context,base);
    }
}
