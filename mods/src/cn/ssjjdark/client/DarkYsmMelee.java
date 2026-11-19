package cn.ssjjdark.client;

import cn.ssjjdark.DarkSkins;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Adds weapon-specific body clips through the optional YSM 2.6.5 animation API. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkYsmMelee {
    private static final String YSM_RENDERER="com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo";
    private static final String[] PHASES={"hold","aim","run","hold:fire","aim:fire","melee"};
    private static final Map<Object,Installation> INSTALLED=new WeakHashMap<>();
    private static final JsonObject[] DEFINITIONS=new JsonObject[2];
    private static final boolean[] FAILED=new boolean[2];
    private static boolean warned;
    private DarkYsmMelee() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->reset());
        }
    }

    private record Installation(Map<String,Object> animations,Map<String,Object> originals,Map<String,Object> added) {
        void restore() {
            added.forEach((name,clip)->{
                if(animations.get(name)!=clip)return;
                if(originals.containsKey(name))animations.put(name,originals.get(name));else animations.remove(name);
            });
        }
    }

    private static void reset() {
        INSTALLED.values().forEach(Installation::restore);
        INSTALLED.clear();
        for(int i=0;i<2;i++){DEFINITIONS[i]=null;FAILED[i]=false;}
        warned=false;
    }

    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void beforeLiving(RenderLivingEvent.Pre<?,?> event) {
        if(event.isCanceled() || !ysmRenderer(event.getRenderer())
            || !(event.getEntity() instanceof AbstractClientPlayer player))return;
        installFor(player);
    }

    static boolean installFor(AbstractClientPlayer player) {
        var held=player.getMainHandItem();var gun=IGun.getIGunOrNull(held);
        if(gun==null || !DarkSkins.family(gun.getGunId(held)) || !Ysm.AVAILABLE)return false;
        try {
            Object animatable=player.getCapability(Ysm.capability).orElse(null);
            if(animatable==null || !(Boolean)Ysm.enabled.invoke(animatable))return false;
            Object handle=Ysm.model.invoke(animatable);if(handle==null)return false;
            Object data=Ysm.loaded.invoke(handle);if(data==null)return false;if(INSTALLED.containsKey(data))return true;
            String id=String.valueOf(Ysm.modelId.invoke(animatable));
            boolean ares=isAresId(id);
            JsonObject definition=load(ares?1:0);if(definition==null)return false;
            install(data,definition,Ysm.nativeApi);
            return true;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError error){warn(error);return false;}
    }

    static boolean isAres(AbstractClientPlayer player) {
        if(player==null || !Ysm.AVAILABLE)return false;
        try {
            Object animatable=player.getCapability(Ysm.capability).orElse(null);
            return animatable!=null && (Boolean)Ysm.enabled.invoke(animatable)
                && Ysm.model.invoke(animatable)!=null && isAresId(String.valueOf(Ysm.modelId.invoke(animatable)));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError error){warn(error);return false;}
    }

    static boolean isReady(AbstractClientPlayer player) {
        if(player==null || !Ysm.AVAILABLE)return false;
        try {
            Object animatable=player.getCapability(Ysm.capability).orElse(null);
            if(animatable==null || !(Boolean)Ysm.enabled.invoke(animatable))return false;
            Object handle=Ysm.model.invoke(animatable);
            return handle!=null && INSTALLED.containsKey(Ysm.loaded.invoke(handle));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError error){warn(error);return false;}
    }

    private static boolean isAresId(String id) {
        return "ssjj_ares_base".equals(id)||"ssjj_ares_absolute".equals(id);
    }

    private static boolean ysmRenderer(Object renderer) {
        if(renderer==null)return false;
        for(Class<?> type=renderer.getClass();type!=null;type=type.getSuperclass())
            if(YSM_RENDERER.equals(type.getName()))return true;
        return false;
    }

    private static JsonObject load(int index) {
        if(DEFINITIONS[index]!=null || FAILED[index])return DEFINITIONS[index];
        var id=new ResourceLocation("ssjj_dark","ysm/"+(index==1?"ares":"generic")+"_melee.json");
        try(var input=Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().open();
            var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            JsonObject document=JsonParser.parseReader(reader).getAsJsonObject();
            validate(document);return DEFINITIONS[index]=document;
        } catch(Exception error){FAILED[index]=true;warn(error);return null;}
    }

    private static void validate(JsonObject document) {
        JsonObject animations=document.getAsJsonObject("animations");
        for(String pose:List.of("melee_hold","melee_hold_crouch")) {
            if(animations==null || !animations.has(pose))throw new IllegalArgumentException("Missing Dark Crystal YSM pose "+pose);
            for(var bone:animations.getAsJsonObject(pose).getAsJsonObject("bones").entrySet())
                for(var channel:bone.getValue().getAsJsonObject().entrySet()) {
                    if(!List.of("rotation","position","scale").contains(channel.getKey()))
                        throw new IllegalArgumentException("Unsupported YSM pose channel "+channel.getKey());
                    vector(channel.getValue(),channel.getKey());
                }
        }
    }

    private static float[] vector(JsonElement value,String channel) {
        if(value==null)return new float[]{"scale".equals(channel)?1:0,"scale".equals(channel)?1:0,"scale".equals(channel)?1:0};
        var array=value.getAsJsonArray();if(array.size()!=3)throw new IllegalArgumentException("Expected three YSM pose components");
        float[] result=new float[3];
        for(int i=0;i<3;i++) {
            result[i]=array.get(i).getAsFloat();
            if(!Float.isFinite(result[i]))throw new IllegalArgumentException("Non-finite YSM pose component");
            if("rotation".equals(channel))result[i]=(float)Math.toRadians(result[i])*(i==2?1:-1);
            else if("position".equals(channel) && i==0)result[i]=-result[i];
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void install(Object data,JsonObject document,Native api)throws ReflectiveOperationException {
        validate(document);
        Map<String,Object> animations=(Map<String,Object>)api.bodyAnimations.invoke(data);
        Object registry=api.registry.invoke(data);
        Map<String,Object> additions=build(document,api,animations),originals=new LinkedHashMap<>();
        if(document.getAsJsonObject("animations").getAsJsonObject("melee_hold").getAsJsonObject("bones").has("RightArm"))DarkYsmLiberation.wrap(additions,api);
        for(String name:additions.keySet())if(animations.containsKey(name))originals.put(name,animations.get(name));
        // Register selectors first; the body map is changed only once every clip was constructed.
        for(String name:additions.keySet())api.register.invoke(registry,name);
        animations.putAll(additions);
        INSTALLED.put(data,new Installation(animations,originals,additions));
    }

    private static Map<String,Object> build(JsonObject document,Native api,Map<String,Object> originals)throws ReflectiveOperationException {
        JsonObject source=document.getAsJsonObject("animations");
        JsonObject stand=source.getAsJsonObject("melee_hold").getAsJsonObject("bones");
        JsonObject crouch=source.getAsJsonObject("melee_hold_crouch").getAsJsonObject("bones");
        var boneNames=new LinkedHashSet<String>();boneNames.addAll(stand.keySet());boneNames.addAll(crouch.keySet());
        List<Object> channels=new ArrayList<>();
        for(String boneName:boneNames) {
            JsonObject a=stand.has(boneName)?stand.getAsJsonObject(boneName):new JsonObject();
            JsonObject b=crouch.has(boneName)?crouch.getAsJsonObject(boneName):new JsonObject();
            List<?> position=channel(a,b,"position",api),rotation=channel(a,b,"rotation",api),scale=channel(a,b,"scale",api);
            channels.add(api.bone.newInstance(boneName,position,rotation,scale));
        }
        Map<String,Object> result=new LinkedHashMap<>();
        for(String base:List.of("darkdiamond","darkdiamond1","darkdiamond2"))
            for(String skin:List.of("","_pure","_mix","_nightguardian"))
                for(String phase:PHASES) {
                    String name="tac:"+phase+"$ssjj_dark:"+base+skin;
                    Object sourceClip=originals.get(name);
                    if(sourceClip==null)sourceClip=originals.get("tac:"+phase+":rifle");
                    if(sourceClip==null)sourceClip=originals.get("tac:"+phase);
                    result.put(name,api.merge(name,sourceClip,channels));
                }
        return result;
    }

    private static List<?> channel(JsonObject a,JsonObject b,String name,Native api)throws ReflectiveOperationException {
        if(!a.has(name)&&!b.has(name))return List.of();
        float[] stand=vector(a.get(name),name),crouch=vector(b.get(name),name);
        Object vector=api.vector.newInstance(api.expression(stand[0],crouch[0]),api.expression(stand[1],crouch[1]),api.expression(stand[2],crouch[2]));
        return List.of(api.constant.newInstance(0f,vector,vector));
    }

    private static void warn(Throwable error) {
        if(!warned){warned=true;LogUtils.getLogger().warn("Cannot install Dark Crystal YSM melee body poses; retaining model animations",error);}
    }

    /** All YSM types remain behind reflection so the mod also loads without YSM. */
    private static final class Ysm {
        static final boolean AVAILABLE=bind();
        static Capability<Object> capability;
        static Method enabled,model,loaded,modelId;
        static Native nativeApi;
        @SuppressWarnings("unchecked") private static boolean bind() {
            if(ModList.get()==null||!ModList.get().isLoaded("yes_steve_model"))return false;
            try {
                Class<?> provider=Native.type("O0OooOo0oOOoOoOoOooO000o"),animatable=Native.type("o0OOO0o0o0OOo000oO00o00O");
                capability=(Capability<Object>)provider.getField("Oo0Oo0o00O00Oo0OOoOOoooo").get(null);
                enabled=animatable.getMethod("o0ooooOo0o000OOo0oO00OoO");
                model=animatable.getMethod("O0ooooOO0oOo000O0Oo00OOO");
                loaded=model.getReturnType().getMethod("Oo0Oo0o00O00Oo0OOoOOoooo");
                modelId=animatable.getMethod("ooooO0o00oO0Oo0OOo0O0O0o");
                nativeApi=new Native();return true;
            } catch(ReflectiveOperationException|RuntimeException|LinkageError error){warn(error);return false;}
        }
    }

    static final class Native {
        final Class<?> expressionType;
        final Constructor<?> clip,bone,vector,constant;
        final Object loop,emptyEffects,emptyParticles;
        final Method contextValue,entity,bodyAnimations,registry,register;
        final Field clipLength,clipLoop,clipExpr1,clipExpr2,clipExpr3,clipOverride,clipBones,clipSounds,clipParticles,clipTimeline;
        final Field boneName,bonePosition,boneRotation,boneScale;
        static Class<?> type(String name)throws ClassNotFoundException {
            return Class.forName("com.elfmcys.yesstevemodel."+name,false,DarkYsmMelee.class.getClassLoader());
        }
        Native()throws ReflectiveOperationException {
            expressionType=type("O0o0OOO00000oO00O00oOOo0");
            Class<?> vectorType=type("o0oO0ooOo0o0OOO0oo0Ooooo"),boneType=type("o0OOOOooooOO00oOoO0o00OO");
            Class<?> effectType=type("oOO0OoOooOOOooOo0O00Oooo"),particleType=type("oOo0o0OoO0OO0oO0ooOOoooO"),loopType=type("OooOO0OOOoO0oOO0OOOO0Ooo");
            vector=vectorType.getConstructor(expressionType,expressionType,expressionType);
            constant=type("OooOo0o00OO00O000oOOo0O0").getConstructor(float.class,vectorType,vectorType);
            bone=boneType.getConstructor(String.class,List.class,List.class,List.class);
            emptyEffects=Array.newInstance(effectType,0);emptyParticles=Array.newInstance(particleType,0);
            Class<?> clipType=type("Oo0OOo00Oo00oO00Ooo0O00O");
            clip=clipType.getConstructor(String.class,double.class,loopType,expressionType,expressionType,expressionType,
                Boolean.class,Array.newInstance(boneType,0).getClass(),emptyEffects.getClass(),emptyParticles.getClass(),emptyEffects.getClass());
            clipLength=clipType.getField("o0OOooo0o0OO00OoOOOo0o0O");clipLoop=clipType.getField("O00OOOooOoooOoo0o0o0oO0O");
            clipExpr1=clipType.getField("oOOOo0OOO0ooooo0O00OO0o0");clipExpr2=clipType.getField("OOOOo0O0oO0OOo0O0O0Oo0O0");
            clipExpr3=clipType.getField("Ooooo0oooO0oooOOOoO0000O");clipOverride=clipType.getField("oo0OoO00oOoo000O0000o0oo");
            clipBones=clipType.getField("oooooooOOoOOoO00OooOo00O");clipSounds=clipType.getField("Oo00o0OooOOo0ooOoo0oO0o0");
            clipParticles=clipType.getField("o0OOO0o0o0OOo000oO00o00O");clipTimeline=clipType.getField("O0OooOo0oOOoOoOoOooO000o");
            boneName=boneType.getField("Oo0Oo0o00O00Oo0OOoOOoooo");bonePosition=boneType.getField("O00OOOooOoooOoo0o0o0oO0O");
            boneRotation=boneType.getField("oOOOo0OOO0ooooo0O00OO0o0");boneScale=boneType.getField("OOOOo0O0oO0OOo0O0O0Oo0O0");
            Object candidate=null;for(Object value:loopType.getEnumConstants())if("LOOP".equals(((Enum<?>)value).name()))candidate=value;
            if(candidate==null)throw new NoSuchFieldException("YSM LOOP enum");loop=candidate;
            contextValue=type("oO0OoO0O0OoO0oo0oo0OOooo").getMethod("Oo0Oo0o00O00Oo0OOoOOoooo");
            entity=type("O0O00Oo0o0oO0oo0oO00OOoO").getMethod("Oo0Oo0o00O00Oo0OOoOOoooo");
            Class<?> data=type("Oo0o0o0o0OOo0000ooo00o00");
            bodyAnimations=data.getMethod("O00OOOooOoooOoo0o0o0oO0O");registry=data.getMethod("OOOOo0O0oO0OOo0O0O0Oo0O0");
            register=registry.getReturnType().getMethod("Oo0Oo0o00O00Oo0OOoOOoooo",String.class);
        }
        Object array(List<?> values,Class<?> component) {
            Object result=Array.newInstance(component,values.size());for(int i=0;i<values.size();i++)Array.set(result,i,values.get(i));return result;
        }
        List<?> replaceChannel(Object replacement,Object original,Field field)throws IllegalAccessException {
            List<?> values=(List<?>)field.get(replacement);return values.isEmpty()?(List<?>)field.get(original):values;
        }
        Object merge(String name,Object source,List<Object> replacements)throws ReflectiveOperationException {
            Map<String,Object> pending=new LinkedHashMap<>();for(Object bone:replacements)pending.put((String)boneName.get(bone),bone);
            List<Object> merged=new ArrayList<>();
            if(source!=null)for(Object original:(List<?>)clipBones.get(source)) {
                String key=(String)boneName.get(original);Object replacement=pending.remove(key);
                if(replacement==null)merged.add(original);
                else merged.add(bone.newInstance(key,replaceChannel(replacement,original,bonePosition),
                    replaceChannel(replacement,original,boneRotation),replaceChannel(replacement,original,boneScale)));
            }
            merged.addAll(pending.values());Object bones=array(merged,bone.getDeclaringClass());
            if(source==null)return clip.newInstance(name,1d,loop,null,null,null,Boolean.FALSE,bones,emptyEffects,emptyParticles,emptyEffects);
            return clip.newInstance(name,((Number)clipLength.get(source)).doubleValue(),clipLoop.get(source),clipExpr1.get(source),clipExpr2.get(source),
                clipExpr3.get(source),clipOverride.get(source),bones,array((List<?>)clipSounds.get(source),emptyEffects.getClass().getComponentType()),
                array((List<?>)clipParticles.get(source),emptyParticles.getClass().getComponentType()),array((List<?>)clipTimeline.get(source),emptyEffects.getClass().getComponentType()));
        }
        Object expression(float stand,float crouch) {
            return Proxy.newProxyInstance(expressionType.getClassLoader(),new Class<?>[]{expressionType},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {
                    case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->"Dark Crystal body pose";
                };
                float value=stand;
                if(stand!=crouch && args!=null && args.length>0 && args[0]!=null) {
                    Object state=contextValue.invoke(args[0]);
                    if(state instanceof LivingEntity living)value=living.isCrouching()?crouch:stand;
                    else if(state!=null && entity.getDeclaringClass().isInstance(state)) {
                        Object owner=entity.invoke(state);if(owner instanceof LivingEntity living)value=living.isCrouching()?crouch:stand;
                    }
                }
                Class<?> returns=method.getReturnType();
                if(returns==boolean.class)return value!=0;if(returns==int.class)return (int)value;
                if(returns==double.class)return (double)value;return value;
            });
        }
    }
}
