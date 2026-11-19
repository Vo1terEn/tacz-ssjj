package cn.ssjjdark.client;

import com.elfmcys.yesstevemodel.*;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;

/** Optional YSM 2.6.5 final controller. Loaded only after the optional mod gate. */
public final class DarkYsmLiberationController implements OoOoOO0O00oOoO0o0ooOO0oO<o0000OoOooO0oo0o0oooo0Oo<?>> {
    private static final String ID="ssjj_dark:liberation_body";
    private static final Map<Object,DarkYsmLiberationController> INSTALLED=new WeakHashMap<>();
    private static final Map<Object,Boolean> COMPATIBLE=new WeakHashMap<>();
    private static DarkLiberationMotion.Data motionData;
    private static final Field NATIVE_STATES=field(OOOooo0o0OOOO0O0OOoOo0OO.class,"O00OOOooOoooOoo0o0o0oO0O");
    private static final Field FORCE_UPDATE=field(o0000OoOooO0oo0o0oooo0Oo.class,"O0OooOo0oOOoOoOoOooO000o");
    private final WeakReference<o0000OoOooO0oo0o0oooo0Oo<?>> model;
    private final WeakReference<AbstractClientPlayer> owner;
    private final List<Slot> slots=new ArrayList<>();
    private boolean wasActive;

