package cn.ssjjares.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Skin the original indexed mesh from the very pose used by native YSM layers. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class AresWeightedBodyVisuals {
    private static final ResourceLocation BODY=new ResourceLocation("ssjj_ares","models/ares_weighted_body.bin");
    private static final ResourceLocation MOTION=new ResourceLocation("ssjj_ares","models/ares_weighted_motion.bin");
    private static final ResourceLocation[] TEXTURES={new ResourceLocation("ssjj_ares","textures/entity/ares_weighted_body1.png"),new ResourceLocation("ssjj_ares","textures/entity/ares_weighted_body2.png")};
    private static final Map<AbstractClientPlayer,Capture> CAPTURES=new WeakHashMap<>();
    private static final Map<Object,Rig> RIGS=new WeakHashMap<>();
    private static final Map<Object,Object> GUI_BONES=new WeakHashMap<>();
    private static AresWeightedBodyMesh mesh;
    private static AresWeightedBodyMesh.Scratch scratch;
    private static boolean failed,warned;
    private AresWeightedBodyVisuals(){}

    private record Capture(PoseStack source,Matrix4f matrix,float partialTick,int tick){}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event){
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->{mesh=null;scratch=null;failed=warned=false;CAPTURES.clear();RIGS.clear();GUI_BONES.clear();});
        }
    }

    private static AresWeightedBodyMesh load(){
        if(mesh!=null||failed)return mesh;
        var manager=Minecraft.getInstance().getResourceManager();
        try(var model=manager.getResource(BODY).orElseThrow().open();var motion=manager.getResource(MOTION).orElseThrow().open()){
            mesh=AresWeightedBodyMesh.read(model,motion);scratch=mesh.scratch();return mesh;
        }catch(Exception error){failed=true;LogUtils.getLogger().error("Unable to load weighted Ares body",error);return null;}
    }

    /** This event is nested inside YSM's NORMAL RenderPlayerEvent.Pre handler. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void nativeRendered(RenderLivingEvent.Post<?,?> event){
        if(!Ysm.AVAILABLE||!Ysm.rendererClass.isInstance(event.getRenderer())||!(event.getEntity() instanceof AbstractClientPlayer player))return;
        if(AresWeightedHands.modelTier(player)==0)return;
        try{
            Object data=player.getCapability(Ysm.cap).orElse(null);
            if(data==null)return;
            Object renderer=event.getRenderer();
            Matrix4f before=(Matrix4f)Ysm.before.get(renderer),outer=(Matrix4f)Ysm.outer.get(renderer);
            if(before==null||outer==null||!before.equals(event.getPoseStack().last().pose()))return;
            // The renderer snapshots outer just before its default pre-render
            // hook applies the user model width/height scale (c_ / e_).
            float width=((Number)Ysm.width.invoke(data)).floatValue(),height=((Number)Ysm.height.invoke(data)).floatValue();
            Matrix4f exact=new Matrix4f(outer).scale(width,height,width);
            if(!exact.isFinite()||Math.abs(exact.determinant())<1e-12f)return;
            CAPTURES.put(player,new Capture(event.getPoseStack(),exact,event.getPartialTick(),player.tickCount));
        }catch(ReflectiveOperationException|RuntimeException error){warn(error);}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void player(RenderPlayerEvent.Pre event){
        if(!(event.getEntity() instanceof AbstractClientPlayer player))return;
        Capture capture=CAPTURES.remove(player);
        int tier=AresWeightedHands.modelTier(player);
        if(tier==0||player.isInvisible()||player==Minecraft.getInstance().player&&AresWeightedHands.selfModelHidden())return;
        if(!event.isCanceled()||Ysm.AVAILABLE&&player!=Minecraft.getInstance().player&&Ysm.hideOthers.get())return;
        try{
            if(!Ysm.AVAILABLE)throw new IllegalStateException("Current YSM pose API unavailable");
            Object data=player.getCapability(Ysm.cap).orElse(null);
            if(data==null)throw new IllegalStateException("Current YSM client capability unavailable");
            Object pose=Ysm.pose.invoke(data);if(pose==null)throw new IllegalStateException("Current YSM Ares pose unavailable");
            // The native preview/inventory mesh already draws the complete body.
            if(guiVisible(pose))return;
            if(capture==null||capture.source()!=event.getPoseStack()||capture.tick()!=player.tickCount||Float.floatToIntBits(capture.partialTick())!=Float.floatToIntBits(event.getPartialTick()))throw new IllegalStateException("Matching current YSM render capture unavailable");
            AresWeightedBodyMesh model=load();if(model==null)throw new IllegalStateException("Weighted Ares body resource unavailable");
            Rig rig=RIGS.get(pose);
            if(rig==null){rig=new Rig(model,(Map<?,?>)Ysm.bones.invoke(pose));RIGS.put(pose,rig);}
            rig.update(model);
            model.skinFromNative(scratch,rig.delta);
            var stack=event.getPoseStack();stack.pushPose();
            try{
                stack.last().pose().set(capture.matrix());
                stack.last().normal().set(capture.matrix()).invert().transpose();
                int overlay=OverlayTexture.pack(0,player.hurtTime>0||player.deathTime>0);
                model.draw(stack,event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(TEXTURES[tier-1])),event.getPackedLight(),overlay,scratch);
            }finally{stack.popPose();}
        }catch(ReflectiveOperationException|RuntimeException error){event.setCanceled(false);warn(error);}
    }

    private static boolean guiVisible(Object pose)throws ReflectiveOperationException{
        Object gui=GUI_BONES.get(pose);
        if(gui==null){
            for(Object bone:((Map<?,?>)Ysm.bones.invoke(pose)).values())if("AresGuiSkin".equals(Ysm.name.invoke(bone))){gui=bone;break;}
            if(gui==null)throw new IllegalStateException("Missing current Ares preview mesh");
            GUI_BONES.put(pose,gui);
        }
        for(Method scale:Ysm.scales)if(Math.abs(((Number)scale.invoke(gui)).floatValue())<1e-6f)return false;
        return true;
    }

    private static void warn(Exception error){if(!warned){warned=true;LogUtils.getLogger().error("Unable to draw current weighted YSM Ares body; restoring vanilla player rendering",error);}}

    private static final class Rig {
        final Object[] bones,prefix;
        final Matrix4f[] delta;
        final Matrix4f root=new Matrix4f();
        final PoseStack local=new PoseStack();
        Rig(AresWeightedBodyMesh model,Map<?,?> all)throws ReflectiveOperationException{
            Map<String,Object> named=new HashMap<>();
            for(Object bone:all.values())named.put((String)Ysm.name.invoke(bone),bone);
            bones=new Object[model.bones];delta=new Matrix4f[model.bones];
            for(int i=0;i<model.bones;i++){bones[i]=required(named,model.names[i]);delta[i]=new Matrix4f();}
            prefix=new Object[]{required(named,"Root"),required(named,"MAllBody"),required(named,"AllBody")};
        }
        private static Object required(Map<String,Object> named,String name){Object value=named.get(name);if(value==null)throw new IllegalStateException("Missing current Ares bone "+name);return value;}
        private void apply(Object bone,Matrix4f out)throws ReflectiveOperationException{
            local.pushPose();
            try{Ysm.transform.invoke(null,local,bone);out.set(local.last().pose());}finally{local.popPose();}
        }
        void update(AresWeightedBodyMesh model)throws ReflectiveOperationException{
            root.identity();
            for(Object bone:prefix){apply(bone,localMatrix);root.mul(localMatrix);}
            for(int i:model.order){apply(bones[i],delta[i]);if(model.parent[i]>=0)delta[i].mulLocal(delta[model.parent[i]]);else delta[i].mulLocal(root);}
        }
        final Matrix4f localMatrix=new Matrix4f();
    }

    /** Reflection keeps the add-on loadable when optional YSM is absent. */
    private static final class Ysm {
        static final boolean AVAILABLE;
        static Capability<Object> cap;
        static Class<?> rendererClass;
        static Field before,outer;
        static Method pose,bones,name,transform,width,height;
        static Method[] scales;
        static ForgeConfigSpec.BooleanValue hideOthers;
        @SuppressWarnings("unchecked") static boolean initialize(){
            if(!ModList.get().isLoaded("yes_steve_model"))return false;
            try{
                String p="com.elfmcys.yesstevemodel.";
                Class<?> provider=Class.forName(p+"O0OooOo0oOOoOoOoOooO000o"),data=Class.forName(p+"o0OOO0o0o0OOo000oO00o00O"),poseType=Class.forName(p+"OOOO0O0O000O000000oOOO0o"),bone=Class.forName(p+"Oo0o00oOOo0OO000000O0oO0"),helper=Class.forName(p+"o000OO00oooOo00000ooo0Oo"),baseRenderer=Class.forName(p+"OOoo0o0oO000ooO0Oo00OoOo");
                rendererClass=Class.forName(p+"o0ooO0o0ooo000OOO0o0oo0o");
                cap=(Capability<Object>)provider.getField("Oo0Oo0o00O00Oo0OOoOOoooo").get(null);
                pose=data.getMethod("OOOoOO000000o0o0oOooo0o0");bones=poseType.getMethod("O00OOOooOoooOoo0o0o0oO0O");name=bone.getMethod("oOOo0Ooo0oOoo0O0OOOOo0oo");transform=helper.getMethod("oo0OoO00oOoo000O0000o0oo",PoseStack.class,bone);
                width=data.getMethod("c_");height=data.getMethod("e_");
                scales=new Method[]{bone.getMethod("oo0OoO00oOoo000O0000o0oo"),bone.getMethod("oooooooOOoOOoO00OooOo00O"),bone.getMethod("Oo00o0OooOOo0ooOoo0oO0o0")};
                hideOthers=(ForgeConfigSpec.BooleanValue)Class.forName(p+"OoOoOOoOOoOo0OooOOoO00Oo").getField("oOOOo0OOO0ooooo0O00OO0o0").get(null);
                before=baseRenderer.getDeclaredField("O00OOOooOoooOoo0o0o0oO0O");outer=baseRenderer.getDeclaredField("oOOOo0OOO0ooooo0O00OO0o0");before.setAccessible(true);outer.setAccessible(true);return true;
            }catch(ReflectiveOperationException|LinkageError error){LogUtils.getLogger().error("Ares weighted body cannot bind YSM 2.6.5 pose API",error);return false;}
        }
        static{AVAILABLE=initialize();}
    }
}
