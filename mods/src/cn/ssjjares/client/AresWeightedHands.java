package cn.ssjjares.client;

import cn.ssjjares.AresArmorItems;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Uses TaCZ's actual RenderArmEvent matrix and source-weighted Ares fingers. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class AresWeightedHands {
    private static final AresHandMesh[] MESH=new AresHandMesh[2];
    private static final AresHandMesh.Scratch[] SCRATCH=new AresHandMesh.Scratch[2];
    private static final boolean[] FAILED=new boolean[2];
    private static final ResourceLocation[] TEXTURE={new ResourceLocation("ssjj_ares","textures/entity/ares_hands1.png"),new ResourceLocation("ssjj_ares","textures/entity/ares_hands2.png")};
    private static final Motion MOTION=new Motion();
    private static final float[] POSE_TIME={Float.NaN,Float.NaN};
    private AresWeightedHands(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->{for(int i=0;i<2;i++){MESH[i]=null;SCRATCH[i]=null;FAILED[i]=false;POSE_TIME[i]=Float.NaN;}MOTION.reset();});}
    }
    private static AresHandMesh load(int i){
        if(MESH[i]!=null||FAILED[i])return MESH[i];
        try{var id=new ResourceLocation("ssjj_ares","models/ares_hands"+(i+1)+".bin");try(var in=Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().open()){MESH[i]=AresHandMesh.read(in);SCRATCH[i]=MESH[i].scratch();}return MESH[i];}
        catch(Exception error){FAILED[i]=true;LogUtils.getLogger().error("Unable to load original Ares viewhands; using YSM fallback",error);return null;}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void arm(RenderArmEvent event){
        int tier=activeTier(event.getPlayer());if(tier==0)return;int i=tier-1;AresHandMesh mesh=load(i);if(mesh==null)return;
        float time=(event.getPlayer().tickCount+Minecraft.getInstance().getFrameTime())/20f;MOTION.update(event.getPlayer(),time);
        if(POSE_TIME[i]!=time){mesh.pose(SCRATCH[i],MOTION.clip,MOTION.progress,MOTION.weight);POSE_TIME[i]=time;}
        var stack=event.getPoseStack();boolean left=event.getArm()==HumanoidArm.LEFT;stack.pushPose();
        try{stack.translate(left?.25:-.25,1.8,0);stack.scale(-1,-1,1);mesh.draw(stack,event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(TEXTURE[i])),event.getPackedLight(),OverlayTexture.NO_OVERLAY,left,SCRATCH[i]);}
        finally{stack.popPose();}event.setCanceled(true);
    }
    /** Shared body/hand gate follows the current, loaded native client model. */
    public static int modelTier(AbstractClientPlayer player){
        int tier=AresArmorItems.setTier(player);if(tier==0||!Ysm.AVAILABLE)return 0;
        try{
            if(!(Boolean)Ysm.available.invoke(null))return 0;
            Object data=player.getCapability(Ysm.cap).orElse(null);if(data==null||!(Boolean)Ysm.enabled.invoke(data)||Ysm.model.invoke(data)==null)return 0;
            String id=(String)Ysm.id.invoke(data);return (tier==1?"ssjj_ares_base":"ssjj_ares_absolute").equals(id)?tier:0;
        }catch(ReflectiveOperationException|RuntimeException error){return 0;}
    }
    public static boolean selfModelHidden(){return Ysm.AVAILABLE&&Ysm.hideModel.get();}
    private static int activeTier(AbstractClientPlayer player){
        int tier=modelTier(player);return tier==0||selfModelHidden()||Ysm.hideHands.get()?0:tier;
    }
    private static final class Ysm {
        static final boolean AVAILABLE;static Capability<Object> cap;static Method available,enabled,model,id;static ForgeConfigSpec.BooleanValue hideModel,hideHands;
        @SuppressWarnings("unchecked") static boolean initialize(){
            if(!ModList.get().isLoaded("yes_steve_model"))return false;
            try{String p="com.elfmcys.yesstevemodel.";Class<?> main=Class.forName(p+"YesSteveModel"),provider=Class.forName(p+"O0OooOo0oOOoOoOoOooO000o"),data=Class.forName(p+"o0OOO0o0o0OOo000oO00o00O"),config=Class.forName(p+"OoOoOOoOOoOo0OooOOoO00Oo");
                available=main.getMethod("isAvailable");cap=(Capability<Object>)provider.getField("Oo0Oo0o00O00Oo0OOoOOoooo").get(null);enabled=data.getMethod("o0ooooOo0o000OOo0oO00OoO");model=data.getMethod("O0ooooOO0oOo000O0Oo00OOO");id=data.getMethod("ooooO0o00oO0Oo0OOo0O0O0o");hideModel=(ForgeConfigSpec.BooleanValue)config.getField("O00OOOooOoooOoo0o0o0oO0O").get(null);hideHands=(ForgeConfigSpec.BooleanValue)config.getField("OOOOo0O0oO0OOo0O0O0Oo0O0").get(null);return true;
            }catch(ReflectiveOperationException|LinkageError error){LogUtils.getLogger().warn("Ares viewhands cannot bind YSM capability",error);return false;}
        }
        static{AVAILABLE=initialize();}
    }
    private static final class Motion {
        AbstractClientPlayer player;float previous=Float.NaN,lastTime,gunStart,reloadStart,reloadDuration,lastReloadCount,attackStart=-100,raiseStart=-100;long shoot;boolean wasGun,reloading;int clip=-1;float progress,weight;
        void reset(){player=null;previous=Float.NaN;weight=0;clip=-1;wasGun=false;reloading=false;shoot=0;attackStart=raiseStart=-100;}
        void update(AbstractClientPlayer p,float time){
            if(player!=p||time<lastTime){reset();player=p;}if(previous==time)return;float dt=Float.isNaN(previous)?.05f:Math.min(.1f,Math.max(0,time-previous));previous=time;lastTime=time;
            boolean gun=IGun.getIGunOrNull(p.getMainHandItem())!=null;
            if(!gun){clip=-1;weight=Math.max(0,weight-dt*10);wasGun=false;reloading=false;return;}
            var op=IGunOperator.fromLivingEntity(p);var state=op.getSynReloadState();boolean reload=state!=null&&state.getStateType().isReloading();
            long cooldown=op.getSynShootCoolDown();if(cooldown>0&&cooldown>shoot)attackStart=time;shoot=cooldown;
            if(!wasGun||op.getSynDrawCoolDown()>0&&time-raiseStart>2)raiseStart=time;wasGun=true;
            if(reload){float remaining=state.getCountDown()/1000f;if(!reloading){reloadStart=time;reloadDuration=Math.max(.2f,remaining);lastReloadCount=remaining;}else if(remaining>lastReloadCount+.15f)reloadDuration=Math.max(reloadDuration,time-reloadStart+remaining);lastReloadCount=remaining;clip=3;progress=Math.min(1,(time-reloadStart)/reloadDuration);}
            else if(time-raiseStart<.667f){clip=1;progress=(time-raiseStart)/.667f;}
            else if(time-attackStart<.234f){clip=2;progress=(time-attackStart)/.234f;}
            else{clip=0;progress=0;}reloading=reload;weight=Math.min(1,weight+dt*12);
        }
    }
}