    private DarkYsmLiberationController(o0000OoOooO0oo0o0oooo0Oo<?> state,AbstractClientPlayer player)throws ReflectiveOperationException,IOException {
        model=new WeakReference<>(state);owner=new WeakReference<>(player);
        @SuppressWarnings("unchecked") var states=(List<oOO0o0oOOOoOoo00oOO0Oo00>)NATIVE_STATES.get(state.OoOOOOOo0oo0000oooOOOoOO());
        bind(states);
    }
    private static Field field(Class<?> type,String name){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
    private static DarkLiberationMotion.Data data()throws IOException {if(motionData==null)motionData=DarkLiberationMotion.read(Minecraft.getInstance().getResourceManager());return motionData;}
    private static Map<String,DarkLiberationMotion.Bone> required()throws IOException {
        var d=data();var result=new LinkedHashMap<String,DarkLiberationMotion.Bone>();
        for(int i=0;i<d.genericMesh.length;i++)if(d.generic.containsKey(d.genericMesh[i].name)||d.genericMesh[i].name.endsWith("HandLocator"))
            for(int j=i;j>=0;j=d.genericMesh[j].parent)result.put(d.genericMesh[j].name,d.genericMesh[j]);
        return result;
    }
    /** This retarget requires the Default rig, not a particular texture or model id. */
    public static boolean compatible(Object value)throws IOException {
        if(!(value instanceof o0000OoOooO0oo0o0oooo0Oo<?> state))return false;
        var body=state.OOOoOO000000o0o0oOooo0o0();if(body==null)return false;
        Boolean known=COMPATIBLE.get(body);if(known!=null)return known;
        Map<String,Oo0o00oOOo0OO000000O0oO0> bones=new HashMap<>();
        for(var b:body.O00OOOooOoooOoo0o0o0oO0O().values())bones.put(b.oOOo0Ooo0oOoo0O0OOOOo0oo(),b);
        boolean valid=true;
        for(var entry:required().entrySet()){
            var actual=bones.get(entry.getKey());var expected=entry.getValue();
            if(actual==null||!near(actual.o0OOO0o0o0OOo000oO00o00O(),expected.pivot[0]*16)||!near(actual.O0OooOo0oOOoOoOoOooO000o(),expected.pivot[1]*16)||!near(actual.ooOO000o0O0OOOoO0Oo0o0Oo(),expected.pivot[2]*16)||actual.OO0ooO00OoO00o0OO0OOooO0().distance(new Vector3f(expected.staticEuler[0],expected.staticEuler[1],expected.staticEuler[2]))>.0001f){valid=false;break;}
        }
        if(valid)for(String side:List.of("Right","Left"))if(!hasPath(body,expectedPath(side+"HandLocator"))){valid=false;break;}
        COMPATIBLE.put(body,valid);return valid;
    }
    private static boolean near(float a,float b){return java.lang.Math.abs(a-b)<.001f;}
    private static List<String> expectedPath(String end)throws IOException {
        var d=data();var out=new ArrayList<String>();
        for(int i=0;i<d.genericMesh.length;i++)if(d.genericMesh[i].name.equals(end)){for(int j=i;j>=0;j=d.genericMesh[j].parent)out.add(d.genericMesh[j].name);Collections.reverse(out);return out;}
        return out;
    }
    private static boolean hasPath(Object body,List<String> path){
        for(Method method:body.getClass().getMethods())if(method.getParameterCount()==0&&List.class.isAssignableFrom(method.getReturnType()))try{if(containsPath(method.invoke(body),path))return true;}catch(ReflectiveOperationException ignored){}
        return false;
    }
    private static boolean containsPath(Object value,List<String> expected){
        if(!(value instanceof List<?> list)||list.isEmpty())return false;
        if(list.get(0) instanceof Oo0o00oOOo0OO000000O0oO0){var names=new ArrayList<String>();for(Object entry:list)names.add(((Oo0o00oOOo0OO000000O0oO0)entry).oOOo0Ooo0oOoo0O0OOOOo0oo());return names.equals(expected);}
        for(Object entry:list)if(containsPath(entry,expected))return true;return false;
    }
    public static boolean install(Object value,AbstractClientPlayer player)throws IOException,ReflectiveOperationException {
        if(!(value instanceof o0000OoOooO0oo0o0oooo0Oo<?> state)||player==null||!compatible(state))return false;
        joinPending(state);
        var controllers=state.ooOoooooO0OoOOOO0oO0OooO().o0OOooo0o0OO00OoOOOo0o0O();
        var controller=INSTALLED.get(state);
        if(controller==null||!controllers.contains(controller)){controller=new DarkYsmLiberationController(state,player);controllers.add(controller);INSTALLED.put(state,controller);}
        else if(controllers.get(controllers.size()-1)!=controller){controllers.remove(controller);controllers.add(controller);}
        var mc=Minecraft.getInstance();boolean active=mc.level!=null&&DarkLiberationPresentation.motion(player.getUUID(),mc.level.getGameTime(),mc.getFrameTime())!=null;
        // The regular held-clip eligibility timer may skip the complete processor.
        // Request its normal refresh path while this independent source action runs.
        if(active||controller.wasActive){FORCE_UPDATE.setBoolean(state,true);state.ooOOOoOOOOo0ooooo0O0O0OO();}
        return true;
    }
    private static void joinPending(o0000OoOooO0oo0o0oooo0Oo<?> state){
        // YSM may already be sampling this list on its pose worker before Pre.
        // Its native join consumes only the existing Future and schedules no work.
        if(state instanceof oOo00OoOO00O000ooO0Oo00o<?> asynchronous)asynchronous.oOOOooO00oo0oOooooOO0Oo0();
    }
    public static void reset(){
        for(var entry:INSTALLED.entrySet())if(entry.getKey() instanceof o0000OoOooO0oo0o0oooo0Oo<?> state){joinPending(state);state.ooOoooooO0OoOOOO0oO0OooO().o0OOooo0o0OO00OoOOOo0o0O().remove(entry.getValue());}
        INSTALLED.clear();COMPATIBLE.clear();motionData=null;
    }
    private void bind(List<oOO0o0oOOOoOoo00oOO0Oo00> states)throws IOException {
        slots.clear();var expected=required();for(var state:states)if(state!=null){var b=expected.get(state.oo0OoO00oOoo000O0000o0oo.oOOo0Ooo0oOoo0O0OOOOo0oo());if(b!=null)slots.add(new Slot(state,b));}
    }
    @Override public String Oo0Oo0o00O00Oo0OOoOOoooo(){return ID;}
    @Override public String o0OOooo0o0OO00OoOOOo0o0O(){return ID;}
    @Override public void Oo0Oo0o00O00Oo0OOoOOoooo(List<oOO0o0oOOOoOoo00oOO0Oo00> states,Object2ReferenceMap<String,List<O0o0OOO00000oO00O00oOOo0>> expressions){try{bind(states);}catch(IOException e){throw new IllegalStateException(e);}}
    @Override public void Oo0Oo0o00O00Oo0OOoOOoooo(OO00O0o0OooOOOo00OO00o00<o0000OoOooO0oo0o0oooo0Oo<?>> frame,O0Oooo00oOo00O00OoOOOooO<O0O00Oo0o0oO0oo0oO00OOoO<?>> context,boolean first){}
    @Override public void oOOOo0OOO0ooooo0O00OO0o0(){slots.clear();wasActive=false;}
    @Override public void Oo0Oo0o00O00Oo0OOoOOoooo(Consumer<O0OO0Oo0Oo0O0oOO0OOoO000> output){
        var player=owner.get();var mc=Minecraft.getInstance();var motion=player==null||mc.level==null?null:DarkLiberationPresentation.motion(player.getUUID(),mc.level.getGameTime(),mc.getFrameTime());
        if(motion==null){wasActive=false;return;}
        wasActive=true;float seconds=motion.clock().sourceSeconds(),recovery=motion.clock().recovery();
        for(var slot:slots){
            var s=slot.state;String name=slot.bone.name;
            var original=s.o0OOO0o0o0OOo000oO00o00O?new Vector3f(s.O00OOOooOoooOoo0o0o0oO0O):new Vector3f();
            var rotation=DarkLiberationMotion.genericBones().contains(name)?DarkLiberationMotion.generic(name,seconds,recovery,original):new Vector3f(original).mul(recovery);
            var position=s.O0OooOo0oOOoOoOoOooO000o?new Vector3f(s.o0OOooo0o0OO00OoOOOo0o0O):new Vector3f();
            position=DarkLiberationMotion.positionBones().contains(name)?DarkLiberationMotion.genericPosition(name,seconds,recovery,position):position.mul(recovery);
            var scale=s.ooOO000o0O0OOOoO0Oo0o0Oo?new Vector3f(s.oOOOo0OOO0ooooo0O00OO0o0):new Vector3f(1);
            scale.lerp(new Vector3f(1),1-recovery);
            slot.rotation=absolute(rotation);slot.position=absolute(position);slot.scale=absolute(scale);output.accept(slot);
        }
    }
    private static OO00000oOOOoO0o00o00OOo0 absolute(Vector3f value){var out=new OO00000oOOOoO0o00o00OOo0(value);out.Oo0Oo0o00O00Oo0OOoOOoooo=0;return out;}
    private static final class Slot implements O0OO0Oo0Oo0O0oOO0OOoO000 {
        final oOO0o0oOOOoOoo00oOO0Oo00 state;final DarkLiberationMotion.Bone bone;
        OO00000oOOOoO0o00o00OOo0 rotation,position,scale;
        Slot(oOO0o0oOOOoOoo00oOO0Oo00 state,DarkLiberationMotion.Bone bone){this.state=state;this.bone=bone;}
        @Override public oOO0o0oOOOoOoo00oOO0Oo00 Oo0Oo0o00O00Oo0OOoOOoooo(){return state;}
        @Override public Optional<OO00000oOOOoO0o00o00OOo0> Oo0Oo0o00O00Oo0OOoOOoooo(O0Oooo00oOo00O00OoOOOooO<O0O00Oo0o0oO0oo0oO00OOoO<?>> context){return Optional.of(rotation);}
        @Override public Optional<OO00000oOOOoO0o00o00OOo0> o0OOooo0o0OO00OoOOOo0o0O(O0Oooo00oOo00O00OoOOOooO<O0O00Oo0o0oO0oo0oO00OOoO<?>> context){return Optional.of(position);}
        @Override public Optional<OO00000oOOOoO0o00o00OOo0> O00OOOooOoooOoo0o0o0oO0O(O0Oooo00oOo00O00OoOOOooO<O0O00Oo0o0oO0oo0oO00OOoO<?>> context){return Optional.of(scale);}
    }
}
