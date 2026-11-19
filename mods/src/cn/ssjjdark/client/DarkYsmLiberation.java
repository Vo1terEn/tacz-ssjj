package cn.ssjjdark.client;

import cn.ssjjdark.DarkSkillPresentationNetwork.Cast;
import cn.ssjjares.client.AresWeightedHands;
import cn.ssjjares.client.DarkAresLiberation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Model-aware bridge. Live YSM controllers are read; the ultimate owns private pose storage. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkYsmLiberation {
    private record Appearance(int tier,ResourceLocation texture,ItemStack weapon,WeakReference<AbstractClientPlayer> owner) {}
    private static final LinkedHashMap<UUID,Appearance> APPEARANCES=new LinkedHashMap<>();
    private static DarkYsmMelee.Native api;
    private static boolean failed,warned;
    private DarkYsmLiberation(){}
    private static boolean resources(){
        if(DarkLiberationMotion.ready())return true;if(failed)return false;
        try{DarkLiberationMotion.read(Minecraft.getInstance().getResourceManager());return true;}
        catch(Exception|LinkageError failure){failed=true;warn(failure);return false;}
    }
    public static void reset(){APPEARANCES.clear();resetDefault();DarkLiberationMotion.reset();DarkAresLiberation.reset();failed=warned=false;}
    public static void forget(UUID cast){APPEARANCES.remove(cast);DarkAresLiberation.forget(cast);}
    public static void capture(Cast cast,AbstractClientPlayer owner){
        if(cast==null||owner==null||!cast.phantom()||APPEARANCES.containsKey(cast.id()))return;
        var appearance=appearance(owner);if(appearance!=null)APPEARANCES.put(cast.id(),appearance);
        while(APPEARANCES.size()>128){UUID first=APPEARANCES.keySet().iterator().next();forget(first);}
    }
    /** Do not cancel the real Default/Ares renderer when its own skeleton can animate. */
    public static boolean handles(AbstractClientPlayer player){
        try{
            if(player==null||!resources())return false;
            if(AresWeightedHands.modelTier(player)>0)return true;
            return appearance(player)!=null&&installDefault(player);
        }catch(RuntimeException|LinkageError failure){warn(failure);return false;}
    }
    private static Appearance appearance(AbstractClientPlayer player){
        try{
            int ares=AresWeightedHands.modelTier(player);if(ares>0)return new Appearance(ares,new ResourceLocation("ssjj_ares","textures/entity/ares_weighted_body"+(ares==2?2:1)+".png"),player.getMainHandItem().copy(),new WeakReference<>(player));
            if(!OptionalYsm.AVAILABLE)return null;Object state=player.getCapability(OptionalYsm.cap).orElse(null);
            if(state==null||!(Boolean)OptionalYsm.enabled.invoke(state)||OptionalYsm.model.invoke(state)==null)return null;
            if(!compatibleDefault(state))return null;
            ResourceLocation texture=new ResourceLocation("ssjj_dark","textures/ysm_liberation/default.png");
            try{Object selected=state.getClass().getMethod("b_").invoke(state);if(selected instanceof ResourceLocation actual)texture=actual;}catch(ReflectiveOperationException ignored){}
            return new Appearance(0,texture,player.getMainHandItem().copy(),new WeakReference<>(player));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){warn(failure);return null;}
    }
    /** Called while native tactical clips are built; original maps/layers remain under their owner. */
    static void wrap(Map<String,Object> clips,DarkYsmMelee.Native nativeApi)throws ReflectiveOperationException {
        // Holding/fire clips remain owned by TaCZ. A single native controller
        // contributes the full-body skill after all of those ordinary layers.
        api=nativeApi;
    }

    /** YSM posts this before its actual model/controller update, unlike RenderPlayer.Pre. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void renderLiving(RenderLivingEvent.Pre<?,?> event){
        if(!(event.getEntity() instanceof AbstractClientPlayer player)||!ysmRenderer(event.getRenderer().getClass()))return;
        try{if(AresWeightedHands.modelTier(player)>0||!resources()||appearance(player)==null)return;installDefault(player);}
        catch(RuntimeException|LinkageError failure){warn(failure);}
    }
    private static boolean ysmRenderer(Class<?> type){for(;type!=null;type=type.getSuperclass())if(type.getName().equals("com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo"))return true;return false;}
    private static Class<?> defaultController()throws ClassNotFoundException{return Class.forName("cn.ssjjdark.client.DarkYsmLiberationController",true,DarkYsmLiberation.class.getClassLoader());}
    private static boolean compatibleDefault(Object state)throws ReflectiveOperationException{return resources()&&(Boolean)defaultController().getMethod("compatible",Object.class).invoke(null,state);}
    private static boolean installDefault(AbstractClientPlayer player){
        try{if(!OptionalYsm.AVAILABLE)return false;Object state=player.getCapability(OptionalYsm.cap).orElse(null);return state!=null&&(Boolean)defaultController().getMethod("install",Object.class,AbstractClientPlayer.class).invoke(null,state,player);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){warn(failure);return false;}
    }
    private static void resetDefault(){try{defaultController().getMethod("reset").invoke(null);}catch(ReflectiveOperationException|LinkageError ignored){}}
    /** The optional subclass calls this using the real native context and original channel value. */
    public static Vector3f channel(String bone,Object context,Vector3f original){
        return channel(bone,context,original,false);
    }
    public static Vector3f positionChannel(String bone,Object context,Vector3f original){return channel(bone,context,original,true);}
    private static Vector3f channel(String bone,Object context,Vector3f original,boolean position){
        try{
            if(api==null||context==null)return original;Object state=api.contextValue.invoke(context);Object entity=state;
            if(state!=null&&api.entity.getDeclaringClass().isInstance(state))entity=api.entity.invoke(state);
            if(!(entity instanceof AbstractClientPlayer player))return original;var mc=Minecraft.getInstance();if(mc.level==null)return original;
            var motion=DarkLiberationPresentation.motion(player.getUUID(),mc.level.getGameTime(),mc.getFrameTime());
            return motion==null?original:position?DarkLiberationMotion.genericPosition(bone,motion.clock().sourceSeconds(),motion.clock().recovery(),original):DarkLiberationMotion.generic(bone,motion.clock().sourceSeconds(),motion.clock().recovery(),original);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){warn(failure);return original;}
    }
    public static boolean renderPhantom(Cast cast,DarkLiberationPresentation.Clock clock,PoseStack pose,MultiBufferSource buffers,int light){
        if(cast==null||!cast.phantom()||!clock.body()||!resources())return false;
        var appearance=APPEARANCES.get(cast.id());
        if(appearance==null){var mc=Minecraft.getInstance();var player=mc.level==null?null:mc.level.getPlayerByUUID(cast.owner());if(player instanceof AbstractClientPlayer owner)capture(cast,owner);appearance=APPEARANCES.get(cast.id());}
        if(appearance==null)return false;
        try{
            var owner=appearance.owner.get();if(appearance.tier>0)return DarkAresLiberation.draw(cast.id(),appearance.tier,clock,pose,buffers,light,owner,appearance.weapon);
            var joints=DarkLiberationMotion.genericPose(clock.sourceSeconds(),clock.recovery());if(joints==null)return false;
            pose.pushPose();try{
                pose.scale(.7f,.7f,.7f);DarkLiberationMotion.drawGeneric(joints,pose.last().pose(),buffers.getBuffer(RenderType.entityCutoutNoCull(appearance.texture)),light);
                if(owner!=null&&!appearance.weapon.isEmpty()){
                    pose.pushPose();try{pose.mulPoseMatrix(DarkLiberationMotion.genericHand(joints,new Matrix4f()));pose.translate(0,-1/16f,-.1f);pose.mulPose(Axis.XP.rotationDegrees(-90));DarkLiberationHeldItem.render(owner,appearance.weapon,"thirdperson_hand_ysm",pose,buffers,light);}finally{pose.popPose();}
                }
            }finally{pose.popPose();}return true;
        }catch(RuntimeException|LinkageError failure){warn(failure);return false;}
    }
    private static void warn(Throwable failure){if(!warned){warned=true;LogUtils.getLogger().warn("Cannot bind original Dark Crystal skill to optional YSM model",failure);}}
    private static final class OptionalYsm {
        static Capability<Object> cap;static Method enabled,model,id;static final boolean AVAILABLE=bind();
        @SuppressWarnings("unchecked") static boolean bind(){
            try{
                if(ModList.get()==null||!ModList.get().isLoaded("yes_steve_model"))return false;
                Class<?> provider=Class.forName("com.elfmcys.yesstevemodel.O0OooOo0oOOoOoOoOooO000o"),state=Class.forName("com.elfmcys.yesstevemodel.o0OOO0o0o0OOo000oO00o00O");
                cap=(Capability<Object>)provider.getField("Oo0Oo0o00O00Oo0OOoOOoooo").get(null);enabled=state.getMethod("o0ooooOo0o000OOo0oO00OoO");model=state.getMethod("O0ooooOO0oOo000O0Oo00OOO");id=state.getMethod("ooooO0o00oO0Oo0OOo0O0O0o");return true;
            }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){return false;}
        }
    }
}
